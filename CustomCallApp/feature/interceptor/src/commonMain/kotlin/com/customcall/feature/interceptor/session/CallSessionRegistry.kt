package com.customcall.feature.interceptor.session

import com.customcall.core.model.CallState
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.IncomingCallEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Snapshot of the currently active call session, combining immutable domain metadata
 * with pre-resolved UI configuration and opaque platform handles (e.g., VoIP PendingIntents
 * or Android Telecom Call instances) for sub-200ms presentation.
 */
data class ActiveCallSession(
    val event: IncomingCallEvent,
    val state: CallState,
    val resolvedTheme: CallThemeConfig? = null,
    val nativeHandle: Any? = null,
    val notificationKey: String? = null,
)

/**
 * Thread-safe in-memory registry tracking the active incoming/ongoing call session.
 *
 * Deduplicates repeated notification updates while a call is [CallState.RINGING] so
 * refreshed `PendingIntent` handles are cached without re-triggering UI mounts, and
 * releases platform handles immediately upon call termination.
 */
class CallSessionRegistry {

    private val mutex = Mutex()
    private val _activeSession = MutableStateFlow<ActiveCallSession?>(null)

    val activeSession: StateFlow<ActiveCallSession?> = _activeSession.asStateFlow()

    /**
     * Lock-free snapshot read for latency-critical UI startup (`IncomingCallActivity.onCreate`)
     * and native action execution.
     */
    fun getCurrentSession(): ActiveCallSession? = _activeSession.value

    /**
     * Registers a new incoming call session or updates an existing [CallState.RINGING] session
     * in place if [event.callId] or [notificationKey] matches.
     *
     * @return `true` if a new incoming call session was created (caller should resolve theme
     * and launch UI); `false` if an existing session was updated in place or an active
     * [CallState.ANSWERED] call is currently in progress.
     */
    suspend fun registerOrUpdateIncomingCall(
        event: IncomingCallEvent,
        nativeHandle: Any? = null,
        notificationKey: String? = null,
        resolvedTheme: CallThemeConfig? = null,
    ): Boolean = mutex.withLock {
        val current = _activeSession.value

        val isSameSession = current != null && (
            current.event.callId == event.callId ||
                (notificationKey != null && current.notificationKey == notificationKey)
            )

        if (isSameSession) {
            // Update metadata and refreshed platform handles in-place without resetting state
            // or discarding an already-resolved theme.
            _activeSession.value = current.copy(
                event = event,
                nativeHandle = nativeHandle ?: current.nativeHandle,
                notificationKey = notificationKey ?: current.notificationKey,
                resolvedTheme = resolvedTheme ?: current.resolvedTheme,
            )
            return@withLock false
        }

        // Do not clobber an active answered call with a secondary background notification.
        if (current != null && current.state == CallState.ANSWERED) {
            return@withLock false
        }

        _activeSession.value = ActiveCallSession(
            event = event,
            state = CallState.RINGING,
            resolvedTheme = resolvedTheme,
            nativeHandle = nativeHandle,
            notificationKey = notificationKey,
        )
        true
    }

    /**
     * Attaches the pre-resolved [CallThemeConfig] to the active session matching [callId].
     */
    suspend fun attachResolvedTheme(callId: String, theme: CallThemeConfig): Boolean = mutex.withLock {
        val current = _activeSession.value ?: return@withLock false
        if (current.event.callId != callId) return@withLock false

        _activeSession.value = current.copy(resolvedTheme = theme)
        true
    }

    /**
     * Attaches or replaces the platform-specific [nativeHandle] for the session matching [callId].
     */
    suspend fun updateNativeHandle(callId: String, nativeHandle: Any?): Boolean = mutex.withLock {
        val current = _activeSession.value ?: return@withLock false
        if (current.event.callId != callId) return@withLock false

        _activeSession.value = current.copy(nativeHandle = nativeHandle)
        true
    }

    /**
     * Transitions the session matching [callId] to [newState].
     * Immediately drops [ActiveCallSession.nativeHandle] on terminal states ([CallState.DECLINED],
     * [CallState.ENDED]) to prevent leaking Android `PendingIntent` or `Call` references.
     */
    suspend fun updateState(callId: String, newState: CallState): Boolean = mutex.withLock {
        val current = _activeSession.value ?: return@withLock false
        if (current.event.callId != callId) return@withLock false

        val isTerminal = newState == CallState.DECLINED || newState == CallState.ENDED
        _activeSession.value = current.copy(
            state = newState,
            nativeHandle = if (isTerminal) null else current.nativeHandle,
        )
        true
    }

    /**
     * Clears the active session if its `callId` matches [callId].
     */
    suspend fun clearSession(callId: String): Boolean = mutex.withLock {
        val current = _activeSession.value ?: return@withLock false
        if (current.event.callId != callId) return@withLock false

        _activeSession.value = null
        true
    }

    /**
     * Looks up the `callId` bound to [notificationKey], if still active.
     */
    fun findCallIdByNotificationKey(notificationKey: String): String? {
        val current = _activeSession.value ?: return null
        return if (current.notificationKey == notificationKey) current.event.callId else null
    }
}