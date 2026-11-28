package com.customcall.feature.interceptor.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallState
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.contract.CallActionExecutor
import com.customcall.core.model.contract.CallEventDispatcher
import com.customcall.feature.callscreen.IncomingCallScreen
import com.customcall.feature.interceptor.session.CallSessionRegistry
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Full-screen lock-screen [ComponentActivity] launched via `FullScreenIntent` when an incoming
 * call arrives while the device is locked or the screen is off.
 *
 * Mounts `:feature:call-screen`'s [IncomingCallScreen] using the pre-resolved [CallThemeConfig]
 * cached in [CallSessionRegistry] for sub-200ms first-frame rendering.
 */
class IncomingCallActivity : ComponentActivity() {

    private val sessionRegistry: CallSessionRegistry by inject()
    private val eventDispatcher: CallEventDispatcher by inject()
    private val actionExecutor: CallActionExecutor by inject()
    private val notificationHelper: CallNotificationHelper by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        configureLockScreenWindow()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val initialSession = sessionRegistry.getCurrentSession()
        if (initialSession == null || initialSession.state != CallState.RINGING) {
            notificationHelper.cancelNotification()
            finishAndRemoveTask()
            return
        }

        setContent {
            val activeSession by sessionRegistry.activeSession.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            var isVideoPlaying by remember { mutableStateOf(true) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> isVideoPlaying = true
                        Lifecycle.Event.ON_STOP -> isVideoPlaying = false
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Dismiss immediately when the call is answered, declined, or ended remotely
            LaunchedEffect(activeSession) {
                val session = activeSession
                if (session == null || session.state != CallState.RINGING) {
                    notificationHelper.cancelNotification()
                    finishAndRemoveTask()
                }
            }

            val session = activeSession ?: return@setContent
            val theme = session.resolvedTheme ?: FALLBACK_THEME

            var avatarBitmap by remember(session.event.avatarUri) {
                mutableStateOf<ImageBitmap?>(null)
            }
            LaunchedEffect(session.event.avatarUri) {
                avatarBitmap = loadAvatarImageBitmap(
                    context = applicationContext,
                    avatarUri = session.event.avatarUri,
                )
            }

            IncomingCallScreen(
                event = session.event,
                theme = theme,
                callState = session.state,
                isVideoPlaying = isVideoPlaying,
                avatarBitmap = avatarBitmap,
                applySystemBarInsets = true,
                onAnswer = {
                    val callId = session.event.callId
                    coroutineScope.launch {
                        actionExecutor.answerCall(callId)
                        notificationHelper.cancelNotification()
                        finishAndRemoveTask()
                    }
                },
                onDecline = {
                    val callId = session.event.callId
                    coroutineScope.launch {
                        actionExecutor.declineCall(callId)
                        eventDispatcher.clearActiveCall(callId)
                        notificationHelper.cancelNotification()
                        finishAndRemoveTask()
                    }
                },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val EXTRA_CALL_ID = "extra_call_id"

        internal val FALLBACK_THEME = CallThemeConfig(
            id = "builtin_default",
            name = "Default",
            videoUri = "",
            isMuted = true,
            overlayGradientAlpha = 0.35f,
            buttonStyle = ButtonStyleType.SWIPE_PILL,
            accentColorHex = 0xFF4CAF50L,
        )

        fun createIntent(context: Context, callId: String): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                putExtra(EXTRA_CALL_ID, callId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION,
                )
            }
        }
    }
}