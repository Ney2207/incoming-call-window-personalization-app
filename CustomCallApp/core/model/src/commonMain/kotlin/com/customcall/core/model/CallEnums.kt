package com.customcall.core.model

import kotlinx.serialization.Serializable

/**
 * Identifies the telephony or VoIP origin of an incoming call.
 */
@Serializable
enum class CallSource {
    /** Standard cellular network call (GSM / SIM / Telecom). */
    PHONE_GSM,

    /** Standard WhatsApp consumer application (`com.whatsapp`). */
    WHATSAPP,

    /** WhatsApp Business application (`com.whatsapp.w4b`)[cite: 1]. */
    WHATSAPP_BUSINESS,

    /** Telegram messenger application (`org.telegram.messenger`)[cite: 1]. */
    TELEGRAM,

    /** Extensible third-party VoIP notification source configured by the user[cite: 1]. */
    OTHER_VOIP
}

/**
 * Represents the lifecycle state of an intercepted incoming call[cite: 1].
 */
@Serializable
enum class CallState {
    /** Incoming call is actively ringing and awaiting user action[cite: 1]. */
    RINGING,

    /** User accepted the call from the custom screen or native interface[cite: 1]. */
    ANSWERED,

    /** User rejected the call[cite: 1]. */
    DECLINED,

    /** Call terminated, timed out, or its system notification was dismissed[cite: 1]. */
    ENDED
}

/**
 * Selectable interactive control layouts rendered on the incoming call screen[cite: 1].
 */
@Serializable
enum class ButtonStyleType {
    /** Traditional separate circular Answer and Decline buttons[cite: 1]. */
    CLASSIC_DUAL,

    /** Horizontal draggable pill slider for answer/decline gestures[cite: 1]. */
    SWIPE_PILL,

    /** Minimalist edge-to-edge swipe or tap gesture controls[cite: 1]. */
    MINIMAL_GESTURE
}