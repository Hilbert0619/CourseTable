package io.github.hilbert0619.coursetable.alerts

import android.content.Context
import io.github.hilbert0619.coursekit.CourseKitJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Settings → Reminders. Per device, as on iOS (`ReminderSettings`, `LiveActivitySettings`). */
data class ReminderSettings(
    /** A notification before each class that has no live notification. Off at first, as on iOS. */
    val remindersEnabled: Boolean = false,
    /** Each class as an ongoing notification from the reminder time until it ends (the iOS Live Activity). */
    val liveEnabled: Boolean = true,
    /** Minutes before class for both. */
    val leadMinutes: Int = DEFAULT_LEAD_MINUTES,
) {
    companion object {
        const val DEFAULT_LEAD_MINUTES = 10
        val LEAD_CHOICES = listOf(0, 5, 10, 15, 20, 30, 45, 60)
    }
}

/**
 * The reminder settings, plus what the alerts remember between syncs: the live class notifications the person closed
 * and the one-off notifications already shown. Kept in `SharedPreferences` (`alerts`).
 */
class AlertPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    fun update(transform: (ReminderSettings) -> ReminderSettings) {
        val settings = transform(_settings.value)
        prefs.edit()
            .putBoolean(REMINDERS_ENABLED, settings.remindersEnabled)
            .putBoolean(LIVE_ENABLED, settings.liveEnabled)
            .putInt(LEAD_MINUTES, settings.leadMinutes)
            .apply()
        _settings.value = settings
    }

    /**
     * Live class notifications the person closed or swiped away ([io.github.hilbert0619.coursekit.ClassActivityWindow.signature]
     * → end of the class, epoch ms): they don't come back. One the system removed (a restart) does.
     */
    var dismissed: Map<String, Long>
        get() = readMap(DISMISSED)
        set(value) = writeMap(DISMISSED, value)

    /** Class reminders and to-do notifications already shown (key → when it can be forgotten, epoch ms). */
    var notified: Map<String, Long>
        get() = readMap(NOTIFIED)
        set(value) = writeMap(NOTIFIED, value)

    /** Whether the app has asked for notifications at launch (once). */
    var askedForNotifications: Boolean
        get() = prefs.getBoolean(ASKED, false)
        set(value) = prefs.edit().putBoolean(ASKED, value).apply()

    @Synchronized
    fun addDismissed(signature: String, until: Long) {
        dismissed = dismissed + (signature to until)
    }

    private fun readSettings() = ReminderSettings(
        remindersEnabled = prefs.getBoolean(REMINDERS_ENABLED, false),
        liveEnabled = prefs.getBoolean(LIVE_ENABLED, true),
        leadMinutes = prefs.getInt(LEAD_MINUTES, ReminderSettings.DEFAULT_LEAD_MINUTES),
    )

    private fun readMap(key: String): Map<String, Long> {
        val text = prefs.getString(key, null) ?: return emptyMap()
        return runCatching { CourseKitJson.decodeFromString(MAP, text) }.getOrDefault(emptyMap())
    }

    private fun writeMap(key: String, value: Map<String, Long>) {
        prefs.edit().putString(key, CourseKitJson.encodeToString(MAP, value)).apply()
    }

    private companion object {
        const val REMINDERS_ENABLED = "reminders.enabled"
        const val LIVE_ENABLED = "live.enabled"
        const val LEAD_MINUTES = "reminders.leadMinutes"
        const val DISMISSED = "live.dismissed"
        const val NOTIFIED = "notified"
        const val ASKED = "notifications.asked"
        val MAP = MapSerializer(String.serializer(), Long.serializer())
    }
}
