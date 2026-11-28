package com.customcall.feature.interceptor.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.customcall.core.model.CallSource
import com.customcall.core.model.IncomingCallEvent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the high-priority `CATEGORY_CALL` notification channel and posts/cancels
 * `FullScreenIntent` notifications used to wake the device and launch [IncomingCallActivity].
 */
class CallNotificationHelper(
    private val context: Context,
) {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        ensureNotificationChannel()
    }

    /**
     * Returns `true` if the app is permitted to launch full-screen intents on lock screens
     * (always `true` below Android 14 / API 34; verified via [NotificationManager] on API 34+).
     */
    fun canUseFullScreenIntent(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }
    }

    /**
     * Posts a high-priority call notification with a `FullScreenIntent` pointing to
     * [IncomingCallActivity] for [event].
     */
    fun showIncomingCallNotification(event: IncomingCallEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureNotificationChannel()

        val launchIntent = IncomingCallActivity.createIntent(context, event.callId)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_FULL_SCREEN,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sourceLabel = when (event.source) {
            CallSource.PHONE_GSM -> "Incoming Phone Call"
            CallSource.WHATSAPP -> "Incoming WhatsApp Call"
            CallSource.WHATSAPP_BUSINESS -> "Incoming WhatsApp Business Call"
            CallSource.TELEGRAM -> "Incoming Telegram Call"
            CallSource.OTHER_VOIP -> "Incoming VoIP Call"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(event.callerName)
            .setContentText(sourceLabel)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Ignored if POST_NOTIFICATIONS is revoked mid-session
        }
    }

    /**
     * Immediately removes any active full-screen incoming call notification from the shade.
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Full-screen incoming call alerts"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            setBypassDnd(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "custom_incoming_calls_v1"
        private const val CHANNEL_NAME = "Incoming Call Screen"
        const val NOTIFICATION_ID = 40_912
        private const val REQUEST_CODE_FULL_SCREEN = 40_913
    }
}

/**
 * Safely decodes a cached `file://` or `content://` contact portrait URI into an [ImageBitmap]
 * on [Dispatchers.IO], returning `null` if unavailable or unreadable.
 */
internal suspend fun loadAvatarImageBitmap(
    context: Context,
    avatarUri: String?,
): ImageBitmap? = withContext(Dispatchers.IO) {
    if (avatarUri.isNullOrBlank()) return@withContext null
    try {
        val parsed = Uri.parse(avatarUri)
        val bitmap = when (parsed.scheme) {
            "file" -> {
                val path = parsed.path ?: return@withContext null
                if (!File(path).exists()) return@withContext null
                BitmapFactory.decodeFile(path)
            }
            "content" -> {
                context.contentResolver.openInputStream(parsed)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
            else -> {
                if (File(avatarUri).exists()) BitmapFactory.decodeFile(avatarUri) else null
            }
        }
        bitmap?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}