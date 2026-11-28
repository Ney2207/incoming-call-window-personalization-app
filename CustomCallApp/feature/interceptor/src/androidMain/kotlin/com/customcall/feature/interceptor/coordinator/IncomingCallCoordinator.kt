package com.customcall.feature.interceptor.coordinator

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.customcall.core.model.CallState
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.contract.CallEventDispatcher
import com.customcall.core.model.contract.CallRepository
import com.customcall.feature.interceptor.cellular.CellularCallStateMonitor
import com.customcall.feature.interceptor.session.CallSessionRegistry
import com.customcall.feature.interceptor.ui.CallNotificationHelper
import com.customcall.feature.interceptor.ui.CallWindowManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Central Android call-presentation orchestrator bridging [CallEventDispatcher],
 * [CallRepository] theme resolution, and lock-screen vs. overlay window presentation.
 *
 * Call [start] once from `Application.onCreate()` in `:composeApp` (Block 7) so incoming
 * VoIP and GSM events are intercepted immediately upon process startup.
 */
class IncomingCallCoordinator(
    private val context: Context,
    private val sessionRegistry: CallSessionRegistry,
    private val eventDispatcher: CallEventDispatcher,
    private val callRepository: CallRepository,
    private val notificationHelper: CallNotificationHelper,
    private val windowManager: CallWindowManager,
    private val cellularCallStateMonitor: CellularCallStateMonitor,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val isStarted = AtomicBoolean(false)
    private var observationJob: Job? = null

    @Volatile
    private var presentedCallId: String? = null

    private val keyguardManager: KeyguardManager? by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }

    private val powerManager: PowerManager? by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    /**
     * Starts observing incoming call events and registers the GSM [CellularCallStateMonitor].
     * Idempotent and safe to invoke multiple times.
     */
    fun start() {
        cellularCallStateMonitor.start()
        if (!isStarted.compareAndSet(false, true)) return

        observationJob = scope.launch {
            combine(
                eventDispatcher.activeCallEvent,
                eventDispatcher.callState,
            ) { event, state ->
                event to state
            }.collect { (event, state) ->
                if (event != null && state == CallState.RINGING) {
                    onIncomingCallRinging(event)
                } else {
                    onCallDismissed()
                }
            }
        }
    }

    /**
     * Stops event observation, unregisters GSM listeners, and dismisses any active call UI.
     */
    fun stop() {
        if (!isStarted.compareAndSet(true, false)) return
        observationJob?.cancel()
        observationJob = null
        cellularCallStateMonitor.stop()
        scope.launch {
            onCallDismissed()
        }
    }

    private suspend fun onIncomingCallRinging(event: IncomingCallEvent) {
        if (presentedCallId == event.callId) return
        presentedCallId = event.callId

        // 1. Resolve contact-specific or global theme (< 5 ms) and cache in CallSessionRegistry
        // before launching the Activity or Overlay so the first frame renders in < 200 ms.
        val resolvedTheme = callRepository.resolveThemeForCall(event)
        sessionRegistry.attachResolvedTheme(event.callId, resolvedTheme)

        // 2. Route to lock-screen FullScreenIntent or unlocked WindowManager overlay
        val isDeviceLockedOrAsleep = isScreenLockedOrOff()
        if (isDeviceLockedOrAsleep) {
            if (notificationHelper.canUseFullScreenIntent()) {
                notificationHelper.showIncomingCallNotification(event)
            } else if (windowManager.canDrawOverlays()) {
                windowManager.show()
            } else {
                notificationHelper.showIncomingCallNotification(event)
            }
        } else {
            if (windowManager.canDrawOverlays()) {
                windowManager.show()
            } else {
                notificationHelper.showIncomingCallNotification(event)
            }
        }
    }

    private suspend fun onCallDismissed() {
        presentedCallId = null
        notificationHelper.cancelNotification()
        windowManager.dismiss()
    }

    private fun isScreenLockedOrOff(): Boolean {
        val isLocked = keyguardManager?.isKeyguardLocked ?: false
        val isScreenOn = powerManager?.isInteractive ?: true
        return isLocked || !isScreenOn
    }
}