package com.customcall.core.database.repository

import com.customcall.core.database.dao.AppSettingsDao
import com.customcall.core.database.dao.ContactRuleDao
import com.customcall.core.database.dao.ThemeDao
import com.customcall.core.database.entity.AppSettingsEntity
import com.customcall.core.database.entity.toDomain
import com.customcall.core.database.entity.toEntity
import com.customcall.core.database.resolver.ThemeRuleResolver
import com.customcall.core.model.AppSettings
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.contract.CallRepository
import com.customcall.core.model.util.IdentifierNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CallRepositoryImpl(
    private val themeDao: ThemeDao,
    private val contactRuleDao: ContactRuleDao,
    private val appSettingsDao: AppSettingsDao,
    private val themeRuleResolver: ThemeRuleResolver
) : CallRepository {

    private val writeMutex = Mutex()

    override fun observeThemes(): Flow<List<CallThemeConfig>> =
        themeDao.observeAllThemes().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getThemeById(themeId: String): CallThemeConfig? =
        themeDao.getThemeById(themeId)?.toDomain()

    override suspend fun upsertTheme(theme: CallThemeConfig) {
        writeMutex.withLock {
            themeDao.upsertTheme(theme.toEntity())
            themeRuleResolver.invalidateCache()
        }
    }

    override suspend fun deleteTheme(themeId: String) {
        writeMutex.withLock {
            themeDao.deleteThemeById(themeId)
            contactRuleDao.deleteRulesByThemeId(themeId)

            val currentSettings = appSettingsDao.getSettings()
            if (currentSettings?.defaultThemeId == themeId) {
                appSettingsDao.upsertSettings(
                    currentSettings.copy(defaultThemeId = null)
                )
            }

            themeRuleResolver.invalidateCache()
        }
    }

    override fun observeContactRules(): Flow<List<ContactRule>> =
        contactRuleDao.observeAllRules().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun upsertContactRule(rule: ContactRule) {
        val canonicalRule = rule.copy(
            callerIdentifier = IdentifierNormalizer.normalizeIdentifier(rule.callerIdentifier),
            normalizedName = IdentifierNormalizer.normalizeDisplayName(rule.normalizedName)
        )
        writeMutex.withLock {
            contactRuleDao.upsertRule(canonicalRule.toEntity())
            themeRuleResolver.invalidateCache()
        }
    }

    override suspend fun deleteContactRule(ruleId: String) {
        writeMutex.withLock {
            contactRuleDao.deleteRuleById(ruleId)
            themeRuleResolver.invalidateCache()
        }
    }

    override fun observeAppSettings(): Flow<AppSettings> =
        appSettingsDao.observeSettings().map { entity ->
            entity?.toDomain() ?: AppSettings()
        }

    override suspend fun updateAppSettings(settings: AppSettings) {
        writeMutex.withLock {
            appSettingsDao.upsertSettings(settings.toEntity())
            themeRuleResolver.invalidateCache()
        }
    }

    override suspend fun setDefaultThemeId(themeId: String?) {
        writeMutex.withLock {
            val current = appSettingsDao.getSettings() ?: AppSettingsEntity()
            appSettingsDao.upsertSettings(current.copy(defaultThemeId = themeId))
            themeRuleResolver.invalidateCache()
        }
    }

    override suspend fun resolveThemeForCall(event: IncomingCallEvent): CallThemeConfig =
        themeRuleResolver.resolveThemeForCall(event)
}