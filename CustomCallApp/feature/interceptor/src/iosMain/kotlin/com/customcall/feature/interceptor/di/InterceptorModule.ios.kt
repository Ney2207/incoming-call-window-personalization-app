package com.customcall.feature.interceptor.di

import com.customcall.core.model.contract.CallActionExecutor
import com.customcall.feature.interceptor.action.PlatformCallActionExecutor
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformInterceptorModule: Module = module {
    single {
        PlatformCallActionExecutor(
            sessionRegistry = get(),
        )
    }
    single<CallActionExecutor> { get<PlatformCallActionExecutor>() }
}