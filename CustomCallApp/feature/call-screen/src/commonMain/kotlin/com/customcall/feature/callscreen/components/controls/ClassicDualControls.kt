package com.customcall.feature.callscreen.components.controls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.customcall.feature.callscreen.theme.CallSemanticColors
import com.customcall.feature.callscreen.theme.accessibleOnAccentColor

/**
 * Classic dual-button layout rendering side-by-side circular **Decline** and **Answer**
 * action targets with tactile press-spring scaling and a subtle pulse on the Answer button.
 */
@Composable
fun ClassicDualControls(
    accentColor: Color,
    hasAnswerAction: Boolean,
    hasDeclineAction: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onAccentColor = remember(accentColor) { accentColor.accessibleOnAccentColor() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasDeclineAction) {
            CircularCallActionButton(
                label = "Decline",
                icon = Icons.Rounded.CallEnd,
                containerColor = CallSemanticColors.DeclineRed,
                contentColor = CallSemanticColors.OnDecline,
                enablePulse = false,
                onClick = onDecline,
            )
        }

        if (hasAnswerAction) {
            CircularCallActionButton(
                label = "Answer",
                icon = Icons.Rounded.Call,
                containerColor = accentColor,
                contentColor = onAccentColor,
                enablePulse = true,
                onClick = onAnswer,
            )
        }
    }
}

@Composable
private fun CircularCallActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    enablePulse: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dualButtonPressScale",
    )

    val pulseTransition = rememberInfiniteTransition(label = "dualButtonPulse")
    val haloScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dualButtonHaloScale",
    )
    val haloAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dualButtonHaloAlpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (enablePulse && !isPressed) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer {
                            scaleX = haloScale
                            scaleY = haloScale
                            alpha = haloAlpha
                        }
                        .border(
                            width = 2.dp,
                            color = containerColor,
                            shape = CircleShape,
                        ),
                )
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(CircleShape)
                    .background(containerColor)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.24f),
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.36f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = CallSemanticColors.TextPrimary,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}