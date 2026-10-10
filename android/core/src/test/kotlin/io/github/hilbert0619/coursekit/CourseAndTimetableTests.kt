package io.github.hilbert0619.coursekit

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseTests {
    @Test
    fun parity() {
        val weeks = CourseWeeks(3, 10, WeekParity.ODD)
        assertEquals(listOf(3, 5, 7, 9), weeks.all)
        assertEquals(listOf(4, 6, 8, 10), CourseWeeks(3, 10, WeekParity.EVEN).all)
        assertEquals(listOf(3, 4, 5), CourseWeeks(3, 5).all)
        assertTrue(5 in weeks)
        assertFalse(4 in weeks)
        assertFalse(11 in weeks)
        assertTrue(CourseWeeks(5, 3).all.isEmpty())
    }

    @Test
    fun periodRange() {
        assertEquals(5..7, Fixture.lab.periods)
        assertEquals(7, Fixture.lab.endPeriod)
    }

    @Test
    fun oddAndEvenCoursesInSameSlotDoNotClash() {
        assertFalse(Fixture.physics.clashes(Fixture.english))
        assertFalse(Fixture.english.clashes(Fixture.physics))
    }

    @Test
    fun overlappingSlotAndWeeksClash() {
        val makeup = Fixture.physics.copy(id = UUID.randomUUID(), startPeriod = 4, weeks = CourseWeeks(5, 5))
        assertTrue(makeup.clashes(Fixture.physics))
        assertFalse(makeup.clashes(Fixture.english)) // week 5 is odd; English is even-only
        assertFalse(Fixture.physics.clashes(Fixture.physics)) // a course never clashes with itself
    }

    @Test
    fun differentDaysOrPeriodsDoNotClash() {
        var other = Fixture.calculus.copy(id = UUID.randomUUID(), weekday = Weekday.TUESDAY)
        assertFalse(other.clashes(Fixture.calculus))
        other = other.copy(weekday = Weekday.MONDAY, startPeriod = 3)
        assertFalse(other.clashes(Fixture.calculus))
        other = other.copy(startPeriod = 2)
        assertTrue(other.clashes(Fixture.calculus))
    }

    @Test
    fun disjointWeekRangesDoNotClash() {
        val other = Fixture.calculus.copy(id = UUID.randomUUID(), weeks = CourseWeeks(17, 18))
        assertFalse(other.clashes(Fixture.calculus))
    }

    @Test
    fun validation() {
        assertTrue(Fixture.calculus.issues(periodsPerDay = 12, totalWeeks = 18).isEmpty())

        var course = Fixture.calculus.copy(name = "   ", startPeriod = 12)
        assertEquals(
            listOf(CourseIssue.MissingName, CourseIssue.PeriodsOutOfRange(periodsPerDay = 12)),
            course.issues(periodsPerDay = 12, totalWeeks = 18),
        )

        course = Fixture.calculus.copy(weeks = CourseWeeks(8, 4))
        assertEquals(listOf(CourseIssue.InvalidWeekRange), course.issues(periodsPerDay = 12, totalWeeks = 18))

        course = course.copy(weeks = CourseWeeks(2, 2, WeekParity.ODD))
        assertEquals(listOf(CourseIssue.NoMatchingWeeks), course.issues(periodsPerDay = 12, totalWeeks = 18))

        course = course.copy(weeks = CourseWeeks(1, 20))
        val issues = course.issues(periodsPerDay = 12, totalWeeks = 18)
        assertEquals(listOf(CourseIssue.WeeksBeyondSemester(totalWeeks = 18)), issues)
        assertFalse(issues.any { it.isBlocking })
        assertTrue(CourseIssue.MissingName.isBlocking)
    }

    @Test
    fun suggestedColourIsLeastUsed() {
        assertEquals(0, Course.suggestedColorIndex(emptyList()))
        var courses = listOf(Fixture.calculus, Fixture.physics.copy(colorIndex = 1)) // colours 0 and 1
        assertEquals(2, Course.suggestedColorIndex(courses))
        courses = (0 until Course.PALETTE_SIZE).map { Fixture.lab.copy(colorIndex = if (it == 3) 4 else it) }
        assertEquals(3, Course.suggestedColorIndex(courses))
    }
}

class TimetableTests {
    private val timetable = Fixture.timetable

    @Test
    fun coursesInWeekRespectParityAndRange() {
        assertEquals(listOf("Calculus", "English", "Lab", "Seminar"), timetable.courses(inWeek = 4).map { it.name })
        assertEquals(listOf("Calculus", "Physics", "Lab", "Seminar"), timetable.courses(inWeek = 5).map { it.name })
        assertEquals(listOf("Seminar"), timetable.courses(inWeek = 17).map { it.name })
        assertEquals(listOf("Calculus", "Physics"), timetable.courses(Weekday.MONDAY, inWeek = 1).map { it.name })
    }

