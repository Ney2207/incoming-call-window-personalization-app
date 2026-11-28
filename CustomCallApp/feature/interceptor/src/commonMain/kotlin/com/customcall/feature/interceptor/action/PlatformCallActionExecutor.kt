package com.customcall.feature.interceptor.action

import com.customcall.core.model.contract.CallActionExecutor

/**
 * Multiplatform bridge translating UI answer/decline callbacks into native OS telephony
 * commands or captured VoIP notification actions.
 */
expect class PlatformCallActionExecutor : CallActionExecutor {
    override suspend fun answerCall(callId: String): Boolean
    override suspend fun declineCall(callId: String): Boolean
}