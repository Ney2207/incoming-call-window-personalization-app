package com.customcall.feature.callscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.customcall.core.model.CallState
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.IncomingCallEvent
import com.customcall.feature.callscreen.components.CallContrastScrimLayer
import com.customcall.feature.callscreen.components.CallVideoLayer
import com.customcall.feature.callscreen.components.CallerIdentityHeader
import com.customcall.feature.callscreen.components.controls.CallControlsDispatcher
import com.customcall.feature.callscreen.theme.toComposeColor

/**
 * Primary edge-to-edge incoming call screen composable shared across Block 5 (`:feature:customizer`
 * live Theme Studio previews) and Block 6 (`:feature:interceptor` lock-screen & overlay hosts).
 *
 * Stacks four strictly ordered z-index layers inside a full-viewport [Box]:
 * 1. **Base Layer (`CallVideoLayer`):** Hardware-accelerated looping video backed by an opaque
 *    theme gradient canvas.
 * 2. **Contrast Layer (`CallContrastScrimLayer`):** Configurable top and bottom translucent
 *    scrim gradients driven by [CallThemeConfig.overlayGradientAlpha].
 * 3. **Caller Identity Layer (`CallerIdentityHeader`):** Call-source badge, circular avatar or
 *    monogram fallback, caller display name, identifier, and animated ringing status indicator.
 * 4. **Interactive Controls Layer (`CallControlsDispatcher`):** Pluggable call controls matching
 *    [CallThemeConfig.buttonStyle] with single-shot [onAnswer] / [onDecline] dispatch.
 *
 * @param event Metadata and available actions for the incoming call.
 * @param theme Visual and audio configuration resolved for this call or previewed in Theme Studio.
 * @param onAnswer Single-shot callback invoked when the user accepts the call.
 * @param onDecline Single-shot callback invoked when the user rejects the call.
 * @param modifier Layout modifier applied to the root viewport container.
 * @param callState Current lifecycle state of the call; controls status indicator text and pulsing.
 * @param isVideoPlaying Controls active video playback in `VideoBackgroundPlayer`.
 * @param avatarBitmap Optional decoded contact portrait bitmap; falls back to initials when `null`.
 * @param applySystemBarInsets Applies `statusBarsPadding()` and `navigationBarsPadding()` to the
 * foreground overlay. Keep `true` for full-screen lock-screen/overlay hosts (Block 6) and set to
 * `false` when rendering inside a compact embedded preview card (Block 5).
 */
@Composable
fun IncomingCallScreen(
    event: IncomingCallEvent,
    theme: CallThemeConfig,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    callState: CallState = CallState.RINGING,
    isVideoPlaying: Boolean = true,
    avatarBitmap: ImageBitmap? = null,
    applySystemBarInsets: Boolean = true,
) {
    val accentColor = remember(theme.accentColorHex) {
        theme.accentColorHex.toComposeColor()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Z-Index Layer 1: Base video viewport + opaque fallback backdrop
        CallVideoLayer(
            videoUri = theme.videoUri,
            isPlaying = isVideoPlaying,
            isMuted = theme.isMuted,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize(),
        )

        // Z-Index Layer 2: Translucent top & bottom WCAG contrast scrims
        CallContrastScrimLayer(
            overlayGradientAlpha = theme.overlayGradientAlpha,
            modifier = Modifier.fillMaxSize(),
        )

        // Z-Index Layers 3 & 4: Safe-area foreground container with adaptive vertical spacing
        val insetsModifier = if (applySystemBarInsets) {
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
        } else {
            Modifier
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(insetsModifier),
        ) {
            val topPadding = remember(maxHeight) {
                (maxHeight * 0.055f).coerceIn(12.dp, 44.dp)
            }
            val bottomPadding = remember(maxHeight) {
                (maxHeight * 0.045f).coerceIn(12.dp, 36.dp)
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Z-Index Layer 3: Caller identity header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(topPadding))
                    CallerIdentityHeader(
                        event = event,
                        accentColor = accentColor,
                        callState = callState,
                        avatarBitmap = avatarBitmap,
                    )
                }

                // Z-Index Layer 4: Pluggable interactive controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CallControlsDispatcher(
                        event = event,
                        buttonStyle = theme.buttonStyle,
                        accentColor = accentColor,
                        onAnswer = onAnswer,
                        onDecline = onDecline,
                    )
                    Spacer(modifier = Modifier.height(bottomPadding))
                }
            }
        }
    }
}