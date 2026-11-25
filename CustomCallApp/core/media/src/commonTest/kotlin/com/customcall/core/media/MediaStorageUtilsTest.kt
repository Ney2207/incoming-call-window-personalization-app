package com.customcall.core.media

import com.customcall.core.media.storage.buildTempAssetFileName
import com.customcall.core.media.storage.buildThemeAssetFileName
import com.customcall.core.media.storage.extractThemeIdFromFileName
import com.customcall.core.media.storage.isValidPlayableVideoUri
import com.customcall.core.media.storage.sanitizeThemeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaStorageUtilsTest {

    @Test
    fun isValidPlayableVideoUri_rejectsBlankAndNullStrings() {
        assertFalse("".isValidPlayableVideoUri())
        assertFalse("   \t\n  ".isValidPlayableVideoUri())
        assertFalse("null".isValidPlayableVideoUri())
        assertFalse("NULL".isValidPlayableVideoUri())
    }

    @Test
    fun isValidPlayableVideoUri_acceptsFileAndContentUris() {
        assertTrue("file:///data/user/0/com.customcall/files/themes/cyberpunk.mp4".isValidPlayableVideoUri())
        assertTrue("content://media/external/video/media/42".isValidPlayableVideoUri())
    }

    @Test
    fun sanitizeThemeId_preventsPathTraversalAndUnsafeCharacters() {
        assertEquals("___etc_passwd", sanitizeThemeId("../../etc/passwd"))
        assertEquals("neon_theme__01", sanitizeThemeId("neon theme #01"))
        assertEquals("theme_default", sanitizeThemeId("..."))
        assertEquals("theme_default", sanitizeThemeId("   "))
    }

    @Test
    fun assetFileNameBuildersAndExtractor_roundTripValidThemeIds() {
        val fileName = buildThemeAssetFileName("anime_loop_01")
        val tempFileName = buildTempAssetFileName("anime_loop_01")

        assertEquals("anime_loop_01.mp4", fileName)
        assertEquals("anime_loop_01.tmp.mp4", tempFileName)
        assertEquals("anime_loop_01", extractThemeIdFromFileName(fileName))
        assertNull(extractThemeIdFromFileName(tempFileName))
        assertNull(extractThemeIdFromFileName("notes.txt"))
    }
}