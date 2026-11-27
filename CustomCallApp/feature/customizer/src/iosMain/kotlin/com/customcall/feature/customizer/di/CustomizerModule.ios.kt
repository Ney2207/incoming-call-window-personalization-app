package com.customcall.feature.customizer.di

import com.customcall.feature.customizer.platform.IosSystemDiagnosticController
import com.customcall.feature.customizer.platform.SystemDiagnosticController
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformCustomizerModule(): Module = module {
    single<SystemDiagnosticController> {
        IosSystemDiagnosticController()
    }
}