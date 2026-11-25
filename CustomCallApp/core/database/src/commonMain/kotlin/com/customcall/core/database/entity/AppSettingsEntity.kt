package com.customcall.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.customcall.core.model.AppSettings
import com.customcall.core.model.CallSource

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val defaultThemeId: String? = null,
    val enabledSources: Set<CallSource> = CallSource.entries.toSet(),
    val customVoipPackages: Set<String> = emptySet(),
    val isOnboardingCompleted: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

fun AppSettingsEntity.toDomain(): AppSettings = AppSettings(
    defaultThemeId = defaultThemeId,
    enabledSources = enabledSources,
    customVoipPackages = customVoipPackages,
    isOnboardingCompleted = isOnboardingCompleted
)

fun AppSettings.toEntity(): AppSettingsEntity = AppSettingsEntity(
    id = AppSettingsEntity.SINGLETON_ID,
    defaultThemeId = defaultThemeId,
    enabledSources = enabledSources,
    customVoipPackages = customVoipPackages,
    isOnboardingCompleted = isOnboardingCompleted
)