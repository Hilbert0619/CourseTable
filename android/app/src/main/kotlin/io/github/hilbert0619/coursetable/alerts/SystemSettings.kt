package io.github.hilbert0619.coursetable.alerts

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * What decides whether reminders arrive on time, and the system pages that change it (Settings → Reliable
 * Reminders). Phones from vivo, iQOO, OPPO, Xiaomi and others also stop apps in the background unless they may
 * start automatically and use power in the background; those pages differ by brand (dontkillmyapp.com).
 */
object SystemSettings {
    fun notificationsAllowed(context: Context): Boolean = AlertNotifications.canNotify(context)

    fun exactAlarmsAllowed(context: Context): Boolean = Alarms.canScheduleExact(context)

    fun ignoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Whether class and focus notifications may show as Live Updates (Android 16+); `null` before Android 16. */
    fun liveUpdatesAllowed(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 36) return null
        return runCatching { context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications() }.getOrNull()
    }

    /** A vivo or iQOO phone (OriginOS / Funtouch OS). */
    val isVivo: Boolean get() = Build.MANUFACTURER.lowercase().let { it.contains("vivo") || it.contains("iqoo") }

    fun openNotificationSettings(context: Context) {
        open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) ||
            openAppInfo(context)
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 31 && open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)))) return
        openAppInfo(context)
    }

    /** Asks to turn battery optimisation off for the app (one tap), or opens the list. */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        open(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))) ||
            open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) ||
            openAppInfo(context)
    }

    fun openLiveUpdateSettings(context: Context) {
        val intent = Intent(PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (Build.VERSION.SDK_INT >= 36 && open(context, intent)) return
        openNotificationSettings(context)
    }

    /** `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` (Android 16, a flagged API missing from the SDK stubs). */
    private const val PROMOTION_SETTINGS = "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"

    /** vivo's auto-start manager (the pages differ by version, so each known one is tried), else App info. */
    fun openAutoStartSettings(context: Context) {
        val pages = listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"),
        )
        if (isVivo && pages.any { open(context, Intent().setComponent(it)) }) return
        openAppInfo(context)
    }

    /** The app's page in the system settings (permissions, battery, notifications). */
    fun openAppInfo(context: Context): Boolean =
        open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))

    private fun packageUri(context: Context) = Uri.fromParts("package", context.packageName, null)

    private fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: ActivityNotFoundException) {
        false
    } catch (error: SecurityException) {
        false
    }
}
