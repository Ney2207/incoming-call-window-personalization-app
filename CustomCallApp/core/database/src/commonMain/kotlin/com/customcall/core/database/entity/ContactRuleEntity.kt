package com.customcall.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.customcall.core.model.CallSource
import com.customcall.core.model.ContactRule

@Entity(
    tableName = "contact_rules",
    indices = [
        Index(value = ["callerIdentifier"]),
        Index(value = ["normalizedName"]),
        Index(value = ["themeId"])
    ]
)
data class ContactRuleEntity(
    @PrimaryKey
    val id: String,
    val callerIdentifier: String,
    val normalizedName: String,
    val sourceFilter: CallSource?,
    val themeId: String
)

fun ContactRuleEntity.toDomain(): ContactRule = ContactRule(
    id = id,
    callerIdentifier = callerIdentifier,
    normalizedName = normalizedName,
    sourceFilter = sourceFilter,
    themeId = themeId
)

fun ContactRule.toEntity(): ContactRuleEntity = ContactRuleEntity(
    id = id,
    callerIdentifier = callerIdentifier,
    normalizedName = normalizedName,
    sourceFilter = sourceFilter,
    themeId = themeId
)