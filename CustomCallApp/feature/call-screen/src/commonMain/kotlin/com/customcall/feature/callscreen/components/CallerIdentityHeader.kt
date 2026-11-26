package com.customcall.feature.callscreen.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.customcall.core.model.CallState
import com.customcall.core.model.IncomingCallEvent
import com.customcall.feature.callscreen.theme.CallSemanticColors

/**
 * Extracts deterministic 1-to-2 character uppercase initials from [callerName], falling back
 * to [callerIdentifier] (or `"?"`) when no alphanumeric name tokens are available.
 */
fun extractInitials(
    callerName: String,
    callerIdentifier: String = "",
): String {
    val tokens = callerName
        .trim()
        .split(Regex("\\s+"))
        .map { token -> token.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }

    if (tokens.size >= 2) {
        val first = tokens.first().first().uppercaseChar()
        val last = tokens.last().first().uppercaseChar()
        return "$first$last"
    }

    if (tokens.size == 1) {
        return tokens.first().take(2).uppercase()
    }

    val cleanIdentifier = callerIdentifier.filter { it.isLetterOrDigit() }
    if (cleanIdentifier.isNotEmpty()) {
        return cleanIdentifier.take(2).uppercase()
    }

    return "?"
}

/**
 * **Z-Index Layer 3 (Caller Identity Layer):** Upper-screen caller metadata section rendering
 * the call-source badge, circular avatar (or monogram fallback), high-contrast caller name,
 * secondary identifier, and 60/120 fps animated ringing indicator.
 */
@Composable
fun CallerIdentityHeader(
    event: IncomingCallEvent,
    accentColor: Color,
    modifier: Modifier = Modifier,
    callState: CallState = CallState.RINGING,
    avatarBitmap: ImageBitmap? = null,
) {
    val displayName = remember(event.callerName, event.callerIdentifier) {
        event.callerName.trim().ifEmpty {
            event.callerIdentifier.trim().ifEmpty { "Unknown Caller" }
        }
    }
    val secondaryIdentifier = remember(event.callerIdentifier, displayName) {
        val trimmed = event.callerIdentifier.trim()
        trimmed.takeIf { it.isNotEmpty() && !it.equals(displayName, ignoreCase = true) }
    }
    val initials = remember(event.callerName, event.callerIdentifier) {
        extractInitials(event.callerName, event.callerIdentifier)
    }

    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.55f),
            offset = Offset(0f, 2f),
            blurRadius = 8f,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CallSourceBadge(
            source = event.source,
            accentColor = accentColor,
        )

        Spacer(modifier = Modifier.height(20.dp))

        CallerAvatar(
            initials = initials,
            displayName = displayName,
            accentColor = accentColor,
            isRinging = callState == CallState.RINGING,
            avatarBitmap = avatarBitmap,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                shadow = textShadow,
            ),
            color = CallSemanticColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (secondaryIdentifier != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = secondaryIdentifier,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    shadow = textShadow,
                ),
                color = CallSemanticColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        CallStatusIndicator(
            callState = callState,
            accentColor = accentColor,
        )
    }
}

@Composable
private fun CallerAvatar(
    initials: String,
    displayName: String,
    accentColor: Color,
    isRinging: Boolean,
    avatarBitmap: ImageBitmap?,
) {
    val pulseTransition = rememberInfiniteTransition(label = "avatarPulseTransition")
    val ringScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "avatarRingScale",
    )
    val ringAlpha by pulseTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "avatarRingAlpha",
    )

    val avatarFillBrush = remember(accentColor) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.36f),
                Color(0xFF131926).copy(alpha = 0.84f),
            ),
        )
    }

    Box(
        modifier = Modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isRinging) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .graphicsLayer {
                        scaleX = ringScale
                        scaleY = ringScale
                        alpha = ringAlpha
                    }
                    .border(
                        width = 2.dp,
                        color = accentColor,
                        shape = CircleShape,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(avatarFillBrush)
                .border(
                    width = 1.5.dp,
                    color = accentColor.copy(alpha = 0.72f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CallStatusIndicator(
    callState: CallState,
    accentColor: Color,
) {
    val dotTransition = rememberInfiniteTransition(label = "ringingDotTransition")
    val dotScale by dotTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringingDotScale",
    )
    val dotAlpha by dotTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringingDotAlpha",
    )

    val statusText = when (callState) {
        CallState.RINGING -> "Incoming call..."
        CallState.ANSWERED -> "Connecting..."
        CallState.DECLINED -> "Call declined"
        CallState.ENDED -> "Call ended"
    }

    val indicatorColor = when (callState) {
        CallState.RINGING, CallState.ANSWERED -> accentColor
        CallState.DECLINED, CallState.ENDED -> CallSemanticColors.DeclineRed
    }

    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        if (callState == CallState.RINGING) {
                            scaleX = dotScale
                            scaleY = dotScale
                            alpha = dotAlpha
                        }
                    }
                    .clip(CircleShape)
                    .background(indicatorColor),
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = CallSemanticColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}