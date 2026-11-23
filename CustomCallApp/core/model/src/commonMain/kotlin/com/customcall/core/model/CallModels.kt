package com.customcall.core.model

import kotlinx.serialization.Serializable

/**
 * Immutable snapshot representing an intercepted incoming call event[cite: 1].
 *
 * @property callId Unique session identifier generated upon call detection[cite: 1].
 * @property source Originating telephony or VoIP platform[cite: 1].
 * @property callerName Display name extracted from contacts or VoIP notification metadata[cite: 1].
 * @property callerIdentifier Raw phone number (for GSM) or VoIP handle/identifier[cite: 1].
 * @property avatarUri Optional URI string pointing to the caller's profile image[cite: 1].
 * @property hasAnswerAction Whether an executable Answer action/PendingIntent is available[cite: 1].
 * @property hasDeclineAction Whether an executable Decline action/PendingIntent is available[cite: 1].
 * @property timestamp Epoch timestamp in milliseconds when the call arrived[cite: 1].
 */
@Serializable
data class IncomingCallEvent(
    val callId: String,
    val source: CallSource,
    val callerName: String,
    val callerIdentifier: String,
    val avatarUri: String?,
    val hasAnswerAction: Boolean,
    val hasDeclineAction: Boolean,
    val timestamp: Long
)

/**
 * Visual and audio theme configuration applied to the incoming call screen[cite: 1].
 *
 * @property id Unique theme identifier[cite: 1].
 * @property name User-facing label for the theme[cite: 1].
 * @property videoUri Internal storage URI pointing to the processed background video[cite: 1].
 * @property isMuted Whether background video audio is muted (defaults to `true` to avoid ringtone clash)[cite: 1].
 * @property overlayGradientAlpha Opacity (`0.0f` to `1.0f`) of vertical scrim gradients for text contrast[cite: 1].
 * @property buttonStyle Interactive answer/decline control layout[cite: 1].
 * @property accentColorHex ARGB 32-bit color value encoded as a [Long] (e.g., `0xFF00E5FF`)[cite: 1].
 */
@Serializable
data class CallThemeConfig(
    val id: String,
    val name: String,
    val videoUri: String,
    val isMuted: Boolean = true,
    val overlayGradientAlpha: Float = 0.35f,
    val buttonStyle: ButtonStyleType = ButtonStyleType.SWIPE_PILL,
    val accentColorHex: Long
) {
    init {
        require(overlayGradientAlpha in 0.0f..1.0f) {
            "overlayGradientAlpha must be between 0.0f and 1.0f, got $overlayGradientAlpha"
        }
    }
}

/**
 * Binds a specific contact or VoIP caller to a dedicated [CallThemeConfig][cite: 1].
 *
 * @property id Unique rule identifier[cite: 1].
 * @property callerIdentifier Normalized phone number or VoIP identifier for exact matching[cite: 1].
 * @property normalizedName Normalized display name used as a secondary match key for VoIP calls[cite: 1].
 * @property sourceFilter Optional platform filter; `null` applies this rule across all call sources[cite: 1].
 * @property themeId Foreign key referencing [CallThemeConfig.id][cite: 1].
 */
@Serializable
data class ContactRule(
    val id: String,
    val callerIdentifier: String,
    val normalizedName: String,
    val sourceFilter: CallSource?,
    val themeId: String
)

/**
 * Global application preferences persisted in local storage[cite: 1].
 *
 * @property defaultThemeId Fallback [CallThemeConfig.id] used when no [ContactRule] matches (`null` if unconfigured)[cite: 1].
 * @property enabledSources Set of [CallSource] platforms for which custom call interception is active[cite: 1].
 * @property customVoipPackages Additional Android package names monitored as [CallSource.OTHER_VOIP][cite: 1].
 * @property isOnboardingCompleted Indicates whether the initial permission diagnostic wizard has been completed[cite: 1].
 */
@Serializable
data class AppSettings(
    val defaultThemeId: String? = null,
    val enabledSources: Set<CallSource> = CallSource.entries.toSet(),
    val customVoipPackages: Set<String> = emptySet(),
    val isOnboardingCompleted: Boolean = false
)