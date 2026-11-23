package com.customcall.core.model.contract

import com.customcall.core.model.AppSettings
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.core.model.IncomingCallEvent
import kotlinx.coroutines.flow.Flow

/**
 * Persistence and rule-resolution contract implemented by `:core:database`.
 *
 * Exposes reactive [Flow] streams for UI observation and fast indexed lookup methods
 * for resolving themes upon incoming call arrival.
 */
interface CallRepository {

    /**
     * Emits the full list of saved [CallThemeConfig] entries whenever the underlying table changes.
     */
    fun observeThemes(): Flow<List<CallThemeConfig>>

    /**
     * Retrieves a single [CallThemeConfig] by its unique [themeId], or `null` if not found.
     */
    suspend fun getThemeById(themeId: String): CallThemeConfig?

    /**
     * Inserts a new [CallThemeConfig] or updates an existing record with the same [CallThemeConfig.id].
     */
    suspend fun upsertTheme(theme: CallThemeConfig)

    /**
     * Removes the theme identified by [themeId] along with any dependent rule references.
     */
    suspend fun deleteTheme(themeId: String)

    /**
     * Emits all configured [ContactRule] mappings whenever rules are added, updated, or removed.
     */
    fun observeContactRules(): Flow<List<ContactRule>>

    /**
     * Inserts a new [ContactRule] or updates an existing rule with the same [ContactRule.id].
     */
    suspend fun upsertContactRule(rule: ContactRule)

    /**
     * Deletes the [ContactRule] identified by [ruleId].
     */
    suspend fun deleteContactRule(ruleId: String)

    /**
     * Emits the current [AppSettings] snapshot and subsequent updates.
     */
    fun observeAppSettings(): Flow<AppSettings>

    /**
     * Persists an updated [AppSettings] configuration.
     */
    suspend fun updateAppSettings(settings: AppSettings)

    /**
     * Updates the global fallback [AppSettings.defaultThemeId].
     */
    suspend fun setDefaultThemeId(themeId: String?)

    /**
     * Resolves the winning [CallThemeConfig] for an incoming [event] in under 5 ms.
     *
     * Resolution priority:
     * 1. Contact rule matching normalized `callerIdentifier` (and optional `sourceFilter`).
     * 2. Contact rule matching normalized `callerName` (and optional `sourceFilter`).
     * 3. Global fallback theme configured via [AppSettings.defaultThemeId].
     * 4. Built-in safe default [CallThemeConfig] if no user theme has been configured yet.
     */
    suspend fun resolveThemeForCall(event: IncomingCallEvent): CallThemeConfig
}