package com.customcall.core.media.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

internal actual class ThemeAssetStorage(
    private val context: Context,
) {
    private val fileLock = Any()

    private fun resolveThemesDir(): File {
        val dir = File(context.filesDir, THEMES_DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    actual fun getFinalOutputPath(themeId: String): String =
        File(resolveThemesDir(), buildThemeAssetFileName(themeId)).absolutePath

    actual fun getFinalOutputUri(themeId: String): String =
        Uri.fromFile(File(getFinalOutputPath(themeId))).toString()

    actual fun getTempOutputPath(themeId: String): String =
        File(resolveThemesDir(), buildTempAssetFileName(themeId)).absolutePath

    actual fun commitTempAsset(themeId: String): String = synchronized(fileLock) {
        val tempFile = File(getTempOutputPath(themeId))
        val finalFile = File(getFinalOutputPath(themeId))

        if (!tempFile.exists()) {
            throw IOException("Temporary transcoded file not found at ${tempFile.absolutePath}")
        }

        if (finalFile.exists() && !finalFile.delete()) {
            throw IOException("Failed to overwrite existing theme asset at ${finalFile.absolutePath}")
        }

        if (!tempFile.renameTo(finalFile)) {
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        }

        Uri.fromFile(finalFile).toString()
    }

    actual fun deleteTempAsset(themeId: String): Boolean = synchronized(fileLock) {
        val tempFile = File(getTempOutputPath(themeId))
        tempFile.exists() && tempFile.delete()
    }

    actual fun deleteThemeAsset(themeId: String): Boolean = synchronized(fileLock) {
        val tempFile = File(getTempOutputPath(themeId))
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val finalFile = File(getFinalOutputPath(themeId))
        finalFile.exists() && finalFile.delete()
    }

    actual fun clearUnusedAssets(activeThemeIds: Set<String>): Int = synchronized(fileLock) {
        val dir = resolveThemesDir()
        val files = dir.listFiles() ?: return@synchronized 0
        val activeSanitizedIds = activeThemeIds.mapTo(HashSet(activeThemeIds.size)) {
            sanitizeThemeId(it)
        }

        var deletedCount = 0
        for (file in files) {
            if (!file.isFile) continue
            val name = file.name

            if (name.endsWith(TEMP_VIDEO_SUFFIX, ignoreCase = true)) {
                file.delete()
                continue
            }

            val assetThemeId = extractThemeIdFromFileName(name)
            if (assetThemeId != null && assetThemeId !in activeSanitizedIds) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        deletedCount
    }
}