package com.customcall.feature.callscreen.components.controls

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.customcall.feature.callscreen.theme.CallSemanticColors
import com.customcall.feature.callscreen.theme.accessibleOnAccentColor
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Bidirectional horizontal swipe pill (`ButtonStyleType.SWIPE_PILL`).
 *
 * Dragging the center thumb past 72% of the right track triggers [onAnswer]; dragging past 72%
 * of the left track triggers [onDecline]. Incomplete gestures spring back smoothly to center.
 */
@Composable
fun SwipePillControls(
    accentColor: Color,
    hasAnswerAction: Boolean,
    hasDeclineAction: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val onAccentColor = remember(accentColor) { accentColor.accessibleOnAccentColor() }

    val thumbOffsetPx = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var crossedThreshold by remember { mutableStateOf(false) }

    val idleBreathTransition = rememberInfiniteTransition(label = "swipePillBreath")
    val idleBreathScale by idleBreathTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swipePillThumbScale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(72.dp),
        ) {
            val trackPaddingDp = 6.dp
            val thumbSizeDp = 60.dp
            val maxTravelPx = remember(maxWidth, density) {
                with(density) {
                    ((maxWidth - (trackPaddingDp * 2) - thumbSizeDp) / 2f)
                        .toPx()
                        .coerceAtLeast(1f)
                }
            }
            val triggerThresholdPx = maxTravelPx * 0.72f
            val minClampPx = if (hasDeclineAction) -maxTravelPx else 0f
            val maxClampPx = if (hasAnswerAction) maxTravelPx else 0f

            val progress = (thumbOffsetPx.value / maxTravelPx).coerceIn(-1f, 1f)
            val answerHighlight = progress.coerceAtLeast(0f)
            val declineHighlight = (-progress).coerceAtLeast(0f)

            val trackBorderColor = when {
                answerHighlight > 0.15f -> accentColor.copy(alpha = 0.35f + 0.55f * answerHighlight)
                declineHighlight > 0.15f -> CallSemanticColors.DeclineRed.copy(alpha = 0.35f + 0.55f * declineHighlight)
                else -> Color.White.copy(alpha = 0.20f)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.48f))
                    .border(width = 1.5.dp, color = trackBorderColor, shape = CircleShape)
                    .padding(horizontal = trackPaddingDp),
                contentAlignment = Alignment.Center,
            ) {
                // Left (Decline) and Right (Answer) target endpoints + directional chevrons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.graphicsLayer {
                            alpha = if (hasDeclineAction) 0.55f + 0.45f * declineHighlight else 0.18f
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    CallSemanticColors.DeclineRed.copy(
                                        alpha = 0.22f + 0.68f * declineHighlight,
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = "Swipe left to decline",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.graphicsLayer {
                            alpha = if (hasAnswerAction) 0.55f + 0.45f * answerHighlight else 0.18f
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(18.dp),
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    accentColor.copy(
                                        alpha = 0.22f + 0.68f * answerHighlight,
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Swipe right to answer",
                                tint = if (answerHighlight > 0.5f) onAccentColor else Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                // Draggable center thumb
                val activeThumbScale = if (isDragging) 1.08f else idleBreathScale
                val thumbColor = when {
                    declineHighlight > 0.65f -> CallSemanticColors.DeclineRed
                    else -> accentColor
                }
                val thumbIcon = when {
                    declineHighlight > 0.55f -> Icons.Rounded.CallEnd
                    else -> Icons.Rounded.Call
                }
                val thumbTint = if (declineHighlight > 0.65f) Color.White else onAccentColor

                Box(
                    modifier = Modifier
                        .size(thumbSizeDp)
                        .graphicsLayer {
                            translationX = thumbOffsetPx.value
                            scaleX = activeThumbScale
                            scaleY = activeThumbScale
                        }
                        .clip(CircleShape)
                        .background(thumbColor)
                        .border(
                            width = 1.5.dp,
                            color = Color.White.copy(alpha = 0.35f),
                            shape = CircleShape,
                        )
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    val next = (thumbOffsetPx.value + delta).coerceIn(minClampPx, maxClampPx)
                                    thumbOffsetPx.snapTo(next)

                                    val nowPastThreshold = abs(next) >= triggerThresholdPx
                                    if (nowPastThreshold && !crossedThreshold) {
                                        crossedThreshold = true
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else if (!nowPastThreshold && crossedThreshold) {
                                        crossedThreshold = false
                                    }
                                }
                            },
                            onDragStarted = {
                                isDragging = true
                                crossedThreshold = false
                            },
                            onDragStopped = {
                                isDragging = false
                                val current = thumbOffsetPx.value
                                scope.launch {
                                    when {
                                        hasAnswerAction && current >= triggerThresholdPx -> {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            thumbOffsetPx.animateTo(
                                                targetValue = maxClampPx,
                                                animationSpec = tween(durationMillis = 90),
                                            )
                                            onAnswer()
                                        }
                                        hasDeclineAction && current <= -triggerThresholdPx -> {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            thumbOffsetPx.animateTo(
                                                targetValue = minClampPx,
                                                animationSpec = tween(durationMillis = 90),
                                            )
                                            onDecline()
                                        }
                                        else -> {
                                            thumbOffsetPx.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                            )
                                        }
                                    }
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = thumbIcon,
                        contentDescription = "Swipe to answer or decline",
                        tint = thumbTint,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = when {
                hasAnswerAction && hasDeclineAction -> "Swipe right to answer  •  left to decline"
                hasAnswerAction -> "Swipe right to answer"
                hasDeclineAction -> "Swipe left to decline"
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = CallSemanticColors.TextSecondary.copy(alpha = 0.88f),
            maxLines = 1,
        )
    }
}