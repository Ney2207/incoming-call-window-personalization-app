package com.customcall.feature.interceptor.voip

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.customcall.core.model.AppSettings
import com.customcall.core.model.CallState
import com.customcall.core.model.contract.CallEventDispatcher
import com.customcall.core.model.contract.CallRepository
import com.customcall.feature.interceptor.session.CallSessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android [NotificationListenerService] intercepting incoming VoIP call notifications
 * from WhatsApp, WhatsApp Business, Telegram, and user-configured packages in [AppSettings].
 *
 * Caches captured Answer/Decline [android.app.PendingIntent] actions inside [CallSessionRegistry]
 * and emits lifecycle transitions to [CallEventDispatcher].
 */
class CallNotificationListenerService : NotificationListenerService(), KoinComponent {

    private val sessionRegistry: CallSessionRegistry by inject()
    private val eventDispatcher: CallEventDispatcher by inject()
    private val callRepository: CallRepository by inject()
    private val notificationParser: VoipNotificationParser by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settingsJob: Job? = null

    @Volatile
    private var cachedSettings: AppSettings = AppSettings()

    override fun onCreate() {
        super.onCreate()
        startObservingSettings()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        startObservingSettings()
    }

    override fun onListenerDisconnected() {
        settingsJob?.cancel()
        settingsJob = null
        super.onListenerDisconnected()
    }

    private fun startObservingSettings() {
        if (settingsJob?.isActive == true) return
        settingsJob = serviceScope.launch {
            callRepository.observeAppSettings().collectLatest { settings ->
                cachedSettings = settings
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val settings = cachedSettings

        val source = notificationParser.resolveCallSource(
            packageName = statusBarNotification.packageName,
            customVoipPackages = settings.customVoipPackages,
        ) ?: return

        if (source !in settings.enabledSources) {
            return
        }

        val parsed = notificationParser.parse(statusBarNotification, source) ?: return

        serviceScope.launch {
            val isNewSession = sessionRegistry.registerOrUpdateIncomingCall(
                event = parsed.event,
                nativeHandle = parsed.nativeHandle,
                notificationKey = parsed.notificationKey,
            )
            if (isNewSession) {
                eventDispatcher.dispatchIncomingCall(parsed.event)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val callId = sessionRegistry.findCallIdByNotificationKey(statusBarNotification.key) ?: return

        serviceScope.launch {
            eventDispatcher.updateCallState(callId, CallState.ENDED)
            eventDispatcher.clearActiveCall(callId)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}