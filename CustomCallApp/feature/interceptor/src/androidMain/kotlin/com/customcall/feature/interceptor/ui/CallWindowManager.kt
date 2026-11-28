package com.customcall.feature.interceptor.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.customcall.core.model.CallState
import com.customcall.core.model.contract.CallActionExecutor
import com.customcall.core.model.contract.CallEventDispatcher
import com.customcall.feature.callscreen.IncomingCallScreen
import com.customcall.feature.interceptor.session.CallSessionRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages a full-screen `TYPE_APPLICATION_OVERLAY` window hosting [IncomingCallScreen]
 * above heads-up notifications when an incoming call arrives while the device is unlocked.
 *
 * Supplies a dedicated [LifecycleOwner], [ViewModelStoreOwner], and [SavedStateRegistryOwner]
 * to the root [ComposeView] and tears down all view/lifecycle resources deterministically upon
 * call answer, decline, or remote termination.
 */
class CallWindowManager(
    private val context: Context,
    private val sessionRegistry: CallSessionRegistry,
    private val eventDispatcher: CallEventDispatcher,
    private val actionExecutor: CallActionExecutor,
) {

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var overlayView: ComposeView? = null
    private var overlayOwner: OverlayViewTreeOwner? = null

    /**
     * Returns `true` if `SYSTEM_ALERT_WINDOW` ("Display over other apps") is currently granted.
     */
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Attaches and displays the full-screen overlay call UI on the main thread.
     * No-op if overlay permission is not granted or the overlay is already mounted.
     */
    suspend fun show() = withContext(Dispatchers.Main.immediate) {
        if (!canDrawOverlays()) return@withContext
        if (overlayView != null) return@withContext

        val initialSession = sessionRegistry.getCurrentSession() ?: return@withContext
        if (initialSession.state != CallState.RINGING) return@withContext

        val treeOwner = OverlayViewTreeOwner().apply {
            performRestore()
            moveToState(Lifecycle.State.RESUMED)
        }

        val composeView = ComposeView(context.applicationContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(treeOwner)
            setViewTreeViewModelStoreOwner(treeOwner)
            setViewTreeSavedStateRegistryOwner(treeOwner)

            setContent {
                val activeSession by sessionRegistry.activeSession.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(activeSession) {
                    val session = activeSession
                    if (session == null || session.state != CallState.RINGING) {
                        dismiss()
                    }
                }

                val session = activeSession ?: return@setContent
                val theme = session.resolvedTheme ?: IncomingCallActivity.FALLBACK_THEME

                var avatarBitmap by remember(session.event.avatarUri) {
                    mutableStateOf<ImageBitmap?>(null)
                }
                LaunchedEffect(session.event.avatarUri) {
                    avatarBitmap = loadAvatarImageBitmap(
                        context = context.applicationContext,
                        avatarUri = session.event.avatarUri,
                    )
                }

                IncomingCallScreen(
                    event = session.event,
                    theme = theme,
                    callState = session.state,
                    isVideoPlaying = true,
                    avatarBitmap = avatarBitmap,
                    applySystemBarInsets = true,
                    onAnswer = {
                        val callId = session.event.callId
                        coroutineScope.launch {
                            actionExecutor.answerCall(callId)
                            dismiss()
                        }
                    },
                    onDecline = {
                        val callId = session.event.callId
                        coroutineScope.launch {
                            actionExecutor.declineCall(callId)
                            eventDispatcher.clearActiveCall(callId)
                            dismiss()
                        }
                    },
                )
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
            overlayView = composeView
            overlayOwner = treeOwner
        } catch (_: Exception) {
            treeOwner.moveToState(Lifecycle.State.DESTROYED)
            treeOwner.viewModelStore.clear()
        }
    }

    /**
     * Immediately removes the overlay window and destroys its Compose lifecycle and video player.
     */
    suspend fun dismiss() = withContext(Dispatchers.Main.immediate) {
        val view = overlayView
        val owner = overlayOwner
        overlayView = null
        overlayOwner = null

        if (view != null) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: IllegalArgumentException) {
                // View was already detached
            }
        }
        if (owner != null) {
            owner.moveToState(Lifecycle.State.DESTROYED)
            owner.viewModelStore.clear()
        }
    }

    /**
     * Self-contained ViewTree owner allowing [ComposeView] to run outside an Activity.
     */
    private class OverlayViewTreeOwner :
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        override val viewModelStore = ViewModelStore()

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun performRestore() {
            savedStateController.performRestore(null)
        }

        fun moveToState(state: Lifecycle.State) {
            lifecycleRegistry.currentState = state
        }
    }
}