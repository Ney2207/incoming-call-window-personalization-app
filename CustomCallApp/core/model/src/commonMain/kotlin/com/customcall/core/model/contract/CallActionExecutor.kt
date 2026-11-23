package com.customcall.core.model.contract

/**
 * Platform action bridge implemented by `:feature:interceptor`.
 *
 * Translates UI answer/decline callbacks into native GSM telecom operations or
 * captured VoIP notification `PendingIntent` invocations.
 */
interface CallActionExecutor {

    /**
     * Executes the native or VoIP answer action for the active session identified by [callId].
     *
     * @return `true` if the answer action was successfully dispatched, `false` if unavailable or expired.
     */
    suspend fun answerCall(callId: String): Boolean

    /**
     * Executes the native or VoIP decline/reject action for the active session identified by [callId].
     *
     * @return `true` if the decline action was successfully dispatched, `false` if unavailable or expired.
     */
    suspend fun declineCall(callId: String): Boolean
}