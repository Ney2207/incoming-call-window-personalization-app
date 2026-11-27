package com.customcall.feature.customizer.platform

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSystemDiagnosticController(
    private val context: Context,
) : SystemDiagnosticController {

    override suspend fun inspectPermissions(): List<PermissionItemStatus> =
        withContext(Dispatchers.Default) {
            listOf(
                PermissionItemStatus(
                    type = DiagnosticPermissionType.NOTIFICATION_LISTENER,
                    isGranted = isNotificationListenerEnabled(),
                    isRequiredOnCurrentOs = true,
                ),
                PermissionItemStatus(
                    type = DiagnosticPermissionType.OVERLAY,
                    isGranted = Settings.canDrawOverlays(context),
                    isRequiredOnCurrentOs = true,
                ),
                PermissionItemStatus(
                    type = DiagnosticPermissionType.FULL_SCREEN_INTENT,
                    isGranted = isFullScreenIntentGranted(),
                    isRequiredOnCurrentOs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                ),
                PermissionItemStatus(
                    type = DiagnosticPermissionType.BATTERY_OPTIMIZATION,
                    isGranted = isIgnoringBatteryOptimizations(),
                    isRequiredOnCurrentOs = false,
                ),
            )
        }

    override fun getOemAutostartGuide(): OemAutostartGuide =
        OemAutostartHelper.buildGuide(
            canOpenDirectly = OemAutostartHelper.hasResolvableAutostartActivity(context),
        )

    @SuppressLint("BatteryLife")
    override fun openPermissionSettings(type: DiagnosticPermissionType) {
        val packageUri = Uri.parse("package:${context.packageName}")
        when (type) {
            DiagnosticPermissionType.NOTIFICATION_LISTENER -> {
                if (!tryStartActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
                    openAppDetailsSettings()
                }
            }

            DiagnosticPermissionType.OVERLAY -> {
                val targeted = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
                if (!tryStartActivity(targeted) &&
                    !tryStartActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                ) {
                    openAppDetailsSettings()
                }
            }

            DiagnosticPermissionType.FULL_SCREEN_INTENT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val targeted = Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        packageUri,
                    )
                    if (!tryStartActivity(targeted)) {
                        openAppDetailsSettings()
                    }
                } else {
                    openAppDetailsSettings()
                }
            }

            DiagnosticPermissionType.BATTERY_OPTIMIZATION -> {
                val requestIgnore = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    packageUri,
                )
                if (!tryStartActivity(requestIgnore) &&
                    !tryStartActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                ) {
                    openAppDetailsSettings()
                }
            }
        }
    }

    override fun openOemAutostartSettings(): Boolean =
        OemAutostartHelper.launchAutostartActivity(context)

    override fun openAppDetailsSettings() {
        val detailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
        tryStartActivity(detailsIntent)
    }

    private fun isNotificationListenerEnabled(): Boolean = runCatching {
        val flatListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()

        flatListeners
            .split(':')
            .filter { it.isNotBlank() }
            .any { entry ->
                val component = ComponentName.unflattenFromString(entry)
                component?.packageName == context.packageName ||
                    entry.startsWith("${context.packageName}/")
            }
    }.getOrDefault(false)

    private fun isFullScreenIntentGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true
        }
        return runCatching {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.canUseFullScreenIntent() ?: false
        }.getOrDefault(false)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean = runCatching {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }.getOrDefault(false)

    private fun tryStartActivity(intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}