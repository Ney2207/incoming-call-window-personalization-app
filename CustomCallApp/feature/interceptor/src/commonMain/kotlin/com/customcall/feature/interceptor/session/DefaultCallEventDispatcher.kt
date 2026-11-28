package com.customcall.feature.interceptor.session

import com.customcall.core.model.CallState
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.contract.CallEventDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Default multiplatform implementation of [CallEventDispatcher] backed by [CallSessionRegistry].
 *
 * Keeps [activeCallEvent] and [callState] synchronously aligned with [CallSessionRegistry]
 * so both Compose UI consumers and native Android services observe deterministic state transitions.
 */
class DefaultCallEventDispatcher(
    private val sessionRegistry: CallSessionRegistry,
) : CallEventDispatcher {

    private val _activeCallEvent = MutableStateFlow<IncomingCallEvent?>(
        sessionRegistry.getCurrentSession()?.event,
    )
    override val activeCallEvent: StateFlow<IncomingCallEvent?> = _activeCallEvent.asStateFlow()

    private val _callState = MutableStateFlow(
        sessionRegistry.getCurrentSession()?.state ?: CallState.ENDED,
    )
    override val callState: StateFlow<CallState> = _callState.asStateFlow()

    override suspend fun dispatchIncomingCall(event: IncomingCallEvent) {
        sessionRegistry.registerOrUpdateIncomingCall(event)
        syncFromRegistry()
    }

    override suspend fun updateCallState(callId: String, state: CallState) {
        val updated = sessionRegistry.updateState(callId, state)
        if (updated) {
            syncFromRegistry()
        }
    }

    override suspend fun clearActiveCall(callId: String) {
        val cleared = sessionRegistry.clearSession(callId)
        if (cleared) {
            syncFromRegistry()
        }
    }

    private fun syncFromRegistry() {
        val session = sessionRegistry.getCurrentSession()
        _activeCallEvent.value = session?.event
        _callState.value = session?.state ?: CallState.ENDED
    }
}