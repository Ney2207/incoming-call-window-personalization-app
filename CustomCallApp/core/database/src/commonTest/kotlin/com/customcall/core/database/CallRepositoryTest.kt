package com.customcall.core.database

import com.customcall.core.database.repository.CallRepositoryImpl
import com.customcall.core.database.resolver.DefaultThemeProvider
import com.customcall.core.database.resolver.ThemeRuleResolver
import com.customcall.core.model.AppSettings
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.core.model.IncomingCallEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class CallRepositoryTest {

    private fun createRepository(): CallRepositoryImpl {
        val themeDao = InMemoryThemeDao()
        val ruleDao = InMemoryContactRuleDao()
        val settingsDao = InMemoryAppSettingsDao()
        val resolver = ThemeRuleResolver(themeDao, ruleDao, settingsDao)
        return CallRepositoryImpl(themeDao, ruleDao, settingsDao, resolver)
    }

    @Test
    fun themeCrudAndDeleteCascadesRulesAndClearsDefaultThemeId() = runTest {
        val repository = createRepository()
        val theme = CallThemeConfig(
            id = "theme_1",
            name = "Neon Rain",
            videoUri = "file:///themes/neon_rain.mp4",
            isMuted = false,
            overlayGradientAlpha = 0.5f,
            buttonStyle = ButtonStyleType.CLASSIC_DUAL,
            accentColorHex = 0xFFFF4081L
        )

        repository.upsertTheme(theme)
        repository.setDefaultThemeId(theme.id)
        repository.upsertContactRule(
            ContactRule(
                id = "rule_1",
                callerIdentifier = "+58 (412) 555-0199",
                normalizedName = "  Elena   Rojas  ",
                sourceFilter = CallSource.WHATSAPP,
                themeId = theme.id
            )
        )

        assertEquals(theme, repository.getThemeById(theme.id))
        assertEquals(listOf(theme), repository.observeThemes().first())
        assertEquals(theme.id, repository.observeAppSettings().first().defaultThemeId)

        // Verify rule keys were canonicalized on save
        val savedRule = repository.observeContactRules().first().single()
        assertEquals("+584125550199", savedRule.callerIdentifier)
        assertEquals("elena rojas", savedRule.normalizedName)

        // Delete theme and verify cascading cleanup + fallback behavior
        repository.deleteTheme(theme.id)

        assertNull(repository.getThemeById(theme.id))
        assertTrue(repository.observeContactRules().first().isEmpty())
        assertNull(repository.observeAppSettings().first().defaultThemeId)

        val fallback = repository.resolveThemeForCall(
            IncomingCallEvent(
                callId = "c-100",
                source = CallSource.WHATSAPP,
                callerName = "Elena Rojas",
                callerIdentifier = "+584125550199",
                avatarUri = null,
                hasAnswerAction = true,
                hasDeclineAction = true,
                timestamp = 1_700_000_000_000L
            )
        )
        assertEquals(DefaultThemeProvider.BUILTIN_FALLBACK_THEME, fallback)
    }

    @Test
    fun appSettingsUpdatesArePersistedAndInvalidatedInResolver() = runTest {
        val repository = createRepository()
        val themeA = CallThemeConfig(
            id = "theme_a",
            name = "Theme A",
            videoUri = "file:///themes/a.mp4",
            accentColorHex = 0xFF00BCD4L
        )
        val themeB = CallThemeConfig(
            id = "theme_b",
            name = "Theme B",
            videoUri = "file:///themes/b.mp4",
            accentColorHex = 0xFFFF9800L
        )

        repository.upsertTheme(themeA)
        repository.upsertTheme(themeB)

        val updatedSettings = AppSettings(
            defaultThemeId = themeA.id,
            enabledSources = setOf(CallSource.PHONE_GSM, CallSource.TELEGRAM),
            customVoipPackages = setOf("com.discord"),
            isOnboardingCompleted = true
        )
        repository.updateAppSettings(updatedSettings)

        val observed = repository.observeAppSettings().first()
        assertNotNull(observed)
        assertEquals(updatedSettings, observed)

        val event = IncomingCallEvent(
            callId = "c-200",
            source = CallSource.TELEGRAM,
            callerName = "Stranger",
            callerIdentifier = "@stranger",
            avatarUri = null,
            hasAnswerAction = true,
            hasDeclineAction = true,
            timestamp = 1_700_000_000_000L
        )

        assertEquals(themeA, repository.resolveThemeForCall(event))

        // Switch default theme and verify resolver immediately reflects the new setting
        repository.setDefaultThemeId(themeB.id)
        assertEquals(themeB, repository.resolveThemeForCall(event))
    }
}