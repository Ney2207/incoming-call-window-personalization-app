package com.customcall.feature.customizer.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Classifies Android OEM families and resolves native autostart/background survival screens.
 */
internal object OemAutostartHelper {

    enum class OemFamily {
        XIAOMI,
        SAMSUNG,
        OPPO_REALME_ONEPLUS,
        VIVO,
        HUAWEI_HONOR,
        GENERIC,
    }

    fun classifyOem(
        manufacturer: String,
        brand: String = "",
    ): OemFamily {
        val token = "${manufacturer.trim()} ${brand.trim()}".lowercase()
        return when {
            token.contains("xiaomi") || token.contains("redmi") || token.contains("poco") ->
                OemFamily.XIAOMI
            token.contains("samsung") ->
                OemFamily.SAMSUNG
            token.contains("oppo") || token.contains("realme") || token.contains("oneplus") ->
                OemFamily.OPPO_REALME_ONEPLUS
            token.contains("vivo") || token.contains("iqoo") ->
                OemFamily.VIVO
            token.contains("huawei") || token.contains("honor") ->
                OemFamily.HUAWEI_HONOR
            else ->
                OemFamily.GENERIC
        }
    }

    fun buildGuide(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty(),
        canOpenDirectly: Boolean,
    ): OemAutostartGuide {
        val normalizedManufacturer = manufacturer.trim().ifBlank { "Android" }
        return when (classifyOem(manufacturer = manufacturer, brand = brand)) {
            OemFamily.XIAOMI -> OemAutostartGuide(
                manufacturer = normalizedManufacturer,
                osSkinName = "HyperOS / MIUI",
                steps = listOf(
                    "Open Security > Permissions > Autostart (or App Info > Autostart) and enable this app.",
                    "Open App Info > Battery saver and select 'No restrictions'.",
                    "In Recent Apps, pull down on this app card and tap the Lock icon so HyperOS/MIUI keeps the call listener alive.",
                ),
                canOpenDirectly = canOpenDirectly,
            )

            OemFamily.SAMSUNG -> OemAutostartGuide(
                manufacturer = normalizedManufacturer,
                osSkinName = "One UI",
                steps = listOf(
                    "Open Settings > Battery > Background usage limits > Never auto sleeping apps and add this app.",
                    "Open App Info > Battery and select 'Unrestricted'.",
                    "Verify 'Appear on top' and 'Notifications' remain enabled under App Info.",
                ),
                canOpenDirectly = canOpenDirectly,
            )

            OemFamily.OPPO_REALME_ONEPLUS -> OemAutostartGuide(
                manufacturer = normalizedManufacturer,
                osSkinName = "ColorOS / OxygenOS",
                steps = listOf(
                    "Open App Info > Battery usage and enable 'Allow foreground activity' and 'Allow background activity'.",
                    "Enable 'Auto launch' in Phone Manager / App Management so VoIP call interception survives reboots.",
                ),
                canOpenDirectly = canOpenDirectly,
            )

            OemFamily.VIVO -> OemAutostartGuide(
                manufacturer = normalizedManufacturer,
                osSkinName = "Funtouch OS / OriginOS",
                steps = listOf(
                    "Open iManager > App Manager > Autostart and toggle this app ON.",
                    "Open Settings > Battery > Background power consumption management and select 'High background power usage'.",
                ),
                canOpenDirectly = canOpenDirectly,
            )

            OemFamily.HUAWEI_HONOR -> OemAutostartGuide(
                manufacturer = normalizedManufacturer,
                osSkinName = "EMUI / MagicOS",
                steps = listOf(
                    "Open Settings > Battery > App launch, locate this app, and switch from Automatic to 'Manage manually'.",
                    "Enable all three toggles: Auto-launch, Secondary launch, and Run in background.",
                ),
                canOpenDirectly = canOpenDirectly,
            )

            OemFamily.GENERIC -> OemAutostartGuide(
                manufacturer = normalizedManufacturer,
                osSkinName = "Standard Android",
                steps = listOf(
                    "Set Battery usage to 'Unrestricted' in App Info so Android does not suspend the call listener in Doze mode.",
                    "Ensure 'Display over other apps' and 'Notification access' remain active after system updates.",
                ),
                canOpenDirectly = canOpenDirectly,
            )
        }
    }

    fun candidateComponents(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty(),
    ): List<ComponentName> = when (classifyOem(manufacturer = manufacturer, brand = brand)) {
        OemFamily.XIAOMI -> listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings",
            ),
        )

        OemFamily.SAMSUNG -> listOf(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity",
            ),
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity",
            ),
            ComponentName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.battery.ui.BatteryActivity",
            ),
        )

        OemFamily.OPPO_REALME_ONEPLUS -> listOf(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            ),
            ComponentName(
                "com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
            ),
            ComponentName(
                "com.coloros.oppoguardelf",
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
            ),
        )

        OemFamily.VIVO -> listOf(
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            ),
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            ),
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ),
        )

        OemFamily.HUAWEI_HONOR -> listOf(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity",
            ),
        )

        OemFamily.GENERIC -> emptyList()
    }

    fun hasResolvableAutostartActivity(context: Context): Boolean =
        candidateComponents().any { component ->
            val intent = Intent().setComponent(component)
            resolveActivityCompat(context.packageManager, intent) != null
        }

    fun launchAutostartActivity(context: Context): Boolean {
        for (component in candidateComponents()) {
            val intent = Intent()
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (resolveActivityCompat(context.packageManager, intent) != null) {
                val started = runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
                if (started) return true
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun resolveActivityCompat(
        packageManager: PackageManager,
        intent: Intent,
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
    } else {
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
}