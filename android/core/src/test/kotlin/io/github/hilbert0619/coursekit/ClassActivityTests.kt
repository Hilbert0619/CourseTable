package io.github.hilbert0619.coursekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ClassActivityTests {
    private val timetable = Fixture.timetable

    private fun summary(windows: List<ClassActivityWindow>) =
        windows.map { "${it.session.course.name} ${it.showMoment.day} ${it.showMoment.time}–${it.session.end}" }

    private fun existing(window: ClassActivityWindow, id: String? = null, pending: Boolean = false, todos: List<String> = emptyList()) =
        ExistingClassActivity(id ?: window.id, window.signature, window.showMoment, pending, todos)

    // Windows

    @Test
    fun windowsRunFromTheReminderTimeToTheEndOfClass() {
        val windows = timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "07:00"), leadMinutes = 10)
        assertEquals(
            listOf(
                "Calculus 2026-09-28 07:50–09:40",
                "Physics 2026-09-28 09:50–11:40",
                "Lab 2026-10-01 13:50–16:45",
                "Seminar 2026-10-02 18:50–20:40",
            ),
            summary(windows),
        )
        assertEquals(listOf(10, 10, 10, 10), windows.map { it.leadMinutes })
        assertEquals(Timetable.MAX_CLASS_ACTIVITIES, windows.size)
        assertTrue(windows[0].isOpen(Fixture.at("2026-09-28", "07:50")))
        assertTrue(windows[0].isOpen(Fixture.at("2026-09-28", "09:39")))
        assertFalse(windows[0].isOpen(Fixture.at("2026-09-28", "07:49")))
        assertFalse(windows[0].isOpen(Fixture.at("2026-09-28", "09:40")))
    }

    @Test
    fun aClassInProgressStillHasItsWindow() {
        val during = timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "09:00"), leadMinutes = 10, limit = 2)
        assertEquals(listOf("Calculus 2026-09-28 07:50–09:40", "Physics 2026-09-28 09:50–11:40"), summary(during))
        assertTrue(during[0].isOpen(Fixture.at("2026-09-28", "09:00")))
        val after = timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "09:40"), leadMinutes = 10, limit = 1)
        assertEquals(listOf("Physics 2026-09-28 09:50–11:40"), summary(after))
    }

    /**
     * With a long lead time, the next class waits for the one in progress to end, so the two never show at once; the
     * same window is worked out after the first class has ended.
     */
    @Test
    fun theNextClassAppearsWhenTheOneBeforeEnds() {
        val morning = timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "07:00"), leadMinutes = 30, limit = 2)
        assertEquals(listOf("Calculus 2026-09-28 07:30–09:40", "Physics 2026-09-28 09:40–11:40"), summary(morning))
        assertEquals(listOf(30, 20), morning.map { it.leadMinutes })
        val later = timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "09:45"), leadMinutes = 30, limit = 1)
        assertEquals(listOf(morning[1]), later)

        // A class that overlaps another (a clash) doesn't wait for it.
        val tutorial = Course(name = "Tutorial", weekday = Weekday.MONDAY, startPeriod = 2, periodCount = 2, weeks = CourseWeeks(1, 16))
        val clash = Timetable(Fixture.semester, courses = listOf(Fixture.calculus, tutorial, Fixture.physics))
        assertEquals(
            listOf("Calculus 2026-09-28 07:30–09:40", "Tutorial 2026-09-28 08:25–10:45", "Physics 2026-09-28 09:40–11:40"),
            summary(clash.upcomingActivityWindows(Fixture.at("2026-09-28", "07:00"), leadMinutes = 30, limit = 3)),
        )
    }

    @Test
    fun windowsStopAtTheLimitAndTheEndOfTerm() {
        assertTrue(timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "07:00"), leadMinutes = 10, limit = 0).isEmpty())
        assertEquals(59, timetable.upcomingActivityWindows(Fixture.at("2026-08-01", "12:00"), leadMinutes = 10, limit = 100).size)
        assertEquals(
            listOf("Seminar 2027-01-01 18:50–20:40"),
            summary(timetable.upcomingActivityWindows(Fixture.at("2026-12-28", "00:00"), leadMinutes = 10)),
        )
        assertTrue(timetable.upcomingActivityWindows(Fixture.at("2027-01-04", "00:00"), leadMinutes = 10).isEmpty())
    }

    @Test
    fun aWindowCanOpenTheEveningBefore() {
        val midnight = PeriodSchedule(listOf(ClassPeriod(Fixture.time("00:10"), Fixture.time("00:50"))))
        val owl = Course(name = "Astronomy", weekday = Weekday.MONDAY, startPeriod = 1, periodCount = 1, weeks = CourseWeeks(1, 18))
        val night = Timetable(Fixture.semester, periods = midnight, courses = listOf(owl))
        val windows = night.upcomingActivityWindows(Fixture.at("2026-08-30", "23:55"), leadMinutes = 20, limit = 1)
        assertEquals(listOf("Astronomy 2026-08-30 23:50–00:50"), summary(windows))
        assertEquals(20, windows[0].leadMinutes)
        assertTrue(windows[0].isOpen(Fixture.at("2026-08-30", "23:55")))
        assertTrue(windows[0].isOpen(Fixture.at("2026-08-31", "00:20")))
    }

    @Test
    fun signaturesFollowWhatIsShown() {
        val windows = timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "07:00"), leadMinutes = 10, limit = 1)
        val later = timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "07:00"), leadMinutes = 15, limit = 1)
        assertEquals(later[0].id, windows[0].id)
        assertEquals(later[0].signature, windows[0].signature)
        assertNotEquals(later[0].showMoment, windows[0].showMoment)

        val moved = Fixture.calculus.copy(room = "A202")
        val edited = Timetable(Fixture.semester, courses = listOf(moved))
            .upcomingActivityWindows(Fixture.at("2026-09-28", "07:00"), leadMinutes = 10, limit = 1)
        assertEquals(windows[0].id, edited[0].id)
        assertNotEquals(windows[0].signature, edited[0].signature)
    }

    // Plan

    @Test
    fun nothingExistsYet() {
        val now = Fixture.at("2026-09-28", "07:55")
        val wanted = timetable.upcomingActivityWindows(now, leadMinutes = 10)
        val plan = ClassActivityPlan.of(emptyList(), wanted, emptySet(), now, canSchedule = true)
        assertTrue(plan.end.isEmpty() && plan.forget.isEmpty())
        assertEquals(listOf("Calculus 2026-09-28 07:50–09:40"), summary(plan.startNow))
        assertEquals(wanted.drop(1), plan.schedule)

        val older = ClassActivityPlan.of(emptyList(), wanted, emptySet(), now, canSchedule = false)
        assertEquals(plan.startNow, older.startNow)
        assertTrue(older.schedule.isEmpty())
    }

    @Test
    fun matchingActivitiesAreKept() {
        val now = Fixture.at("2026-09-28", "08:30")
        val wanted = timetable.upcomingActivityWindows(now, leadMinutes = 10)
        val current = wanted.map { existing(it, pending = it.showMoment > now) }
        val plan = ClassActivityPlan.of(current, wanted, wanted.map { it.signature }.toSet(), now, canSchedule = true)
        assertEquals(ClassActivityPlan.of(current, wanted, emptySet(), now, canSchedule = true), plan)
        assertTrue(plan.end.isEmpty() && plan.forget.isEmpty() && plan.startNow.isEmpty() && plan.schedule.isEmpty())
    }

    @Test
    fun finishedAndUnwantedActivitiesEnd() {
        val morning = timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "07:55"), leadMinutes = 10, limit = 2)
        val now = Fixture.at("2026-09-28", "10:00")
        val wanted = timetable.upcomingActivityWindows(now, leadMinutes = 10)
        val plan = ClassActivityPlan.of(morning.map { existing(it) }, wanted, morning.map { it.signature }.toSet(), now, canSchedule = true)
        // Calculus is over; Physics is showing.
        assertEquals(listOf(morning[0].id), plan.end)
        assertTrue(plan.forget.isEmpty())
        assertTrue(plan.startNow.isEmpty())
        assertEquals(
            listOf("Lab 2026-10-01 13:50–16:45", "Seminar 2026-10-02 18:50–20:40", "Calculus 2026-10-05 07:50–09:40"),
            summary(plan.schedule),
        )

        val off = ClassActivityPlan.of(morning.map { existing(it) }, emptyList(), emptySet(), now, canSchedule = true)
        assertEquals(morning.map { it.id }, off.end)
        assertTrue(off.forget.isEmpty() && off.startNow.isEmpty() && off.schedule.isEmpty())
    }

    @Test
    fun aClosedActivityIsNotCreatedAgain() {
        val now = Fixture.at("2026-09-28", "08:00")
        val wanted = timetable.upcomingActivityWindows(now, leadMinutes = 10, limit = 1)
        val plan = ClassActivityPlan.of(emptyList(), wanted, setOf(wanted[0].signature), now, canSchedule = true)
        assertTrue(plan.startNow.isEmpty() && plan.schedule.isEmpty() && plan.end.isEmpty())

        // Not at a new reminder time either.
        val fifteen = timetable.upcomingActivityWindows(now, leadMinutes = 15, limit = 1)
        val moved = ClassActivityPlan.of(emptyList(), fifteen, setOf(wanted[0].signature), now, canSchedule = true)
        assertTrue(moved.startNow.isEmpty() && moved.schedule.isEmpty())
    }

    @Test
    fun editedDetailsReplaceTheActivity() {
        val now = Fixture.at("2026-09-28", "08:00")
        val before = timetable.upcomingActivityWindows(now, leadMinutes = 10, limit = 1)
        val moved = Fixture.calculus.copy(room = "A202")
        val after = Timetable(Fixture.semester, courses = listOf(moved)).upcomingActivityWindows(now, leadMinutes = 10, limit = 1)
        val plan = ClassActivityPlan.of(listOf(existing(before[0], id = "old")), after, setOf(before[0].signature), now, canSchedule = true)
        assertEquals(listOf("old"), plan.end)
        assertEquals(listOf(after[0].signature), plan.startNow.map { it.signature })
    }

    @Test
    fun aNewLeadTimeReschedulesOnlyActivitiesNotStartedYet() {
        val now = Fixture.at("2026-09-28", "08:00")
        val ten = timetable.upcomingActivityWindows(now, leadMinutes = 10, limit = 2)
        val fifteen = timetable.upcomingActivityWindows(now, leadMinutes = 15, limit = 2)
        val current = listOf(existing(ten[0], id = "showing"), existing(ten[1], id = "scheduled", pending = true))
        val plan = ClassActivityPlan.of(current, fifteen, ten.map { it.signature }.toSet(), now, canSchedule = true)
        assertEquals(listOf("scheduled"), plan.end)
        assertEquals(setOf(ten[1].signature), plan.forget)
        assertTrue(plan.startNow.isEmpty())
        assertEquals(listOf("Physics 2026-09-28 09:45–11:40"), summary(plan.schedule))
    }

    /**
     * Scheduled activities ended because the feature is off never showed, so they're forgotten and come back when it's
     * on again.
     */
    @Test
    fun scheduledActivitiesEndedBeforeTheyShowAreForgotten() {
        val now = Fixture.at("2026-09-28", "07:00")
        val wanted = timetable.upcomingActivityWindows(now, leadMinutes = 10, limit = 2)
        val scheduled = wanted.map { existing(it, pending = true) }
        val off = ClassActivityPlan.of(scheduled, emptyList(), wanted.map { it.signature }.toSet(), now, canSchedule = true)
        assertEquals(wanted.map { it.id }, off.end)
        assertEquals(wanted.map { it.signature }.toSet(), off.forget)

        val on = ClassActivityPlan.of(emptyList(), wanted, emptySet(), now, canSchedule = true)
        assertEquals(wanted, on.schedule)
    }

    /** New to-dos: a showing activity is updated, a scheduled one is replaced (it never showed). */
    @Test
    fun changedToDosUpdateOrReplace() {
        val now = Fixture.at("2026-09-28", "08:00")
        val before = timetable.upcomingActivityWindows(now, leadMinutes = 10, limit = 2)
        val monday = Fixture.day("2026-09-28")
        val todos = listOf(
            Todo(title = "Hand in the paper", day = monday, timing = TodoTiming.AfterClass(Fixture.calculus.id)),
            Todo(title = "Return the book", day = monday, timing = TodoTiming.AfterClass(Fixture.physics.id)),
        )
        val after = timetable.upcomingActivityWindows(now, leadMinutes = 10, limit = 2, todos = todos)
        val current = listOf(existing(before[0], id = "showing"), existing(before[1], id = "scheduled", pending = true))
        val plan = ClassActivityPlan.of(current, after, before.map { it.signature }.toSet(), now, canSchedule = true)
        assertEquals(listOf(ClassActivityUpdate("showing", after[0])), plan.update)
        assertEquals(listOf("scheduled"), plan.end)
        assertEquals(setOf(before[1].signature), plan.forget)
        assertEquals(listOf(after[1]), plan.schedule)
        assertEquals(listOf("Return the book"), plan.schedule[0].todos)

        // Unchanged to-dos: nothing to do.
        val same = listOf(
            existing(after[0], id = "showing", todos = after[0].todos),
            existing(after[1], id = "scheduled", pending = true, todos = after[1].todos),
        )
        val quiet = ClassActivityPlan.of(same, after, after.map { it.signature }.toSet(), now, canSchedule = true)
        assertTrue(quiet.update.isEmpty() && quiet.end.isEmpty() && quiet.schedule.isEmpty())
    }

    /** The classes that need no reminder: those with an activity, and those whose activity was closed. */
    @Test
    fun coveredClassesHaveOrHadAnActivity() {
        val now = Fixture.at("2026-09-28", "07:00")
        val wanted = timetable.upcomingActivityWindows(now, leadMinutes = 10)
        val plan = ClassActivityPlan.of(
            listOf(existing(wanted[1], id = "physics", pending = true)), wanted,
            setOf(wanted[1].signature, wanted[2].signature), now, canSchedule = true,
        )
        // Physics is scheduled, Lab was closed; Calculus and Seminar are to be scheduled (the app adds them).
        assertEquals(setOf(wanted[1].id, wanted[2].id), plan.covered)
        assertEquals(listOf(wanted[0], wanted[3]), plan.schedule)
    }

    /** A scheduled one gone although nobody saw it is created again: scheduled if its window is ahead, started if open. */
    @Test
    fun aScheduledActivityGoneUnseenIsCreatedAgain() {
        val evening = Fixture.at("2026-09-27", "21:00")
        val wanted = timetable.upcomingActivityWindows(evening, leadMinutes = 10, limit = 2)
        val signatures = wanted.map { it.signature }.toSet()
        val later = ClassActivityPlan.of(emptyList(), wanted, signatures, evening, canSchedule = true, scheduled = signatures)
        assertEquals(signatures, later.forget)
        assertTrue(later.covered.isEmpty())
        assertEquals(wanted, later.schedule)

        val inClass = Fixture.at("2026-09-28", "07:55")
        val open = ClassActivityPlan.of(emptyList(), wanted, signatures, inClass, canSchedule = true, scheduled = signatures)
        assertEquals(listOf("Calculus 2026-09-28 07:50–09:40"), summary(open.startNow))
        assertEquals(listOf(wanted[1]), open.schedule)
    }

    /** Still scheduled: kept. Showing: seen, and once it's gone it isn't created again. */
    @Test
    fun aScheduledActivitySeenShowingStaysClosedWhenGone() {
        val now = Fixture.at("2026-09-28", "08:00")
        val wanted = timetable.upcomingActivityWindows(now, leadMinutes = 10, limit = 2)
        val signatures = wanted.map { it.signature }.toSet()
        val current = listOf(existing(wanted[0], id = "showing"), existing(wanted[1], id = "scheduled", pending = true))
        val plan = ClassActivityPlan.of(current, wanted, signatures, now, canSchedule = true, scheduled = signatures)
        assertEquals(setOf(wanted[0].signature), plan.seen)
        assertTrue(plan.forget.isEmpty() && plan.end.isEmpty() && plan.startNow.isEmpty() && plan.schedule.isEmpty())
        assertEquals(wanted.map { it.id }.toSet(), plan.covered)

        val closed = ClassActivityPlan.of(listOf(current[1]), wanted, signatures, now, canSchedule = true, scheduled = setOf(wanted[1].signature))
        assertTrue(closed.startNow.isEmpty() && closed.schedule.isEmpty() && closed.forget.isEmpty())
        assertEquals(wanted.map { it.id }.toSet(), closed.covered)
    }

    @Test
    fun duplicatesAreEnded() {
        val now = Fixture.at("2026-09-28", "08:00")
        val wanted = timetable.upcomingActivityWindows(now, leadMinutes = 10, limit = 1)
        val plan = ClassActivityPlan.of(
            listOf(existing(wanted[0], id = "a"), existing(wanted[0], id = "b", pending = true)),
            wanted, setOf(wanted[0].signature), now, canSchedule = true,
        )
        assertEquals(listOf("b"), plan.end)
        // "a" still stands for the class, so it stays remembered.
        assertTrue(plan.forget.isEmpty())
    }
}
