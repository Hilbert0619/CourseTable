package io.github.hilbert0619.coursekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderTests {
    private val timetable = Fixture.timetable

    private fun summary(reminders: List<ClassReminder>) =
        reminders.map { "${it.session.course.name} ${it.fireMoment.day} ${it.fireMoment.time}" }

    @Test
    fun momentArithmeticCrossesMidnight() {
        assertEquals(Fixture.at("2026-09-27", "23:55"), Fixture.at("2026-09-28", "00:05").adding(-10))
        assertEquals(Fixture.at("2027-01-01", "00:10"), Fixture.at("2026-12-31", "23:50").adding(20))
        assertEquals(Fixture.at("2026-09-28", "08:00"), Fixture.at("2026-09-28", "08:00").adding(0))
        assertEquals(Fixture.at("2026-09-30", "08:00"), Fixture.at("2026-09-28", "08:00").adding(2 * 1440))
        assertEquals(Fixture.at("2026-09-27", "07:59"), Fixture.at("2026-09-28", "08:00").adding(-1441))
    }

    @Test
    fun remindersFollowTheWeekInOrder() {
        val reminders = timetable.upcomingReminders(Fixture.at("2026-09-28", "07:00"), leadMinutes = 10, limit = 6)
        assertEquals(
            listOf(
                "Calculus 2026-09-28 07:50", // week 5 is odd: Physics, not English
                "Physics 2026-09-28 09:50",
                "Lab 2026-10-01 13:50",
                "Seminar 2026-10-02 18:50",
                "Calculus 2026-10-05 07:50", // week 6 is even
                "English 2026-10-05 09:50",
            ),
            summary(reminders),
        )
        assertTrue(reminders.all { it.leadMinutes == 10 })
        assertEquals(TimeOfDay(14, 0), reminders[2].session.start)
        assertEquals(5, reminders[2].session.week)
    }

    @Test
    fun skipsRemindersWhoseTimeHasPassed() {
        val late = timetable.upcomingReminders(Fixture.at("2026-09-28", "07:55"), leadMinutes = 10, limit = 1)
        assertEquals(listOf("Physics 2026-09-28 09:50"), summary(late))
        val exact = timetable.upcomingReminders(Fixture.at("2026-09-28", "07:50"), leadMinutes = 10, limit = 1)
        assertEquals(listOf("Physics 2026-09-28 09:50"), summary(exact))
        val justBefore = timetable.upcomingReminders(Fixture.at("2026-09-28", "07:49"), leadMinutes = 10, limit = 1)
        assertEquals(listOf("Calculus 2026-09-28 07:50"), summary(justBefore))
    }

    @Test
    fun coversTheWholeTermUpToTheLimit() {
        val beforeTerm = Fixture.at("2026-08-01", "12:00")
        val all = timetable.upcomingReminders(beforeTerm, leadMinutes = 10, limit = 1000)
        // Calculus 16 + Physics 8 + English 8 + Lab 9 + Seminar 18.
        assertEquals(59, all.size)
        assertEquals(listOf("Calculus 2026-08-31 07:50"), summary(all.take(1)))
        assertTrue(all.zipWithNext().all { (a, b) -> a.fireMoment < b.fireMoment })
        assertEquals(all.size, all.map { it.id }.toSet().size)

        assertEquals(all.take(Timetable.MAX_SCHEDULED_REMINDERS), timetable.upcomingReminders(beforeTerm, leadMinutes = 10))
        assertEquals(all.take(20), timetable.upcomingReminders(beforeTerm, leadMinutes = 10, limit = 20))
        assertTrue(timetable.upcomingReminders(beforeTerm, leadMinutes = 10, limit = 0).isEmpty())
        assertTrue(Timetable.MAX_SCHEDULED_REMINDERS < 64)
    }

    @Test
    fun stopsAtTheEndOfTerm() {
        val lastWeek = timetable.upcomingReminders(Fixture.at("2026-12-28", "00:00"), leadMinutes = 10)
        assertEquals(listOf("Seminar 2027-01-01 18:50"), summary(lastWeek))
        assertTrue(timetable.upcomingReminders(Fixture.at("2027-01-04", "00:00"), leadMinutes = 10).isEmpty())
    }

    @Test
    fun leadTimeOfZeroAndNegativeFireAtTheStart() {
        val atStart = timetable.upcomingReminders(Fixture.at("2026-09-28", "07:00"), leadMinutes = 0, limit = 1)
        assertEquals(listOf("Calculus 2026-09-28 08:00"), summary(atStart))
        assertEquals(atStart, timetable.upcomingReminders(Fixture.at("2026-09-28", "07:00"), leadMinutes = -5, limit = 1))
    }

    @Test
    fun reminderCanFireTheEveningBefore() {
        val midnight = PeriodSchedule(listOf(ClassPeriod(Fixture.time("00:10"), Fixture.time("00:50"))))
        val owl = Course(name = "Astronomy", weekday = Weekday.MONDAY, startPeriod = 1, periodCount = 1, weeks = CourseWeeks(1, 18))
        val night = Timetable(Fixture.semester, periods = midnight, courses = listOf(owl))
        val reminders = night.upcomingReminders(Fixture.at("2026-08-30", "23:00"), leadMinutes = 20, limit = 2)
        assertEquals(listOf("Astronomy 2026-08-30 23:50", "Astronomy 2026-09-06 23:50"), summary(reminders))
        assertEquals(Fixture.day("2026-08-31"), reminders[0].session.day)
    }

    @Test
    fun idsAreStablePerCourseAndDay() {
        val first = timetable.upcomingReminders(Fixture.at("2026-09-28", "07:00"), leadMinutes = 10, limit = 3)
        val again = timetable.upcomingReminders(Fixture.at("2026-09-28", "07:30"), leadMinutes = 15, limit = 3)
        assertEquals(first.map { it.id }, again.map { it.id })
        assertEquals("class-${Fixture.calculus.id.uuidString}@2026-09-28", first[0].id)
    }

    /** Classes with a live class notification get no reminder. */
    @Test
    fun classesWithALiveActivityAreLeftOut() {
        val now = Fixture.at("2026-09-28", "07:00")
        val windows = timetable.upcomingActivityWindows(now, leadMinutes = 10, limit = 2)
        val reminders = timetable.upcomingReminders(now, leadMinutes = 10, limit = 3, excluding = windows.map { it.id }.toSet())
        assertEquals(listOf("Lab 2026-10-01 13:50", "Seminar 2026-10-02 18:50", "Calculus 2026-10-05 07:50"), summary(reminders))
        assertTrue(reminders.map { it.session.id }.toSet().intersect(windows.map { it.id }.toSet()).isEmpty())
    }
}
