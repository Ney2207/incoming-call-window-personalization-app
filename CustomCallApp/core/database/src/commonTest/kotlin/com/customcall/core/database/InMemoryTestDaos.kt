package com.customcall.core.database

import com.customcall.core.database.dao.AppSettingsDao
import com.customcall.core.database.dao.ContactRuleDao
import com.customcall.core.database.dao.ThemeDao
import com.customcall.core.database.entity.AppSettingsEntity
import com.customcall.core.database.entity.ContactRuleEntity
import com.customcall.core.database.entity.ThemeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private typealias ThemeMap = Map<String, ThemeEntity>
private typealias RuleMap = Map<String, ContactRuleEntity>

class InMemoryThemeDao : ThemeDao {
    private val themes = MutableStateFlow<ThemeMap>(emptyMap())

    override fun observeAllThemes(): Flow<List<ThemeEntity>> =
        themes.map { map -> map.values.sortedBy { it.name } }

    override fun observeThemeById(id: String): Flow<ThemeEntity?> =
        themes.map { map -> map[id] }

    override suspend fun getThemeById(id: String): ThemeEntity? =
        themes.value[id]

    override suspend fun getAllThemes(): List<ThemeEntity> =
        themes.value.values.toList()

    override suspend fun upsertTheme(theme: ThemeEntity) {
        themes.update { map -> map + (theme.id to theme) }
    }

    override suspend fun deleteThemeById(id: String) {
        themes.update { map -> map - id }
    }
}

class InMemoryContactRuleDao : ContactRuleDao {
    private val rules = MutableStateFlow<RuleMap>(emptyMap())

    override fun observeAllRules(): Flow<List<ContactRuleEntity>> =
        rules.map { map -> map.values.sortedBy { it.normalizedName } }

    override suspend fun findByCallerIdentifier(identifier: String): List<ContactRuleEntity> =
        rules.value.values.filter { it.callerIdentifier == identifier }

    override suspend fun findByNormalizedName(normalizedName: String): List<ContactRuleEntity> =
        rules.value.values.filter { it.normalizedName == normalizedName }

    override suspend fun getAllRules(): List<ContactRuleEntity> =
        rules.value.values.toList()

    override suspend fun upsertRule(rule: ContactRuleEntity) {
        rules.update { map -> map + (rule.id to rule) }
    }

    override suspend fun deleteRuleById(id: String) {
        rules.update { map -> map - id }
    }

    override suspend fun deleteRulesByThemeId(themeId: String) {
        rules.update { map ->
            map.filterValues { rule -> rule.themeId != themeId }
        }
    }
}

class InMemoryAppSettingsDao : AppSettingsDao {
    private val settings = MutableStateFlow<AppSettingsEntity?>(null)

    override fun observeSettings(): Flow<AppSettingsEntity?> = settings

    override suspend fun getSettings(): AppSettingsEntity? = settings.value

    override suspend fun upsertSettings(settings: AppSettingsEntity) {
        this.settings.value = settings
    }
}