package com.rootslab.localllm

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Central place for every system-level permission and vendor-specific deep
 * link used by the onboarding wizard.
 */
object PermissionManager {

    // --- runtime permissions -------------------------------------------------

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        activity.requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_NOTIFICATIONS
        )
    }

    // --- battery optimisation ------------------------------------------------

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { openAppDetails(activity) }
    }

    // --- vendor specific (MIUI / HyperOS) ------------------------------------

    /**
     * Opens the Xiaomi "Autostart" manager. On HyperOS the component name
     * changed, therefore several candidates are probed in order.
     */
    fun openAutostartSettings(activity: Activity) {
        val candidates = listOf(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            Intent("com.miui.securitycenter.action.APP_PERMISSION_EDIT").apply {
                package = "com.miui.securitycenter"
                putExtra("extra_pkgname", activity.packageName)
            },
            Intent("miui.intent.action.OP_AUTO_START").apply {
                putExtra("package_name", activity.packageName)
                putExtra("package_label", activity.getString(R.string.app_name))
            }
        )

        for (intent in candidates) {
            if (intent.resolveActivity(activity.packageManager) != null) {
                runCatching { activity.startActivity(intent) }
                return
            }
        }
        openAppDetails(activity)
    }

    /**
     * Opens the Xiaomi security centre where "No restrictions" for the battery
     * saver can be selected.
     */
    fun openXiaomiSecurityCenter(activity: Activity) {
        val candidates = listOf(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings"
            ),
            Intent("com.miui.securitycenter.action.APP_PERMISSION_EDIT").apply {
                package = "com.miui.securitycenter"
                putExtra("extra_pkgname", activity.packageName)
            },
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerCenter"
            )
        )

        for (intent in candidates) {
            if (intent.resolveActivity(activity.packageManager) != null) {
                runCatching { activity.startActivity(intent) }
                return
            }
        }
        openAppDetails(activity)
    }

    fun openAppDetails(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        runCatching { activity.startActivity(intent) }
    }

    fun isXiaomiDevice(): Boolean =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("POCO", ignoreCase = true) ||
            Build.BRAND.equals("Xiaomi", ignoreCase = true)

    private const val REQUEST_CODE_NOTIFICATIONS = 4201
}
