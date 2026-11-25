package com.customcall.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.customcall.core.database.entity.ContactRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactRuleDao {

    @Query("SELECT * FROM contact_rules ORDER BY normalizedName ASC")
    fun observeAllRules(): Flow<List<ContactRuleEntity>>

    @Query("SELECT * FROM contact_rules WHERE callerIdentifier = :identifier")
    suspend fun findByCallerIdentifier(identifier: String): List<ContactRuleEntity>

    @Query("SELECT * FROM contact_rules WHERE normalizedName = :normalizedName")
    suspend fun findByNormalizedName(normalizedName: String): List<ContactRuleEntity>

    @Query("SELECT * FROM contact_rules")
    suspend fun getAllRules(): List<ContactRuleEntity>

    @Upsert
    suspend fun upsertRule(rule: ContactRuleEntity)

    @Query("DELETE FROM contact_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Query("DELETE FROM contact_rules WHERE themeId = :themeId")
    suspend fun deleteRulesByThemeId(themeId: String)
}