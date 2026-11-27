package com.customcall.feature.customizer.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallThemeConfig
import com.customcall.feature.customizer.presentation.CustomizerDefaults
import com.customcall.feature.customizer.presentation.VideoTrimRange
import kotlin.math.roundToInt

// from update in between Step 5 and 6
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeControlsPanel(
    draftTheme: CallThemeConfig,
    pendingVideoDisplayName: String?,
    hasVideoSource: Boolean,
    trimRange: VideoTrimRange,
    isProcessing: Boolean,
    onPickVideoClick: () -> Unit,
    onTrimRangeChange: (startMs: Long, endMs: Long?) -> Unit,
    onThemeNameChange: (String) -> Unit,
    onMutedChange: (Boolean) -> Unit,
    onGradientAlphaChange: (Float) -> Unit,
    onButtonStyleChange: (ButtonStyleType) -> Unit,
    onAccentColorChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Video Source Picker & Optional Trim Range
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Background Video",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                OutlinedButton(
                    onClick = onPickVideoClick,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !pendingVideoDisplayName.isNullOrBlank() -> pendingVideoDisplayName
                            hasVideoSource -> "Replace Selected Video"
                            else -> "Choose Video from Gallery"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (trimRange.totalDurationMs > 1000L) {
                    val maxDurationFloat = trimRange.totalDurationMs.toFloat()
                    val currentStart = trimRange.startMs.coerceIn(0L, trimRange.totalDurationMs - 500L)
                    val currentEnd = (trimRange.endMs ?: trimRange.totalDurationMs)
                        .coerceIn(currentStart + 500L, trimRange.totalDurationMs)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Trim Clip",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${formatDurationMs(currentStart)} – ${formatDurationMs(currentEnd)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    RangeSlider(
                        value = currentStart.toFloat()..currentEnd.toFloat(),
                        onValueChange = { range ->
                            val newStart = range.start.toLong().coerceAtLeast(0L)
                            val newEnd = range.endInclusive.toLong()
                                .coerceAtLeast(newStart + 500L)
                                .coerceAtMost(trimRange.totalDurationMs)
                            onTrimRangeChange(newStart, newEnd)
                        },
                        enabled = !isProcessing,
                        valueRange = 0f..maxDurationFloat,
                    )
                }
            }

            HorizontalDivider()

            // 2. Theme Name
            OutlinedTextField(
                value = draftTheme.name,
                onValueChange = onThemeNameChange,
                enabled = !isProcessing,
                singleLine = true,
                label = { Text("Theme Name") },
                placeholder = { Text("e.g., Neon Cyberpunk, Minimal Dark") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            // 3. Video Audio Track Mute Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (draftTheme.isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "Mute Video Audio",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (draftTheme.isMuted) {
                                "Recommended so system ringtone stays clear"
                            } else {
                                "Plays video audio alongside incoming call"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = draftTheme.isMuted,
                    onCheckedChange = onMutedChange,
                    enabled = !isProcessing,
                )
            }

            HorizontalDivider()

            // 4. Contrast Gradient Scrim Alpha Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val alphaPercent = (draftTheme.overlayGradientAlpha.coerceIn(0f, 1f) * 100f).roundToInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Readability Scrim Contrast",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$alphaPercent%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = draftTheme.overlayGradientAlpha.coerceIn(0f, 1f),
                    onValueChange = onGradientAlphaChange,
                    enabled = !isProcessing,
                    valueRange = 0f..1f,
                )
            }

            HorizontalDivider()

            // 5. Interactive Call Control Style Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Call Control Style",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ButtonStyleOption(
                        label = "Swipe Pill",
                        selected = draftTheme.buttonStyle == ButtonStyleType.SWIPE_PILL,
                        enabled = !isProcessing,
                        onClick = { onButtonStyleChange(ButtonStyleType.SWIPE_PILL) },
                    )
                    ButtonStyleOption(
                        label = "Classic Dual",
                        selected = draftTheme.buttonStyle == ButtonStyleType.CLASSIC_DUAL,
                        enabled = !isProcessing,
                        onClick = { onButtonStyleChange(ButtonStyleType.CLASSIC_DUAL) },
                    )
                    ButtonStyleOption(
                        label = "Minimal Gesture",
                        selected = draftTheme.buttonStyle == ButtonStyleType.MINIMAL_GESTURE,
                        enabled = !isProcessing,
                        onClick = { onButtonStyleChange(ButtonStyleType.MINIMAL_GESTURE) },
                    )
                }
            }

            HorizontalDivider()

            // 6. Accent Color Palette
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Accent Color",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CustomizerDefaults.ACCENT_PALETTE.forEach { colorHex ->
                        val isSelected = draftTheme.accentColorHex == colorHex
                        val swatchColor = Color(colorHex)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    border = if (isSelected) {
                                        BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    },
                                    shape = CircleShape,
                                )
                                .clickable(enabled = !isProcessing) {
                                    onAccentColorChange(colorHex)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected Accent Color",
                                    tint = if (colorHex == 0xFFE2E8F0L || colorHex == 0xFFF59E0BL) {
                                        Color(0xFF0F172A)
                                    } else {
                                        Color.White
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun ButtonStyleOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
    )
}

internal fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val paddedSeconds = seconds.toString().padStart(2, '0')
    return "$minutes:$paddedSeconds"
}