package com.customcall.feature.customizer.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallState
import com.customcall.core.model.CallThemeConfig
import com.customcall.feature.callscreen.IncomingCallScreen
import com.customcall.feature.customizer.presentation.CustomizerDefaults

@Composable
fun ThemePreviewCard(
    draftTheme: CallThemeConfig,
    previewVideoUri: String,
    previewCallerName: String,
    previewCallerIdentifier: String,
    previewCallSource: CallSource,
    onPreviewSourceChange: (CallSource) -> Unit,
    modifier: Modifier = Modifier,
    forceLayoutlibSafePreview: Boolean = false,
) {
    var isPlaybackEnabled by remember { mutableStateOf(true) }
    var simulatedCallState by remember { mutableStateOf(CallState.RINGING) }
    var previewSessionNonce by remember { mutableIntStateOf(0) }

    // Reset simulated ringing state whenever the user switches button styles or call sources
    LaunchedEffect(draftTheme.buttonStyle, previewCallSource) {
        simulatedCallState = CallState.RINGING
        previewSessionNonce += 1
    }

    val effectiveTheme = remember(draftTheme, previewVideoUri, forceLayoutlibSafePreview) {
        draftTheme.copy(
            videoUri = if (forceLayoutlibSafePreview) "" else previewVideoUri,
        )
    }

    val previewEvent = remember(
        previewCallerName,
        previewCallerIdentifier,
        previewCallSource,
        previewSessionNonce,
    ) {
        CustomizerDefaults.buildPreviewCallEvent(
            callerName = previewCallerName,
            callerIdentifier = previewCallerIdentifier,
            source = previewCallSource,
        ).copy(callId = "preview_call_session_$previewSessionNonce")
    }

    val isVideoActuallyPlaying = !forceLayoutlibSafePreview &&
        isPlaybackEnabled &&
        effectiveTheme.videoUri.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Call source simulator pills (GSM, WhatsApp, Telegram)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreviewSourceOption(
                label = "Phone",
                selected = previewCallSource == CallSource.PHONE_GSM,
                onClick = { onPreviewSourceChange(CallSource.PHONE_GSM) },
            )
            PreviewSourceOption(
                label = "WhatsApp",
                selected = previewCallSource == CallSource.WHATSAPP,
                onClick = { onPreviewSourceChange(CallSource.WHATSAPP) },
            )
            PreviewSourceOption(
                label = "Telegram",
                selected = previewCallSource == CallSource.TELEGRAM,
                onClick = { onPreviewSourceChange(CallSource.TELEGRAM) },
            )
        }

        // Constrained 9:19.5 phone viewport embedding Block 4's IncomingCallScreen
        Surface(
            modifier = Modifier
                .widthIn(max = 276.dp)
                .fillMaxWidth()
                .aspectRatio(9f / 19.5f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp)),
            ) {
                IncomingCallScreen(
                    event = previewEvent,
                    theme = effectiveTheme,
                    onAnswer = { simulatedCallState = CallState.ANSWERED },
                    onDecline = { simulatedCallState = CallState.DECLINED },
                    modifier = Modifier.fillMaxSize(),
                    callState = simulatedCallState,
                    isVideoPlaying = isVideoActuallyPlaying,
                    applySystemBarInsets = false,
                )
            }
        }

        // Interactive preview playback & gesture reset controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (effectiveTheme.videoUri.isNotBlank() && !forceLayoutlibSafePreview) {
                TextButton(
                    onClick = { isPlaybackEnabled = !isPlaybackEnabled },
                ) {
                    Icon(
                        imageVector = if (isPlaybackEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlaybackEnabled) "Pause Video" else "Play Video",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            if (simulatedCallState != CallState.RINGING) {
                TextButton(
                    onClick = {
                        simulatedCallState = CallState.RINGING
                        previewSessionNonce += 1
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reset Call Gesture",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewSourceOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
    )
}