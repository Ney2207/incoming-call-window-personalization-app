package com.customcall.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.customcall.core.database.entity.ThemeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {

    @Query("SELECT * FROM themes ORDER BY name ASC")
    fun observeAllThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id LIMIT 1")
    fun observeThemeById(id: String): Flow<ThemeEntity?>

    @Query("SELECT * FROM themes WHERE id = :id LIMIT 1")
    suspend fun getThemeById(id: String): ThemeEntity?

    @Query("SELECT * FROM themes")
    suspend fun getAllThemes(): List<ThemeEntity>

    @Upsert
    suspend fun upsertTheme(theme: ThemeEntity)

    @Query("DELETE FROM themes WHERE id = :id")
    suspend fun deleteThemeById(id: String)
}