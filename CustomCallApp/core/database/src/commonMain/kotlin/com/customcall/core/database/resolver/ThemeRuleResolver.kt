package com.customcall.core.database.resolver

import com.customcall.core.database.dao.AppSettingsDao
import com.customcall.core.database.dao.ContactRuleDao
import com.customcall.core.database.dao.ThemeDao
import com.customcall.core.database.entity.toDomain
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.util.IdentifierNormalizer
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ThemeRuleResolver(
    private val themeDao: ThemeDao,
    private val contactRuleDao: ContactRuleDao,
    private val appSettingsDao: AppSettingsDao
) {
    private data class ResolverSnapshot(
        val themesById: Map<String, CallThemeConfig>,
        val rulesByIdentifier: Map<String, List<ContactRule>>,
        val rulesByNormalizedName: Map<String, List<ContactRule>>,
        val defaultThemeId: String?
    )

    private val loadMutex = Mutex()

    @Volatile
    private var cachedSnapshot: ResolverSnapshot? = null

    /**
     * Invalidates the in-memory lookup snapshot after any write to themes,
     * contact rules, or app settings so subsequent calls reflect the latest state.
     */
    fun invalidateCache() {
        cachedSnapshot = null
    }

    /**
     * Pre-warms the lookup snapshot in memory so the first incoming call resolves
     * in microseconds without waiting on disk I/O.
     */
    suspend fun warmUp() {
        getOrLoadSnapshot()
    }

    /**
     * Resolves the winning [CallThemeConfig] for an [IncomingCallEvent] in < 5 ms
     * using strict priority order:
     * 1. Canonicalized callerIdentifier (source-specific rule > wildcard rule)
     * 2. Canonicalized normalizedName (source-specific rule > wildcard rule)
     * 3. Global defaultThemeId from AppSettings
     * 4. [DefaultThemeProvider.BUILTIN_FALLBACK_THEME]
     */
    suspend fun resolveThemeForCall(event: IncomingCallEvent): CallThemeConfig {
        val snapshot = getOrLoadSnapshot()

        // Priority 1: Exact normalized callerIdentifier match
        val normalizedIdentifier = IdentifierNormalizer.normalizeIdentifier(event.callerIdentifier)
        if (normalizedIdentifier.isNotBlank()) {
            snapshot.rulesByIdentifier[normalizedIdentifier]
                ?.selectBestMatchingTheme(event.source, snapshot.themesById)
                ?.let { return it }
        }

        // Priority 2: Normalized caller display name match
        val normalizedName = IdentifierNormalizer.normalizeDisplayName(event.callerName)
        if (normalizedName.isNotBlank()) {
            snapshot.rulesByNormalizedName[normalizedName]
                ?.selectBestMatchingTheme(event.source, snapshot.themesById)
                ?.let { return it }
        }

        // Priority 3: Global default theme configured in AppSettings
        snapshot.defaultThemeId
            ?.let { defaultId -> snapshot.themesById[defaultId] }
            ?.let { return it }

        // Priority 4: Built-in safe fallback theme
        return DefaultThemeProvider.BUILTIN_FALLBACK_THEME
    }

    private suspend fun getOrLoadSnapshot(): ResolverSnapshot {
        cachedSnapshot?.let { return it }

        return loadMutex.withLock {
            cachedSnapshot?.let { return@withLock it }

            val themesById = themeDao.getAllThemes()
                .associate { entity -> entity.id to entity.toDomain() }

            val allRules = contactRuleDao.getAllRules()
                .map { entity -> entity.toDomain() }

            val rulesByIdentifier = allRules
                .mapNotNull { rule ->
                    val key = IdentifierNormalizer.normalizeIdentifier(rule.callerIdentifier)
                    if (key.isBlank()) null else key to rule
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second }
                )

            val rulesByNormalizedName = allRules
                .mapNotNull { rule ->
                    val key = IdentifierNormalizer.normalizeDisplayName(rule.normalizedName)
                    if (key.isBlank()) null else key to rule
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second }
                )

            val defaultThemeId = appSettingsDao.getSettings()?.defaultThemeId

            ResolverSnapshot(
                themesById = themesById,
                rulesByIdentifier = rulesByIdentifier,
                rulesByNormalizedName = rulesByNormalizedName,
                defaultThemeId = defaultThemeId
            ).also { loaded ->
                cachedSnapshot = loaded
            }
        }
    }

    private fun List<ContactRule>.selectBestMatchingTheme(
        callSource: CallSource,
        themesById: Map<String, CallThemeConfig>
    ): CallThemeConfig? {
        // Prefer an exact sourceFilter match (e.g., WHATSAPP only) over a wildcard (null) rule
        val sourceSpecificTheme = firstOrNull { it.sourceFilter == callSource }
            ?.let { themesById[it.themeId] }
        if (sourceSpecificTheme != null) {
            return sourceSpecificTheme
        }

        return firstOrNull { it.sourceFilter == null }
            ?.let { themesById[it.themeId] }
    }
}