package io.github.hilbert0619.coursetable.alerts

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import io.github.hilbert0619.coursetable.StudyHourApp
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the class and to-do notifications in step with the timetable, the to-dos and the settings ([AlertPlanner]).
 * A sync runs when the app comes to the foreground, after every change, at each alarm, and after a restart or a
 * change of the clock ([SystemEventReceiver]).
 */
object Alerts {
    private const val TAG = "Alerts"
    private val mutex = Mutex()

    /** Syncs in the background. */
    fun requestSync(context: Context) {
        val app = context.applicationContext as StudyHourApp
        app.scope.launch { sync(app) }
    }

    suspend fun sync(context: Context, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Unit = mutex.withLock {
        val app = context.applicationContext as StudyHourApp
        val preferences = app.alertPreferences
        val manager = context.getSystemService(NotificationManager::class.java)
        val active = runCatching { manager.activeNotifications.toList() }.getOrDefault(emptyList())
        val input = AlertInput(
            timetable = app.repository.currentTimetable(),
            todos = app.repository.todos.first(),
            settings = preferences.settings.value,
            now = now,
            zone = zone,
            postedLive = active.mapNotNull { AlertNotifications.readLive(it, zone) },
            postedTodos = active.filter { it.id == NotificationIds.TODO }.mapNotNull { it.tag },
            dismissed = preferences.dismissed,
            notified = preferences.notified,
        )
        val plan = AlertPlanner.plan(input)
        for (tag in plan.cancelLive) manager.cancel(tag, NotificationIds.LIVE)
        for (tag in plan.cancelTodos) manager.cancel(tag, NotificationIds.TODO)
        for (post in plan.postLive) AlertNotifications.postLive(context, post, now, zone)
        for (reminder in plan.reminders) AlertNotifications.postReminder(context, reminder, zone)
        for (todo in plan.todos) AlertNotifications.postTodo(context, todo)
        preferences.dismissed = plan.dismissed
        preferences.notified = plan.notified
        Alarms.set(context, Alarms.SYNC, plan.nextAlarm)
        Log.d(TAG, "Synced: ${plan.postLive.size} live, ${plan.reminders.size} reminders, ${plan.todos.size} to-dos; next ${plan.nextAlarm}")
    }

    /**
     * Remembers that the person closed (Close button, with [tag]) or swiped away a live class notification, so it
     * doesn't come back. Under the sync's lock, so a sync running at the same time can't post it again.
     */
    suspend fun dismiss(context: Context, signature: String, end: Long, tag: String? = null): Unit = mutex.withLock {
        val app = context.applicationContext as StudyHourApp
        app.alertPreferences.addDismissed(signature, end)
        if (tag != null) context.getSystemService(NotificationManager::class.java).cancel(tag, NotificationIds.LIVE)
    }
}

/** The app's exact alarms: one per kind, each replaced when set again. */
object Alarms {
    /** The next class or to-do event ([Alerts.sync]). */
    const val SYNC = AlertReceiver.ACTION_SYNC

    /** The next switch of the focus session. */
    const val FOCUS = AlertReceiver.ACTION_FOCUS_ALARM

    /** Settings → Reliable Reminders → Send a Test Reminder. */
    const val TEST = AlertReceiver.ACTION_TEST

    /** Whether exact alarms are allowed (always on Android 13+ with USE_EXACT_ALARM, and before 12). */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Wakes the phone at [at] (also in Doze) and runs [AlertReceiver] with [action]. */
    fun set(context: Context, action: String, at: Instant) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = AlertReceiver.alarmIntent(context, action)
        val millis = at.toEpochMilli()
        try {
            if (canScheduleExact(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            }
        } catch (error: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    fun cancel(context: Context, action: String) {
        context.getSystemService(AlarmManager::class.java).cancel(AlertReceiver.alarmIntent(context, action))
    }

    internal fun requestCode(action: String): Int = when (action) {
        SYNC -> 1
        FOCUS -> 2
        else -> 3
    }

    internal const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
