package com.customcall.feature.customizer.di

import com.customcall.feature.customizer.presentation.ContactRulesViewModel
import com.customcall.feature.customizer.presentation.PermissionWizardViewModel
import com.customcall.feature.customizer.presentation.ThemeStudioViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

internal expect fun platformCustomizerModule(): Module

/**
 * Koin dependency injection module for `:feature:customizer`.
 *
 * Requires upstream `databaseModule` (`CallRepository`) and `mediaModule` (`VideoAssetProcessor`)
 * to be registered in the root Koin graph.
 */
val customizerModule: Module = module {
    includes(platformCustomizerModule())

    viewModel {
        ThemeStudioViewModel(
            callRepository = get(),
            videoAssetProcessor = get(),
        )
    }

    viewModel {
        ContactRulesViewModel(
            callRepository = get(),
        )
    }

    viewModel {
        PermissionWizardViewModel(
            systemDiagnosticController = get(),
            callRepository = get(),
        )
    }
}