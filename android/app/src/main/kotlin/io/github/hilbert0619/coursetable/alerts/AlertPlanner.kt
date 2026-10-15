package io.github.hilbert0619.coursetable.alerts

import io.github.hilbert0619.coursekit.ClassActivityPlan
import io.github.hilbert0619.coursekit.ClassActivityWindow
import io.github.hilbert0619.coursekit.ClassReminder
import io.github.hilbert0619.coursekit.ClassSession
import io.github.hilbert0619.coursekit.ExistingClassActivity
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.ResolvedTodo
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.upcomingActivityWindows
import io.github.hilbert0619.coursekit.upcomingReminders
import io.github.hilbert0619.coursekit.upcomingTodoAlerts
import io.github.hilbert0619.coursekit.uuidString
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** A live class notification that is showing, as read back from its extras. */
data class PostedLive(
    /** The notification's tag ([AlertPlanner.liveTag]). */
    val tag: String,
    val signature: String,
    val showMoment: Moment,
    val todos: List<String>,
    /** Shows the class in progress ("Until 09:40") rather than the countdown to its start. */
    val started: Boolean,
)

/** Everything a sync decides from. */
data class AlertInput(
    val timetable: Timetable,
    val todos: List<Todo>,
    val settings: ReminderSettings,
    val now: Instant,
    val zone: ZoneId,
    val postedLive: List<PostedLive> = emptyList(),
    /** Tags of the to-do notifications showing ([AlertPlanner.todoTag]). */
    val postedTodos: List<String> = emptyList(),
    /** [AlertPreferences.dismissed] */
    val dismissed: Map<String, Long> = emptyMap(),
    /** [AlertPreferences.notified] */
    val notified: Map<String, Long> = emptyMap(),
)

/** A live class notification to post: a new one (alerting when it comes on time) or a changed one (silently). */
data class LivePost(val window: ClassActivityWindow, val started: Boolean, val alert: Boolean) {
    val tag: String get() = AlertPlanner.liveTag(window.session)

    /** What it counts down to: the start of the class, then its end. */
    val countdownTarget: Moment get() = if (started) window.session.endMoment else window.session.startMoment
}

/** What a sync does now, and when the next one runs. */
data class AlertPlan(
    /** Live class notifications to remove (tags): finished, outdated, or the feature turned off. */
    val cancelLive: List<String>,
    val postLive: List<LivePost>,
    /** Live class notifications that stay as they are (only an island's minutes may change: [LiveUpdates]). */
    val keepLive: List<LivePost>,
    /** Class reminders due now. */
    val reminders: List<ClassReminder>,
    /** To-dos due now. */
    val todos: List<ResolvedTodo>,
    /** To-do notifications to remove (tags): ticked off or deleted. */
    val cancelTodos: List<String>,
    val dismissed: Map<String, Long>,
    val notified: Map<String, Long>,
    /** The next moment something changes: a class notification appears, switches or ends, a reminder or to-do is due. */
    val nextAlarm: Instant,
)

/**
 * The class and to-do alerts, decided at one moment. Instead of scheduling every notification ahead (iOS), Android
 * keeps one exact alarm: each sync posts, updates and removes what is due at [AlertInput.now] and names the next
 * moment to run again ([AlertPlan.nextAlarm]), at the latest the next midnight.
 *
 * - Live class notification (setting on): each class from its window's show time (the reminder time, or the end of
 *   the class before) until it ends, counting down to the start, then "Until 09:40". It alerts once when it appears on
 *   time. One the person closed stays closed ([AlertPreferences.dismissed]); one the system removed comes back silently.
 * - Class reminders (live notification off): a notification at the reminder time, unless the class has started.
 * - To-dos: a notification when one is due (up to [TODO_GRACE] late), removed when it's ticked off or deleted.
 */
object AlertPlanner {
    const val LIVE_PREFIX = "class:"
    const val REMINDER_PREFIX = "reminder:"
    const val TODO_PREFIX = "todo:"

    /** Upcoming class windows looked at in one sync. */
    const val LIVE_LOOKAHEAD = 16

    /** A notification posted up to this late still alerts (an alarm the system held back a little). */
    val ON_TIME: Duration = Duration.ofMinutes(5)

    /** A to-do notification still comes up to this late. */
    val TODO_GRACE: Duration = Duration.ofMinutes(15)

    fun liveTag(session: ClassSession) = LIVE_PREFIX + session.id
    fun reminderTag(session: ClassSession) = REMINDER_PREFIX + session.id
    fun todoTag(id: UUID) = TODO_PREFIX + id.uuidString

