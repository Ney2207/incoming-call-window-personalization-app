package com.customcall.feature.interceptor.voip

import com.customcall.core.model.CallSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoipNotificationParserTest {

    @Test
    fun `resolveCallSource maps built-in WhatsApp and Telegram packages accurately`() {
        assertEquals(
            CallSource.WHATSAPP,
            VoipNotificationParser.resolveCallSource("com.whatsapp"),
        )
        assertEquals(
            CallSource.WHATSAPP_BUSINESS,
            VoipNotificationParser.resolveCallSource("com.whatsapp.w4b"),
        )
        assertEquals(
            CallSource.TELEGRAM,
            VoipNotificationParser.resolveCallSource("org.telegram.messenger"),
        )
        assertEquals(
            CallSource.TELEGRAM,
            VoipNotificationParser.resolveCallSource("org.telegram.messenger.web"),
        )
    }

    @Test
    fun `resolveCallSource maps configured customVoipPackages to OTHER_VOIP and ignores unknown apps`() {
        val customPackages = setOf("com.discord", "com.viber.voip")

        assertEquals(
            CallSource.OTHER_VOIP,
            VoipNotificationParser.resolveCallSource("com.discord", customPackages),
        )
        assertEquals(
            CallSource.OTHER_VOIP,
            VoipNotificationParser.resolveCallSource("com.viber.voip", customPackages),
        )
        assertNull(
            VoipNotificationParser.resolveCallSource("com.android.chrome", customPackages),
        )
    }
}