package com.customcall.core.media.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual class ThemeAssetStorage {

    private val fileManager: NSFileManager
        get() = NSFileManager.defaultManager

    private fun resolveThemesDirPath(): String {
        val baseDir = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: "/tmp"

        val themesDir = "$baseDir/$THEMES_DIRECTORY_NAME"
        if (!fileManager.fileExistsAtPath(themesDir)) {
            fileManager.createDirectoryAtPath(
                path = themesDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        return themesDir
    }

    actual fun getFinalOutputPath(themeId: String): String =
        "${resolveThemesDirPath()}/${buildThemeAssetFileName(themeId)}"

    actual fun getFinalOutputUri(themeId: String): String =
        NSURL.fileURLWithPath(getFinalOutputPath(themeId)).absoluteString
            ?: "file://${getFinalOutputPath(themeId)}"

    actual fun getTempOutputPath(themeId: String): String =
        "${resolveThemesDirPath()}/${buildTempAssetFileName(themeId)}"

    actual fun commitTempAsset(themeId: String): String {
        val tempPath = getTempOutputPath(themeId)
        val finalPath = getFinalOutputPath(themeId)

        if (fileManager.fileExistsAtPath(finalPath)) {
            fileManager.removeItemAtPath(finalPath, error = null)
        }
        fileManager.moveItemAtPath(tempPath, toPath = finalPath, error = null)
        return getFinalOutputUri(themeId)
    }

    actual fun deleteTempAsset(themeId: String): Boolean {
        val tempPath = getTempOutputPath(themeId)
        return fileManager.fileExistsAtPath(tempPath) &&
            fileManager.removeItemAtPath(tempPath, error = null)
    }

    actual fun deleteThemeAsset(themeId: String): Boolean {
        deleteTempAsset(themeId)
        val finalPath = getFinalOutputPath(themeId)
        return fileManager.fileExistsAtPath(finalPath) &&
            fileManager.removeItemAtPath(finalPath, error = null)
    }

    actual fun clearUnusedAssets(activeThemeIds: Set<String>): Int {
        val dirPath = resolveThemesDirPath()
        val entries = fileManager.contentsOfDirectoryAtPath(dirPath, error = null)
            ?.filterIsInstance<String>()
            ?: return 0

        val activeSanitizedIds = activeThemeIds.mapTo(HashSet(activeThemeIds.size)) {
            sanitizeThemeId(it)
        }

        var deletedCount = 0
        for (name in entries) {
            val fullPath = "$dirPath/$name"
            if (name.endsWith(TEMP_VIDEO_SUFFIX, ignoreCase = true)) {
                fileManager.removeItemAtPath(fullPath, error = null)
                continue
            }

            val assetThemeId = extractThemeIdFromFileName(name)
            if (assetThemeId != null && assetThemeId !in activeSanitizedIds) {
                if (fileManager.removeItemAtPath(fullPath, error = null)) {
                    deletedCount++
                }
            }
        }
        return deletedCount
    }
}