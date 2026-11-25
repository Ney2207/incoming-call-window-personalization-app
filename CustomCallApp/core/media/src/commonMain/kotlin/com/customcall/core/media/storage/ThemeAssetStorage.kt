package com.customcall.core.media.storage

internal const val THEMES_DIRECTORY_NAME = "themes"
internal const val THEME_VIDEO_EXTENSION = "mp4"
internal const val TEMP_VIDEO_SUFFIX = ".tmp.mp4"

private val UNSAFE_FILENAME_CHARS_REGEX = Regex("[^a-zA-Z0-9._-]")

/**
 * Validates whether a URI string points to a playable media source.
 * Returns `false` for blank or empty URIs (such as `DefaultThemeProvider.BUILTIN_FALLBACK_THEME`).
 */
internal fun String.isValidPlayableVideoUri(): Boolean {
    val trimmed = trim()
    return trimmed.isNotEmpty() && !trimmed.equals("null", ignoreCase = true)
}

/**
 * Sanitizes a [themeId] so it can be safely used as a filename inside private theme storage
 * without risk of path traversal.
 */
internal fun sanitizeThemeId(themeId: String): String {
    val cleaned = themeId
        .trim()
        .replace(UNSAFE_FILENAME_CHARS_REGEX, "_")
        .trim('.')
        .replace(Regex("\\.{2,}"), "_")
    return cleaned.ifEmpty { "theme_default" }
}

internal fun buildThemeAssetFileName(themeId: String): String =
    "${sanitizeThemeId(themeId)}.$THEME_VIDEO_EXTENSION"

internal fun buildTempAssetFileName(themeId: String): String =
    "${sanitizeThemeId(themeId)}$TEMP_VIDEO_SUFFIX"

/**
 * Extracts the sanitized theme ID from a committed `.mp4` filename, returning `null`
 * for temporary (`.tmp.mp4`) or non-MP4 files.
 */
internal fun extractThemeIdFromFileName(fileName: String): String? {
    if (fileName.endsWith(TEMP_VIDEO_SUFFIX, ignoreCase = true)) return null
    val suffix = ".$THEME_VIDEO_EXTENSION"
    if (!fileName.endsWith(suffix, ignoreCase = true)) return null
    return fileName.dropLast(suffix.length).takeIf { it.isNotEmpty() }
}

/**
 * Platform-specific manager for private internal theme video files (`filesDir/themes/`).
 */
internal expect class ThemeAssetStorage {
    fun getFinalOutputPath(themeId: String): String
    fun getFinalOutputUri(themeId: String): String
    fun getTempOutputPath(themeId: String): String
    fun commitTempAsset(themeId: String): String
    fun deleteTempAsset(themeId: String): Boolean
    fun deleteThemeAsset(themeId: String): Boolean
    fun clearUnusedAssets(activeThemeIds: Set<String>): Int
}