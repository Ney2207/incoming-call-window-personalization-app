package com.customcall.feature.customizer.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberVideoPickerLauncher(
    onVideoPicked: (PickedVideoAsset?) -> Unit,
): () -> Unit = {
    onVideoPicked(null)
}

@Composable
actual fun rememberContactPickerLauncher(
    onContactPicked: (PickedDeviceContact?) -> Unit,
    onPermissionDenied: () -> Unit,
): () -> Unit = {
    onContactPicked(null)
}

class IosSystemDiagnosticController : SystemDiagnosticController {

    override suspend fun inspectPermissions(): List<PermissionItemStatus> = listOf(
        PermissionItemStatus(
            type = DiagnosticPermissionType.NOTIFICATION_LISTENER,
            isGranted = true,
            isRequiredOnCurrentOs = false,
        ),
        PermissionItemStatus(
            type = DiagnosticPermissionType.OVERLAY,
            isGranted = true,
            isRequiredOnCurrentOs = false,
        ),
        PermissionItemStatus(
            type = DiagnosticPermissionType.FULL_SCREEN_INTENT,
            isGranted = true,
            isRequiredOnCurrentOs = false,
        ),
        PermissionItemStatus(
            type = DiagnosticPermissionType.BATTERY_OPTIMIZATION,
            isGranted = true,
            isRequiredOnCurrentOs = false,
        ),
    )

    override fun getOemAutostartGuide(): OemAutostartGuide = OemAutostartGuide(
        manufacturer = "Apple",
        osSkinName = "iOS",
        steps = listOf(
            "iOS manages incoming CallKit VoIP sessions automatically without background autostart restrictions.",
        ),
        canOpenDirectly = false,
    )

    override fun openPermissionSettings(type: DiagnosticPermissionType) = Unit

    override fun openOemAutostartSettings(): Boolean = false

    override fun openAppDetailsSettings() = Unit
}