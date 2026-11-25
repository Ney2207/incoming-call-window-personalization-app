package com.customcall.core.database.di

import com.customcall.core.database.AppDatabase
import com.customcall.core.database.dao.AppSettingsDao
import com.customcall.core.database.dao.ContactRuleDao
import com.customcall.core.database.dao.ThemeDao
import com.customcall.core.database.repository.CallRepositoryImpl
import com.customcall.core.database.resolver.ThemeRuleResolver
import com.customcall.core.model.contract.CallRepository
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformDatabaseModule: Module

val databaseModule: Module = module {
    includes(platformDatabaseModule)

    single<ThemeDao> { get<AppDatabase>().themeDao() }
    single<ContactRuleDao> { get<AppDatabase>().contactRuleDao() }
    single<AppSettingsDao> { get<AppDatabase>().appSettingsDao() }

    single {
        ThemeRuleResolver(
            themeDao = get(),
            contactRuleDao = get(),
            appSettingsDao = get()
        )
    }

    single<CallRepository> {
        CallRepositoryImpl(
            themeDao = get(),
            contactRuleDao = get(),
            appSettingsDao = get(),
            themeRuleResolver = get()
        )
    }
}