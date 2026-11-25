package com.customcall.core.media.di

import com.customcall.core.media.processor.AndroidVideoAssetProcessor
import com.customcall.core.media.storage.ThemeAssetStorage
import com.customcall.core.model.contract.VideoAssetProcessor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformMediaModule: Module = module {
    single { ThemeAssetStorage(context = androidContext()) }
    single<VideoAssetProcessor> {
        AndroidVideoAssetProcessor(
            context = androidContext(),
            storage = get(),
        )
    }
}