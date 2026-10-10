package io.github.hilbert0619.coursekit

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CourseListParsingTests {
    private fun summary(templates: List<CourseTemplate>) = templates.map { "${it.name}|${it.room}|${it.teacher}" }

    @Test
    fun readsNameRoomTeacherLines() {
        val list = CourseTemplate.parseList(
            """
            高等数学 A101 李老师
            大学英语，B204，王老师
            College English, B204, Wang
            College Physics C3 Zhao
            Data Structures${"\t"}C305${"\t"}Chen
            线性代数
            体育 操场

            # comments and blank lines are skipped
              高等数学   A101  李老师
            , A1, nobody
            Math,,Li
            Art | Studio 2 | Wu | extra
            Music、R1
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "高等数学|A101|李老师",
                "大学英语|B204|王老师",
                "College English|B204|Wang",
                "College Physics|C3|Zhao",
                "Data Structures|C305|Chen",
                "线性代数||",
                "体育|操场|",
                "Math||Li",
                "Art|Studio 2|Wu",
                "Music|R1|",
            ),
            summary(list),
        )
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertTrue(list.all { it.colorIndex == 0 })
    }

    @Test
    fun emptyInput() {
        assertTrue(CourseTemplate.parseList("").isEmpty())
        assertTrue(CourseTemplate.parseList("\n \r\n# just a comment\n").isEmpty())
    }

    @Test
    fun matchingDetails() {
        val template = CourseTemplate(name = "Calculus", room = "A101", teacher = "Li")
        assertTrue(template.describes(Fixture.calculus))
        assertFalse(template.describes(Fixture.physics))
        assertTrue(template.hasSameDetails(CourseTemplate(name = "Calculus", room = "A101", teacher = "Li", colorIndex = 5)))
        assertFalse(template.hasSameDetails(CourseTemplate(name = "Calculus", room = "B1", teacher = "Li")))

        val fromCourse = CourseTemplate.from(Fixture.calculus)
        assertEquals(Fixture.calculus.id, fromCourse.id)
        assertTrue(fromCourse.describes(Fixture.calculus))
    }
}

class PlacementTests {
    private val timetable = Fixture.timetable

    @Test
    fun savedTemplatesComeFirstThenCoursesNotInTheList() {
        val saved = listOf(
            CourseTemplate(name = "Calculus", room = "A101", teacher = "Li", colorIndex = 4),
            CourseTemplate(name = "Biology"),
        )
        val calculusWednesday = Fixture.calculus.copy(id = UUID.randomUUID(), weekday = Weekday.WEDNESDAY)
        val physicsFriday = Fixture.physics.copy(id = UUID.randomUUID(), weekday = Weekday.FRIDAY)
        val withRepeat = timetable.copy(courses = timetable.courses + calculusWednesday + physicsFriday)

        val templates = withRepeat.placementTemplates(saved)
        assertEquals(listOf("Calculus", "Biology", "English", "Physics", "Lab", "Seminar"), templates.map { it.name })
        assertEquals(saved[0], templates[0])
        assertEquals(Fixture.english.id, templates[2].id)
        assertEquals(Fixture.physics.id, templates[3].id) // the earlier of the two Physics courses
        assertEquals(5, timetable.placementTemplates(emptyList()).size)
        assertTrue(Timetable(Fixture.semester).placementTemplates(emptyList()).isEmpty())
    }

    @Test
    fun newTemplatesReuseColoursByName() {
        val coloured = Timetable(
            Fixture.semester,
            courses = listOf(Fixture.calculus.copy(colorIndex = 3), Fixture.physics.copy(colorIndex = 5)),
        )
        val saved = listOf(CourseTemplate(name = "Biology", colorIndex = 1))
        val new = listOf(
            CourseTemplate(name = "Calculus", room = "B1"),
            CourseTemplate(name = "Biology", room = "Lab"),
            CourseTemplate(name = "Chemistry"),
            CourseTemplate(name = "Drama"),
            CourseTemplate(name = "Chemistry", room = "Lab 2"),
        )
        val result = coloured.assigningColors(new, saved)
        assertEquals(listOf(3, 1, 0, 2, 0), result.map { it.colorIndex })
        assertEquals(new.map { it.id }, result.map { it.id })
    }

    @Test
    fun placingCreatesAWholeTermCourse() {
        val template = CourseTemplate(name = "Biology", room = "Lab", teacher = "Sun", colorIndex = 6)
        val course = timetable.placing(template, Weekday.TUESDAY, period = 3, periodCount = 2)
        assertEquals("Biology", course.name)
        assertEquals("Lab", course.room)
        assertEquals("Sun", course.teacher)
        assertEquals(6, course.colorIndex)
        assertEquals(Weekday.TUESDAY, course.weekday)
        assertEquals(3..4, course.periods)
        assertEquals(CourseWeeks(1, 18), course.weeks)
        assertNotEquals(template.id, course.id)
        assertTrue(timetable.issues(course).isEmpty())

        val odd = timetable.placing(template, Weekday.TUESDAY, period = 3, periodCount = 2, parity = WeekParity.ODD)
        assertEquals(CourseWeeks(1, 18, WeekParity.ODD), odd.weeks)
    }

    @Test
    fun placingFitsTheDay() {
        val template = CourseTemplate(name = "Late")
        assertEquals(12..12, timetable.placing(template, Weekday.TUESDAY, period = 12, periodCount = 3).periods)
        assertEquals(11..12, timetable.placing(template, Weekday.TUESDAY, period = 11, periodCount = 3).periods)
        assertEquals(12..12, timetable.placing(template, Weekday.TUESDAY, period = 20, periodCount = 2).periods)
        assertEquals(1..1, timetable.placing(template, Weekday.TUESDAY, period = 0, periodCount = 0).periods)
    }

    @Test
    fun placingStopsBeforeTheNextCourse() {
        val template = CourseTemplate(name = "Biology")
        // Lab is Thursday 5–7, Seminar Friday 9–10.
        assertEquals(4..4, timetable.placing(template, Weekday.THURSDAY, period = 4, periodCount = 3).periods)
        assertEquals(3..4, timetable.placing(template, Weekday.THURSDAY, period = 3, periodCount = 3).periods)
        assertEquals(8..8, timetable.placing(template, Weekday.FRIDAY, period = 8, periodCount = 2).periods)
        // Monday 3–4 is taken in every week (Physics odd, English even): keep the requested length.
        val taken = timetable.placing(template, Weekday.MONDAY, period = 3, periodCount = 2)
        assertEquals(3..4, taken.periods)
        assertTrue(timetable.clashes(taken).isNotEmpty())
    }

    @Test
    fun oppositeParityFitsBesideAnOddWeekCourse() {
        val oddOnly = Timetable(Fixture.semester, courses = listOf(Fixture.physics)) // Monday 3–4, odd weeks
        val even = oddOnly.placing(CourseTemplate(name = "English"), Weekday.MONDAY, period = 3, periodCount = 2, parity = WeekParity.EVEN)
        assertEquals(3..4, even.periods)
        assertTrue(oddOnly.clashes(even).isEmpty())
        val every = oddOnly.placing(CourseTemplate(name = "English"), Weekday.MONDAY, period = 2, periodCount = 2)
        assertEquals(2..2, every.periods)
    }

    @Test
    fun parityWithoutMatchingWeekFallsBackToEveryWeek() {
        val oneWeek = Timetable(Semester(Fixture.semester.firstMonday, totalWeeks = 1))
        val course = oneWeek.placing(CourseTemplate(name = "Talk"), Weekday.MONDAY, period = 1, periodCount = 1, parity = WeekParity.EVEN)
        assertEquals(CourseWeeks(1, 1), course.weeks)
    }
}

class AllWeeksGridTests {
    @Test
    fun includesCoursesFromOtherWeeksDimmed() {
        val week5 = Fixture.timetable.gridPlacements(inWeek = 5, includingOtherWeeks = true)
        assertEquals(5, week5.size)
        val byName = week5.associateBy { it.course.name }
        assertEquals(false, byName["English"]?.isInWeek) // even weeks only
        assertEquals(4, byName.values.count { it.isInWeek })
        // Physics and English share Monday 3–4 side by side.
        assertEquals(2, byName["Physics"]?.laneCount)
        assertEquals(2, byName["English"]?.laneCount)
        assertEquals(1, byName["Calculus"]?.laneCount)

        val week2 = Fixture.timetable.gridPlacements(inWeek = 2, includingOtherWeeks = true)
        assertEquals(false, week2.firstOrNull { it.course.name == "Lab" }?.isInWeek) // weeks 4–12
        assertEquals(false, week2.firstOrNull { it.course.name == "Physics" }?.isInWeek)
    }

    @Test
    fun currentWeekOnlyByDefault() {
        val placements = Fixture.timetable.gridPlacements(inWeek = 5)
        assertEquals(4, placements.size)
        assertEquals(4, placements.count { it.isInWeek })
    }

    @Test
    fun skipsCoursesThatNeverMeet() {
        val never = Course(name = "Never", weekday = Weekday.TUESDAY, startPeriod = 1, weeks = CourseWeeks(2, 2, WeekParity.ODD))
        val timetable = Fixture.timetable.copy(courses = Fixture.timetable.courses + never)
        assertEquals(5, timetable.gridPlacements(inWeek = 5, includingOtherWeeks = true).size)
    }
}
