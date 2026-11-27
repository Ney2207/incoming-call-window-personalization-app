package com.customcall.feature.customizer.platform

import androidx.compose.runtime.Composable

/**
 * Metadata for a user-selected video returned by the platform gallery picker.
 *
 * @property uri Platform content or file URI to feed into [com.customcall.core.model.contract.VideoAssetProcessor].
 * @property durationMs Total media duration in milliseconds, or `0L` if unavailable prior to transcoding.
 * @property displayName Optional human-readable filename from the media provider.
 */
data class PickedVideoAsset(
    val uri: String,
    val durationMs: Long,
    val displayName: String? = null,
)

/**
 * Registers a platform video picker launcher bound to the current Compose lifecycle.
 *
 * Invoking the returned lambda launches the system gallery picker. When the user selects a video,
 * [onVideoPicked] receives a [PickedVideoAsset]; when cancelled or invalid, it receives `null`.
 */
@Composable
expect fun rememberVideoPickerLauncher(
    onVideoPicked: (PickedVideoAsset?) -> Unit,
): () -> Unit