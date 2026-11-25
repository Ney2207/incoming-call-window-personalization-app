package com.customcall.core.media.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Full-bleed hardware-accelerated looping video background surface.
 *
 * Renders nothing (transparent layout bounds) when [videoUri] is blank or invalid,
 * allowing fallback background gradients to show through without player errors.
 */
@Composable
expect fun VideoBackgroundPlayer(
    videoUri: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
)