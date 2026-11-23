package com.customcall.core.model.contract

import com.customcall.core.model.CallState
import com.customcall.core.model.IncomingCallEvent
import kotlinx.coroutines.flow.StateFlow

/**
 * Reactive state dispatcher connecting native call interceptors (`:feature:interceptor`)
 * with the full-screen incoming call UI (`:feature:call-screen`).
 */
interface CallEventDispatcher {

    /**
     * Currently active [IncomingCallEvent], or `null` when no call is ringing or active.
     */
    val activeCallEvent: StateFlow<IncomingCallEvent?>

    /**
     * Current lifecycle [CallState] of [activeCallEvent] (defaults to [CallState.ENDED] when idle).
     */
    val callState: StateFlow<CallState>

    /**
     * Publishes a newly detected incoming call [event] and sets [callState] to [CallState.RINGING].
     */
    suspend fun dispatchIncomingCall(event: IncomingCallEvent)

    /**
     * Transitions the call identified by [callId] to [state] (e.g., [CallState.ANSWERED],
     * [CallState.DECLINED], or [CallState.ENDED]). No-op if [callId] does not match [activeCallEvent].
     */
    suspend fun updateCallState(callId: String, state: CallState)

    /**
     * Clears [activeCallEvent] and resets [callState] to [CallState.ENDED] when the call session
     * finishes or its system notification is removed. No-op if [callId] does not match.
     */
    suspend fun clearActiveCall(callId: String)
}