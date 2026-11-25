package com.customcall.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.customcall.core.database.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = ${AppSettingsEntity.SINGLETON_ID} LIMIT 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = ${AppSettingsEntity.SINGLETON_ID} LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: AppSettingsEntity)
}