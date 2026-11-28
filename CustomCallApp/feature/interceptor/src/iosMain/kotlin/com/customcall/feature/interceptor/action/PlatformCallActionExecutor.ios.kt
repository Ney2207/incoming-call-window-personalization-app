package com.customcall.feature.interceptor.action

import com.customcall.core.model.CallState
import com.customcall.core.model.contract.CallActionExecutor
import com.customcall.feature.interceptor.session.CallSessionRegistry

/**
 * iOS CallKit-ready stub ensuring multiplatform Koin dependency graphs compile cleanly
 * until native CXAnswerCallAction / CXEndCallAction bridges are bound.
 */
actual class PlatformCallActionExecutor(
    private val sessionRegistry: CallSessionRegistry,
) : CallActionExecutor {

    actual override suspend fun answerCall(callId: String): Boolean {
        val session = sessionRegistry.getCurrentSession() ?: return false
        if (session.event.callId != callId) return false
        return sessionRegistry.updateState(callId, CallState.ANSWERED)
    }

    actual override suspend fun declineCall(callId: String): Boolean {
        val session = sessionRegistry.getCurrentSession() ?: return false
        if (session.event.callId != callId) return false
        return sessionRegistry.updateState(callId, CallState.DECLINED)
    }
}