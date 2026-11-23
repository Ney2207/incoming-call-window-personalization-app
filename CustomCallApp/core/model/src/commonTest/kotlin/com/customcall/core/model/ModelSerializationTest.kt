package com.customcall.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModelSerializationTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @Test
    fun enumsRoundTripSerialization() {
        CallSource.entries.forEach { source ->
            val encoded = json.encodeToString(source)
            assertEquals(source, json.decodeFromString<CallSource>(encoded))
        }

        CallState.entries.forEach { state ->
            val encoded = json.encodeToString(state)
            assertEquals(state, json.decodeFromString<CallState>(encoded))
        }

        ButtonStyleType.entries.forEach { style ->
            val encoded = json.encodeToString(style)
            assertEquals(style, json.decodeFromString<ButtonStyleType>(encoded))
        }
    }

    @Test
    fun incomingCallEventRoundTripAndNullableFields() {
        val withAvatar = IncomingCallEvent(
            callId = "call-101",
            source = CallSource.WHATSAPP,
            callerName = "Alice Mercer",
            callerIdentifier = "+15551234567",
            avatarUri = "content://contacts/avatar/12",
            hasAnswerAction = true,
            hasDeclineAction = true,
            timestamp = 1_760_000_000_000L
        )

        val decodedWithAvatar = json.decodeFromString<IncomingCallEvent>(json.encodeToString(withAvatar))
        assertEquals(withAvatar, decodedWithAvatar)

        val withoutAvatar = withAvatar.copy(
            callId = "call-102",
            source = CallSource.TELEGRAM,
            avatarUri = null,
            hasAnswerAction = false
        )
        val decodedWithoutAvatar = json.decodeFromString<IncomingCallEvent>(json.encodeToString(withoutAvatar))
        assertEquals(withoutAvatar, decodedWithoutAvatar)
        assertNull(decodedWithoutAvatar.avatarUri)
        assertFalse(decodedWithoutAvatar.hasAnswerAction)
    }

    @Test
    fun callThemeConfigDefaultsSerializationAndAlphaBounds() {
        val defaultTheme = CallThemeConfig(
            id = "theme-neon",
            name = "Cyber Cyan",
            videoUri = "file:///data/user/0/com.customcall/files/themes/theme-neon.mp4",
            accentColorHex = 0xFF00E5FF
        )

        assertTrue(defaultTheme.isMuted)
        assertEquals(0.35f, defaultTheme.overlayGradientAlpha)
        assertEquals(ButtonStyleType.SWIPE_PILL, defaultTheme.buttonStyle)

        val roundTripped = json.decodeFromString<CallThemeConfig>(json.encodeToString(defaultTheme))
        assertEquals(defaultTheme, roundTripped)

        // Boundary values 0.0f and 1.0f are valid
        assertEquals(0.0f, defaultTheme.copy(overlayGradientAlpha = 0.0f).overlayGradientAlpha)
        assertEquals(1.0f, defaultTheme.copy(overlayGradientAlpha = 1.0f).overlayGradientAlpha)

        // Out-of-range alpha values must fail fast
        assertFailsWith<IllegalArgumentException> {
            defaultTheme.copy(overlayGradientAlpha = -0.01f)
        }
        assertFailsWith<IllegalArgumentException> {
            defaultTheme.copy(overlayGradientAlpha = 1.01f)
        }
    }

    @Test
    fun contactRuleAndAppSettingsRoundTrip() {
        val rule = ContactRule(
            id = "rule-1",
            callerIdentifier = "+584121234567",
            normalizedName = "carlos mendez",
            sourceFilter = CallSource.WHATSAPP_BUSINESS,
            themeId = "theme-neon"
        )
        assertEquals(rule, json.decodeFromString<ContactRule>(json.encodeToString(rule)))

        val defaultSettings = AppSettings()
        assertNull(defaultSettings.defaultThemeId)
        assertEquals(CallSource.entries.toSet(), defaultSettings.enabledSources)
        assertTrue(defaultSettings.customVoipPackages.isEmpty())
        assertFalse(defaultSettings.isOnboardingCompleted)

        val customizedSettings = AppSettings(
            defaultThemeId = "theme-neon",
            enabledSources = setOf(CallSource.PHONE_GSM, CallSource.WHATSAPP),
            customVoipPackages = setOf("com.discord", "org.thoughtcrime.securesms"),
            isOnboardingCompleted = true
        )
        assertEquals(customizedSettings, json.decodeFromString<AppSettings>(json.encodeToString(customizedSettings)))
    }

    @Test
    fun mediaModelsValidationAndPolymorphicSerialization() {
        val request = VideoProcessingRequest(
            themeId = "theme-neon",
            sourceUri = "content://media/external/video/media/42",
            trimStartMs = 1_500L,
            trimEndMs = 12_000L
        )
        assertEquals(1080, request.maxShortSidePx)
        assertEquals(30, request.targetFps)
        assertFalse(request.stripAudio)
        assertEquals(request, json.decodeFromString<VideoProcessingRequest>(json.encodeToString(request)))

        assertFailsWith<IllegalArgumentException> {
            request.copy(trimStartMs = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(trimStartMs = 5_000L, trimEndMs = 5_000L)
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(maxShortSidePx = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(targetFps = 0)
        }

        val states: List<VideoProcessingState> = listOf(
            VideoProcessingState.InProgress(0.45f),
            VideoProcessingState.Completed(
                ProcessedVideoAsset(
                    themeId = "theme-neon",
                    outputUri = "file:///themes/theme-neon.mp4",
                    durationMs = 10_500L,
                    width = 1080,
                    height = 1920,
                    fileSizeBytes = 2_450_000L
                )
            ),
            VideoProcessingState.Failed("Unsupported codec")
        )

        states.forEach { state ->
            val encoded = json.encodeToString<VideoProcessingState>(state)
            assertEquals(state, json.decodeFromString<VideoProcessingState>(encoded))
        }
    }
}