package com.customcall.feature.customizer.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.ContactRule
import com.customcall.feature.customizer.platform.DiagnosticPermissionType
import com.customcall.feature.customizer.platform.OemAutostartGuide
import com.customcall.feature.customizer.platform.PermissionItemStatus
import com.customcall.feature.customizer.presentation.ContactRuleItemUi
import com.customcall.feature.customizer.presentation.ContactRulesUiState
import com.customcall.feature.customizer.presentation.PermissionWizardUiState
import com.customcall.feature.customizer.presentation.ThemeStudioUiState
import com.customcall.feature.customizer.ui.contacts.ContactRulesScreen
import com.customcall.feature.customizer.ui.permissions.PermissionWizardScreen
import com.customcall.feature.customizer.ui.studio.ThemeStudioScreen

private val SampleThemes = listOf(
    CallThemeConfig(
        id = "theme_preview_1",
        name = "Cobalt Pulse",
        videoUri = "",
        isMuted = true,
        overlayGradientAlpha = 0.35f,
        buttonStyle = ButtonStyleType.SWIPE_PILL,
        accentColorHex = 0xFF3B82F6L,
    ),
    CallThemeConfig(
        id = "theme_preview_2",
        name = "Emerald Minimal",
        videoUri = "",
        isMuted = true,
        overlayGradientAlpha = 0.45f,
        buttonStyle = ButtonStyleType.MINIMAL_GESTURE,
        accentColorHex = 0xFF10B981L,
    ),
)

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ThemeStudioScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ThemeStudioScreen(
            state = ThemeStudioUiState(
                savedThemes = SampleThemes,
                defaultThemeId = "theme_preview_1",
                draftTheme = SampleThemes.first(),
                isEditingExisting = true,
                previewCallerName = "Elena Vance",
                previewCallerIdentifier = "+1 (555) 892-1402",
                previewCallSource = CallSource.WHATSAPP,
            ),
            onStartNewTheme = {},
            onSelectTheme = {},
            onVideoPicked = {},
            onTrimRangeChange = { _, _ -> },
            onThemeNameChange = {},
            onMutedChange = {},
            onGradientAlphaChange = {},
            onButtonStyleChange = {},
            onAccentColorChange = {},
            onPreviewSourceChange = {},
            onSaveTheme = {},
            onCancelTranscoding = {},
            onSetDefaultTheme = {},
            onDeleteTheme = {},
            onDismissStatusMessage = {},
            forceLayoutlibSafePreview = true,
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ContactRulesScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ContactRulesScreen(
            state = ContactRulesUiState(
                rules = listOf(
                    ContactRuleItemUi(
                        rule = ContactRule(
                            id = "rule_1",
                            callerIdentifier = "+15558921402",
                            normalizedName = "elena vance",
                            sourceFilter = CallSource.WHATSAPP,
                            themeId = "theme_preview_1",
                        ),
                        assignedTheme = SampleThemes[0],
                    ),
                    ContactRuleItemUi(
                        rule = ContactRule(
                            id = "rule_2",
                            callerIdentifier = "@marcus_dev",
                            normalizedName = "marcus chen",
                            sourceFilter = CallSource.TELEGRAM,
                            themeId = "theme_preview_2",
                        ),
                        assignedTheme = SampleThemes[1],
                    ),
                ),
                availableThemes = SampleThemes,
            ),
            onSearchQueryChange = {},
            onOpenNewManualRule = {},
            onContactPicked = {},
            onContactPermissionDenied = {},
            onEditRule = {},
            onDeleteRule = {},
            onCloseEditor = {},
            onEditorDisplayNameChange = {},
            onEditorIdentifierChange = {},
            onEditorSourceFilterChange = {},
            onEditorThemeIdChange = {},
            onSaveRule = {},
            onDismissStatusMessage = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun PermissionWizardScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        PermissionWizardScreen(
            state = PermissionWizardUiState(
                permissions = listOf(
                    PermissionItemStatus(
                        type = DiagnosticPermissionType.NOTIFICATION_LISTENER,
                        isGranted = true,
                        isRequiredOnCurrentOs = true,
                    ),
                    PermissionItemStatus(
                        type = DiagnosticPermissionType.OVERLAY,
                        isGranted = true,
                        isRequiredOnCurrentOs = true,
                    ),
                    PermissionItemStatus(
                        type = DiagnosticPermissionType.FULL_SCREEN_INTENT,
                        isGranted = false,
                        isRequiredOnCurrentOs = true,
                    ),
                    PermissionItemStatus(
                        type = DiagnosticPermissionType.BATTERY_OPTIMIZATION,
                        isGranted = false,
                        isRequiredOnCurrentOs = false,
                    ),
                ),
                oemGuide = OemAutostartGuide(
                    manufacturer = "Xiaomi",
                    osSkinName = "HyperOS / MIUI",
                    steps = listOf(
                        "Open Security > Permissions > Autostart and enable this app.",
                        "Set Battery saver to 'No restrictions'.",
                    ),
                    canOpenDirectly = true,
                ),
            ),
            onRefreshDiagnostics = {},
            onOpenPermissionSettings = {},
            onOpenOemAutostartSettings = {},
            onOpenAppDetailsSettings = {},
            onCompleteOnboarding = {},
            onDismissStatusMessage = {},
        )
    }
}