package com.customcall.feature.interceptor.di

import com.customcall.core.model.contract.CallActionExecutor
import com.customcall.feature.interceptor.action.PlatformCallActionExecutor
import com.customcall.feature.interceptor.cellular.CellularCallStateMonitor
import com.customcall.feature.interceptor.coordinator.IncomingCallCoordinator
import com.customcall.feature.interceptor.ui.CallNotificationHelper
import com.customcall.feature.interceptor.ui.CallWindowManager
import com.customcall.feature.interceptor.voip.VoipNotificationParser
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformInterceptorModule: Module = module {
    single {
        PlatformCallActionExecutor(
            context = androidContext(),
            sessionRegistry = get(),
        )
    }
    single<CallActionExecutor> { get<PlatformCallActionExecutor>() }

    single {
        VoipNotificationParser(
            context = androidContext(),
        )
    }

    single {
        CellularCallStateMonitor(
            context = androidContext(),
            sessionRegistry = get(),
            eventDispatcher = get(),
            callRepository = get(),
        )
    }

    single {
        CallNotificationHelper(
            context = androidContext(),
        )
    }

    single {
        CallWindowManager(
            context = androidContext(),
            sessionRegistry = get(),
            eventDispatcher = get(),
            actionExecutor = get(),
        )
    }

    single {
        IncomingCallCoordinator(
            context = androidContext(),
            sessionRegistry = get(),
            eventDispatcher = get(),
            callRepository = get(),
            notificationHelper = get(),
            windowManager = get(),
            cellularCallStateMonitor = get(),
        )
    }
}