package com.customcall.feature.customizer.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallThemeConfig
import com.customcall.feature.customizer.platform.PickedVideoAsset
import com.customcall.feature.customizer.platform.rememberVideoPickerLauncher
import com.customcall.feature.customizer.presentation.ThemeStudioUiState
import com.customcall.feature.customizer.presentation.TranscodingStatus
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioScreen(
    state: ThemeStudioUiState,
    onStartNewTheme: () -> Unit,
    onSelectTheme: (CallThemeConfig) -> Unit,
    onVideoPicked: (PickedVideoAsset?) -> Unit,
    onTrimRangeChange: (startMs: Long, endMs: Long?) -> Unit,
    onThemeNameChange: (String) -> Unit,
    onMutedChange: (Boolean) -> Unit,
    onGradientAlphaChange: (Float) -> Unit,
    onButtonStyleChange: (ButtonStyleType) -> Unit,
    onAccentColorChange: (Long) -> Unit,
    onPreviewSourceChange: (CallSource) -> Unit,
    onSaveTheme: () -> Unit,
    onCancelTranscoding: () -> Unit,
    onSetDefaultTheme: (String) -> Unit,
    onDeleteTheme: (String) -> Unit,
    onDismissStatusMessage: () -> Unit,
    modifier: Modifier = Modifier,
    forceLayoutlibSafePreview: Boolean = false,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val launchVideoPicker = rememberVideoPickerLauncher(onVideoPicked = onVideoPicked)

    LaunchedEffect(state.statusMessage) {
        val message = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissStatusMessage()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Theme Studio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (state.isEditingExisting) "Editing Saved Theme" else "New Call Theme",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onStartNewTheme,
                        enabled = !state.isProcessing,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. Saved Themes Library Carousel
            if (state.savedThemes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Saved Themes (${state.savedThemes.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(
                            items = state.savedThemes,
                            key = { it.id },
                        ) { theme ->
                            SavedThemeChipCard(
                                theme = theme,
                                isSelected = theme.id == state.draftTheme.id,
                                isGlobalDefault = theme.id == state.defaultThemeId,
                                enabled = !state.isProcessing,
                                onSelect = { onSelectTheme(theme) },
                                onSetDefault = { onSetDefaultTheme(theme.id) },
                                onDelete = { onDeleteTheme(theme.id) },
                            )
                        }
                    }
                }
            }

            // 2. Live Interactive Incoming Call Preview
            ThemePreviewCard(
                draftTheme = state.draftTheme,
                previewVideoUri = state.previewVideoUri,
                previewCallerName = state.previewCallerName,
                previewCallerIdentifier = state.previewCallerIdentifier,
                previewCallSource = state.previewCallSource,
                onPreviewSourceChange = onPreviewSourceChange,
                forceLayoutlibSafePreview = forceLayoutlibSafePreview,
            )

            // 3. Customization Controls
            ThemeControlsPanel(
                draftTheme = state.draftTheme,
                pendingVideoDisplayName = state.pendingVideoDisplayName,
                hasVideoSource = state.hasVideoSource,
                trimRange = state.trimRange,
                isProcessing = state.isProcessing,
                onPickVideoClick = launchVideoPicker,
                onTrimRangeChange = onTrimRangeChange,
                onThemeNameChange = onThemeNameChange,
                onMutedChange = onMutedChange,
                onGradientAlphaChange = onGradientAlphaChange,
                onButtonStyleChange = onButtonStyleChange,
                onAccentColorChange = onAccentColorChange,
            )

            // 4. Video Transcoding Progress Banner
            when (val status = state.transcodingStatus) {
                is TranscodingStatus.InProgress -> {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Optimizing video for instant call wake...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${(status.progress * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                            )
                            OutlinedButton(
                                onClick = onCancelTranscoding,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Cancel Processing")
                            }
                        }
                    }
                }

                is TranscodingStatus.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = status.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                TranscodingStatus.Idle -> Unit
            }

            // 5. Primary Save & Set Global Default Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isEditingExisting && state.draftTheme.id != state.defaultThemeId) {
                    OutlinedButton(
                        onClick = { onSetDefaultTheme(state.draftTheme.id) },
                        enabled = !state.isProcessing,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set Default")
                    }
                }

                Button(
                    onClick = onSaveTheme,
                    enabled = !state.isProcessing,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MovieCreation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            state.isProcessing -> "Processing..."
                            state.isEditingExisting -> "Update Theme"
                            else -> "Save Theme"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SavedThemeChipCard(
    theme: CallThemeConfig,
    isSelected: Boolean,
    isGlobalDefault: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(196.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(theme.accentColorHex)),
                )
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isGlobalDefault) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    TextButton(
                        onClick = onSetDefault,
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = "Make Default",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    enabled = enabled,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${theme.name}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}