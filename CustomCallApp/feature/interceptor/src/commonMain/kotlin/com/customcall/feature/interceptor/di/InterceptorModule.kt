package com.customcall.feature.interceptor.di

import com.customcall.core.model.contract.CallEventDispatcher
import com.customcall.feature.interceptor.session.CallSessionRegistry
import com.customcall.feature.interceptor.session.DefaultCallEventDispatcher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform-specific Koin bindings for `:feature:interceptor` (`CallActionExecutor`,
 * native Android services/helpers, or iOS CallKit stubs).
 */
internal expect val platformInterceptorModule: Module

/**
 * Root Koin dependency module for `:feature:interceptor` (Block 6).
 *
 * Register alongside `databaseModule`, `mediaModule`, and `customizerModule` inside
 * `:composeApp`'s `startKoin { ... }` block.
 */
val interceptorModule: Module = module {
    single { CallSessionRegistry() }
    single<CallEventDispatcher> { DefaultCallEventDispatcher(sessionRegistry = get()) }
    includes(platformInterceptorModule)
}