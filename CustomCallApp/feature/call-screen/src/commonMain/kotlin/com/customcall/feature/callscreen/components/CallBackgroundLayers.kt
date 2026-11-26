package com.customcall.feature.callscreen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.customcall.core.media.player.VideoBackgroundPlayer
import com.customcall.feature.callscreen.theme.createBottomScrimBrush
import com.customcall.feature.callscreen.theme.createFallbackBackdropBrush
import com.customcall.feature.callscreen.theme.createTopScrimBrush

/**
 * **Z-Index Layer 1 (Base Layer):** Full-bleed video viewport backed by an opaque theme gradient.
 *
 * Paints [createFallbackBackdropBrush] behind [VideoBackgroundPlayer] so blank fallback themes
 * (`videoUri = ""`), playback errors, and pre-first-frame states render seamlessly without
 * transparent window artifacts, while allowing Android's hardware `SurfaceView` to display
 * video frames when active.
 */
@Composable
fun CallVideoLayer(
    videoUri: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val fallbackBrush = remember(accentColor) {
        createFallbackBackdropBrush(accentColor)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackBrush),
    ) {
        VideoBackgroundPlayer(
            videoUri = videoUri,
            isPlaying = isPlaying,
            isMuted = isMuted,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * **Z-Index Layer 2 (Contrast Layer):** Dual translucent vertical gradient scrims protecting
 * upper caller metadata and lower call controls without obscuring the center video subject.
 */
@Composable
fun CallContrastScrimLayer(
    overlayGradientAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val topScrimBrush = remember(overlayGradientAlpha) {
        createTopScrimBrush(overlayGradientAlpha)
    }
    val bottomScrimBrush = remember(overlayGradientAlpha) {
        createBottomScrimBrush(overlayGradientAlpha)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(topScrimBrush),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.46f)
                .background(bottomScrimBrush),
        )
    }
}