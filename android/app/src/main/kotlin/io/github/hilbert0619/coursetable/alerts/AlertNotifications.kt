package io.github.hilbert0619.coursetable.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.hilbert0619.coursekit.ClassReminder
import io.github.hilbert0619.coursekit.ClassSession
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.ResolvedTodo
import io.github.hilbert0619.coursetable.MainActivity
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.alerts.LiveUpdates.countdown
import io.github.hilbert0619.coursetable.island.Island
import io.github.hilbert0619.coursetable.ui.Tab
import io.github.hilbert0619.coursetable.ui.details
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette
import io.github.hilbert0619.coursetable.ui.timeRange
import java.time.Instant
import java.time.ZoneId

/** Notification ids; each kind tells its notifications apart by tag. */
object NotificationIds {
    const val LIVE = 1
    const val REMINDER = 2
    const val TODO = 3
    const val TEST = 4
    const val TEST_LIVE = 5
    const val FOCUS_RUNNING = 10
    const val FOCUS_SWITCH = 11
}

/** The notification channels, created at launch. */
object Channels {
    /** Live class notifications and class reminders. */
    const val CLASSES = "classes"
    const val TODOS = "todos"

    /** A switch between focus and break, and the end of a session. */
    const val FOCUS = "focus"

    /** The running focus session (silent). */
    const val FOCUS_RUNNING = "focus_running"

    fun create(context: Context) {
        val channels = listOf(
            NotificationChannelCompat.Builder(CLASSES, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.class_reminders)).build(),
            NotificationChannelCompat.Builder(TODOS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_todos)).build(),
            NotificationChannelCompat.Builder(FOCUS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_focus_alerts)).build(),
            // Not "low": a Live Update needs a channel that may show in the status bar.
            NotificationChannelCompat.Builder(FOCUS_RUNNING, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_focus_timer))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build(),
        )
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels)
    }
}

/** Builds and posts the class, reminder and to-do notifications. */
object AlertNotifications {
    private const val EXTRA_SIGNATURE = "studyhour.signature"
    private const val EXTRA_SHOW = "studyhour.show"
    private const val EXTRA_TODOS = "studyhour.todos"
    internal const val EXTRA_STARTED = "studyhour.started"

    /** Whether the app may post notifications at all. */
    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    @SuppressLint("MissingPermission")
    fun notify(context: Context, tag: String?, id: Int, notification: Notification) {
        if (canNotify(context)) NotificationManagerCompat.from(context).notify(tag, id, notification)
        Island.changed()
    }

