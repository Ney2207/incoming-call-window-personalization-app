package com.customcall.core.model.contract

import com.customcall.core.model.VideoProcessingRequest
import com.customcall.core.model.VideoProcessingState
import kotlinx.coroutines.flow.Flow

/**
 * Media ingestion and background transcoding contract implemented by `:core:media`.
 *
 * Converts external gallery media into trimmed, resolution-capped (`<= 1080p`, `<= 30 fps`)
 * files inside private internal app storage (`filesDir/themes/`) for sub-200ms playback.
 */
interface VideoAssetProcessor {

    /**
     * Starts transcoding the media specified by [request] and emits incremental
     * [VideoProcessingState.InProgress] updates followed by a terminal
     * [VideoProcessingState.Completed] or [VideoProcessingState.Failed] state.
     */
    fun processVideo(request: VideoProcessingRequest): Flow<VideoProcessingState>

    /**
     * Deletes the internal video asset file associated with [themeId], if present.
     *
     * @return `true` if the file existed and was deleted, `false` otherwise.
     */
    suspend fun deleteProcessedAsset(themeId: String): Boolean

    /**
     * Removes any orphaned internal theme video files whose theme IDs are not in [activeThemeIds].
     *
     * @return The number of orphaned asset files deleted from internal storage.
     */
    suspend fun clearUnusedAssets(activeThemeIds: Set<String>): Int
}