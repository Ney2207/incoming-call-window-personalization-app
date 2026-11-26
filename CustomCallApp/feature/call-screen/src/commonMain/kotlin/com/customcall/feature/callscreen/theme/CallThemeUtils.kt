package com.customcall.feature.callscreen.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Semantic palette shared across incoming call controls and status indicators.
 */
object CallSemanticColors {
    val AnswerGreen = Color(0xFF22C55E)
    val DeclineRed = Color(0xFFEF4444)
    val OnDecline = Color.White
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFE2E8F0)
    val DarkOnLightAccent = Color(0xFF0D1117)
    val FallbackBaseTop = Color(0xFF090C12)
    val FallbackBaseMid = Color(0xFF111724)
    val FallbackBaseBottom = Color(0xFF07090E)
}

/**
 * Converts a theme's [Long] ARGB (or 24-bit RGB) hex value into a Compose [Color].
 * If the upper alpha byte is omitted (`0x00RRGGBB`), full opacity (`0xFF`) is applied.
 */
fun Long.toComposeColor(): Color {
    val hasExplicitAlpha = ((this ushr 24) and 0xFFL) != 0L
    val normalizedArgb = if (hasExplicitAlpha) this else (this or 0xFF000000L)
    return Color(normalizedArgb)
}

/**
 * Selects a high-contrast foreground color ([CallSemanticColors.DarkOnLightAccent] or [Color.White])
 * based on the WCAG relative luminance of this accent [Color].
 */
fun Color.accessibleOnAccentColor(): Color {
    return if (luminance() > 0.45f) {
        CallSemanticColors.DarkOnLightAccent
    } else {
        Color.White
    }
}

/**
 * Clamps [rawAlpha] to a valid `[0f, 1f]` range while guarding against [Float.NaN].
 */
fun normalizeScrimAlpha(rawAlpha: Float, fallback: Float = 0.35f): Float {
    if (rawAlpha.isNaN()) return fallback.coerceIn(0f, 1f)
    return rawAlpha.coerceIn(0f, 1f)
}

/**
 * Builds an **opaque** vertical gradient brush painted behind `VideoBackgroundPlayer`.
 *
 * Guarantees clean rendering when `videoUri` is blank (e.g., `BUILTIN_FALLBACK_THEME`),
 * when a playback error occurs, or before Android's hardware `SurfaceView` renders its first frame.
 */
fun createFallbackBackdropBrush(accentColor: Color): Brush {
    val opaqueAccent = accentColor.copy(alpha = 1f)
    val topStop = lerp(CallSemanticColors.FallbackBaseTop, opaqueAccent, 0.16f)
    val midStop = lerp(CallSemanticColors.FallbackBaseMid, opaqueAccent, 0.08f)
    val bottomStop = lerp(CallSemanticColors.FallbackBaseBottom, opaqueAccent, 0.12f)

    return Brush.verticalGradient(
        0.0f to topStop,
        0.52f to midStop,
        1.0f to bottomStop,
    )
}

/**
 * Builds the upper vertical contrast scrim protecting caller metadata and status badges
 * over bright or high-motion video backgrounds while leaving the center viewport unobstructed.
 */
fun createTopScrimBrush(overlayGradientAlpha: Float): Brush {
    val baseAlpha = normalizeScrimAlpha(overlayGradientAlpha)
    val edgeAlpha = (baseAlpha * 1.45f).coerceIn(0f, 0.92f)

    return Brush.verticalGradient(
        0.0f to Color.Black.copy(alpha = edgeAlpha),
        0.55f to Color.Black.copy(alpha = baseAlpha),
        1.0f to Color.Transparent,
    )
}

/**
 * Builds the lower vertical contrast scrim protecting interactive answer/decline controls
 * while preserving transparency above the control zone.
 */
fun createBottomScrimBrush(overlayGradientAlpha: Float): Brush {
    val baseAlpha = normalizeScrimAlpha(overlayGradientAlpha)
    val edgeAlpha = (baseAlpha * 1.55f).coerceIn(0f, 0.95f)

    return Brush.verticalGradient(
        0.0f to Color.Transparent,
        0.45f to Color.Black.copy(alpha = baseAlpha),
        1.0f to Color.Black.copy(alpha = edgeAlpha),
    )
}