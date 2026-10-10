package io.github.hilbert0619.coursetable

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseTemplate
import io.github.hilbert0619.coursekit.CourseWeeks
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.PeriodSchedule
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursekit.WeekParity
import io.github.hilbert0619.coursekit.Weekday
import io.github.hilbert0619.coursetable.data.AppDatabase
import io.github.hilbert0619.coursetable.data.Repository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The stored data round-trips through Room into the same core values (an in-memory database). */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: Repository
    private var changes = 0

    // Monday 2026-09-28, 09:00 in Shanghai.
    private val clock = Clock.fixed(Instant.parse("2026-09-28T01:00:00Z"), ZoneId.of("Asia/Shanghai"))

    private val calculus = Course(
        name = "Calculus", teacher = "Li", room = "A101", weekday = Weekday.MONDAY, startPeriod = 1, periodCount = 2,
        weeks = CourseWeeks(1, 16), colorIndex = 3,
    )
    private val physics = Course(
        name = "Physics", room = "B204", weekday = Weekday.WEDNESDAY, startPeriod = 3, periodCount = 3,
        weeks = CourseWeeks(1, 15, WeekParity.ODD), colorIndex = 5,
    )

    @Before
    fun setUp() {
        database = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repository = Repository(database, clock, onChange = { changes++ })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun firstLaunchStartsTheTermThisWeek() = runTest {
        repository.ensureSettings()
        val timetable = repository.currentTimetable()
        assertEquals(LocalDate(2026, 9, 28), timetable.semester.firstMonday)
        assertEquals(Semester.DEFAULT_TOTAL_WEEKS, timetable.semester.totalWeeks)
        assertEquals(PeriodSchedule.STANDARD, timetable.periods)
        assertTrue(timetable.courses.isEmpty())
    }

    @Test
    fun coursesKeepEveryFieldAndTheirOrder() = runTest {
        repository.saveCourse(physics)
        repository.saveCourse(calculus)
        assertEquals(listOf(physics, calculus), repository.currentTimetable().courses)
    }

    @Test
    fun savingAnEditedCourseTrimsItAndKeepsItsPlace() = runTest {
        repository.saveCourse(physics)
        repository.saveCourse(calculus)
        repository.saveCourse(physics.copy(name = "  Physics II ", room = " B205"))
        val courses = repository.currentTimetable().courses
        assertEquals(listOf("Physics II", "Calculus"), courses.map { it.name })
        assertEquals("B205", courses[0].room)
        repository.deleteCourse(physics.id)
        assertEquals(listOf(calculus), repository.currentTimetable().courses)
    }

    @Test
    fun importAddsOrReplaces() = runTest {
        repository.saveCourse(calculus)
        val imported = listOf(physics, physics.copy(id = java.util.UUID.randomUUID(), name = "Lab"))
        repository.addCourses(imported)
        assertEquals(listOf("Calculus", "Physics", "Lab"), repository.currentTimetable().courses.map { it.name })
        repository.addCourses(listOf(calculus.copy(id = java.util.UUID.randomUUID(), name = "Only")), replacingExisting = true)
        assertEquals(listOf("Only"), repository.currentTimetable().courses.map { it.name })
    }

    @Test
    fun termAndPeriodsAreSavedSeparately() = runTest {
        repository.ensureSettings()
        val periods = PeriodSchedule.STANDARD.appendingPeriod()
        repository.savePeriods(periods)
        repository.saveSemester(Semester(LocalDate(2027, 2, 24), totalWeeks = 18))
        val timetable = repository.currentTimetable()
        assertEquals(Semester(LocalDate(2027, 2, 22), 18), timetable.semester)
        assertEquals(periods, timetable.periods)
    }

    @Test
    fun courseListKeepsTheTypedOrder() = runTest {
        val templates = listOf(CourseTemplate(name = "B"), CourseTemplate(name = "A", room = "R1", teacher = "T", colorIndex = 2))
        repository.addTemplates(templates)
        assertEquals(templates, repository.templates.first())
        repository.removeTemplates(setOf(templates[0].id))
        assertEquals(listOf(templates[1]), repository.templates.first())
    }

    @Test
    fun todosKeepTheirTiming() = runTest {
        val monday = LocalDate(2026, 9, 28)
        val todos = listOf(
            Todo(title = "Pick up the package", day = monday, timing = TodoTiming.At(TimeOfDay(12, 10))),
            Todo(title = "Hand in the paper", day = monday, timing = TodoTiming.AfterClass(calculus.id)),
            Todo(title = "Buy milk", day = monday.adding(1)),
        )
        todos.forEach { repository.saveTodo(it) }
        assertEquals(todos, repository.todos.first())

        repository.setTodoDone(todos[1].id, true)
        repository.saveTodo(todos[2].copy(title = "  Buy milk and bread  "))
        repository.saveTodo(Todo(title = "   ", day = monday)) // an empty title isn't saved
        val saved = repository.todos.first()
        assertEquals(listOf(false, true, false), saved.map { it.isDone })
        assertEquals("Buy milk and bread", saved[2].title)
        assertEquals(3, saved.size)

        repository.deleteTodo(todos[0].id)
        assertEquals(listOf("Hand in the paper", "Buy milk and bread"), repository.todos.first().map { it.title })
    }

    @Test
    fun everyChangeIsReported() = runTest {
        repository.saveCourse(calculus)
        repository.saveTodo(Todo(title = "A", day = LocalDate(2026, 9, 28)))
        repository.addTemplates(listOf(CourseTemplate(name = "B")))
        assertEquals(3, changes)
    }
}
