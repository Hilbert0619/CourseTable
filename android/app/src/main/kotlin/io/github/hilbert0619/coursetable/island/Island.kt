package io.github.hilbert0619.coursetable.island

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import io.github.hilbert0619.coursetable.alerts.SystemSettings

/**
 * StudyHour's own island: for phones whose island doesn't show StudyHour's Live Updates (vivo/iQOO in China grant
 * theirs only to apps vivo approves), [IslandService] draws one around the camera hole with the class and focus
 * countdowns. It is an accessibility service the person turns on in the system settings, used only to draw at the
 * top of the screen: it reads no screen content and receives no events.
 *
 * This object connects the app to the service: [changed] after StudyHour posts or removes a notification, and whether
 * StudyHour itself is on screen (the island hides then, like the brands' islands).
 */
object Island {
    @Volatile
    private var listener: (() -> Unit)? = null

    /** Whether one of StudyHour's screens is showing. */
    @Volatile
    var appVisible: Boolean = false
        private set

    /** The service while it runs: told about every change. */
    internal fun attach(onChange: () -> Unit) {
        listener = onChange
    }

    internal fun detach() {
        listener = null
    }

    /** A StudyHour notification was posted or removed: the island reads them again. Any thread. */
    fun changed() {
        listener?.invoke()
    }

    /** Follows whether StudyHour is on screen ([Application.registerActivityLifecycleCallbacks]). */
    val visibility = object : Application.ActivityLifecycleCallbacks {
        private var started = 0

        override fun onActivityStarted(activity: Activity) {
            started++
            update()
        }

        override fun onActivityStopped(activity: Activity) {
            started = (started - 1).coerceAtLeast(0)
            update()
        }

        private fun update() {
            val visible = started > 0
            if (visible != appVisible) {
                appVisible = visible
                changed()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    /** Whether the person has turned the island on (Settings → Accessibility). */
    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val name = IslandService::class.java.name
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val service = it.resolveInfo?.serviceInfo
            service?.packageName == context.packageName && service.name == name
        }
    }

    /** The island's page in the accessibility settings, else the list of accessibility services, else App info. */
    fun openSettings(context: Context) {
        val component = ComponentName(context, IslandService::class.java).flattenToString()
        val details = Intent(ACCESSIBILITY_DETAILS_SETTINGS).putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        SystemSettings.open(context, details) ||
            SystemSettings.open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) ||
            SystemSettings.openAppInfo(context)
    }

    /** `Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS` (Android 11+), by name so older phones fall back. */
    private const val ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
}
