package com.customcall.core.media.di

import org.koin.core.module.Module
import org.koin.dsl.module

internal expect val platformMediaModule: Module

/**
 * Root Koin dependency injection module for `:core:media`.
 *
 * Automatically includes [platformMediaModule] and binds
 * [com.customcall.core.model.contract.VideoAssetProcessor] to the platform-specific
 * media ingestion and transcoding implementation.
 */
val mediaModule: Module = module {
    includes(platformMediaModule)
}