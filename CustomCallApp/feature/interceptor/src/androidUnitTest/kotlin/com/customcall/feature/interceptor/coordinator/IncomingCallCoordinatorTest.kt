package com.customcall.feature.interceptor.coordinator

import com.customcall.core.model.AppSettings
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallState
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.contract.CallRepository
import com.customcall.feature.interceptor.session.CallSessionRegistry
import com.customcall.feature.interceptor.session.DefaultCallEventDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class IncomingCallCoordinatorTest {

    private class FakeCallRepository(
        private val contactTheme: CallThemeConfig,
        private val fallbackTheme: CallThemeConfig,
        private val matchingIdentifier: String,
    ) : CallRepository {
        override fun observeThemes(): Flow<List<CallThemeConfig>> =
            MutableStateFlow(listOf(contactTheme, fallbackTheme))

        override suspend fun getThemeById(themeId: String): CallThemeConfig? =
            listOf(contactTheme, fallbackTheme).firstOrNull { it.id == themeId }

        override suspend fun upsertTheme(theme: CallThemeConfig) = Unit
        override suspend fun deleteTheme(themeId: String) = Unit
        override fun observeContactRules(): Flow<List<ContactRule>> = MutableStateFlow(emptyList())
        override suspend fun upsertContactRule(rule: ContactRule) = Unit
        override suspend fun deleteContactRule(ruleId: String) = Unit
        override fun observeAppSettings(): Flow<AppSettings> = MutableStateFlow(AppSettings())
        override suspend fun updateAppSettings(settings: AppSettings) = Unit
        override suspend fun setDefaultThemeId(themeId: String?) = Unit

        override suspend fun resolveThemeForCall(event: IncomingCallEvent): CallThemeConfig {
            return if (event.callerIdentifier == matchingIdentifier) contactTheme else fallbackTheme
        }
    }

    @Test
    fun `dispatcher and session registry coordinate theme pre-resolution and call lifecycle`() = runTest {
        val registry = CallSessionRegistry()
        val dispatcher = DefaultCallEventDispatcher(registry)

        val vipTheme = CallThemeConfig(
            id = "theme_vip",
            name = "VIP Theme",
            videoUri = "file:///themes/vip.mp4",
            isMuted = false,
            overlayGradientAlpha = 0.25f,
            buttonStyle = ButtonStyleType.CLASSIC_DUAL,
            accentColorHex = 0xFFFF4081L,
        )
        val defaultTheme = CallThemeConfig(
            id = "theme_default",
            name = "Default Theme",
            videoUri = "file:///themes/default.mp4",
            isMuted = true,
            overlayGradientAlpha = 0.35f,
            buttonStyle = ButtonStyleType.SWIPE_PILL,
            accentColorHex = 0xFF4CAF50L,
        )
        val repository = FakeCallRepository(
            contactTheme = vipTheme,
            fallbackTheme = defaultTheme,
            matchingIdentifier = "+584149998877",
        )

        val incomingEvent = IncomingCallEvent(
            callId = "gsm_vip_1",
            source = CallSource.PHONE_GSM,
            callerName = "Elena",
            callerIdentifier = "+584149998877",
            avatarUri = null,
            hasAnswerAction = true,
            hasDeclineAction = true,
            timestamp = 1_700_000_100_000L,
        )

        dispatcher.dispatchIncomingCall(incomingEvent)
        assertEquals(CallState.RINGING, dispatcher.callState.value)
        assertEquals(incomingEvent, dispatcher.activeCallEvent.value)

        // Simulate pre-presentation theme resolution cached prior to UI launch
        val resolved = repository.resolveThemeForCall(incomingEvent)
        registry.attachResolvedTheme(incomingEvent.callId, resolved)
        assertEquals(vipTheme, registry.getCurrentSession()?.resolvedTheme)

        // Non-matching callId state updates are ignored
        dispatcher.updateCallState("wrong_id", CallState.DECLINED)
        assertEquals(CallState.RINGING, dispatcher.callState.value)

        // Matching callId transitions cleanly to ENDED and clears active session
        dispatcher.updateCallState(incomingEvent.callId, CallState.ENDED)
        assertEquals(CallState.ENDED, dispatcher.callState.value)

        dispatcher.clearActiveCall(incomingEvent.callId)
        assertNull(dispatcher.activeCallEvent.value)
        assertNull(registry.getCurrentSession())
    }
}