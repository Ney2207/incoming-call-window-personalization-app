package com.customcall.core.media.processor

import com.customcall.core.media.storage.ThemeAssetStorage
import com.customcall.core.media.storage.isValidPlayableVideoUri
import com.customcall.core.model.ProcessedVideoAsset
import com.customcall.core.model.VideoProcessingRequest
import com.customcall.core.model.VideoProcessingState
import com.customcall.core.model.contract.VideoAssetProcessor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetExportPreset1920x1080
import platform.AVFoundation.AVAssetExportPresetHighestQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCancelled
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetExportSessionStatusExporting
import platform.AVFoundation.AVAssetExportSessionStatusFailed
import platform.AVFoundation.AVAssetExportSessionStatusWaiting
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val IOS_PROGRESS_POLL_INTERVAL_MS = 100L
private const val DEFAULT_TIMESCALE = 600

private data class IosVideoMetadata(
    val width: Int,
    val height: Int,
    val durationMs: Long,
)

@OptIn(ExperimentalForeignApi::class)
internal class IosVideoAssetProcessor(
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

            val sourceUrl = resolveNsUrl(request.sourceUri)
            if (sourceUrl == null) {
                trySend(VideoProcessingState.Failed("Unable to parse source video URI."))
                close()
                return@callbackFlow
            }

            trySend(VideoProcessingState.InProgress(0f))

            val sourceAsset = AVURLAsset.URLAssetWithURL(sourceUrl, options = null)
            val sourceMetadata = extractAssetMetadata(sourceAsset)
            if (sourceMetadata == null) {
                trySend(VideoProcessingState.Failed("Source asset contains no readable video track."))
                close()
                return@callbackFlow
            }

            storage.deleteTempAsset(request.themeId)
            val tempOutputPath = storage.getTempOutputPath(request.themeId)
            val tempOutputUrl = NSURL.fileURLWithPath(tempOutputPath)

            val exportAsset: AVAsset = if (request.stripAudio) {
                buildVideoOnlyComposition(sourceAsset) ?: sourceAsset
            } else {
                sourceAsset
            }

            val presetName = if (request.maxShortSidePx in 1..1080) {
                AVAssetExportPreset1920x1080
            } else {
                AVAssetExportPresetHighestQuality
            }

            val exportSession = AVAssetExportSession.exportSessionWithAsset(
                asset = exportAsset,
                presetName = presetName,
            )

            if (exportSession == null) {
                trySend(VideoProcessingState.Failed("Unable to initialize AVAssetExportSession."))
                close()
                return@callbackFlow
            }

            exportSession.outputURL = tempOutputUrl
            exportSession.outputFileType = AVFileTypeMPEG4
            exportSession.shouldOptimizeForNetworkUse = true

            val startSeconds = request.trimStartMs / 1000.0
            val totalSeconds = sourceMetadata.durationMs / 1000.0
            if (request.trimStartMs > 0L || (trimEndMs != null && trimEndMs > request.trimStartMs)) {
                val endSeconds = if (trimEndMs != null) {
                    (trimEndMs / 1000.0).coerceAtMost(totalSeconds.coerceAtLeast(startSeconds))
                } else {
                    totalSeconds.coerceAtLeast(startSeconds)
                }
                val durationSeconds = (endSeconds - startSeconds).coerceAtLeast(0.0)
                exportSession.timeRange = CMTimeRangeMake(
                    start = CMTimeMakeWithSeconds(startSeconds, DEFAULT_TIMESCALE),
                    duration = CMTimeMakeWithSeconds(durationSeconds, DEFAULT_TIMESCALE),
                )
            }

            val progressJob = launch {
                var lastProgress = 0f
                while (isActive) {
                    delay(IOS_PROGRESS_POLL_INTERVAL_MS)
                    val status = exportSession.status
                    if (status != AVAssetExportSessionStatusExporting &&
                        status != AVAssetExportSessionStatusWaiting
                    ) {
                        break
                    }
                    val normalized = exportSession.progress.coerceIn(0f, 0.99f)
                    if (normalized > lastProgress) {
                        lastProgress = normalized
                        trySend(VideoProcessingState.InProgress(normalized))
                    }
                }
            }

            exportSession.exportAsynchronouslyWithCompletionHandler {
                progressJob.cancel()
                when (exportSession.status) {
                    AVAssetExportSessionStatusCompleted -> {
                        val outputUri = storage.commitTempAsset(request.themeId)
                        val finalPath = storage.getFinalOutputPath(request.themeId)
                        val finalAsset = AVURLAsset.URLAssetWithURL(
                            NSURL.fileURLWithPath(finalPath),
                            options = null,
                        )
                        val finalMeta = extractAssetMetadata(finalAsset) ?: sourceMetadata
                        val fileSize = queryFileSizeBytes(finalPath)

                        trySend(
                            VideoProcessingState.Completed(
                                ProcessedVideoAsset(
                                    themeId = request.themeId,
                                    outputUri = outputUri,
                                    durationMs = finalMeta.durationMs,
                                    width = finalMeta.width,
                                    height = finalMeta.height,
                                    fileSizeBytes = fileSize,
                                ),
                            ),
                        )
                    }

                    AVAssetExportSessionStatusFailed,
                    AVAssetExportSessionStatusCancelled,
                    -> {
                        storage.deleteTempAsset(request.themeId)
                        val reason = exportSession.error?.localizedDescription
                            ?: "iOS video export failed or was cancelled."
                        trySend(VideoProcessingState.Failed(reason))
                    }

                    else -> {
                        storage.deleteTempAsset(request.themeId)
                        trySend(VideoProcessingState.Failed("Unexpected export terminal state."))
                    }
                }
                close()
            }

            awaitClose {
                progressJob.cancel()
                val status = exportSession.status
                if (status == AVAssetExportSessionStatusExporting ||
                    status == AVAssetExportSessionStatusWaiting
                ) {
                    exportSession.cancelExport()
                    storage.deleteTempAsset(request.themeId)
                }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun deleteProcessedAsset(themeId: String): Boolean =
        withContext(Dispatchers.IO) {
            storage.deleteThemeAsset(themeId)
        }

    override suspend fun clearUnusedAssets(activeThemeIds: Set<String>): Int =
        withContext(Dispatchers.IO) {
            storage.clearUnusedAssets(activeThemeIds)
        }

    private fun buildVideoOnlyComposition(sourceAsset: AVAsset): AVMutableComposition? {
        val sourceVideoTrack = sourceAsset.tracksWithMediaType(AVMediaTypeVideo)
            .firstOrNull() as? AVAssetTrack ?: return null

        val composition = AVMutableComposition.composition()
        val compositionVideoTrack = composition.addMutableTrackWithMediaType(
            mediaType = AVMediaTypeVideo,
            preferredTrackID = kCMPersistentTrackID_Invalid,
        ) ?: return null

        val fullRange = CMTimeRangeMake(start = kCMTimeZero, duration = sourceAsset.duration)
        val inserted = compositionVideoTrack.insertTimeRange(
            timeRange = fullRange,
            ofTrack = sourceVideoTrack,
            atTime = kCMTimeZero,
            error = null,
        )
        if (!inserted) return null

        compositionVideoTrack.setPreferredTransform(sourceVideoTrack.preferredTransform)
        return composition
    }

    private fun extractAssetMetadata(asset: AVAsset): IosVideoMetadata? {
        val videoTrack = asset.tracksWithMediaType(AVMediaTypeVideo)
            .firstOrNull() as? AVAssetTrack ?: return null

        val (rawWidth, rawHeight) = videoTrack.naturalSize.useContents { width to height }
        if (rawWidth <= 0.0 || rawHeight <= 0.0) return null

        val transformedRect = CGRectApplyAffineTransform(
            CGRectMake(0.0, 0.0, rawWidth, rawHeight),
            videoTrack.preferredTransform,
        )
        val (displayWidth, displayHeight) = transformedRect.useContents {
            abs(size.width).roundToInt() to abs(size.height).roundToInt()
        }

        val durationSeconds = CMTimeGetSeconds(asset.duration)
        val durationMs = if (durationSeconds.isFinite() && durationSeconds > 0.0) {
            (durationSeconds * 1000.0).roundToLong()
        } else {
            0L
        }

        return IosVideoMetadata(
            width = displayWidth.coerceAtLeast(1),
            height = displayHeight.coerceAtLeast(1),
            durationMs = durationMs,
        )
    }

    private fun queryFileSizeBytes(filePath: String): Long {
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(filePath, error = null)
        val sizeNumber = attributes?.get(NSFileSize) as? NSNumber
        return sizeNumber?.longLongValue ?: 0L
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