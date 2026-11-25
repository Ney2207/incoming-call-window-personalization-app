package com.customcall.core.database.di

import com.customcall.core.database.AppDatabase
import com.customcall.core.database.buildAppDatabase
import com.customcall.core.database.getAndroidDatabaseBuilder
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDatabaseModule: Module = module {
    single<AppDatabase> {
        buildAppDatabase(getAndroidDatabaseBuilder(context = get()))
    }
}