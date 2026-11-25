package com.customcall.core.media.player

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.customcall.core.media.storage.isValidPlayableVideoUri
import java.util.concurrent.atomic.AtomicBoolean

private class ManagedExoPlayer(
    val player: ExoPlayer,
) {
    private val released = AtomicBoolean(false)

    val isReleased: Boolean
        get() = released.get()

    fun releaseSafely() {
        if (released.compareAndSet(false, true)) {
            player.clearVideoSurface()
            player.stop()
            player.release()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
actual fun VideoBackgroundPlayer(
    videoUri: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    modifier: Modifier,
) {
    if (!videoUri.isValidPlayableVideoUri()) {
        Box(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPlaybackError by remember(videoUri) { mutableStateOf(false) }
    var isLifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val stateObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isLifecycleStarted = true
                Lifecycle.Event.ON_STOP -> isLifecycleStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(stateObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(stateObserver)
        }
    }

    if (hasPlaybackError || !isLifecycleStarted) {
        Box(modifier = modifier)
        return
    }

    val managedPlayer = remember(context, videoUri, isLifecycleStarted) {
        val exoPlayer = ExoPlayer.Builder(context)
            .setLoadControl(buildSub200msLoadControl())
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (isMuted) 0f else 1f
                playWhenReady = isPlaying
                addListener(
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            hasPlaybackError = true
                        }
                    },
                )
                setMediaItem(MediaItem.fromUri(videoUri))
                prepare()
            }
        ManagedExoPlayer(exoPlayer)
    }

    DisposableEffect(lifecycleOwner, managedPlayer) {
        val stopReleaseObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                managedPlayer.releaseSafely()
            }
        }
        lifecycleOwner.lifecycle.addObserver(stopReleaseObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(stopReleaseObserver)
            managedPlayer.releaseSafely()
        }
    }

    LaunchedEffect(managedPlayer, isPlaying, isMuted) {
        if (managedPlayer.isReleased) return@LaunchedEffect
        val player = managedPlayer.player
        player.volume = if (isMuted) 0f else 1f
        player.playWhenReady = isPlaying
        if (isPlaying && player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            SurfaceView(viewContext).apply {
                keepScreenOn = true
            }
        },
        update = { surfaceView ->
            if (!managedPlayer.isReleased) {
                managedPlayer.player.setVideoSurfaceView(surfaceView)
            }
        },
        onRelease = { surfaceView ->
            if (!managedPlayer.isReleased) {
                managedPlayer.player.clearVideoSurfaceView(surfaceView)
            }
        },
    )
}