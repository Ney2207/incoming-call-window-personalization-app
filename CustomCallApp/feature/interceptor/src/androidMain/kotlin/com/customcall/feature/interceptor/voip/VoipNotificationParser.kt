package com.customcall.feature.interceptor.voip

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.customcall.core.model.CallSource
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.util.IdentifierNormalizer
import com.customcall.feature.interceptor.action.AndroidCallNativeHandle
import java.io.File
import java.io.FileOutputStream

/**
 * Result of parsing an incoming VoIP `CATEGORY_CALL` notification.
 */
data class ParsedVoipNotification(
    val event: IncomingCallEvent,
    val nativeHandle: AndroidCallNativeHandle.VoipNotificationHandle,
    val notificationKey: String,
)

/**
 * Stateless parser converting Android [StatusBarNotification] payloads from VoIP apps
 * into domain [IncomingCallEvent] instances and captured [PendingIntent] actions.
 */
class VoipNotificationParser(
    private val context: Context,
) {

    /**
     * Maps [packageName] to a supported [CallSource], or returns `null` if the package
     * is not a tracked VoIP client.
     */
    fun resolveCallSource(
        packageName: String,
        customVoipPackages: Set<String> = emptySet(),
    ): CallSource? = Companion.resolveCallSource(packageName, customVoipPackages)

    /**
     * Parses [sbn] if it represents a **ringing** incoming call notification.
     *
     * Ongoing/connected VoIP call notifications (which only expose a single "Hang up" action
     * and lack an Answer action) are intentionally ignored so the incoming call UI does not
     * re-trigger mid-call.
     */
    fun parse(
        sbn: StatusBarNotification,
        source: CallSource,
    ): ParsedVoipNotification? {
        val notification = sbn.notification ?: return null

        // Ignore group summary wrapper notifications
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return null
        }

        if (!isCallNotification(notification)) {
            return null
        }

        val (answerIntent, declineIntent) = extractCallActions(notification)
        // Must have an answer action to be an incoming ringing call (vs. an ongoing call)
        if (answerIntent == null) {
            return null
        }

        val extras = notification.extras
        val callerPerson = extractCallerPerson(notification)
        val rawCallerName = callerPerson?.name?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                .takeUnless { it.isNullOrEmpty() }
            ?: extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
                .takeUnless { it.isNullOrEmpty() }
            ?: DEFAULT_CALLER_NAME

        val rawIdentifier = extractRawIdentifier(callerPerson, rawCallerName)
        val normalizedIdentifier = IdentifierNormalizer.normalizeIdentifier(rawIdentifier)
            .ifBlank { rawCallerName.lowercase() }

        val callId = buildCallId(source, sbn.key)
        val avatarUri = extractAndCacheAvatarUri(notification, callerPerson, callId)

        val event = IncomingCallEvent(
            callId = callId,
            source = source,
            callerName = rawCallerName,
            callerIdentifier = normalizedIdentifier,
            avatarUri = avatarUri,
            hasAnswerAction = true,
            hasDeclineAction = declineIntent != null,
            timestamp = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )

        return ParsedVoipNotification(
            event = event,
            nativeHandle = AndroidCallNativeHandle.VoipNotificationHandle(
                answerIntent = answerIntent,
                declineIntent = declineIntent,
            ),
            notificationKey = sbn.key,
        )
    }

    private fun isCallNotification(notification: Notification): Boolean {
        if (notification.category == Notification.CATEGORY_CALL) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val template = notification.extras?.getString(Notification.EXTRA_TEMPLATE)
            if (template == Notification.CallStyle::class.java.name) {
                return true
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun extractCallerPerson(notification: Notification): Person? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val extras = notification.extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_CALL_PERSON, Person::class.java)
        } else {
            extras.getParcelable(Notification.EXTRA_CALL_PERSON)
        }
    }

    private fun extractRawIdentifier(person: Person?, fallbackName: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && person != null) {
            val uri = person.uri
            if (!uri.isNullOrBlank()) {
                return uri.removePrefix("tel:").removePrefix("mailto:").trim()
            }
            val key = person.key
            if (!key.isNullOrBlank()) {
                return key.trim()
            }
        }
        return fallbackName
    }

    @Suppress("DEPRECATION")
    private fun extractCallActions(notification: Notification): Pair<PendingIntent?, PendingIntent?> {
        // 1. Android 12+ Notification.CallStyle explicit extras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val extras = notification.extras
            val styleAnswer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras?.getParcelable("android.answerIntent", PendingIntent::class.java)
            } else {
                extras?.getParcelable("android.answerIntent")
            }
            val styleDecline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras?.getParcelable("android.declineIntent", PendingIntent::class.java)
            } else {
                extras?.getParcelable("android.declineIntent")
            }
            if (styleAnswer != null || styleDecline != null) {
                return styleAnswer to styleDecline
            }
        }

        // 2. Standard Notification.actions inspection (API 26–30 & custom VoIP layouts)
        val actions = notification.actions ?: return null to null
        if (actions.size < 2) {
            // Single-button call notifications represent active calls ("Hang up"), not ringing calls
            return null to null
        }

        var answerIntent: PendingIntent? = null
        var declineIntent: PendingIntent? = null

        for (action in actions) {
            val title = action.title?.toString()?.lowercase().orEmpty()
            val semantic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                action.semanticAction
            } else {
                NotificationCompat.Action.SEMANTIC_ACTION_NONE
            }

            when {
                semantic == NotificationCompat.Action.SEMANTIC_ACTION_CALL ||
                    ANSWER_KEYWORDS.any { title.contains(it) } -> {
                    if (answerIntent == null) answerIntent = action.actionIntent
                }
                DECLINE_KEYWORDS.any { title.contains(it) } -> {
                    if (declineIntent == null) declineIntent = action.actionIntent
                }
            }
        }

        // Fallback positional convention on Android VoIP notifications: [0] = Decline, [1] = Answer
        if (answerIntent == null && declineIntent == null && actions.size >= 2) {
            declineIntent = actions[0].actionIntent
            answerIntent = actions[1].actionIntent
        }

        return answerIntent to declineIntent
    }

    private fun extractAndCacheAvatarUri(
        notification: Notification,
        person: Person?,
        callId: String,
    ): String? {
        val icon: Icon? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            person?.icon ?: notification.getLargeIcon()
        } else {
            notification.getLargeIcon()
        }
        icon ?: return null

        return try {
            val drawable = icon.loadDrawable(context) ?: return null
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }

            val avatarDir = File(context.cacheDir, AVATAR_CACHE_DIR).apply { mkdirs() }
            val avatarFile = File(avatarDir, "${callId.hashCode().toUInt()}.png")
            FileOutputStream(avatarFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            Uri.fromFile(avatarFile).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildCallId(source: CallSource, notificationKey: String): String {
        return "voip_${source.name.lowercase()}_${notificationKey.hashCode().toUInt()}"
    }

    companion object {
        const val PACKAGE_WHATSAPP = "com.whatsapp"
        const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        const val PACKAGE_TELEGRAM = "org.telegram.messenger"
        const val PACKAGE_TELEGRAM_WEB = "org.telegram.messenger.web"

        private const val DEFAULT_CALLER_NAME = "Incoming VoIP Call"
        private const val AVATAR_CACHE_DIR = "call_avatars"

        private val ANSWER_KEYWORDS = listOf(
            "answer", "accept", "join", "responder", "contestar", "aceptar", "atender",
        )
        private val DECLINE_KEYWORDS = listOf(
            "decline", "reject", "dismiss", "hang up", "end",
            "rechazar", "colgar", "ignorar", "finalizar",
        )

        /**
         * Pure-JVM package-to-[CallSource] resolver exposed on the companion object so unit
         * tests can verify package routing without instantiating an Android [Context].
         */
        fun resolveCallSource(
            packageName: String,
            customVoipPackages: Set<String> = emptySet(),
        ): CallSource? = when (packageName) {
            PACKAGE_WHATSAPP -> CallSource.WHATSAPP
            PACKAGE_WHATSAPP_BUSINESS -> CallSource.WHATSAPP_BUSINESS
            PACKAGE_TELEGRAM,
            PACKAGE_TELEGRAM_WEB,
            -> CallSource.TELEGRAM
            else -> if (packageName in customVoipPackages) CallSource.OTHER_VOIP else null
        }
    }
}