package com.customcall.core.database.converter

import androidx.room.TypeConverter
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DatabaseTypeConverters {

    @TypeConverter
    fun fromButtonStyleType(style: ButtonStyleType): String = style.name

    @TypeConverter
    fun toButtonStyleType(raw: String): ButtonStyleType =
        ButtonStyleType.entries.find { it.name == raw } ?: ButtonStyleType.SWIPE_PILL

    @TypeConverter
    fun fromNullableCallSource(source: CallSource?): String? = source?.name

    @TypeConverter
    fun toNullableCallSource(raw: String?): CallSource? =
        raw?.let { value -> CallSource.entries.find { it.name == value } }

    @TypeConverter
    fun fromCallSourceSet(sources: Set<CallSource>): String =
        Json.encodeToString(sources)

    @TypeConverter
    fun toCallSourceSet(raw: String): Set<CallSource> =
        runCatching { Json.decodeFromString<Set<CallSource>>(raw) }
            .getOrElse { CallSource.entries.toSet() }

    @TypeConverter
    fun fromStringSet(values: Set<String>): String =
        Json.encodeToString(values)

    @TypeConverter
    fun toStringSet(raw: String): Set<String> =
        runCatching { Json.decodeFromString<Set<String>>(raw) }
            .getOrElse { emptySet() }
}