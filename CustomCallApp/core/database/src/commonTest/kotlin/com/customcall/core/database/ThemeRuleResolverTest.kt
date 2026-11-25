package com.customcall.core.database

import com.customcall.core.database.entity.AppSettingsEntity
import com.customcall.core.database.entity.toEntity
import com.customcall.core.database.resolver.DefaultThemeProvider
import com.customcall.core.database.resolver.ThemeRuleResolver
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.core.model.IncomingCallEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import kotlinx.coroutines.test.runTest

class ThemeRuleResolverTest {

    private fun sampleTheme(id: String, name: String = "Theme $id") = CallThemeConfig(
        id = id,
        name = name,
        videoUri = "file:///themes/$id.mp4",
        isMuted = true,
        overlayGradientAlpha = 0.4f,
        buttonStyle = ButtonStyleType.SWIPE_PILL,
        accentColorHex = 0xFF6200EEL
    )

    private fun sampleEvent(
        source: CallSource = CallSource.PHONE_GSM,
        callerName: String = "Unknown Caller",
        callerIdentifier: String = ""
    ) = IncomingCallEvent(
        callId = "call-1",
        source = source,
        callerName = callerName,
        callerIdentifier = callerIdentifier,
        avatarUri = null,
        hasAnswerAction = true,
        hasDeclineAction = true,
        timestamp = 1_700_000_000_000L
    )

    @Test
    fun returnsBuiltinFallbackWhenDatabaseIsEmpty() = runTest {
        val resolver = ThemeRuleResolver(
            themeDao = InMemoryThemeDao(),
            contactRuleDao = InMemoryContactRuleDao(),
            appSettingsDao = InMemoryAppSettingsDao()
        )

        val resolved = resolver.resolveThemeForCall(
            sampleEvent(callerName = "Alice", callerIdentifier = "+15551234567")
        )

        assertEquals(DefaultThemeProvider.BUILTIN_FALLBACK_THEME, resolved)
    }

    @Test
    fun resolvesGlobalDefaultThemeWhenNoContactRuleMatches() = runTest {
        val themeDao = InMemoryThemeDao()
        val settingsDao = InMemoryAppSettingsDao()
        val globalTheme = sampleTheme("theme_global", "Cyberpunk Loop")

        themeDao.upsertTheme(globalTheme.toEntity())
        settingsDao.upsertSettings(AppSettingsEntity(defaultThemeId = globalTheme.id))

        val resolver = ThemeRuleResolver(themeDao, InMemoryContactRuleDao(), settingsDao)
        val resolved = resolver.resolveThemeForCall(
            sampleEvent(callerName = "Bob", callerIdentifier = "+15559998888")
        )

        assertEquals(globalTheme, resolved)
    }

    @Test
    fun identifierMatchBeatsNameMatchAndGlobalDefault() = runTest {
        val themeDao = InMemoryThemeDao()
        val ruleDao = InMemoryContactRuleDao()
        val settingsDao = InMemoryAppSettingsDao()

        val globalTheme = sampleTheme("theme_global")
        val nameTheme = sampleTheme("theme_name")
        val idTheme = sampleTheme("theme_id")

        themeDao.upsertTheme(globalTheme.toEntity())
        themeDao.upsertTheme(nameTheme.toEntity())
        themeDao.upsertTheme(idTheme.toEntity())
        settingsDao.upsertSettings(AppSettingsEntity(defaultThemeId = globalTheme.id))

        ruleDao.upsertRule(
            ContactRule(
                id = "rule_name",
                callerIdentifier = "",
                normalizedName = "alice m.",
                sourceFilter = null,
                themeId = nameTheme.id
            ).toEntity()
        )
        ruleDao.upsertRule(
            ContactRule(
                id = "rule_id",
                callerIdentifier = "+15552345678",
                normalizedName = "",
                sourceFilter = null,
                themeId = idTheme.id
            ).toEntity()
        )

        val resolver = ThemeRuleResolver(themeDao, ruleDao, settingsDao)

        // Formatted number "+1 (555) 234-5678" must normalize to "+15552345678"
        val resolved = resolver.resolveThemeForCall(
            sampleEvent(
                callerName = "  Alice   M.  ",
                callerIdentifier = "+1 (555) 234-5678"
            )
        )

        assertEquals(idTheme, resolved)
    }

    @Test
    fun sourceSpecificRuleTakesPrecedenceOverWildcardRule() = runTest {
        val themeDao = InMemoryThemeDao()
        val ruleDao = InMemoryContactRuleDao()
        val settingsDao = InMemoryAppSettingsDao()

        val wildcardTheme = sampleTheme("theme_wildcard")
        val whatsappTheme = sampleTheme("theme_whatsapp")

        themeDao.upsertTheme(wildcardTheme.toEntity())
        themeDao.upsertTheme(whatsappTheme.toEntity())

        ruleDao.upsertRule(
            ContactRule(
                id = "rule_any",
                callerIdentifier = "+15550001111",
                normalizedName = "carlos",
                sourceFilter = null,
                themeId = wildcardTheme.id
            ).toEntity()
        )
        ruleDao.upsertRule(
            ContactRule(
                id = "rule_wa",
                callerIdentifier = "+15550001111",
                normalizedName = "carlos",
                sourceFilter = CallSource.WHATSAPP,
                themeId = whatsappTheme.id
            ).toEntity()
        )

        val resolver = ThemeRuleResolver(themeDao, ruleDao, settingsDao)

        val whatsappResolved = resolver.resolveThemeForCall(
            sampleEvent(source = CallSource.WHATSAPP, callerIdentifier = "+15550001111")
        )
        val gsmResolved = resolver.resolveThemeForCall(
            sampleEvent(source = CallSource.PHONE_GSM, callerIdentifier = "+15550001111")
        )

        assertEquals(whatsappTheme, whatsappResolved)
        assertEquals(wildcardTheme, gsmResolved)
    }

    @Test
    fun resolvesUnder5MillisecondsWithOver1000Rules() = runTest {
        val themeDao = InMemoryThemeDao()
        val ruleDao = InMemoryContactRuleDao()
        val settingsDao = InMemoryAppSettingsDao()

        val targetTheme = sampleTheme("theme_target")
        themeDao.upsertTheme(targetTheme.toEntity())

        repeat(1_200) { index ->
            val phone = "+1555" + index.toString().padStart(7, '0')
            ruleDao.upsertRule(
                ContactRule(
                    id = "rule_$index",
                    callerIdentifier = phone,
                    normalizedName = "contact $index",
                    sourceFilter = null,
                    themeId = targetTheme.id
                ).toEntity()
            )
        }

        val resolver = ThemeRuleResolver(themeDao, ruleDao, settingsDao)
        resolver.warmUp()

        val event = sampleEvent(
            source = CallSource.TELEGRAM,
            callerName = "Contact 1199",
            callerIdentifier = "+1 (555) 000-1199"
        )

        // Warm up JIT & classloading before measuring steady-state latency
        repeat(50) {
            assertEquals(targetTheme, resolver.resolveThemeForCall(event))
        }

        val iterations = 100
        val totalElapsed = measureTime {
            repeat(iterations) {
                resolver.resolveThemeForCall(event)
            }
        }
        val averagePerCall = totalElapsed / iterations

        assertTrue(
            averagePerCall < 5.milliseconds,
            "Expected average rule resolution in < 5 ms, but took $averagePerCall (total $totalElapsed over $iterations runs)"
        )
    }
}