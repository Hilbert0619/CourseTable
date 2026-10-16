package io.github.hilbert0619.coursetable.island

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.service.notification.StatusBarNotification
import io.github.hilbert0619.coursetable.alerts.AlertNotifications
import io.github.hilbert0619.coursetable.alerts.AlertPlanner
import io.github.hilbert0619.coursetable.alerts.NotificationIds
import java.time.Instant

/** A button of the notification, shown in the island's card. */
data class IslandAction(val title: String, val intent: PendingIntent?)

/** A countdown with what tapping it does (the notification's own intent) and its buttons (Close, Pause, Stop). */
data class IslandEntry(val item: IslandItem, val open: PendingIntent? = null, val actions: List<IslandAction> = emptyList())

/**
 * The island shows exactly the countdowns StudyHour is showing as notifications: the live class notifications, the
 * running focus session and the test Live Update. They are read back from the app's own active notifications (no
 * permission needed), so the island appears, changes and goes away with them, and a notification the person closed
 * or swiped away leaves the island too.
 */
object IslandSource {
    fun read(context: Context): List<IslandEntry> {
        val active = runCatching { context.getSystemService(NotificationManager::class.java).activeNotifications }.getOrNull()
        return active.orEmpty().mapNotNull { entry(it) }
    }

    fun entry(posted: StatusBarNotification): IslandEntry? {
        val kind = when {
            posted.id == NotificationIds.LIVE && posted.tag?.startsWith(AlertPlanner.LIVE_PREFIX) == true -> IslandKind.CLASS
            posted.id == NotificationIds.FOCUS_RUNNING -> IslandKind.FOCUS
            posted.id == NotificationIds.TEST_LIVE -> IslandKind.TEST
            else -> return null
        }
        val notification = posted.notification
        val extras = notification.extras
        // A countdown runs the chronometer down to `when`; a paused session shows no time.
        val countsDown = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) && extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
        val item = IslandItem(
            key = "${posted.id}:${posted.tag.orEmpty()}",
            kind = kind,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            target = if (countsDown) Instant.ofEpochMilli(notification.`when`) else null,
            started = extras.getBoolean(AlertNotifications.EXTRA_STARTED),
            color = notification.color,
        )
        val actions = notification.actions.orEmpty().map { IslandAction(it.title?.toString().orEmpty(), it.actionIntent) }
        return IslandEntry(item, notification.contentIntent, actions)
    }
}
