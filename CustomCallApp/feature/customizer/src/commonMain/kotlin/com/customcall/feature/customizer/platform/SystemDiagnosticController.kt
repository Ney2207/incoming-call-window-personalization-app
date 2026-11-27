package com.customcall.feature.customizer.platform

/**
 * System-level capabilities required for reliable lock-screen and overlay call interception.
 */
enum class DiagnosticPermissionType {
    NOTIFICATION_LISTENER,
    OVERLAY,
    FULL_SCREEN_INTENT,
    BATTERY_OPTIMIZATION,
}

/**
 * Real-time inspection result for a single [DiagnosticPermissionType].
 *
 * @property type Target capability being checked.
 * @property isGranted Whether the capability is currently active/granted on the device.
 * @property isRequiredOnCurrentOs Whether the current OS version enforces this gate explicitly
 * (for example, `FULL_SCREEN_INTENT` is explicitly gated on Android 14 / API 34+).
 */
data class PermissionItemStatus(
    val type: DiagnosticPermissionType,
    val isGranted: Boolean,
    val isRequiredOnCurrentOs: Boolean,
)

/**
 * Manufacturer-specific background execution and autostart survival guidance.
 *
 * @property manufacturer Raw or normalized device manufacturer name (e.g., `"Xiaomi"`, `"Samsung"`).
 * @property osSkinName OEM Android skin name (e.g., `"HyperOS / MIUI"`, `"One UI"`).
 * @property steps Ordered user instructions to prevent background process termination.
 * @property canOpenDirectly Whether a direct OEM settings activity resolved on this device.
 */
data class OemAutostartGuide(
    val manufacturer: String,
    val osSkinName: String,
    val steps: List<String>,
    val canOpenDirectly: Boolean,
)

/**
 * Platform abstraction for inspecting call-interception permissions and launching OS/OEM settings.
 */
interface SystemDiagnosticController {

    /**
     * Evaluates current grant states for all [DiagnosticPermissionType] entries.
     */
    suspend fun inspectPermissions(): List<PermissionItemStatus>

    /**
     * Returns manufacturer-tailored autostart and battery survival instructions.
     */
    fun getOemAutostartGuide(): OemAutostartGuide

    /**
     * Opens the system settings screen corresponding to [type], falling back to app details
     * if the manufacturer ROM blocks the specific intent.
     */
    fun openPermissionSettings(type: DiagnosticPermissionType)

    /**
     * Attempts to launch the manufacturer's autostart or background app management screen.
     *
     * @return `true` if a native OEM activity was launched, `false` if manual navigation is needed.
     */
    fun openOemAutostartSettings(): Boolean

    /**
     * Opens the application's system App Info screen as a universal fallback.
     */
    fun openAppDetailsSettings()
}