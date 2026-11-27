package com.customcall.feature.customizer.presentation

import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.core.model.IncomingCallEvent
import com.customcall.feature.customizer.platform.OemAutostartGuide
import com.customcall.feature.customizer.platform.PermissionItemStatus
import kotlin.random.Random

/**
 * Trim bounds in milliseconds for video ingestion via [com.customcall.core.model.contract.VideoAssetProcessor].
 */
data class VideoTrimRange(
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val totalDurationMs: Long = 0L,
)

/**
 * Lifecycle of a background Media3 / AVAssetExportSession transcoding job.
 */
sealed interface TranscodingStatus {
    data object Idle : TranscodingStatus
    data class InProgress(val progress: Float) : TranscodingStatus
    data class Error(val message: String) : TranscodingStatus
}

/**
 * Reactive UI state for the Theme Studio gallery and live theme editor.
 */
data class ThemeStudioUiState(
    val savedThemes: List<CallThemeConfig> = emptyList(),
    val defaultThemeId: String? = null,
    val draftTheme: CallThemeConfig = CustomizerDefaults.createBlankTheme(),
    val isEditingExisting: Boolean = false,
    val pendingSourceUri: String? = null,
    val pendingVideoDisplayName: String? = null,
    val trimRange: VideoTrimRange = VideoTrimRange(),
    val transcodingStatus: TranscodingStatus = TranscodingStatus.Idle,
    val previewCallerName: String = "Alex Rivera",
    val previewCallerIdentifier: String = "+1 (555) 019-2834",
    val previewCallSource: CallSource = CallSource.WHATSAPP,
    val statusMessage: String? = null,
) {
    val isProcessing: Boolean
        get() = transcodingStatus is TranscodingStatus.InProgress

    val previewVideoUri: String
        get() = pendingSourceUri?.takeIf { it.isNotBlank() } ?: draftTheme.videoUri

    val hasVideoSource: Boolean
        get() = previewVideoUri.isNotBlank()
}

/**
 * Joined display model pairing a [ContactRule] with its resolved [CallThemeConfig].
 */
data class ContactRuleItemUi(
    val rule: ContactRule,
    val assignedTheme: CallThemeConfig?,
)

/**
 * Modal/sheet state for creating or editing a [ContactRule].
 */
data class ContactRuleEditorState(
    val isVisible: Boolean = false,
    val editingRuleId: String? = null,
    val displayName: String = "",
    val identifierInput: String = "",
    val sourceFilter: CallSource? = null,
    val selectedThemeId: String = "",
    val validationError: String? = null,
)

/**
 * Reactive UI state for the Contact Rule Manager screen.
 */
data class ContactRulesUiState(
    val rules: List<ContactRuleItemUi> = emptyList(),
    val availableThemes: List<CallThemeConfig> = emptyList(),
    val searchQuery: String = "",
    val editor: ContactRuleEditorState = ContactRuleEditorState(),
    val statusMessage: String? = null,
) {
    val filteredRules: List<ContactRuleItemUi>
        get() {
            val query = searchQuery.trim().lowercase()
            if (query.isEmpty()) return rules
            return rules.filter { item ->
                item.rule.normalizedName.contains(query) ||
                    item.rule.callerIdentifier.lowercase().contains(query) ||
                    (item.assignedTheme?.name?.lowercase()?.contains(query) == true)
            }
        }
}

/**
 * Reactive UI state for the OEM Permission & Survival Diagnostic Wizard.
 */
data class PermissionWizardUiState(
    val permissions: List<PermissionItemStatus> = emptyList(),
    val oemGuide: OemAutostartGuide? = null,
    val isOnboardingCompleted: Boolean = false,
    val isInspecting: Boolean = false,
    val statusMessage: String? = null,
) {
    val allRequiredGranted: Boolean
        get() = permissions.filter { it.isRequiredOnCurrentOs }.all { it.isGranted }

    val grantedCount: Int
        get() = permissions.count { it.isGranted }
}

/**
 * Curated defaults and pure-Kotlin helpers for Theme Studio previews and identifiers.
 */
object CustomizerDefaults {

    val ACCENT_PALETTE: List<Long> = listOf(
        0xFF3B82F6L, // Cobalt Blue
        0xFF10B981L, // Emerald Green
        0xFFF43F5EL, // Crimson Rose
        0xFFF59E0BL, // Amber Gold
        0xFF8B5CF6L, // Electric Violet
        0xFF06B6D4L, // Cyan Pulse
        0xFFEC4899L, // Neon Magenta
        0xFFE2E8F0L, // Frosted Silver
    )

    fun generateId(prefix: String): String {
        val randomPart = Random.nextLong().toULong().toString(16).padStart(16, '0')
        return "${prefix}_$randomPart"
    }

    fun createBlankTheme(
        id: String = generateId("theme"),
        name: String = "",
    ): CallThemeConfig = CallThemeConfig(
        id = id,
        name = name,
        videoUri = "",
        isMuted = true,
        overlayGradientAlpha = 0.35f,
        buttonStyle = ButtonStyleType.SWIPE_PILL,
        accentColorHex = ACCENT_PALETTE.first(),
    )

    fun buildPreviewCallEvent(
        callerName: String,
        callerIdentifier: String,
        source: CallSource,
    ): IncomingCallEvent = IncomingCallEvent(
        callId = "preview_call_session",
        source = source,
        callerName = callerName.ifBlank { "Incoming Caller" },
        callerIdentifier = callerIdentifier.ifBlank { "+1 (555) 010-0000" },
        avatarUri = null,
        hasAnswerAction = true,
        hasDeclineAction = true,
        timestamp = 0L,
    )
}