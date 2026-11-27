package com.customcall.feature.customizer.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.customcall.feature.customizer.platform.DiagnosticPermissionType
import com.customcall.feature.customizer.platform.OemAutostartGuide
import com.customcall.feature.customizer.platform.PermissionItemStatus
import com.customcall.feature.customizer.presentation.PermissionWizardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionWizardScreen(
    state: PermissionWizardUiState,
    onRefreshDiagnostics: () -> Unit,
    onOpenPermissionSettings: (DiagnosticPermissionType) -> Unit,
    onOpenOemAutostartSettings: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    onDismissStatusMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-evaluate permissions immediately whenever the user returns from System Settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefreshDiagnostics()
    }

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
                            text = "Call Interception Setup",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "System permissions & OEM background survival",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshDiagnostics) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Permission Status",
                        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Readiness Summary Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (state.allRequiredGranted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = if (state.allRequiredGranted) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.WarningAmber
                        },
                        contentDescription = null,
                        tint = if (state.allRequiredGranted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.allRequiredGranted) {
                                "Core Call Interception Ready"
                            } else {
                                "Action Required for Reliable Call Popups"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state.allRequiredGranted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                        Text(
                            text = "${state.grantedCount} of ${state.permissions.size} system checks enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.allRequiredGranted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                    }
                }
            }

            // Individual Permission Cards
            state.permissions.forEach { item ->
                PermissionStatusCard(
                    item = item,
                    onConfigureClick = { onOpenPermissionSettings(item.type) },
                )
            }

            // Manufacturer-Specific Background Survival Guide
            state.oemGuide?.let { guide ->
                OemSurvivalCard(
                    guide = guide,
                    onOpenOemSettings = onOpenOemAutostartSettings,
                    onOpenAppInfo = onOpenAppDetailsSettings,
                )
            }

            // Onboarding Completion CTA
            Button(
                onClick = onCompleteOnboarding,
                enabled = state.allRequiredGranted && !state.isOnboardingCompleted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isOnboardingCompleted) {
                        "Setup Completed"
                    } else {
                        "Confirm & Complete Setup"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionStatusCard(
    item: PermissionItemStatus,
    onConfigureClick: () -> Unit,
) {
    val meta = item.type.toPresentationMeta()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = meta.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meta.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            item.isGranted -> "Active"
                            item.isRequiredOnCurrentOs -> "Required"
                            else -> "Recommended"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            item.isGranted -> MaterialTheme.colorScheme.primary
                            item.isRequiredOnCurrentOs -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                    )
                }

                if (item.isGranted) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    FilledTonalButton(
                        onClick = onConfigureClick,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Grant")
                    }
                }
            }

            Text(
                text = meta.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OemSurvivalCard(
    guide: OemAutostartGuide,
    onOpenOemSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "${guide.osSkinName} Background Survival",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Device OEM: ${guide.manufacturer}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            guide.steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (guide.canOpenDirectly) {
                    FilledTonalButton(
                        onClick = onOpenOemSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open OEM Settings")
                    }
                }

                OutlinedButton(
                    onClick = onOpenAppInfo,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Open App Info")
                }
            }
        }
    }
}

private data class PermissionPresentationMeta(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private fun DiagnosticPermissionType.toPresentationMeta(): PermissionPresentationMeta = when (this) {
    DiagnosticPermissionType.NOTIFICATION_LISTENER -> PermissionPresentationMeta(
        title = "Notification Listener Access",
        description = "Detects incoming WhatsApp and Telegram call notifications and captures Answer/Decline actions.",
        icon = Icons.Rounded.NotificationsActive,
    )

    DiagnosticPermissionType.OVERLAY -> PermissionPresentationMeta(
        title = "Display Over Other Apps",
        description = "Renders the custom video call screen immediately over unlocked apps and heads-up banners.",
        icon = Icons.Rounded.Layers,
    )

    DiagnosticPermissionType.FULL_SCREEN_INTENT -> PermissionPresentationMeta(
        title = "Full-Screen Call Alerts (Android 14+)",
        description = "Allows incoming calls to wake the lock screen directly into your custom video interface.",
        icon = Icons.Rounded.Fullscreen,
    )

    DiagnosticPermissionType.BATTERY_OPTIMIZATION -> PermissionPresentationMeta(
        title = "Unrestricted Background Battery",
        description = "Prevents aggressive Doze termination so incoming calls trigger in under 200 ms.",
        icon = Icons.Rounded.BatteryChargingFull,
    )
}