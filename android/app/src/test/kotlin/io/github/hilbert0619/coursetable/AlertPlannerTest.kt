package io.github.hilbert0619.coursetable

import io.github.hilbert0619.coursekit.ClassActivityWindow
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseWeeks
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursekit.Weekday
import io.github.hilbert0619.coursekit.upcomingActivityWindows
import io.github.hilbert0619.coursetable.alerts.AlertInput
import io.github.hilbert0619.coursetable.alerts.AlertPlan
import io.github.hilbert0619.coursetable.alerts.AlertPlanner
import io.github.hilbert0619.coursetable.alerts.PostedLive
import io.github.hilbert0619.coursetable.alerts.ReminderSettings
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What one sync of the class and to-do alerts does at a given moment (plain JVM). Monday 2026-09-28 has Calculus
 * 08:00–09:40 and Physics 10:00–11:40, the only classes of the week; with reminders 10 minutes before, Calculus shows
 * from 07:50 and Physics from 09:50.
 */
class AlertPlannerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate(2026, 9, 28)
    private val calculus = Course(
        name = "Calculus", teacher = "Li", room = "A101", weekday = Weekday.MONDAY, startPeriod = 1, periodCount = 2,
        weeks = CourseWeeks(1, 16), colorIndex = 3,
    )
    private val physics = Course(name = "Physics", room = "B204", weekday = Weekday.MONDAY, startPeriod = 3, periodCount = 2, weeks = CourseWeeks(1, 16))
    private val timetable = Timetable(Semester(LocalDate(2026, 9, 7)), courses = listOf(calculus, physics))

    private fun at(time: String, day: LocalDate = monday): Instant = Moment(day, TimeOfDay.parse(time)!!).toInstant(zone)

    private fun plan(
        time: String,
        settings: ReminderSettings = ReminderSettings(),
        todos: List<Todo> = emptyList(),
        posted: List<PostedLive> = emptyList(),
        postedTodos: List<String> = emptyList(),
        dismissed: Map<String, Long> = emptyMap(),
        notified: Map<String, Long> = emptyMap(),
    ): AlertPlan = AlertPlanner.plan(
        AlertInput(timetable, todos, settings, at(time), zone, posted, postedTodos, dismissed, notified),
    )

    private fun window(course: Course, todos: List<Todo> = emptyList()): ClassActivityWindow =
        timetable.upcomingActivityWindows(Moment(monday, TimeOfDay(0)), 10, todos = todos).first { it.session.course.id == course.id }

    private fun posted(course: Course, started: Boolean, todos: List<String> = emptyList(), signature: String? = null): PostedLive {
        val window = window(course)
        return PostedLive(AlertPlanner.liveTag(window.session), signature ?: window.signature, window.showMoment, todos, started)
    }

    // Live class notification

    @Test
    fun nothingBeforeTheFirstClassShows() {
        val plan = plan("07:00")
        assertTrue(plan.postLive.isEmpty())
        assertEquals(at("07:50"), plan.nextAlarm)
    }

    @Test
    fun aClassAppearsAtItsReminderTimeWithAnAlert() {
        val plan = plan("07:50")
        val post = plan.postLive.single()
        assertEquals("Calculus", post.window.session.course.name)
        assertEquals(false, post.started)
        assertEquals(true, post.alert)
        assertEquals(at("08:00"), plan.nextAlarm)
    }

    @Test
    fun itSwitchesToTheClassInProgressSilentlyAtTheStart() {
        val plan = plan("08:00", posted = listOf(posted(calculus, started = false)))
        val post = plan.postLive.single()
        assertEquals(true, post.started)
        assertEquals(false, post.alert)
        assertTrue(plan.cancelLive.isEmpty())
        assertEquals(at("09:40"), plan.nextAlarm)
    }

    @Test
    fun nothingChangesWhileTheClassRuns() {
        val plan = plan("08:30", posted = listOf(posted(calculus, started = true)))
        assertTrue(plan.postLive.isEmpty())
        assertTrue(plan.cancelLive.isEmpty())
    }

    @Test
    fun itGoesWhenTheClassEnds() {
        val tag = posted(calculus, started = true).tag
        val plan = plan("09:40", posted = listOf(posted(calculus, started = true)))
        assertEquals(listOf(tag), plan.cancelLive)
        assertTrue(plan.postLive.isEmpty())
        // Physics shows at its reminder time.
        assertEquals(at("09:50"), plan.nextAlarm)
    }

    @Test
    fun oneThePersonClosedStaysClosed() {
        val end = at("09:40").toEpochMilli()
        val plan = plan("08:30", dismissed = mapOf(window(calculus).signature to end))
        assertTrue(plan.postLive.isEmpty())
        assertEquals(mapOf(window(calculus).signature to end), plan.dismissed)
    }

    @Test
    fun oneTheSystemRemovedComesBackSilently() {
        val post = plan("08:30").postLive.single()
        assertEquals(true, post.started)
        assertEquals(false, post.alert)
    }

    @Test
    fun aNewAfterClassTodoUpdatesItSilently() {
        val todo = Todo(title = "Hand in the paper", day = monday, timing = TodoTiming.AfterClass(calculus.id))
        val plan = plan("08:30", todos = listOf(todo), posted = listOf(posted(calculus, started = true)))
        val post = plan.postLive.single()
        assertEquals(listOf("Hand in the paper"), post.window.todos)
        assertEquals(false, post.alert)
    }

    @Test
    fun anEditedClassIsReplacedWithoutAnotherAlert() {
        val old = posted(calculus, started = false, signature = "old")
        val plan = plan("07:51", posted = listOf(old))
        val post = plan.postLive.single()
        assertEquals(old.tag, post.tag)
        assertEquals(false, post.alert)
        // Posted again under the same tag instead of removed.
        assertTrue(plan.cancelLive.isEmpty())
    }

    @Test
    fun turningItOffRemovesThemAndForgetsClosedOnes() {
        val settings = ReminderSettings(liveEnabled = false)
        val plan = plan(
            "08:30", settings,
            posted = listOf(posted(calculus, started = true)),
            dismissed = mapOf(window(physics).signature to at("11:40").toEpochMilli()),
        )
        assertEquals(listOf(posted(calculus, started = true).tag), plan.cancelLive)
        assertTrue(plan.dismissed.isEmpty())
    }

    @Test
    fun withoutEventsTheNextAlarmIsMidnight() {
        // After Physics, the next classes are a week later.
        assertEquals(at("00:00", monday.adding(1)), plan("12:00").nextAlarm)
    }

    // Class reminders

    private val remindersOnly = ReminderSettings(remindersEnabled = true, liveEnabled = false)

    @Test
    fun aReminderComesOnceAtItsTime() {
        val first = plan("07:50", remindersOnly)
        assertEquals("Calculus", first.reminders.single().session.course.name)
        assertTrue(first.postLive.isEmpty())
        assertEquals(at("09:50"), first.nextAlarm)
        val again = plan("07:51", remindersOnly, notified = first.notified)
        assertTrue(again.reminders.isEmpty())
    }

    @Test
    fun noReminderOnceTheClassHasStarted() {
        assertTrue(plan("08:01", remindersOnly).reminders.isEmpty())
        // "Starting now" may still come a few minutes late.
        assertEquals(1, plan("08:03", remindersOnly.copy(leadMinutes = 0)).reminders.size)
    }

    @Test
    fun noRemindersWhileTheLiveNotificationIsOn() {
        val plan = plan("07:50", ReminderSettings(remindersEnabled = true, liveEnabled = true))
        assertTrue(plan.reminders.isEmpty())
        assertEquals(1, plan.postLive.size)
    }

    @Test
    fun remindersOffMeansNothingForClasses() {
        val plan = plan("07:50", ReminderSettings(remindersEnabled = false, liveEnabled = false))
        assertTrue(plan.reminders.isEmpty())
        assertTrue(plan.postLive.isEmpty())
        assertEquals(at("00:00", monday.adding(1)), plan.nextAlarm)
    }

    // To-dos

    private val lunch = Todo(title = "Pick up the package", day = monday, timing = TodoTiming.At(TimeOfDay(12, 0)))

    @Test
    fun aTodoNotifiesOnceWhenDue() {
        val before = plan("11:59", ReminderSettings(liveEnabled = false), todos = listOf(lunch))
        assertTrue(before.todos.isEmpty())
        assertEquals(at("12:00"), before.nextAlarm)
        val due = plan("12:00", todos = listOf(lunch))
        assertEquals(lunch.id, due.todos.single().id)
        assertTrue(plan("12:01", todos = listOf(lunch), notified = due.notified).todos.isEmpty())
    }

    @Test
    fun aLateAlarmStillBringsTheTodoWithinTheGrace() {
        assertEquals(1, plan("12:14", todos = listOf(lunch)).todos.size)
        assertTrue(plan("12:16", todos = listOf(lunch)).todos.isEmpty())
    }

    @Test
    fun anAfterClassTodoComesWhenTheClassEnds() {
        val todo = Todo(title = "Hand in the paper", day = monday, timing = TodoTiming.AfterClass(calculus.id))
        assertEquals(1, plan("09:40", todos = listOf(todo)).todos.size)
    }

    @Test
    fun aTickedOffOrDeletedTodosNotificationGoes() {
        val done = lunch.copy(isDone = true)
        val other = Todo(title = "Call home", day = monday)
        val tags = listOf(AlertPlanner.todoTag(done.id), AlertPlanner.todoTag(other.id), AlertPlanner.todoTag(java.util.UUID.randomUUID()))
        val plan = plan("12:05", todos = listOf(done, other), postedTodos = tags)
        assertEquals(listOf(tags[0], tags[2]), plan.cancelTodos)
        assertTrue(plan.todos.isEmpty())
    }

    @Test
    fun expiredMemoryIsForgotten() {
        val plan = plan(
            "12:00",
            dismissed = mapOf("old" to at("09:00").toEpochMilli()),
            notified = mapOf("old" to at("11:00").toEpochMilli(), "kept" to at("13:00").toEpochMilli()),
        )
        assertTrue(plan.dismissed.isEmpty())
        assertEquals(setOf("kept"), plan.notified.keys)
    }
}
