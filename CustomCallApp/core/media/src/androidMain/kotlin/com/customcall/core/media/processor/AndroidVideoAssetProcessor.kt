package com.customcall.core.media.processor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.customcall.core.media.storage.ThemeAssetStorage
import com.customcall.core.media.storage.isValidPlayableVideoUri
import com.customcall.core.model.ProcessedVideoAsset
import com.customcall.core.model.VideoProcessingRequest
import com.customcall.core.model.VideoProcessingState
import com.customcall.core.model.contract.VideoAssetProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private const val PROGRESS_POLL_INTERVAL_MS = 100L

private data class VideoMetadata(
    val width: Int,
    val height: Int,
    val durationMs: Long,
)

@OptIn(UnstableApi::class)
internal class AndroidVideoAssetProcessor(
    private val context: Context,
    private val storage: ThemeAssetStorage,
) : VideoAssetProcessor {

    override fun processVideo(request: VideoProcessingRequest): Flow<VideoProcessingState> =
        callbackFlow {
            if (request.themeId.isBlank()) {
                trySend(VideoProcessingState.Failed("Theme ID must not be blank."))
                close()
                return@callbackFlow
            }

            if (!request.sourceUri.isValidPlayableVideoUri()) {
                trySend(VideoProcessingState.Failed("Source video URI is empty or invalid."))
                close()
                return@callbackFlow
            }

            val trimEndMs = request.trimEndMs
            if (request.trimStartMs < 0L || (trimEndMs != null && trimEndMs <= request.trimStartMs)) {
                trySend(VideoProcessingState.Failed("Invalid trim range: [${request.trimStartMs}, $trimEndMs]."))
                close()
                return@callbackFlow
            }

            trySend(VideoProcessingState.InProgress(0f))

            val sourceUri = runCatching { Uri.parse(request.sourceUri) }.getOrNull()
            if (sourceUri == null) {
                trySend(VideoProcessingState.Failed("Unable to parse source video URI."))
                close()
                return@callbackFlow
            }
            val sourceMetadata = withContext(Dispatchers.IO) {
                extractSourceMetadata(sourceUri)
            }

            if (sourceMetadata == null) {
                trySend(VideoProcessingState.Failed("Unable to read video metadata from source URI."))
                close()
                return@callbackFlow
            }

            val tempOutputPath = withContext(Dispatchers.IO) {
                storage.deleteTempAsset(request.themeId)
                storage.getTempOutputPath(request.themeId)
            }

            val isFinished = AtomicBoolean(false)

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult,
                        ) {
                            if (!isFinished.compareAndSet(false, true)) return

                            launch(Dispatchers.IO) {
                                runCatching {
                                    val outputUri = storage.commitTempAsset(request.themeId)
                                    val finalPath = storage.getFinalOutputPath(request.themeId)
                                    val finalFile = File(finalPath)
                                    val finalMeta = extractFileMetadata(finalPath)

                                    ProcessedVideoAsset(
                                        themeId = request.themeId,
                                        outputUri = outputUri,
                                        durationMs = finalMeta?.durationMs
                                            ?: exportResult.durationMs.takeIf { it > 0L }
                                            ?: sourceMetadata.durationMs,
                                        width = finalMeta?.width
                                            ?: exportResult.width.takeIf { it > 0 }
                                            ?: sourceMetadata.width,
                                        height = finalMeta?.height
                                            ?: exportResult.height.takeIf { it > 0 }
                                            ?: sourceMetadata.height,
                                        fileSizeBytes = finalFile.length(),
                                    )
                                }.onSuccess { asset ->
                                    trySend(VideoProcessingState.Completed(asset))
                                }.onFailure { error ->
                                    storage.deleteTempAsset(request.themeId)
                                    trySend(
                                        VideoProcessingState.Failed(
                                            error.message ?: "Failed to finalize processed video asset.",
                                        ),
                                    )
                                }
                                close()
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (!isFinished.compareAndSet(false, true)) return

                            launch(Dispatchers.IO) {
                                storage.deleteTempAsset(request.themeId)
                                trySend(
                                    VideoProcessingState.Failed(
                                        exportException.message ?: "Media3 Transformer export failed.",
                                    ),
                                )
                                close()
                            }
                        }
                    },
                )
                .build()

            val editedMediaItem = buildEditedMediaItem(
                sourceUri = sourceUri,
                sourceMetadata = sourceMetadata,
                request = request,
            )

            try {
                transformer.start(editedMediaItem, tempOutputPath)
            } catch (t: Throwable) {
                if (isFinished.compareAndSet(false, true)) {
                    withContext(Dispatchers.IO) {
                        storage.deleteTempAsset(request.themeId)
                    }
                    trySend(VideoProcessingState.Failed(t.message ?: "Failed to start video transcoding."))
                    close()
                }
                return@callbackFlow
            }

            val progressJob = launch {
                val progressHolder = ProgressHolder()
                var lastProgress = 0f
                while (isActive && !isFinished.get()) {
                    delay(PROGRESS_POLL_INTERVAL_MS)
                    if (isFinished.get()) break

                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val normalized = (progressHolder.progress / 100f).coerceIn(0f, 0.99f)
                        if (normalized > lastProgress) {
                            lastProgress = normalized
                            trySend(VideoProcessingState.InProgress(normalized))
                        }
                    }
                }
            }

            awaitClose {
                progressJob.cancel()
                if (isFinished.compareAndSet(false, true)) {
                    runCatching { transformer.cancel() }
                    storage.deleteTempAsset(request.themeId)
                }
            }
        }.flowOn(Dispatchers.Main)

    override suspend fun deleteProcessedAsset(themeId: String): Boolean =
        withContext(Dispatchers.IO) {
            storage.deleteThemeAsset(themeId)
        }

    override suspend fun clearUnusedAssets(activeThemeIds: Set<String>): Int =
        withContext(Dispatchers.IO) {
            storage.clearUnusedAssets(activeThemeIds)
        }

    private fun buildEditedMediaItem(
        sourceUri: Uri,
        sourceMetadata: VideoMetadata,
        request: VideoProcessingRequest,
    ): EditedMediaItem {
        val mediaItemBuilder = MediaItem.Builder().setUri(sourceUri)
        val startMs = request.trimStartMs.coerceAtLeast(0L)
        val endMs = request.trimEndMs

        if (startMs > 0L || (endMs != null && endMs > startMs)) {
            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
            if (endMs != null && endMs > startMs) {
                clippingBuilder.setEndPositionMs(endMs)
            }
            mediaItemBuilder.setClippingConfiguration(clippingBuilder.build())
        }

        val videoEffects = buildList<Effect> {
            val sourceShortSide = minOf(sourceMetadata.width, sourceMetadata.height)
            if (request.maxShortSidePx > 0 && sourceShortSide > request.maxShortSidePx) {
                val scale = request.maxShortSidePx.toFloat() / sourceShortSide.toFloat()
                val targetWidth = ((sourceMetadata.width * scale).roundToInt() and 1.inv()).coerceAtLeast(2)
                val targetHeight = ((sourceMetadata.height * scale).roundToInt() and 1.inv()).coerceAtLeast(2)
                add(
                    Presentation.createForWidthAndHeight(
                        targetWidth,
                        targetHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT,
                    ),
                )
            }
            if (request.targetFps > 0) {
                add(FrameDropEffect.createDefaultFrameDropEffect(request.targetFps.toFloat()))
            }
        }

        return EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveAudio(request.stripAudio)
            .setEffects(Effects(/* audioProcessors = */ emptyList(), videoEffects))
            .build()
    }

    private fun extractSourceMetadata(uri: Uri): VideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            if (!hasVideo.equals("yes", ignoreCase = true)) return null
            readMetadataFromRetriever(retriever)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractFileMetadata(filePath: String): VideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            readMetadataFromRetriever(retriever)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readMetadataFromRetriever(retriever: MediaMetadataRetriever): VideoMetadata? {
        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: return null
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L

        val isSwapped = rotation == 90 || rotation == 270
        val displayWidth = if (isSwapped) rawHeight else rawWidth
        val displayHeight = if (isSwapped) rawWidth else rawHeight

        if (displayWidth <= 0 || displayHeight <= 0) return null
        return VideoMetadata(
            width = displayWidth,
            height = displayHeight,
            durationMs = durationMs,
        )
    }
}