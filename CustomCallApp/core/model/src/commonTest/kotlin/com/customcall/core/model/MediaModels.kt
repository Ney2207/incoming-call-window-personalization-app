package com.customcall.core.model

import kotlinx.serialization.Serializable

/**
 * Input parameters for transcoding and importing a user-selected video into private app storage[cite: 1].
 *
 * @property themeId Target [CallThemeConfig.id] used to name the internal asset file[cite: 1].
 * @property sourceUri External gallery or content URI selected by the user[cite: 1].
 * @property trimStartMs Start trim offset in milliseconds (inclusive)[cite: 1].
 * @property trimEndMs End trim offset in milliseconds (`null` processes until the end of the source media)[cite: 1].
 * @property maxShortSidePx Maximum short-edge resolution in pixels (defaults to `1080` for 1080p cap)[cite: 1].
 * @property targetFps Maximum output frame rate (defaults to `30` fps)[cite: 1].
 * @property stripAudio When `true`, removes the audio track during transcoding to reduce file size[cite: 1].
 */
@Serializable
data class VideoProcessingRequest(
    val themeId: String,
    val sourceUri: String,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
    val maxShortSidePx: Int = 1080,
    val targetFps: Int = 30,
    val stripAudio: Boolean = false
) {
    init {
        require(trimStartMs >= 0L) { "trimStartMs must be non-negative, got $trimStartMs" }
        require(trimEndMs == null || trimEndMs > trimStartMs) {
            "trimEndMs ($trimEndMs) must be greater than trimStartMs ($trimStartMs)"
        }
        require(maxShortSidePx > 0) { "maxShortSidePx must be positive, got $maxShortSidePx" }
        require(targetFps > 0) { "targetFps must be positive, got $targetFps" }
    }
}

/**
 * Metadata describing a transcoded video stored in private app storage (`filesDir/themes/`)[cite: 1].
 *
 * @property themeId Associated [CallThemeConfig.id][cite: 1].
 * @property outputUri Local file URI ready for low-latency playback[cite: 1].
 * @property durationMs Final trimmed duration in milliseconds[cite: 1].
 * @property width Output video width in pixels[cite: 1].
 * @property height Output video height in pixels[cite: 1].
 * @property fileSizeBytes Size of the transcoded file on disk in bytes[cite: 1].
 */
@Serializable
data class ProcessedVideoAsset(
    val themeId: String,
    val outputUri: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long
)

/**
 * Represents incremental progress and terminal states emitted during background video transcoding[cite: 1].
 */
@Serializable
sealed interface VideoProcessingState {
    /** Transcoding is actively running; [progress] ranges from `0.0f` to `1.0f`[cite: 1]. */
    @Serializable
    data class InProgress(val progress: Float) : VideoProcessingState

    /** Transcoding finished successfully and produced [asset][cite: 1]. */
    @Serializable
    data class Completed(val asset: ProcessedVideoAsset) : VideoProcessingState

    /** Transcoding failed with a diagnostic [message][cite: 1]. */
    @Serializable
    data class Failed(val message: String) : VideoProcessingState
}