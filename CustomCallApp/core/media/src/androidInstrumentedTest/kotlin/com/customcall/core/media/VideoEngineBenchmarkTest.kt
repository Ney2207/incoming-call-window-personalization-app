package com.customcall.core.media

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.customcall.core.media.player.buildSub200msLoadControl
import com.customcall.core.media.processor.AndroidVideoAssetProcessor
import com.customcall.core.media.storage.ThemeAssetStorage
import com.customcall.core.model.VideoProcessingRequest
import com.customcall.core.model.VideoProcessingState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
class VideoEngineBenchmarkTest {

    private fun resolveAppContext(): Context {
        val app = Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as Application
        return app.applicationContext
    }

    @Test
    fun exoPlayer_rendersFirstFrameUnder200msAndLoopsWithoutError() = runBlocking {
        val context = resolveAppContext()
        val storage = ThemeAssetStorage(context)
        val sourceFile = File(context.cacheDir, "bench_raw_source.mp4")
        val themeId = "bench_loop_theme"

        try {
            generateSyntheticMp4(outputFile = sourceFile, width = 320, height = 240, frameCount = 15)
            sourceFile.copyTo(File(storage.getTempOutputPath(themeId)), overwrite = true)
            val internalUri = storage.commitTempAsset(themeId)

            val firstFrameElapsedMs = CompletableDeferred<Long>()
            val loopTransitions = AtomicInteger(0)
            val playbackError = CompletableDeferred<PlaybackException>()

            val mainHandler = Handler(Looper.getMainLooper())
            var playerRef: ExoPlayer? = null
            var surfaceTextureRef: SurfaceTexture? = null
            var surfaceRef: Surface? = null

            mainHandler.post {
                val startMs = SystemClock.elapsedRealtime()
                val surfaceTexture = SurfaceTexture(0).apply {
                    setDefaultBufferSize(320, 240)
                }
                val surface = Surface(surfaceTexture)
                surfaceTextureRef = surfaceTexture
                surfaceRef = surface

                val player = ExoPlayer.Builder(context)
                    .setLoadControl(buildSub200msLoadControl())
                    .build()
                    .apply {
                        repeatMode = Player.REPEAT_MODE_ONE
                        volume = 0f
                        setVideoSurface(surface)
                        addListener(
                            object : Player.Listener {
                                override fun onRenderedFirstFrame() {
                                    firstFrameElapsedMs.complete(SystemClock.elapsedRealtime() - startMs)
                                }

                                override fun onPositionDiscontinuity(
                                    oldPosition: Player.PositionInfo,
                                    newPosition: Player.PositionInfo,
                                    reason: Int,
                                ) {
                                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                                        loopTransitions.incrementAndGet()
                                    }
                                }

                                override fun onPlayerError(error: PlaybackException) {
                                    playbackError.complete(error)
                                }
                            },
                        )
                        setMediaItem(MediaItem.fromUri(internalUri))
                        prepare()
                        playWhenReady = true
                    }
                playerRef = player
            }

            val renderLatencyMs = withTimeout(3_000L) { firstFrameElapsedMs.await() }
            assertTrue(
                renderLatencyMs < 200L,
                "Expected first frame under 200 ms, actual was ${renderLatencyMs} ms",
            )

            // Wait across ~2 loop cycles (~1.2s for 15 frames at 30fps) to verify gapless REPEAT_MODE_ONE
            kotlinx.coroutines.delay(1_200L)
            assertFalse(playbackError.isCompleted, "Unexpected ExoPlayer error during loop playback")
            assertTrue(loopTransitions.get() >= 1, "Expected at least one seamless REPEAT_MODE_ONE cycle")

            val releaseDone = CompletableDeferred<Unit>()
            mainHandler.post {
                playerRef?.clearVideoSurface()
                playerRef?.stop()
                playerRef?.release()
                surfaceRef?.release()
                surfaceTextureRef?.release()
                releaseDone.complete(Unit)
            }
            withTimeout(2_000L) { releaseDone.await() }
        } finally {
            sourceFile.delete()
            storage.deleteThemeAsset(themeId)
        }
    }

    @Test
    fun androidVideoAssetProcessor_transcodesAndCommitsToPrivateStorage() = runBlocking {
        val context = resolveAppContext()
        val storage = ThemeAssetStorage(context)
        val processor = AndroidVideoAssetProcessor(context, storage)
        val sourceFile = File(context.cacheDir, "transcode_raw_source.mp4")
        val themeId = "transcode_test_theme"

        try {
            generateSyntheticMp4(outputFile = sourceFile, width = 320, height = 240, frameCount = 30)

            val states = withTimeout(10_000L) {
                processor.processVideo(
                    VideoProcessingRequest(
                        themeId = themeId,
                        sourceUri = Uri.fromFile(sourceFile).toString(),
                        trimStartMs = 0L,
                        trimEndMs = 500L,
                        maxShortSidePx = 1080,
                        targetFps = 30,
                        stripAudio = true,
                    ),
                ).toList()
            }

            val terminal = states.lastOrNull()
            assertIs<VideoProcessingState.Completed>(terminal)
            assertEquals(themeId, terminal.asset.themeId)
            assertTrue(terminal.asset.fileSizeBytes > 0L)
            assertTrue(File(storage.getFinalOutputPath(themeId)).exists())

            assertTrue(processor.deleteProcessedAsset(themeId))
            assertFalse(File(storage.getFinalOutputPath(themeId)).exists())
            assertNull(null)
        } finally {
            sourceFile.delete()
            storage.deleteThemeAsset(themeId)
        }
    }

    private fun generateSyntheticMp4(
        outputFile: File,
        width: Int,
        height: Int,
        frameCount: Int,
    ) {
        if (outputFile.exists()) outputFile.delete()

        val fps = 30
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            for (frameIndex in 0 until frameCount) {
                val canvas = inputSurface.lockCanvas(null)
                canvas.drawColor(if (frameIndex % 2 == 0) Color.rgb(24, 32, 64) else Color.rgb(48, 64, 112))
                inputSurface.unlockCanvasAndPost(canvas)
                drainEncoder(encoder, muxer, bufferInfo, endOfStream = false) { newFormat ->
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }.let { if (it >= 0) trackIndex = it }
            }

            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufferInfo, endOfStream = true) { newFormat ->
                if (!muxerStarted) {
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onFormatChanged: (MediaFormat) -> Unit,
    ): Int {
        var resolvedTrack = -1
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                onFormatChanged(encoder.outputFormat)
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                if (encodedData != null && bufferInfo.size > 0 &&
                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                ) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    if (resolvedTrack >= 0) {
                        muxer.writeSampleData(resolvedTrack, encodedData, bufferInfo)
                    }
                }
                val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                encoder.releaseOutputBuffer(status, false)
                if (isEos) break
            }
        }
        return resolvedTrack
    }
}