    @Test
    fun sessionsHaveConcreteTimes() {
        val sessions = timetable.sessions(Fixture.day("2026-09-24")) // Thursday, week 4
        val lab = sessions.first()
        assertEquals(1, sessions.size)
        assertEquals("Lab", lab.course.name)
        assertEquals(4, lab.week)
        assertEquals("14:00", lab.start.toString())
        assertEquals("16:45", lab.end.toString())
    }

    @Test
    fun noSessionsOutsideTerm() {
        assertTrue(timetable.sessions(Fixture.day("2026-08-24")).isEmpty()) // Monday before term
        assertTrue(timetable.sessions(Fixture.day("2027-01-04")).isEmpty()) // Monday after term
    }

    @Test
    fun sessionsSkipPeriodsMissingFromSchedule() {
        val short = timetable.copy(periods = PeriodSchedule(PeriodSchedule.STANDARD.periods.take(8)))
        assertTrue(short.sessions(Fixture.day("2026-09-25")).isEmpty()) // Seminar is periods 9–10
    }

    @Test
    fun daySchedule() {
        val monday = timetable.daySchedule(Fixture.day("2026-09-28")) // week 5 (odd)
        assertEquals(SemesterStatus.InProgress(week = 5), monday.semesterStatus)
        assertEquals(listOf("Calculus", "Physics"), monday.sessions.map { it.course.name })

        val nine = Fixture.time("09:00")
        assertEquals("Calculus", monday.current(nine)?.course?.name)
        assertEquals("Physics", monday.next(nine)?.course?.name)
        assertEquals(2, monday.remaining(nine).size)

        val breakTime = Fixture.time("09:50")
        assertNull(monday.current(breakTime))
        assertEquals("Physics", monday.next(breakTime)?.course?.name)

        assertNull(monday.current(Fixture.time("09:40"))) // end time is exclusive
        assertTrue(monday.remaining(Fixture.time("12:00")).isEmpty())
    }

    @Test
    fun currentOrNextFindsInProgressClass() {
        val session = timetable.currentOrNextSession(Fixture.at("2026-09-28", "10:30"))
        assertEquals("Physics", session?.course?.name)
        assertEquals(SessionStatus.IN_PROGRESS, session?.status(Fixture.at("2026-09-28", "10:30")))
    }

    @Test
    fun currentOrNextLooksAheadAcrossDays() {
        // After Monday's last class, the next is Thursday's lab (no Tuesday/Wednesday classes).
        val afterMonday = timetable.currentOrNextSession(Fixture.at("2026-09-28", "12:00"))
        assertEquals("Lab", afterMonday?.course?.name)
        assertEquals(Fixture.day("2026-10-01"), afterMonday?.day)
        assertEquals(SessionStatus.UPCOMING, afterMonday?.status(Fixture.at("2026-09-28", "12:00")))

        // After Friday's evening seminar, the next class is Monday's first class of week 6.
        val weekend = timetable.currentOrNextSession(Fixture.at("2026-10-02", "21:00"))
        assertEquals("Calculus", weekend?.course?.name)
        assertEquals(Fixture.day("2026-10-05"), weekend?.day)
    }

    @Test
    fun currentOrNextBeforeTermStartsReturnsFirstClass() {
        val beforeTerm = timetable.currentOrNextSession(Fixture.at("2026-08-29", "12:00"))
        assertEquals("Calculus", beforeTerm?.course?.name)
        assertEquals(Fixture.day("2026-08-31"), beforeTerm?.day)
        assertNull(timetable.currentOrNextSession(Fixture.at("2027-06-01", "08:00")))
    }

    @Test
    fun refreshMomentsCoverStartsEndsAndMidnight() {
        val moments = timetable.refreshMoments(Fixture.day("2026-09-28"))
        assertEquals(listOf("08:00", "09:40", "10:00", "11:40", "00:00"), moments.map { it.time.toString() })
        assertEquals(Fixture.day("2026-09-29"), moments.last().day)
        assertEquals(1, timetable.refreshMoments(Fixture.day("2026-09-29")).size) // no classes: just midnight
    }

    @Test
    fun currentAndNext() {
        val during = timetable.currentAndNext(Fixture.at("2026-09-28", "10:30"))
        assertEquals("Physics", during.current?.course?.name)
        assertEquals("Lab", during.next?.course?.name)
        assertEquals(Fixture.day("2026-10-01"), during.next?.day)

        val morning = timetable.currentAndNext(Fixture.at("2026-09-28", "09:00"))
        assertEquals("Calculus", morning.current?.course?.name)
        assertEquals("Physics", morning.next?.course?.name)

        val inBreak = timetable.currentAndNext(Fixture.at("2026-09-28", "09:50"))
        assertNull(inBreak.current)
        assertEquals("Physics", inBreak.next?.course?.name)

        val afterTerm = timetable.currentAndNext(Fixture.at("2027-06-01", "09:00"))
        assertTrue(afterTerm.current == null && afterTerm.next == null)
    }

