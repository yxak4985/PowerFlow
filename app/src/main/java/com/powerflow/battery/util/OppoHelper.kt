package com.powerflow.battery.util

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** 权限与 OPPO / ColorOS 系统设置跳转。 */
object OppoHelper {

    fun isColorOs(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return m.contains("oppo") || m.contains("oneplus") || m.contains("realme")
    }

    fun canPostPromoted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
    }

    fun openNotificationSettings(context: Context) {
        start(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
            Settings.EXTRA_APP_PACKAGE, context.packageName
        ))
    }

    fun openOverlaySettings(context: Context) {
        start(context, Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ))
    }

    fun openPromotedSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 36) {
            val intent = Intent(
                Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { context.startActivity(intent) }.onFailure { openNotificationSettings(context) }
        } else {
            openNotificationSettings(context)
        }
    }

    /** 打开 ColorOS 自启动管理（不同版本包名不同，逐个尝试）。 */
    fun openAutoStartSettings(context: Context) {
        val candidates = listOf(
            "com.coloros.safecenter/.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter/.startupapp.StartupAppListActivity",
            "com.oppo.safe/.permission.startup.StartupAppListActivity",
            "com.coloros.phonemanager/.permission.startup.StartupAppListActivity",
            "com.coloros.phonemanager/.startupapp.StartupAppListActivity",
            "com.oplus.safecenter/.permission.startup.StartupAppListActivity"
        )
        for (candidate in candidates) {
            val cn = ComponentName.unflattenFromString(candidate) ?: continue
            runCatching {
                context.startActivity(Intent().setComponent(cn))
                return
            }
        }
        openAppDetails(context)
    }

    fun openBatteryOptimization(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { context.startActivity(intent); return }
        }
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            .onFailure { openAppDetails(context) }
    }

    private fun openAppDetails(context: Context) {
        start(context, Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ))
    }

    private fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure { openAppDetails(context) }
    }
}
