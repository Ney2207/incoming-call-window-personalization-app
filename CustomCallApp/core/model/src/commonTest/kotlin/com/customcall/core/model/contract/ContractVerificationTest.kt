package com.customcall.core.model.contract

import com.customcall.core.model.AppSettings
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallState
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.ProcessedVideoAsset
import com.customcall.core.model.VideoProcessingRequest
import com.customcall.core.model.VideoProcessingState
import com.customcall.core.model.util.IdentifierNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class ContractVerificationTest {

    @Test
    fun callRepositoryContractResolvesContactRuleAndFallbackTheme() = runTest {
        val fallbackTheme = CallThemeConfig(
            id = "default",
            name = "Default Waves",
            videoUri = "file:///themes/default.mp4",
            accentColorHex = 0xFF6200EE
        )
        val vipTheme = CallThemeConfig(
            id = "vip",
            name = "Golden Flare",
            videoUri = "file:///themes/vip.mp4",
            accentColorHex = 0xFFFFD600
        )

        val repo = InMemoryCallRepository(fallbackTheme)
        repo.upsertTheme(fallbackTheme)
        repo.upsertTheme(vipTheme)
        repo.setDefaultThemeId(fallbackTheme.id)
        repo.upsertContactRule(
            ContactRule(
                id = "rule-vip",
                callerIdentifier = "+15559990000",
                normalizedName = "vip caller",
                sourceFilter = null,
                themeId = vipTheme.id
            )
        )

        val matchingEvent = IncomingCallEvent(
            callId = "c-1",
            source = CallSource.PHONE_GSM,
            callerName = "VIP Caller",
            callerIdentifier = "+1 (555) 999-0000",
            avatarUri = null,
            hasAnswerAction = true,
            hasDeclineAction = true,
            timestamp = 1_000L
        )
        assertEquals(vipTheme, repo.resolveThemeForCall(matchingEvent))

        val unknownEvent = matchingEvent.copy(
            callId = "c-2",
            callerName = "Unknown Number",
            callerIdentifier = "+15550001111"
        )
        assertEquals(fallbackTheme, repo.resolveThemeForCall(unknownEvent))
    }

    @Test
    fun callEventDispatcherAndActionExecutorCoordinateCallLifecycle() = runTest {
        val dispatcher = InMemoryCallEventDispatcher()
        val executor = FakeCallActionExecutor(dispatcher)

        assertNull(dispatcher.activeCallEvent.value)
        assertEquals(CallState.ENDED, dispatcher.callState.value)

        val incoming = IncomingCallEvent(
            callId = "call-77",
            source = CallSource.WHATSAPP,
            callerName = "Elena",
            callerIdentifier = "elena_wa",
            avatarUri = null,
            hasAnswerAction = true,
            hasDeclineAction = true,
            timestamp = 2_000L
        )

        dispatcher.dispatchIncomingCall(incoming)
        assertEquals(incoming, dispatcher.activeCallEvent.value)
        assertEquals(CallState.RINGING, dispatcher.callState.value)

        assertTrue(executor.answerCall("call-77"))
        assertEquals(CallState.ANSWERED, dispatcher.callState.value)

        assertFalse(executor.declineCall("non-existent-id"))

        dispatcher.clearActiveCall("call-77")
        assertNull(dispatcher.activeCallEvent.value)
        assertEquals(CallState.ENDED, dispatcher.callState.value)
    }

    @Test
    fun videoAssetProcessorContractEmitsProgressAndTerminalState() = runTest {
        val processor = FakeVideoAssetProcessor()
        val emissions = processor.processVideo(
            VideoProcessingRequest(
                themeId = "theme-1",
                sourceUri = "content://media/video/1"
            )
        ).toList()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is VideoProcessingState.InProgress)
        val completed = emissions[1] as VideoProcessingState.Completed
        assertEquals("theme-1", completed.asset.themeId)

        assertTrue(processor.deleteProcessedAsset("theme-1"))
        assertFalse(processor.deleteProcessedAsset("theme-1"))
    }

    private class InMemoryCallRepository(
        private val builtInTheme: CallThemeConfig
    ) : CallRepository {
        private val themes = MutableStateFlow>(emptyMap())
        private val rules = MutableStateFlow>(emptyMap())
        private val settings = MutableStateFlow(AppSettings())

        override fun observeThemes(): Flow<List<CallThemeConfig>> =
            MutableStateFlow(themes.value.values.toList())

        override suspend fun getThemeById(themeId: String): CallThemeConfig? = themes.value[themeId]

        override suspend fun upsertTheme(theme: CallThemeConfig) {
            themes.value = themes.value + (theme.id to theme)
        }

        override suspend fun deleteTheme(themeId: String) {
            themes.value = themes.value - themeId
        }

        override fun observeContactRules(): Flow<List<ContactRule>> =
            MutableStateFlow(rules.value.values.toList())

        override suspend fun upsertContactRule(rule: ContactRule) {
            rules.value = rules.value + (rule.id to rule)
        }

        override suspend fun deleteContactRule(ruleId: String) {
            rules.value = rules.value - ruleId
        }

        override fun observeAppSettings(): Flow<AppSettings> = settings.asStateFlow()

        override suspend fun updateAppSettings(settings: AppSettings) {
            this.settings.value = settings
        }

        override suspend fun setDefaultThemeId(themeId: String?) {
            settings.value = settings.value.copy(defaultThemeId = themeId)
        }

        override suspend fun resolveThemeForCall(event: IncomingCallEvent): CallThemeConfig {
            val normalizedId = IdentifierNormalizer.normalizeIdentifier(event.callerIdentifier)
            val normalizedName = IdentifierNormalizer.normalizeDisplayName(event.callerName)

            val matchedRule = rules.value.values.firstOrNull { rule ->
                val sourceMatches = rule.sourceFilter == null || rule.sourceFilter == event.source
                val idMatches = normalizedId.isNotEmpty() && rule.callerIdentifier == normalizedId
                val nameMatches = normalizedName.isNotEmpty() && rule.normalizedName == normalizedName
                sourceMatches && (idMatches || nameMatches)
            }

            val resolvedId = matchedRule?.themeId ?: settings.value.defaultThemeId
            return resolvedId?.let { themes.value[it] } ?: builtInTheme
        }
    }

    private class InMemoryCallEventDispatcher : CallEventDispatcher {
        private val _activeCallEvent = MutableStateFlow<IncomingCallEvent?>(null)
        private val _callState = MutableStateFlow(CallState.ENDED)

        override val activeCallEvent: StateFlow<IncomingCallEvent?> = _activeCallEvent.asStateFlow()
        override val callState: StateFlow<CallState> = _callState.asStateFlow()

        override suspend fun dispatchIncomingCall(event: IncomingCallEvent) {
            _activeCallEvent.value = event
            _callState.value = CallState.RINGING
        }

        override suspend fun updateCallState(callId: String, state: CallState) {
            if (_activeCallEvent.value?.callId == callId) {
                _callState.value = state
            }
        }

        override suspend fun clearActiveCall(callId: String) {
            if (_activeCallEvent.value?.callId == callId) {
                _activeCallEvent.value = null
                _callState.value = CallState.ENDED
            }
        }
    }

    private class FakeCallActionExecutor(
        private val dispatcher: CallEventDispatcher
    ) : CallActionExecutor {
        override suspend fun answerCall(callId: String): Boolean {
            if (dispatcher.activeCallEvent.value?.callId != callId) return false
            dispatcher.updateCallState(callId, CallState.ANSWERED)
            return true
        }

        override suspend fun declineCall(callId: String): Boolean {
            if (dispatcher.activeCallEvent.value?.callId != callId) return false
            dispatcher.updateCallState(callId, CallState.DECLINED)
            return true
        }
    }

    private class FakeVideoAssetProcessor : VideoAssetProcessor {
        private val storedIds = mutableSetOf<String>()

        override fun processVideo(request: VideoProcessingRequest): Flow<VideoProcessingState> {
            storedIds.add(request.themeId)
            return flowOf(
                VideoProcessingState.InProgress(0.5f),
                VideoProcessingState.Completed(
                    ProcessedVideoAsset(
                        themeId = request.themeId,
                        outputUri = "file:///themes/${request.themeId}.mp4",
                        durationMs = 10_000L,
                        width = 1080,
                        height = 1920,
                        fileSizeBytes = 1_024_000L
                    )
                )
            )
        }

        override suspend fun deleteProcessedAsset(themeId: String): Boolean =
            storedIds.remove(themeId)

        override suspend fun clearUnusedAssets(activeThemeIds: Set<String>): Int {
            val orphans = storedIds - activeThemeIds
            storedIds.removeAll(orphans)
            return orphans.size
        }
    }
}