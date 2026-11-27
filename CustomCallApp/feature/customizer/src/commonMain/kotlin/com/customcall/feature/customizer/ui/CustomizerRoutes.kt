package com.customcall.feature.customizer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.customcall.feature.customizer.presentation.ContactRulesViewModel
import com.customcall.feature.customizer.presentation.PermissionWizardViewModel
import com.customcall.feature.customizer.presentation.ThemeStudioViewModel
import com.customcall.feature.customizer.ui.contacts.ContactRulesScreen
import com.customcall.feature.customizer.ui.permissions.PermissionWizardScreen
import com.customcall.feature.customizer.ui.studio.ThemeStudioScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navigation tabs available inside [CustomizerHubScreen].
 */
enum class CustomizerTab(
    val label: String,
    val icon: ImageVector,
) {
    STUDIO("Studio", Icons.Rounded.MovieCreation),
    CONTACTS("Contacts", Icons.Rounded.Contacts),
    PERMISSIONS("Setup", Icons.Rounded.Security),
}

/**
 * Koin-injected entry point for the Theme Studio screen.
 */
@Composable
fun ThemeStudioRoute(
    modifier: Modifier = Modifier,
    viewModel: ThemeStudioViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    ThemeStudioScreen(
        state = state,
        onStartNewTheme = viewModel::startNewTheme,
        onSelectTheme = viewModel::selectThemeForEditing,
        onVideoPicked = viewModel::onVideoPicked,
        onTrimRangeChange = viewModel::updateTrimRange,
        onThemeNameChange = viewModel::updateThemeName,
        onMutedChange = viewModel::updateMuted,
        onGradientAlphaChange = viewModel::updateOverlayGradientAlpha,
        onButtonStyleChange = viewModel::updateButtonStyle,
        onAccentColorChange = viewModel::updateAccentColor,
        onPreviewSourceChange = { source ->
            viewModel.updatePreviewMetadata(
                callerName = state.previewCallerName,
                callerIdentifier = state.previewCallerIdentifier,
                source = source,
            )
        },
        onSaveTheme = viewModel::saveDraftTheme,
        onCancelTranscoding = viewModel::cancelActiveTranscoding,
        onSetDefaultTheme = viewModel::setAsDefaultTheme,
        onDeleteTheme = viewModel::deleteTheme,
        onDismissStatusMessage = viewModel::dismissStatusMessage,
        modifier = modifier,
    )
}

/**
 * Koin-injected entry point for the Contact Rules Manager screen.
 */
@Composable
fun ContactRulesRoute(
    modifier: Modifier = Modifier,
    viewModel: ContactRulesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    ContactRulesScreen(
        state = state,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onOpenNewManualRule = viewModel::openNewManualRuleEditor,
        onContactPicked = viewModel::openEditorFromPickedContact,
        onContactPermissionDenied = viewModel::notifyPermissionDenied,
        onEditRule = viewModel::openEditRuleEditor,
        onDeleteRule = viewModel::deleteContactRule,
        onCloseEditor = viewModel::closeEditor,
        onEditorDisplayNameChange = viewModel::updateEditorDisplayName,
        onEditorIdentifierChange = viewModel::updateEditorIdentifier,
        onEditorSourceFilterChange = viewModel::updateEditorSourceFilter,
        onEditorThemeIdChange = viewModel::updateEditorThemeId,
        onSaveRule = viewModel::saveContactRule,
        onDismissStatusMessage = viewModel::dismissStatusMessage,
        modifier = modifier,
    )
}

/**
 * Koin-injected entry point for the OEM Permission & Survival Diagnostic Wizard.
 */
@Composable
fun PermissionWizardRoute(
    modifier: Modifier = Modifier,
    viewModel: PermissionWizardViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    PermissionWizardScreen(
        state = state,
        onRefreshDiagnostics = viewModel::refreshDiagnostics,
        onOpenPermissionSettings = viewModel::openPermissionSettings,
        onOpenOemAutostartSettings = viewModel::openOemAutostartSettings,
        onOpenAppDetailsSettings = viewModel::openAppDetailsSettings,
        onCompleteOnboarding = viewModel::completeOnboarding,
        onDismissStatusMessage = viewModel::dismissStatusMessage,
        modifier = modifier,
    )
}

/**
 * Self-contained multi-tab host uniting Theme Studio, Contact Rules, and Permission Diagnostics.
 */
@Composable
fun CustomizerHubScreen(
    modifier: Modifier = Modifier,
    initialTab: CustomizerTab = CustomizerTab.STUDIO,
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    val permissionViewModel: PermissionWizardViewModel = koinViewModel()
    val permissionState by permissionViewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                CustomizerTab.entries.forEach { tab ->
                    val showWarningBadge = tab == CustomizerTab.PERMISSIONS &&
                        permissionState.permissions.isNotEmpty() &&
                        !permissionState.allRequiredGranted

                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            if (showWarningBadge) {
                                BadgedBox(badge = { Badge() }) {
                                    Icon(imageVector = tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(imageVector = tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                CustomizerTab.STUDIO -> ThemeStudioRoute(modifier = Modifier.fillMaxSize())
                CustomizerTab.CONTACTS -> ContactRulesRoute(modifier = Modifier.fillMaxSize())
                CustomizerTab.PERMISSIONS -> PermissionWizardRoute(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = permissionViewModel,
                )
            }
        }
    }
}