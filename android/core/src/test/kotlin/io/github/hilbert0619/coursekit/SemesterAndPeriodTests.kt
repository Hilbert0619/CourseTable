package io.github.hilbert0619.coursekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SemesterTests {
    // Term picked on a Wednesday: week 1 is the whole Monday–Sunday week around it.
    private val semester = Semester(LocalDate(2026, 9, 2), totalWeeks = 18)

    @Test
    fun startIsNormalisedToMonday() {
        assertEquals(LocalDate(2026, 8, 31), semester.firstMonday)
        assertEquals(Weekday.MONDAY, semester.startDate.weekday)
        assertEquals(LocalDate(2027, 1, 3), semester.lastDay)
        assertEquals(Weekday.SUNDAY, semester.lastDay.weekday)
    }

    @ParameterizedTest
    @CsvSource(
        "2026-08-30, 0", "2026-08-31, 1", "2026-09-06, 1", "2026-09-07, 2", "2026-09-24, 4", "2027-01-03, 18",
        "2027-01-04, 19", "2026-08-23, -1", "2026-08-24, 0",
    )
    fun weekNumbers(text: String, week: Int) {
        assertEquals(week, semester.week(containing = LocalDate.parse(text)!!))
    }

    @Test
    fun status() {
        assertEquals(SemesterStatus.NotStarted(daysUntilStart = 3), semester.status(LocalDate(2026, 8, 28)))
        assertEquals(SemesterStatus.InProgress(week = 4), semester.status(LocalDate(2026, 9, 24)))
        assertEquals(SemesterStatus.Finished, semester.status(LocalDate(2027, 1, 4)))
        assertEquals(4, SemesterStatus.InProgress(week = 4).week)
        assertNull(SemesterStatus.Finished.week)
    }

    @Test
    fun displayWeekIsClampedIntoTerm() {
        assertEquals(1, semester.displayWeek(LocalDate(2026, 7, 1)))
        assertEquals(4, semester.displayWeek(LocalDate(2026, 9, 24)))
        assertEquals(18, semester.displayWeek(LocalDate(2027, 3, 1)))
    }

    @Test
    fun dateOfWeekdayInWeek() {
        assertEquals(LocalDate(2026, 8, 31), semester.date(Weekday.MONDAY, inWeek = 1))
        assertEquals(LocalDate(2026, 9, 18), semester.date(Weekday.FRIDAY, inWeek = 3))
        assertEquals(semester.lastDay, semester.date(Weekday.SUNDAY, inWeek = 18))
    }

    @Test
    fun settingStartDateNormalises() {
        val copy = semester.withStartDate(LocalDate(2027, 2, 27)) // Saturday
        assertEquals(LocalDate(2027, 2, 22), copy.firstMonday)
    }

    @Test
    fun totalWeeksIsAtLeastOne() {
        assertEquals(1, Semester(LocalDate(2026, 9, 1), totalWeeks = 0).totalWeeks)
    }

    @Test
    fun codableNormalisesStart() {
        val json = """{"firstMonday":"2026-09-02","totalWeeks":18}"""
        val decoded = CourseKitJson.decodeFromString(Semester.serializer(), json)
        assertEquals(semester, decoded)
        val reencoded = CourseKitJson.decodeFromString(Semester.serializer(), CourseKitJson.encodeToString(Semester.serializer(), decoded))
        assertEquals(semester, reencoded)
    }
}

class PeriodScheduleTests {
    private val standard = PeriodSchedule.STANDARD

    @Test
    fun standardScheduleIsValid() {
        assertEquals(12, standard.count)
        assertTrue(standard.issues.isEmpty())
        assertEquals(TimeOfDay(8, 0), standard.period(1)?.start)
        assertEquals(TimeOfDay(22, 30), standard.period(12)?.end)
        assertNull(standard.period(0))
        assertNull(standard.period(13))
    }

    @Test
    fun timeSpanCoversFirstToLastPeriod() {
        val span = standard.timeSpan(1..2)!!
        assertEquals("08:00", span.start.toString())
        assertEquals("09:40", span.end.toString())
        assertNull(standard.timeSpan(11..13))
    }

    @Test
    fun detectsBrokenSchedules() {
        assertEquals(listOf(PeriodScheduleIssue.Empty), PeriodSchedule(emptyList()).issues)
        val periods = standard.periods.toMutableList()
        periods[2] = periods[2].copy(end = periods[2].start) // period 3 has zero length
        periods[4] = periods[4].copy(start = TimeOfDay(11, 0)) // period 5 starts inside period 4
        assertEquals(
            listOf(PeriodScheduleIssue.EndsBeforeStart(3), PeriodScheduleIssue.OverlapsPrevious(5)),
            PeriodSchedule(periods).issues,
        )
    }

    @Test
    fun appendingKeepsLengthAndBreak() {
        val longer = standard.appendingPeriod()
        assertEquals(13, longer.count)
        val added = longer.period(13)!!
        assertEquals("22:40", added.start.toString())
        assertEquals("23:25", added.end.toString())
        assertTrue(longer.issues.isEmpty())
    }

    @Test
    fun appendingStopsAtMaximum() {
        var schedule = PeriodSchedule(emptyList())
        repeat(PeriodSchedule.MAX_PERIODS + 3) { schedule = schedule.appendingPeriod() }
        assertEquals(PeriodSchedule.MAX_PERIODS, schedule.count)
    }

    @Test
    fun codableUsesReadableTimes() {
        val json = CourseKitJson.encodeToString(PeriodSchedule.serializer(), PeriodSchedule(listOf(standard.periods[0])))
        assertTrue("08:00" in json)
        assertTrue("08:45" in json)
        val decoded = CourseKitJson.decodeFromString(PeriodSchedule.serializer(), CourseKitJson.encodeToString(PeriodSchedule.serializer(), standard))
        assertEquals(standard, decoded)
    }
}
