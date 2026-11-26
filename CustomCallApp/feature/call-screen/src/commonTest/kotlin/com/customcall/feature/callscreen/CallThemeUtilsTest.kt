package com.customcall.feature.callscreen

import androidx.compose.ui.graphics.Color
import com.customcall.feature.callscreen.components.controls.SingleShotActionGuard
import com.customcall.feature.callscreen.components.extractInitials
import com.customcall.feature.callscreen.theme.CallSemanticColors
import com.customcall.feature.callscreen.theme.accessibleOnAccentColor
import com.customcall.feature.callscreen.theme.normalizeScrimAlpha
import com.customcall.feature.callscreen.theme.toComposeColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallThemeUtilsTest {

    @Test
    fun `toComposeColor preserves explicit alpha and defaults 24-bit RGB to opaque`() {
        assertEquals(Color(0xFF22C55E), 0xFF22C55EL.toComposeColor())
        assertEquals(Color(0xFF22C55E), 0x0022C55EL.toComposeColor())
        assertEquals(Color(0x8022C55E), 0x8022C55EL.toComposeColor())
    }

    @Test
    fun `accessibleOnAccentColor selects dark foreground for bright accents and white for dark accents`() {
        assertEquals(CallSemanticColors.DarkOnLightAccent, Color.White.accessibleOnAccentColor())
        assertEquals(CallSemanticColors.DarkOnLightAccent, Color(0xFFFACC15).accessibleOnAccentColor())
        assertEquals(Color.White, Color(0xFF090C12).accessibleOnAccentColor())
        assertEquals(Color.White, Color(0xFF1D4ED8).accessibleOnAccentColor())
    }

    @Test
    fun `normalizeScrimAlpha clamps out-of-bounds values and recovers from NaN`() {
        assertEquals(0.35f, normalizeScrimAlpha(0.35f))
        assertEquals(0.0f, normalizeScrimAlpha(-0.5f))
        assertEquals(1.0f, normalizeScrimAlpha(1.8f))
        assertEquals(0.35f, normalizeScrimAlpha(Float.NaN))
        assertEquals(0.50f, normalizeScrimAlpha(Float.NaN, fallback = 0.50f))
    }

    @Test
    fun `extractInitials handles multi-word names, single names, identifiers, and empty inputs`() {
        assertEquals("EV", extractInitials("Elena Vance"))
        assertEquals("MT", extractInitials("  marcus   middle   thorne  "))
        assertEquals("AO", extractInitials("Aoi"))
        assertEquals("X", extractInitials("X"))
        assertEquals("15", extractInitials("   ", "+1 (555) 234-8910"))
        assertEquals("AO", extractInitials("🎉✨", "@aoi_takahashi"))
        assertEquals("?", extractInitials("", ""))
    }

    @Test
    fun `SingleShotActionGuard fires callback at most once until reset`() {
        val guard = SingleShotActionGuard()
        var triggerCount = 0

        assertFalse(guard.isTriggered)

        val firstAccepted = guard.tryFire { triggerCount++ }
        assertTrue(firstAccepted)
        assertTrue(guard.isTriggered)
        assertEquals(1, triggerCount)

        val secondAccepted = guard.tryFire { triggerCount++ }
        assertFalse(secondAccepted)
        assertEquals(1, triggerCount)

        guard.reset()
        assertFalse(guard.isTriggered)

        val afterResetAccepted = guard.tryFire { triggerCount++ }
        assertTrue(afterResetAccepted)
        assertEquals(2, triggerCount)
    }
}