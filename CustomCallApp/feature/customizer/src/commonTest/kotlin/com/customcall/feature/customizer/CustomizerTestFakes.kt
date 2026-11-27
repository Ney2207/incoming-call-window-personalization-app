package com.customcall.feature.customizer

import com.customcall.core.model.AppSettings
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.ProcessedVideoAsset
import com.customcall.core.model.VideoProcessingRequest
import com.customcall.core.model.VideoProcessingState
import com.customcall.core.model.contract.CallRepository
import com.customcall.core.model.contract.VideoAssetProcessor
import com.customcall.feature.customizer.platform.DiagnosticPermissionType
import com.customcall.feature.customizer.platform.OemAutostartGuide
import com.customcall.feature.customizer.platform.PermissionItemStatus
import com.customcall.feature.customizer.platform.SystemDiagnosticController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

internal class FakeCallRepository : CallRepository {

    private val themesFlow = MutableStateFlow<List<CallThemeConfig>>(emptyList())
    private val rulesFlow = MutableStateFlow<List<ContactRule>>(emptyList())
    private val settingsFlow = MutableStateFlow(AppSettings())

    val deletedThemeIds = mutableListOf<String>()
    val deletedRuleIds = mutableListOf<String>()

    override fun observeThemes(): Flow<List<CallThemeConfig>> = themesFlow

    override fun observeContactRules(): Flow<List<ContactRule>> = rulesFlow

    override fun observeAppSettings(): Flow<AppSettings> = settingsFlow

    override suspend fun getThemeById(themeId: String): CallThemeConfig? =
        themesFlow.value.firstOrNull { it.id == themeId }

    override suspend fun upsertTheme(theme: CallThemeConfig) {
        val updated = themesFlow.value.filterNot { it.id == theme.id } + theme
        themesFlow.value = updated
    }

    override suspend fun deleteTheme(themeId: String) {
        deletedThemeIds.add(themeId)
        themesFlow.value = themesFlow.value.filterNot { it.id == themeId }
        rulesFlow.value = rulesFlow.value.filterNot { it.themeId == themeId }
        if (settingsFlow.value.defaultThemeId == themeId) {
            settingsFlow.value = settingsFlow.value.copy(defaultThemeId = null)
        }
    }

    override suspend fun upsertContactRule(rule: ContactRule) {
        val updated = rulesFlow.value.filterNot { it.id == rule.id } + rule
        rulesFlow.value = updated
    }

    override suspend fun deleteContactRule(ruleId: String) {
        deletedRuleIds.add(ruleId)
        rulesFlow.value = rulesFlow.value.filterNot { it.id == ruleId }
    }

    override suspend fun updateAppSettings(settings: AppSettings) {
        settingsFlow.value = settings
    }

    override suspend fun setDefaultThemeId(themeId: String?) {
        settingsFlow.value = settingsFlow.value.copy(defaultThemeId = themeId)
    }

    override suspend fun resolveThemeForCall(event: IncomingCallEvent): CallThemeConfig {
        val defaultId = settingsFlow.value.defaultThemeId
        return themesFlow.value.firstOrNull { it.id == defaultId }
            ?: CallThemeConfig(
                id = "builtin_default",
                name = "Default",
                videoUri = "",
                isMuted = true,
                overlayGradientAlpha = 0.35f,
                buttonStyle = ButtonStyleType.SWIPE_PILL,
                accentColorHex = 0xFF3B82F6L,
            )
    }
}

internal class FakeVideoAssetProcessor : VideoAssetProcessor {

    enum class Mode {
        AUTO_COMPLETE,
        FAIL,
        MANUAL_STREAM,
    }

    var mode: Mode = Mode.AUTO_COMPLETE
    var failureMessage: String = "Unsupported codec"
    val recordedRequests = mutableListOf<VideoProcessingRequest>()
    val deletedAssetThemeIds = mutableListOf<String>()
    val manualStateFlow = MutableSharedFlow<VideoProcessingState>(replay = 1)

    override fun processVideo(request: VideoProcessingRequest): Flow<VideoProcessingState> {
        recordedRequests.add(request)
        return when (mode) {
            Mode.AUTO_COMPLETE -> flow {
                emit(VideoProcessingState.InProgress(0.5f))
                emit(
                    VideoProcessingState.Completed(
                        ProcessedVideoAsset(
                            themeId = request.themeId,
                            outputUri = "file:///data/user/0/com.customcall/files/themes/${request.themeId}.mp4",
                            durationMs = 5_000L,
                            width = 1080,
                            height = 1920,
                            fileSizeBytes = 1_024_000L,
                        ),
                    ),
                )
            }

            Mode.FAIL -> flow {
                emit(VideoProcessingState.InProgress(0.2f))
                emit(VideoProcessingState.Failed(failureMessage))
            }

            Mode.MANUAL_STREAM -> manualStateFlow
        }
    }

    override suspend fun deleteProcessedAsset(themeId: String): Boolean {
        deletedAssetThemeIds.add(themeId)
        return true
    }

    override suspend fun clearUnusedAssets(activeThemeIds: Set<String>): Int = 0
}

internal class FakeSystemDiagnosticController : SystemDiagnosticController {

    var permissionsResult: List<PermissionItemStatus> = listOf(
        PermissionItemStatus(DiagnosticPermissionType.NOTIFICATION_LISTENER, isGranted = true, isRequiredOnCurrentOs = true),
        PermissionItemStatus(DiagnosticPermissionType.OVERLAY, isGranted = false, isRequiredOnCurrentOs = true),
        PermissionItemStatus(DiagnosticPermissionType.FULL_SCREEN_INTENT, isGranted = false, isRequiredOnCurrentOs = true),
        PermissionItemStatus(DiagnosticPermissionType.BATTERY_OPTIMIZATION, isGranted = false, isRequiredOnCurrentOs = false),
    )

    var guideResult: OemAutostartGuide = OemAutostartGuide(
        manufacturer = "Xiaomi",
        osSkinName = "HyperOS / MIUI",
        steps = listOf("Enable Autostart", "Set Battery to No restrictions"),
        canOpenDirectly = true,
    )

    var canLaunchOemDirectly: Boolean = true
    val openedPermissionTypes = mutableListOf<DiagnosticPermissionType>()
    var oemSettingsLaunchCount: Int = 0
    var appDetailsLaunchCount: Int = 0

    override suspend fun inspectPermissions(): List<PermissionItemStatus> = permissionsResult

    override fun getOemAutostartGuide(): OemAutostartGuide = guideResult

    override fun openPermissionSettings(type: DiagnosticPermissionType) {
        openedPermissionTypes.add(type)
    }

    override fun openOemAutostartSettings(): Boolean {
        oemSettingsLaunchCount += 1
        return canLaunchOemDirectly
    }

    override fun openAppDetailsSettings() {
        appDetailsLaunchCount += 1
    }
}