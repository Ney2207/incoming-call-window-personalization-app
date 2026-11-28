@file:Suppress("DEPRECATION")

package com.customcall.feature.interceptor.cellular

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.customcall.core.model.AppSettings
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallState
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.contract.CallEventDispatcher
import com.customcall.core.model.contract.CallRepository
import com.customcall.core.model.util.IdentifierNormalizer
import com.customcall.feature.interceptor.session.CallSessionRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Monitors system GSM telephony state transitions (`RINGING`, `OFFHOOK`, `IDLE`) across
 * both modern [TelephonyCallback] (API 31+) and legacy [PhoneStateListener] (API 26–30).
 *
 * Ensures the custom call screen dismisses immediately when a GSM call is answered externally
 * (e.g., via Bluetooth headset or car audio) or when the remote party hangs up, and provides
 * incoming call detection fallback on Android 8.0–9.0 (API 26–28) where [CustomCallScreeningService]
 * is not invoked for inbound calls.
 */
class CellularCallStateMonitor(
    private val context: Context,
    private val sessionRegistry: CallSessionRegistry,
    private val eventDispatcher: CallEventDispatcher,
    private val callRepository: CallRepository,
) {

    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRegistered = AtomicBoolean(false)
    private var settingsJob: Job? = null

    @Volatile
    private var cachedSettings: AppSettings = AppSettings()

    private var telephonyCallback: Any? = null

    @Suppress("DEPRECATION")
    private var legacyPhoneStateListener: PhoneStateListener? = null

    /**
     * Begins observing system telephony call states if [Manifest.permission.READ_PHONE_STATE]
     * is granted. Safe to call repeatedly; registration is idempotent.
     */
    fun start() {
        if (!hasReadPhoneStatePermission()) return
        val manager = telephonyManager ?: return
        if (!isRegistered.compareAndSet(false, true)) return

        startObservingSettings()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                registerModernCallback(manager)
            } else {
                registerLegacyListener(manager)
            }
        } catch (_: SecurityException) {
            isRegistered.set(false)
        }
    }

    /**
     * Unregisters active telephony listeners and cancels background setting observation.
     */
    fun stop() {
        if (!isRegistered.compareAndSet(true, false)) return
        settingsJob?.cancel()
        settingsJob = null

        val manager = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = telephonyCallback as? TelephonyCallback
            if (callback != null) {
                manager.unregisterTelephonyCallback(callback)
            }
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            val listener = legacyPhoneStateListener
            if (listener != null) {
                @Suppress("DEPRECATION")
                manager.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
            legacyPhoneStateListener = null
        }
    }

    private fun startObservingSettings() {
        if (settingsJob?.isActive == true) return
        settingsJob = scope.launch {
            callRepository.observeAppSettings().collectLatest { settings ->
                cachedSettings = settings
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernCallback(manager: TelephonyManager) {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleTelephonyStateChange(state = state, incomingNumber = null)
            }
        }
        telephonyCallback = callback
        manager.registerTelephonyCallback(context.mainExecutor, callback)
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyListener(manager: TelephonyManager) {
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleTelephonyStateChange(state = state, incomingNumber = phoneNumber)
            }
        }
        legacyPhoneStateListener = listener
        manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun handleTelephonyStateChange(state: Int, incomingNumber: String?) {
        scope.launch {
            val currentSession = sessionRegistry.getCurrentSession()
            val gsmSession = currentSession?.takeIf { it.event.source == CallSource.PHONE_GSM }

            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    // On API 29+, CustomCallScreeningService or CustomInCallService normally creates
                    // the GSM session first with full Caller ID details. If no GSM session exists yet
                    // (e.g., API 26–28 or restricted screening), create a fallback ringing session.
                    if (gsmSession == null && CallSource.PHONE_GSM in cachedSettings.enabledSources) {
                        val rawNumber = incomingNumber?.trim().orEmpty()
                        val normalized = IdentifierNormalizer.normalizePhoneNumber(rawNumber)
                            .ifBlank { UNKNOWN_IDENTIFIER }
                        val displayName = rawNumber.ifBlank { UNKNOWN_CALLER_NAME }
                        val callId = "gsm_telephony_${System.currentTimeMillis()}"

                        val fallbackEvent = IncomingCallEvent(
                            callId = callId,
                            source = CallSource.PHONE_GSM,
                            callerName = displayName,
                            callerIdentifier = normalized,
                            avatarUri = null,
                            hasAnswerAction = true,
                            hasDeclineAction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
                            timestamp = System.currentTimeMillis(),
                        )
                        val created = sessionRegistry.registerOrUpdateIncomingCall(fallbackEvent)
                        if (created) {
                            eventDispatcher.dispatchIncomingCall(fallbackEvent)
                        }
                    }
                }

                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (gsmSession != null && gsmSession.state == CallState.RINGING) {
                        eventDispatcher.updateCallState(gsmSession.event.callId, CallState.ANSWERED)
                    }
                }

                TelephonyManager.CALL_STATE_IDLE -> {
                    if (gsmSession != null) {
                        eventDispatcher.updateCallState(gsmSession.event.callId, CallState.ENDED)
                        eventDispatcher.clearActiveCall(gsmSession.event.callId)
                    }
                }
            }
        }
    }

    private fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val UNKNOWN_CALLER_NAME = "Incoming Call"
        private const val UNKNOWN_IDENTIFIER = "private_number"
    }
}