    @Test
    fun timelineMomentsStartNowAndEndAtMidnight() {
        val moments = timetable.timelineMoments(Fixture.at("2026-09-28", "09:00"))
        assertEquals(listOf("09:00", "09:40", "10:00", "11:40", "00:00"), moments.map { it.time.toString() })
        assertEquals(Fixture.day("2026-09-28"), moments.first().day)

        val evening = timetable.timelineMoments(Fixture.at("2026-09-28", "20:00"))
        assertEquals(2, evening.size) // now, then midnight
    }

    @Test
    fun clashesForCourse() {
        val makeup = Fixture.english.copy(id = UUID.randomUUID(), name = "Makeup", weeks = CourseWeeks(4, 5))
        assertEquals(listOf("English", "Physics"), timetable.clashes(makeup).map { it.name })
        assertTrue(timetable.clashes(Fixture.calculus).isEmpty())
    }

    @Test
    fun weekendDetection() {
        assertFalse(timetable.hasWeekendCourses)
        val weekend = timetable.copy(
            courses = timetable.courses + Course(name = "Club", weekday = Weekday.SATURDAY, startPeriod = 1, weeks = CourseWeeks(1, 2)),
        )
        assertTrue(weekend.hasWeekendCourses)
    }
}

class WeekGridTests {
    @Test
    fun nonOverlappingCoursesGetOneLane() {
        val placements = Fixture.timetable.gridPlacements(inWeek = 5)
        assertEquals(4, placements.size)
        assertTrue(placements.all { it.lane == 0 && it.laneCount == 1 })
    }

    @Test
    fun clashingCoursesShareTheColumn() {
        val makeup = Fixture.physics.copy(id = UUID.randomUUID(), name = "Makeup", startPeriod = 4, periodCount = 2)
        val timetable = Fixture.timetable.copy(courses = Fixture.timetable.courses + makeup)

        val monday = timetable.gridPlacements(inWeek = 5).filter { it.weekday == Weekday.MONDAY }
        val byName = monday.associateBy { it.course.name }
        assertEquals(1, byName["Calculus"]?.laneCount)
        assertEquals(0, byName["Physics"]?.lane)
        assertEquals(2, byName["Physics"]?.laneCount)
        assertEquals(1, byName["Makeup"]?.lane)
        assertEquals(2, byName["Makeup"]?.laneCount)
    }

    @Test
    fun freedLaneIsReusedWithinCluster() {
        val a = Course(name = "A", weekday = Weekday.TUESDAY, startPeriod = 1, periodCount = 4, weeks = CourseWeeks(1, 1))
        val b = Course(name = "B", weekday = Weekday.TUESDAY, startPeriod = 1, periodCount = 1, weeks = CourseWeeks(1, 1))
        val c = Course(name = "C", weekday = Weekday.TUESDAY, startPeriod = 3, periodCount = 1, weeks = CourseWeeks(1, 1))
        val timetable = Timetable(Fixture.semester, courses = listOf(a, b, c))
        val byName = timetable.gridPlacements(inWeek = 1).associateBy { it.course.name }
        // Sorted by start then end: B (1–1) takes lane 0, A (1–4) lane 1, C (3–3) reuses lane 0 once B has ended.
        assertEquals(0, byName["B"]?.lane)
        assertEquals(1, byName["A"]?.lane)
        assertEquals(0, byName["C"]?.lane)
        assertTrue(byName.values.all { it.laneCount == 2 })
    }
}

class SnapshotTests {
    @Test
    fun roundTrips() {
        val snapshot = ScheduleSnapshot(timetable = Fixture.timetable)
        val json = snapshot.encoded()
        assertEquals(snapshot, ScheduleSnapshot.decode(json))
        assertTrue(""""firstMonday":"2026-08-31"""" in json)
        assertTrue(""""version":1""" in json)
    }

    @Test
    fun rejectsNewerVersions() {
        val snapshot = ScheduleSnapshot(version = ScheduleSnapshot.CURRENT_VERSION + 1, timetable = Fixture.timetable)
        val error = assertFailsWith<SnapshotException.UnsupportedVersion> { ScheduleSnapshot.decode(snapshot.encoded()) }
        assertEquals(ScheduleSnapshot.CURRENT_VERSION + 1, error.version)
    }
}
