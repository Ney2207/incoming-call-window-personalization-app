package com.customcall.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.customcall.core.database.converter.DatabaseTypeConverters
import com.customcall.core.database.dao.AppSettingsDao
import com.customcall.core.database.dao.ContactRuleDao
import com.customcall.core.database.dao.ThemeDao
import com.customcall.core.database.entity.AppSettingsEntity
import com.customcall.core.database.entity.ContactRuleEntity
import com.customcall.core.database.entity.ThemeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        ThemeEntity::class,
        ContactRuleEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun themeDao(): ThemeDao

    abstract fun contactRuleDao(): ContactRuleDao

    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        const val DATABASE_NAME = "custom_call.db"
    }
}

// KSP generates the actual platform implementations for Android and iOS targets
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun buildAppDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()