package com.customcall.feature.callscreen.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallState
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.IncomingCallEvent
import com.customcall.feature.callscreen.IncomingCallScreen

private val PreviewWhatsAppEvent = IncomingCallEvent(
    callId = "preview-wa-001",
    source = CallSource.WHATSAPP,
    callerName = "Elena Vance",
    callerIdentifier = "+1 (555) 234-8910",
    avatarUri = null,
    hasAnswerAction = true,
    hasDeclineAction = true,
    timestamp = 1_735_689_600_000L,
)

private val PreviewGsmEvent = IncomingCallEvent(
    callId = "preview-gsm-002",
    source = CallSource.PHONE_GSM,
    callerName = "Marcus Thorne",
    callerIdentifier = "+58 412-555-0194",
    avatarUri = null,
    hasAnswerAction = true,
    hasDeclineAction = true,
    timestamp = 1_735_689_660_000L,
)

private val PreviewTelegramEvent = IncomingCallEvent(
    callId = "preview-tg-003",
    source = CallSource.TELEGRAM,
    callerName = "Aoi Takahashi",
    callerIdentifier = "@aoi_takahashi",
    avatarUri = null,
    hasAnswerAction = true,
    hasDeclineAction = true,
    timestamp = 1_735_689_720_000L,
)

private val PreviewBusinessSingleActionEvent = IncomingCallEvent(
    callId = "preview-wab-004",
    source = CallSource.WHATSAPP_BUSINESS,
    callerName = "Studio Dispatch",
    callerIdentifier = "+1 (555) 901-4422",
    avatarUri = null,
    hasAnswerAction = true,
    hasDeclineAction = false,
    timestamp = 1_735_689_780_000L,
)

/**
 * Full-screen preview exercising `ButtonStyleType.SWIPE_PILL` with a WhatsApp call event.
 * Uses `videoUri = ""` so Android Studio layoutlib skips hardware `MediaCodec` initialization.
 */
@Preview(
    name = "1. Swipe Pill - WhatsApp",
    showBackground = true,
    backgroundColor = 0xFF090C12,
    widthDp = 412,
    heightDp = 915,
)
@Composable
private fun IncomingCallSwipePillWhatsAppPreview() {
    IncomingCallScreen(
        event = PreviewWhatsAppEvent,
        theme = CallThemeConfig(
            id = "preview-theme-swipe",
            name = "Emerald Pulse",
            videoUri = "",
            isMuted = true,
            overlayGradientAlpha = 0.35f,
            buttonStyle = ButtonStyleType.SWIPE_PILL,
            accentColorHex = 0xFF22C55E,
        ),
        onAnswer = {},
        onDecline = {},
        callState = CallState.RINGING,
        isVideoPlaying = false,
    )
}

/**
 * Full-screen preview exercising `ButtonStyleType.CLASSIC_DUAL` with a GSM cellular call event.
 */
@Preview(
    name = "2. Classic Dual - GSM Phone",
    showBackground = true,
    backgroundColor = 0xFF090C12,
    widthDp = 412,
    heightDp = 915,
)
@Composable
private fun IncomingCallClassicDualGsmPreview() {
    IncomingCallScreen(
        event = PreviewGsmEvent,
        theme = CallThemeConfig(
            id = "preview-theme-classic",
            name = "Cyber Cyan",
            videoUri = "",
            isMuted = true,
            overlayGradientAlpha = 0.50f,
            buttonStyle = ButtonStyleType.CLASSIC_DUAL,
            accentColorHex = 0xFF38BDF8,
        ),
        onAnswer = {},
        onDecline = {},
        callState = CallState.RINGING,
        isVideoPlaying = false,
    )
}

/**
 * Full-screen preview exercising `ButtonStyleType.MINIMAL_GESTURE`, a Telegram call event,
 * and a high-luminance amber accent (`0xFFFACC15`) validating dark foreground contrast.
 */
@Preview(
    name = "3. Minimal Gesture - Telegram (Light Accent)",
    showBackground = true,
    backgroundColor = 0xFF090C12,
    widthDp = 412,
    heightDp = 915,
)
@Composable
private fun IncomingCallMinimalGestureTelegramPreview() {
    IncomingCallScreen(
        event = PreviewTelegramEvent,
        theme = CallThemeConfig(
            id = "preview-theme-minimal",
            name = "Solar Amber",
            videoUri = "",
            isMuted = true,
            overlayGradientAlpha = 0.28f,
            buttonStyle = ButtonStyleType.MINIMAL_GESTURE,
            accentColorHex = 0xFFFACC15,
        ),
        onAnswer = {},
        onDecline = {},
        callState = CallState.RINGING,
        isVideoPlaying = false,
    )
}

/**
 * Compact embedded preview validating proportional spacing and `applySystemBarInsets = false`
 * for Block 5's Theme Studio preview card.
 */
@Preview(
    name = "4. Embedded Theme Studio Card (Compact)",
    showBackground = true,
    backgroundColor = 0xFF090C12,
    widthDp = 240,
    heightDp = 480,
)
@Composable
private fun IncomingCallCompactEmbeddedPreview() {
    IncomingCallScreen(
        event = PreviewWhatsAppEvent,
        theme = CallThemeConfig(
            id = "preview-theme-compact",
            name = "Violet Neon",
            videoUri = "",
            isMuted = true,
            overlayGradientAlpha = 0.40f,
            buttonStyle = ButtonStyleType.SWIPE_PILL,
            accentColorHex = 0xFFA855F7,
        ),
        onAnswer = {},
        onDecline = {},
        callState = CallState.RINGING,
        isVideoPlaying = false,
        applySystemBarInsets = false,
    )
}

/**
 * Edge-case preview verifying capability-gated controls when `hasDeclineAction = false`.
 */
@Preview(
    name = "5. Answer-Only Capability (WhatsApp Business)",
    showBackground = true,
    backgroundColor = 0xFF090C12,
    widthDp = 412,
    heightDp = 915,
)
@Composable
private fun IncomingCallSingleActionOnlyPreview() {
    IncomingCallScreen(
        event = PreviewBusinessSingleActionEvent,
        theme = CallThemeConfig(
            id = "preview-theme-single",
            name = "Teal Wave",
            videoUri = "",
            isMuted = true,
            overlayGradientAlpha = 0.35f,
            buttonStyle = ButtonStyleType.SWIPE_PILL,
            accentColorHex = 0xFF14B8A6,
        ),
        onAnswer = {},
        onDecline = {},
        callState = CallState.RINGING,
        isVideoPlaying = false,
    )
}