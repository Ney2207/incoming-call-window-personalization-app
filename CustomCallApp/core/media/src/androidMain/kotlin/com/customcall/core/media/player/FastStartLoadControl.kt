package com.customcall.core.media.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

internal const val FAST_START_BUFFER_FOR_PLAYBACK_MS = 50
internal const val FAST_START_BUFFER_AFTER_REBUFFER_MS = 100
internal const val FAST_START_MIN_BUFFER_MS = 500
internal const val FAST_START_MAX_BUFFER_MS = 2_000

/**
 * Configures a low-latency, low-RAM [DefaultLoadControl] tailored for local on-disk
 * MP4 assets in `filesDir/themes/` so the initial video frame renders in under 200 ms.
 */
@OptIn(UnstableApi::class)
internal fun buildSub200msLoadControl(): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ FAST_START_MIN_BUFFER_MS,
            /* maxBufferMs = */ FAST_START_MAX_BUFFER_MS,
            /* bufferForPlaybackMs = */ FAST_START_BUFFER_FOR_PLAYBACK_MS,
            /* bufferForPlaybackAfterRebufferMs = */ FAST_START_BUFFER_AFTER_REBUFFER_MS,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()