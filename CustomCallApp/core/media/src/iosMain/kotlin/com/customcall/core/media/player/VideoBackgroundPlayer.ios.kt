package com.customcall.core.media.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.customcall.core.media.storage.isValidPlayableVideoUri
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerLooper
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
private class LoopingPlayerUIView : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    private val playerLayer = AVPlayerLayer().apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    private var currentUri: String? = null
    private var queuePlayer: AVQueuePlayer? = null
    private var playerLooper: AVPlayerLooper? = null

    init {
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.frame = bounds
        CATransaction.commit()
    }

    fun bind(
        videoUri: String,
        isPlaying: Boolean,
        isMuted: Boolean,
    ) {
        if (currentUri != videoUri || queuePlayer == null) {
            releasePlayer()
            val url = resolveNsUrl(videoUri) ?: return
            val templateItem = AVPlayerItem.playerItemWithURL(url)
            val player = AVQueuePlayer().apply {
                automaticallyWaitsToMinimizeStalling = false
            }
            val looper = AVPlayerLooper.playerLooperWithPlayer(
                player = player,
                templateItem = templateItem,
            )
            playerLayer.player = player
            queuePlayer = player
            playerLooper = looper
            currentUri = videoUri
        }

        val activePlayer = queuePlayer ?: return
        activePlayer.muted = isMuted
        if (isPlaying) {
            activePlayer.play()
        } else {
            activePlayer.pause()
        }
    }

    fun releasePlayer() {
        queuePlayer?.pause()
        playerLooper?.disableLooping()
        playerLooper = null
        playerLayer.player = null
        queuePlayer?.removeAllItems()
        queuePlayer = null
        currentUri = null
    }

    private fun resolveNsUrl(rawUri: String): NSURL? {
        val trimmed = rawUri.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("/")) {
            NSURL.fileURLWithPath(trimmed)
        } else {
            NSURL.URLWithString(trimmed)
        }
    }
}

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

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isLifecycleStarted = true
                Lifecycle.Event.ON_STOP -> isLifecycleStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!isLifecycleStarted) {
        Box(modifier = modifier)
        return
    }

    UIKitView(
        factory = {
            LoopingPlayerUIView().apply {
                bind(
                    videoUri = videoUri,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                )
            }
        },
        modifier = modifier,
        update = { playerView ->
            playerView.bind(
                videoUri = videoUri,
                isPlaying = isPlaying,
                isMuted = isMuted,
            )
        },
        onRelease = { playerView ->
            playerView.releasePlayer()
        },
    )
}