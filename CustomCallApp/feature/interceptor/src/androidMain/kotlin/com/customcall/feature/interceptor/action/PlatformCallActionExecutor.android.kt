package com.customcall.feature.interceptor.action

import android.Manifest
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.content.ContextCompat
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallState
import com.customcall.core.model.contract.CallActionExecutor
import com.customcall.feature.interceptor.session.CallSessionRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Strongly-typed Android platform handles stored inside [ActiveCallSession.nativeHandle].
 */
sealed interface AndroidCallNativeHandle {
    /**
     * Captured `PendingIntent` actions extracted from a VoIP `CATEGORY_CALL` notification.
     */
    data class VoipNotificationHandle(
        val answerIntent: PendingIntent?,
        val declineIntent: PendingIntent?,
    ) : AndroidCallNativeHandle

    /**
     * Live Telecom [Call] reference provided when the app acts as the active `InCallService`.
     */
    data class TelecomCallHandle(
        val call: Call,
    ) : AndroidCallNativeHandle
}

actual class PlatformCallActionExecutor(
    private val context: Context,
    private val sessionRegistry: CallSessionRegistry,
) : CallActionExecutor {

    private val telecomManager: TelecomManager? by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }

    actual override suspend fun answerCall(callId: String): Boolean = withContext(Dispatchers.Main.immediate) {
        val session = sessionRegistry.getCurrentSession() ?: return@withContext false
        if (session.event.callId != callId) return@withContext false

        val dispatched = when (session.event.source) {
            CallSource.PHONE_GSM -> answerGsmCall(session.nativeHandle)
            CallSource.WHATSAPP,
            CallSource.WHATSAPP_BUSINESS,
            CallSource.TELEGRAM,
            CallSource.OTHER_VOIP,
            -> {
                val handle = session.nativeHandle as? AndroidCallNativeHandle.VoipNotificationHandle
                val intent = handle?.answerIntent ?: return@withContext false
                sendPendingIntent(intent)
            }
        }

        if (dispatched) {
            sessionRegistry.updateState(callId, CallState.ANSWERED)
        }
        dispatched
    }

    actual override suspend fun declineCall(callId: String): Boolean = withContext(Dispatchers.Main.immediate) {
        val session = sessionRegistry.getCurrentSession() ?: return@withContext false
        if (session.event.callId != callId) return@withContext false

        val dispatched = when (session.event.source) {
            CallSource.PHONE_GSM -> declineGsmCall(session.nativeHandle)
            CallSource.WHATSAPP,
            CallSource.WHATSAPP_BUSINESS,
            CallSource.TELEGRAM,
            CallSource.OTHER_VOIP,
            -> {
                val handle = session.nativeHandle as? AndroidCallNativeHandle.VoipNotificationHandle
                val intent = handle?.declineIntent ?: return@withContext false
                sendPendingIntent(intent)
            }
        }

        if (dispatched) {
            sessionRegistry.updateState(callId, CallState.DECLINED)
        }
        dispatched
    }

    @Suppress("DEPRECATION")
    private fun answerGsmCall(nativeHandle: Any?): Boolean {
        if (nativeHandle is AndroidCallNativeHandle.TelecomCallHandle) {
            return try {
                nativeHandle.call.answer(VideoProfile.STATE_AUDIO_ONLY)
                true
            } catch (_: Exception) {
                false
            }
        }

        if (!hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) {
            return false
        }

        return try {
            telecomManager?.acceptRingingCall()
            telecomManager != null
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun declineGsmCall(nativeHandle: Any?): Boolean {
        if (nativeHandle is AndroidCallNativeHandle.TelecomCallHandle) {
            return try {
                nativeHandle.call.reject(false, null)
                true
            } catch (_: Exception) {
                false
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false
        }

        if (!hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) {
            return false
        }

        return try {
            telecomManager?.endCall() ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun sendPendingIntent(pendingIntent: PendingIntent): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
                pendingIntent.send(
                    context,
                    0,
                    null,
                    null,
                    null,
                    null,
                    options.toBundle(),
                )
            } else {
                pendingIntent.send()
            }
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}