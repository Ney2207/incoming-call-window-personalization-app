package com.customcall.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallThemeConfig

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val videoUri: String,
    val isMuted: Boolean = true,
    val overlayGradientAlpha: Float = 0.35f,
    val buttonStyle: ButtonStyleType = ButtonStyleType.SWIPE_PILL,
    val accentColorHex: Long
)

fun ThemeEntity.toDomain(): CallThemeConfig = CallThemeConfig(
    id = id,
    name = name,
    videoUri = videoUri,
    isMuted = isMuted,
    overlayGradientAlpha = overlayGradientAlpha.coerceIn(0.0f, 1.0f),
    buttonStyle = buttonStyle,
    accentColorHex = accentColorHex
)

fun CallThemeConfig.toEntity(): ThemeEntity = ThemeEntity(
    id = id,
    name = name,
    videoUri = videoUri,
    isMuted = isMuted,
    overlayGradientAlpha = overlayGradientAlpha.coerceIn(0.0f, 1.0f),
    buttonStyle = buttonStyle,
    accentColorHex = accentColorHex
)