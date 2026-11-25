package com.customcall.core.database.resolver

import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallThemeConfig

object DefaultThemeProvider {

    const val BUILTIN_FALLBACK_ID = "builtin_default"

    val BUILTIN_FALLBACK_THEME = CallThemeConfig(
        id = BUILTIN_FALLBACK_ID,
        name = "Default Theme",
        videoUri = "",
        isMuted = true,
        overlayGradientAlpha = 0.35f,
        buttonStyle = ButtonStyleType.SWIPE_PILL,
        accentColorHex = 0xFF00E5FFL
    )
}