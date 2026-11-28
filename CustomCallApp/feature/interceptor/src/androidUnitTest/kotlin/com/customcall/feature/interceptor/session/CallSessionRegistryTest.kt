package com.customcall.feature.interceptor.session

import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallState
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.IncomingCallEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CallSessionRegistryTest {

    private fun sampleEvent(
        callId: String = "call_1",
        callerName: String = "Alice",
        source: CallSource = CallSource.WHATSAPP,
    ) = IncomingCallEvent(
        callId = callId,
        source = source,
        callerName = callerName,
        callerIdentifier = "+584121234567",
        avatarUri = null,
        hasAnswerAction = true,
        hasDeclineAction = true,
        timestamp = 1_700_000_000_000L,
    )

    private fun sampleTheme(id: String = "theme_anime") = CallThemeConfig(
        id = id,
        name = "Cyberpunk Loop",
        videoUri = "file:///themes/cyberpunk.mp4",
        isMuted = true,
        overlayGradientAlpha = 0.4f,
        buttonStyle = ButtonStyleType.SWIPE_PILL,
        accentColorHex = 0xFF00E5FFL,
    )

    @Test
    fun `registerOrUpdateIncomingCall creates new session and deduplicates ringing updates`() = runTest {
        val registry = CallSessionRegistry()
        val initialEvent = sampleEvent(callId = "voip_1", callerName = "Alice")

        val created = registry.registerOrUpdateIncomingCall(
            event = initialEvent,
            nativeHandle = "handle_v1",
            notificationKey = "0|com.whatsapp|1|null|1001",
        )
        assertTrue(created)
        assertEquals(CallState.RINGING, registry.getCurrentSession()?.state)

        // Attach resolved theme before duplicate notification update arrives
        val theme = sampleTheme()
        assertTrue(registry.attachResolvedTheme("voip_1", theme))

        // Duplicate update matching notificationKey refreshes handle without losing resolvedTheme
        val duplicateCreated = registry.registerOrUpdateIncomingCall(
            event = initialEvent.copy(callerName = "Alice (WhatsApp)"),
            nativeHandle = "handle_v2",
            notificationKey = "0|com.whatsapp|1|null|1001",
        )
        assertFalse(duplicateCreated)

        val updatedSession = registry.getCurrentSession()
        assertNotNull(updatedSession)
        assertEquals("Alice (WhatsApp)", updatedSession.event.callerName)
        assertEquals("handle_v2", updatedSession.nativeHandle)
        assertEquals(theme, updatedSession.resolvedTheme)
    }

    @Test
    fun `active answered session is not clobbered by secondary incoming call`() = runTest {
        val registry = CallSessionRegistry()
        registry.registerOrUpdateIncomingCall(sampleEvent(callId = "call_active"))
        assertTrue(registry.updateState("call_active", CallState.ANSWERED))

        val secondCreated = registry.registerOrUpdateIncomingCall(
            sampleEvent(callId = "call_secondary", source = CallSource.TELEGRAM),
        )
        assertFalse(secondCreated)
        assertEquals("call_active", registry.getCurrentSession()?.event?.callId)
        assertEquals(CallState.ANSWERED, registry.getCurrentSession()?.state)
    }

    @Test
    fun `terminal states immediately release nativeHandle references`() = runTest {
        val registry = CallSessionRegistry()
        registry.registerOrUpdateIncomingCall(
            event = sampleEvent(callId = "call_term"),
            nativeHandle = Any(),
            notificationKey = "notif_key_1",
        )
        assertNotNull(registry.getCurrentSession()?.nativeHandle)

        assertTrue(registry.updateState("call_term", CallState.ENDED))
        val sessionAfterEnd = registry.getCurrentSession()
        assertNotNull(sessionAfterEnd)
        assertEquals(CallState.ENDED, sessionAfterEnd.state)
        assertNull(sessionAfterEnd.nativeHandle)

        assertEquals("call_term", registry.findCallIdByNotificationKey("notif_key_1"))
        assertTrue(registry.clearSession("call_term"))
        assertNull(registry.getCurrentSession())
        assertNull(registry.findCallIdByNotificationKey("notif_key_1"))
    }
}