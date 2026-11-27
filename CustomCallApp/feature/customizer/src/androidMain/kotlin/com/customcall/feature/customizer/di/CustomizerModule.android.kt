package com.customcall.feature.customizer.di

import com.customcall.feature.customizer.platform.AndroidSystemDiagnosticController
import com.customcall.feature.customizer.platform.SystemDiagnosticController
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformCustomizerModule(): Module = module {
    single<SystemDiagnosticController> {
        AndroidSystemDiagnosticController(context = androidContext())
    }
}