    fun plan(input: AlertInput): AlertPlan {
        val now = input.now
        val zone = input.zone
        val moment = Moment.from(now, zone)
        val nowMillis = now.toEpochMilli()
        val settings = input.settings
        fun Moment.instant(): Instant = toInstant(zone)
        val alarms = mutableListOf(moment.day.adding(1).toInstant(TimeOfDay.MIDNIGHT, zone))

        // Live class notifications. Turning them off and on again shows a closed one again (as iOS).
        val dismissed = if (settings.liveEnabled) input.dismissed.filterValues { it > nowMillis } else emptyMap()
        val wanted = if (settings.liveEnabled) {
            input.timetable.upcomingActivityWindows(moment, settings.leadMinutes, LIVE_LOOKAHEAD, input.todos)
        } else {
            emptyList()
        }
        val existing = input.postedLive.map {
            ExistingClassActivity(it.tag, it.signature, it.showMoment, isPending = false, todos = it.todos)
        }
        val remembered = dismissed.keys + input.postedLive.map { it.signature }
        val live = ClassActivityPlan.of(existing, wanted, remembered, moment, canSchedule = false)
        val postedTags = input.postedLive.map { it.tag }.toSet()
        val posts = mutableListOf<LivePost>()
        for (window in live.startNow) {
            val tag = liveTag(window.session)
            val onTime = now < window.showMoment.instant().plus(ON_TIME)
            // A replaced one (the room was edited) doesn't alert again.
            posts += LivePost(window, started = moment >= window.session.startMoment, alert = onTime && tag !in postedTags)
        }
        val updates = live.update.associateBy { it.id }
        val kept = mutableListOf<LivePost>()
        for (posted in input.postedLive) {
            if (posted.tag in live.end) continue
            val window = updates[posted.tag]?.window ?: wanted.firstOrNull { it.signature == posted.signature } ?: continue
            val started = moment >= window.session.startMoment
            val post = LivePost(window, started, alert = false)
            if (posted.tag in updates || posted.started != started) posts += post else kept += post
        }
        val postTags = posts.map { it.tag }.toSet()
        for (window in wanted) {
            alarms += listOf(window.showMoment, window.session.startMoment, window.session.endMoment).map { it.instant() }
        }

        // Class reminders, for classes without a live notification.
        val notified = input.notified.filterValues { it > nowMillis }.toMutableMap()
        val reminders = mutableListOf<ClassReminder>()
        if (settings.remindersEnabled && !settings.liveEnabled) {
            val since = Moment.from(now.minus(Duration.ofHours(1)), zone)
            for (reminder in input.timetable.upcomingReminders(since, settings.leadMinutes)) {
                val fire = reminder.fireMoment.instant()
                if (fire > now) {
                    alarms += fire
                    break
                }
                val key = "${reminderTag(reminder.session)}@${reminder.fireMoment.day} ${reminder.fireMoment.time}"
                // "Starts in 10 min" after the start would be wrong; "Starting now" may come a little late.
                val latest = maxOf(reminder.session.startMoment.instant(), fire.plus(ON_TIME))
                if (key !in notified && now < latest) {
                    reminders += reminder
                    notified[key] = reminder.session.endMoment.instant().toEpochMilli()
                }
            }
        }

        // To-dos.
        val todoAlerts = mutableListOf<ResolvedTodo>()
        val since = Moment.from(now.minus(TODO_GRACE), zone)
        for (todo in input.timetable.upcomingTodoAlerts(since, input.todos, limit = Int.MAX_VALUE)) {
            val dueMoment = todo.due ?: continue
            val due = dueMoment.instant()
            if (due > now) {
                alarms += due
                break
            }
            val key = "${todoTag(todo.id)}@${dueMoment.day} ${dueMoment.time}"
            if (key !in notified && now < due.plus(TODO_GRACE)) {
                todoAlerts += todo
                notified[key] = due.plus(TODO_GRACE).toEpochMilli()
            }
        }
        val openTodos = input.todos.filter { !it.isDone }.map { todoTag(it.id) }.toSet()

        return AlertPlan(
            cancelLive = live.end.filter { it !in postTags },
            postLive = posts,
            keepLive = kept.filter { it.tag !in postTags },
            reminders = reminders,
            todos = todoAlerts,
            cancelTodos = input.postedTodos.filter { it !in openTodos },
            dismissed = dismissed,
            notified = notified,
            nextAlarm = alarms.filter { it > now }.min(),
        )
    }
}
