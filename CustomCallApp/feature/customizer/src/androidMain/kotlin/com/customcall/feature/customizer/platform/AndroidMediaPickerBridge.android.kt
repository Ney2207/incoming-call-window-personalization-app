package com.customcall.feature.customizer.platform

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberVideoPickerLauncher(
    onVideoPicked: (PickedVideoAsset?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnVideoPicked by rememberUpdatedState(onVideoPicked)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) {
            currentOnVideoPicked(null)
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            val asset = withContext(Dispatchers.IO) {
                extractVideoMetadata(context = context, uri = uri)
            }
            currentOnVideoPicked(asset)
        }
    }

    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
        )
    }
}

private fun extractVideoMetadata(
    context: Context,
    uri: Uri,
): PickedVideoAsset {
    val durationMs = readVideoDurationMs(context = context, uri = uri)
    val displayName = readDisplayName(context = context, uri = uri)
    return PickedVideoAsset(
        uri = uri.toString(),
        durationMs = durationMs.coerceAtLeast(0L),
        displayName = displayName,
    )
}

private fun readVideoDurationMs(
    context: Context,
    uri: Uri,
): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

private fun readDisplayName(
    context: Context,
    uri: Uri,
): String? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(0)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }
}.getOrNull()