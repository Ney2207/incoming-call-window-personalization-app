package com.customcall.feature.interceptor.cellular

import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallState
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.contract.CallEventDispatcher
import com.customcall.core.model.contract.CallRepository
import com.customcall.core.model.util.IdentifierNormalizer
import com.customcall.feature.interceptor.action.AndroidCallNativeHandle
import com.customcall.feature.interceptor.session.CallSessionRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Optional [InCallService] supporting full `ROLE_DIALER` replacement mode.
 *
 * Binds the live [android.telecom.Call] handle into [CallSessionRegistry] so [PlatformCallActionExecutor]
 * can invoke [Call.answer] and [Call.reject] directly, and mirrors Telecom call state callbacks
 * (`STATE_RINGING`, `STATE_ACTIVE`, `STATE_DISCONNECTED`) to [CallEventDispatcher].
 */
class CustomInCallService : InCallService(), KoinComponent {

    private val sessionRegistry: CallSessionRegistry by inject()
    private val eventDispatcher: CallEventDispatcher by inject()
    private val callRepository: CallRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeCallbacks = ConcurrentHashMap<Call, Pair<String, Call.Callback>>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        val rawNumber = call.details?.handle?.schemeSpecificPart?.trim().orEmpty()
        val normalizedIdentifier = IdentifierNormalizer.normalizePhoneNumber(rawNumber)
            .ifBlank { UNKNOWN_IDENTIFIER }

        val existingGsmSession = sessionRegistry.getCurrentSession()
            ?.takeIf { it.event.source == CallSource.PHONE_GSM }
        val callId = existingGsmSession?.event?.callId
            ?: "gsm_incall_${normalizedIdentifier}_${System.currentTimeMillis()}"

        val nativeHandle = AndroidCallNativeHandle.TelecomCallHandle(call)

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                serviceScope.launch {
                    when (state) {
                        Call.STATE_ACTIVE -> {
                            eventDispatcher.updateCallState(callId, CallState.ANSWERED)
                        }
                        Call.STATE_DISCONNECTED -> {
                            eventDispatcher.updateCallState(callId, CallState.ENDED)
                            eventDispatcher.clearActiveCall(callId)
                        }
                    }
                }
            }
        }

        activeCallbacks[call] = callId to callback
        call.registerCallback(callback)

        serviceScope.launch {
            if (existingGsmSession != null) {
                // Upgrade existing CallScreeningService session with direct Telecom Call control
                sessionRegistry.updateNativeHandle(callId, nativeHandle)
                return@launch
            }

            if (readCallState(call) != Call.STATE_RINGING) {
                return@launch
            }

            val settings = runCatching { callRepository.observeAppSettings().first() }.getOrNull()
            if (settings != null && CallSource.PHONE_GSM !in settings.enabledSources) {
                return@launch
            }

            val displayName = resolveDisplayName(call, rawNumber)
            val event = IncomingCallEvent(
                callId = callId,
                source = CallSource.PHONE_GSM,
                callerName = displayName,
                callerIdentifier = normalizedIdentifier,
                avatarUri = null,
                hasAnswerAction = true,
                hasDeclineAction = true,
                timestamp = call.details?.creationTimeMillis?.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
            )

            val created = sessionRegistry.registerOrUpdateIncomingCall(
                event = event,
                nativeHandle = nativeHandle,
            )
            if (created) {
                eventDispatcher.dispatchIncomingCall(event)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        val entry = activeCallbacks.remove(call)
        if (entry != null) {
            val (callId, callback) = entry
            call.unregisterCallback(callback)
            serviceScope.launch {
                eventDispatcher.updateCallState(callId, CallState.ENDED)
                eventDispatcher.clearActiveCall(callId)
            }
        }
        super.onCallRemoved(call)
    }

    @Suppress("DEPRECATION")
    private fun readCallState(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            call.state
        }
    }

    private fun resolveDisplayName(call: Call, rawNumber: String): String {
        val details = call.details
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contactName = details?.contactDisplayName?.toString()?.trim()
            if (!contactName.isNullOrEmpty()) return contactName
        }
        val callerDisplay = details?.callerDisplayName?.trim()
        if (!callerDisplay.isNullOrEmpty()) return callerDisplay
        return rawNumber.ifBlank { PRIVATE_CALLER_NAME }
    }

    override fun onDestroy() {
        activeCallbacks.forEach { (call, pair) ->
            call.unregisterCallback(pair.second)
        }
        activeCallbacks.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PRIVATE_CALLER_NAME = "Private Number"
        private const val UNKNOWN_IDENTIFIER = "private_number"
    }
}