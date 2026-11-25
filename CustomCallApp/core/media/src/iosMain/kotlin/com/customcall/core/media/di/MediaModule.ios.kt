package com.customcall.core.media.di

import com.customcall.core.media.processor.IosVideoAssetProcessor
import com.customcall.core.media.storage.ThemeAssetStorage
import com.customcall.core.model.contract.VideoAssetProcessor
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformMediaModule: Module = module {
    single { ThemeAssetStorage() }
    single<VideoAssetProcessor> {
        IosVideoAssetProcessor(storage = get())
    }
}