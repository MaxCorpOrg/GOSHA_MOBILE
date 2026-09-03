package com.maxcorp.gosha.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal object BackgroundAccess {
    private const val TRANSSION_APP_SAVING_ACTION = "com.transsion.batterylab.app_saving"
    private const val TRANSSION_BATTERY_LAB_PACKAGE = "com.transsion.batterylab"

    fun isTranssionFamily(): Boolean =
        BackgroundAccessPolicy.isTranssionFamily(Build.MANUFACTURER, Build.BRAND)

    fun canPostNotifications(context: Context): Boolean {
        val runtimePermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openSettings(context: Context): Boolean {
        val candidates = buildList {
            if (isTranssionFamily()) {
                add(Intent(TRANSSION_APP_SAVING_ACTION).setPackage(TRANSSION_BATTERY_LAB_PACKAGE))
            }
            add(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
            )
            add(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        return openFirstAvailable(context, candidates)
    }

    fun openNotificationSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
            Intent(Settings.ACTION_SETTINGS),
        )
        return openFirstAvailable(context, candidates)
    }

    private fun openFirstAvailable(context: Context, candidates: List<Intent>): Boolean {
        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Пробуем следующий системный экран: набор настроек зависит от прошивки телефона.
            }
        }
        return false
    }
}