    /**
     * Each class from its window's show time until it ends: "08:00–09:40 · A101 · Li" with a countdown to the start,
     * then "Until 09:40 · A101 · Li" with a countdown to the end, and the to-dos for after class. A Live Update on
     * Android 16: the status bar chip shows the countdown, or with [islandText] the island the minutes left
     * ([LiveUpdates]).
     */
    fun postLive(context: Context, post: LivePost, now: Instant, zone: ZoneId, islandText: Boolean) {
        val window = post.window
        val session = window.session
        val end = session.endMoment.toInstant(zone).toEpochMilli()
        val text = listOf(
            if (post.started) context.getString(R.string.until_time, session.end.toString()) else session.timeRange,
            session.details,
        ).filter { it.isNotEmpty() }.joinToString(" · ")
        val todos = todoSummary(window.todos)?.let { context.getString(R.string.after_class_todos, it) }
        val extras = Bundle().apply {
            putString(EXTRA_SIGNATURE, window.signature)
            putLong(EXTRA_SHOW, window.showMoment.toInstant(zone).toEpochMilli())
            putStringArray(EXTRA_TODOS, window.todos.toTypedArray())
            putBoolean(EXTRA_STARTED, post.started)
        }
        val closeData = Uri.parse("studyhour://class/${Uri.encode(post.tag)}")
        val builder = NotificationCompat.Builder(context, Channels.CLASSES)
            .setSmallIcon(R.drawable.ic_notification_class)
            .setColor(CoursePalette.color(session.course.colorIndex).toArgb())
            .setContentTitle(session.course.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (todos != null) "$text\n$todos" else text))
            .countdown(context, post.countdownTarget.toInstant(zone), now, islandText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(!post.alert)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, Tab.TODAY))
            .setDeleteIntent(AlertReceiver.dismissIntent(context, closeData, window.signature, end))
            .addAction(0, context.getString(R.string.close), AlertReceiver.closeIntent(context, closeData, post.tag, window.signature, end))
            .setTimeoutAfter(maxOf(end - now.toEpochMilli(), 0) + 60_000)
            .setRequestPromotedOngoing(true)
            .addExtras(extras)
        notify(context, post.tag, NotificationIds.LIVE, LiveUpdates.build(builder))
    }

    /** Reads a live class notification back ([postLive]), or `null` for any other notification. */
    fun readLive(notification: StatusBarNotification, zone: ZoneId): PostedLive? {
        val tag = notification.tag ?: return null
        if (notification.id != NotificationIds.LIVE || !tag.startsWith(AlertPlanner.LIVE_PREFIX)) return null
        val extras = notification.notification.extras
        val signature = extras.getString(EXTRA_SIGNATURE) ?: return null
        val show = Moment.from(Instant.ofEpochMilli(extras.getLong(EXTRA_SHOW)), zone)
        val todos = extras.getStringArray(EXTRA_TODOS)?.toList().orEmpty()
        return PostedLive(tag, signature, show, todos, extras.getBoolean(EXTRA_STARTED))
    }

    /** "Calculus" / "Starts in 10 min · 08:00–09:40 · A101 · Li" (iOS `ClassReminders.payload`). */
    fun postReminder(context: Context, reminder: ClassReminder, zone: ZoneId) {
        val session = reminder.session
        val end = session.endMoment.toInstant(zone).toEpochMilli()
        val notification = NotificationCompat.Builder(context, Channels.CLASSES)
            .setSmallIcon(R.drawable.ic_notification_class)
            .setColor(CoursePalette.color(session.course.colorIndex).toArgb())
            .setContentTitle(session.course.name)
            .setContentText(reminderBody(context, session, reminder.leadMinutes))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, Tab.TODAY))
            .setTimeoutAfter(maxOf(end - System.currentTimeMillis(), 60_000))
            .build()
        notify(context, AlertPlanner.reminderTag(session), NotificationIds.REMINDER, notification)
    }

    /** "Starts in 10 min · 08:00–09:40 · A101 · Li" */
    fun reminderBody(context: Context, session: ClassSession, leadMinutes: Int): String {
        val whenText = if (leadMinutes <= 0) context.getString(R.string.starting_now) else context.getString(R.string.starts_in_min, leadMinutes)
        return listOf(whenText, session.timeRange, session.details).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** "Pick up the package" / "12:00" or "After Calculus · 09:40" (iOS `TodoReminders.payload`). */
    fun postTodo(context: Context, todo: ResolvedTodo) {
        val due = todo.due ?: return
        val session = todo.session
        val body = if (session != null) "${context.getString(R.string.after_course, session.course.name)} · ${due.time}" else "${due.time}"
        val notification = NotificationCompat.Builder(context, Channels.TODOS)
            .setSmallIcon(R.drawable.ic_notification_todo)
            .setContentTitle(todo.todo.title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, Tab.TODAY))
            .build()
        notify(context, AlertPlanner.todoTag(todo.id), NotificationIds.TODO, notification)
    }

    /** Settings → Reliable Reminders → Send a Test Reminder, from an alarm 10 seconds later. */
    fun postTest(context: Context) {
        val notification = NotificationCompat.Builder(context, Channels.CLASSES)
            .setSmallIcon(R.drawable.ic_notification_class)
            .setContentTitle(context.getString(R.string.test_reminder_title))
            .setContentText(context.getString(R.string.test_reminder_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, Tab.SETTINGS))
            .build()
        notify(context, null, NotificationIds.TEST, notification)
    }

    /** How long the test Live Update counts down. */
    const val TEST_LIVE_MINUTES = 2

    /**
     * Settings → Reliable Reminders → Send a Test Live Update: a notification like the live class one, counting down
     * [TEST_LIVE_MINUTES] minutes from now, to see how this phone shows it ([LiveUpdates.shown]).
     */
    fun postTestLive(context: Context, now: Instant = Instant.now()) {
        val end = now.plusSeconds(TEST_LIVE_MINUTES * 60L)
        val text = context.getString(R.string.test_live_text, TEST_LIVE_MINUTES)
        val builder = NotificationCompat.Builder(context, Channels.CLASSES)
            .setSmallIcon(R.drawable.ic_notification_class)
            .setColor(CoursePalette.color(0).toArgb())
            .setContentTitle(context.getString(R.string.test_live_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .countdown(context, end, now, LiveUpdates.islandNeedsText(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, Tab.SETTINGS))
            .addAction(0, context.getString(R.string.close), AlertReceiver.closeTestLiveIntent(context))
            .setTimeoutAfter(TEST_LIVE_MINUTES * 60_000L)
            .setRequestPromotedOngoing(true)
        notify(context, null, NotificationIds.TEST_LIVE, LiveUpdates.build(builder))
    }

    /** "Hand in the paper · Return the book +1": the first two and how many more (iOS `todoSummary`). */
    fun todoSummary(todos: List<String>): String? {
        if (todos.isEmpty()) return null
        val shown = todos.take(2).joinToString(" · ")
        return if (todos.size > 2) "$shown +${todos.size - 2}" else shown
    }

    /** Opens the app on [tab]. */
    fun openApp(context: Context, tab: Tab): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_TAB, tab.name)
        return PendingIntent.getActivity(context, tab.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
