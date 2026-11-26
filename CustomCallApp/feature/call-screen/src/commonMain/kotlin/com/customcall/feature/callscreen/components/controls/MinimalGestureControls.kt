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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.customcall.feature.callscreen.theme.CallSemanticColors
import com.customcall.feature.callscreen.theme.accessibleOnAccentColor
import kotlinx.coroutines.launch

/**
 * Minimalist gesture control capsule (`ButtonStyleType.MINIMAL_GESTURE`) leaving maximum
 * screen area unobstructed for the background video.
 *
 * Users swipe upward on the floating capsule to answer (or tap it as an accessible fallback),
 * and swipe downward or tap the compact secondary pill below to decline.
 */
@Composable
fun MinimalGestureControls(
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

    val dragOffsetY = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val upThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    val downThresholdPx = remember(density) { with(density) { 44.dp.toPx() } }
    val minClampPx = if (hasAnswerAction) -upThresholdPx * 1.35f else 0f
    val maxClampPx = if (hasDeclineAction) downThresholdPx * 1.25f else 0f

    val floatTransition = rememberInfiniteTransition(label = "minimalFloatTransition")
    val floatOffsetPx by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = with(density) { -6.dp.toPx() },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "minimalFloatOffset",
    )

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hasAnswerAction) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        translationY = if (isDragging) 0f else floatOffsetPx
                    },
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = dragOffsetY.value + if (isDragging) 0f else (floatOffsetPx * 0.5f)
                    }
                    .clip(CircleShape)
                    .background(accentColor)
                    .border(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = 0.32f),
                        shape = CircleShape,
                    )
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                val next = (dragOffsetY.value + delta).coerceIn(minClampPx, maxClampPx)
                                dragOffsetY.snapTo(next)
                            }
                        },
                        onDragStarted = { isDragging = true },
                        onDragStopped = {
                            isDragging = false
                            val current = dragOffsetY.value
                            scope.launch {
                                when {
                                    current <= -upThresholdPx -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAnswer()
                                    }
                                    hasDeclineAction && current >= downThresholdPx -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDecline()
                                    }
                                    else -> {
                                        dragOffsetY.animateTo(
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
                    )
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswer()
                        },
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = "Swipe up or tap to answer",
                        tint = onAccentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Swipe up to answer",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = onAccentColor,
                        maxLines = 1,
                    )
                }
            }
        }

        if (hasDeclineAction) {
            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDecline()
                        },
                    ),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.44f),
                border = BorderStroke(1.dp, CallSemanticColors.DeclineRed.copy(alpha = 0.55f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "Decline call",
                        tint = CallSemanticColors.DeclineRed,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Decline",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = CallSemanticColors.TextPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}