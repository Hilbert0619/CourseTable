package io.github.hilbert0619.coursekit

import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoTests {
    private val timetable = Fixture.timetable
    private val monday = Fixture.day("2026-09-28") // week 5: Calculus 08:00–09:40, Physics 10:00–11:40

    private fun names(agenda: DayAgenda): List<String> = agenda.items.map { item ->
        when (item) {
            is DayAgenda.Item.Session -> (listOf(item.session.course.name) + item.after.map { "  after: ${it.todo.title}" }).joinToString("\n")
            is DayAgenda.Item.TodoItem -> "${item.todo.due!!.time} ${item.todo.todo.title}"
        }
    }

    @Test
    fun timesComeFromTheClockOrTheClass() {
        val pkg = Todo(title = "Pick up the package", day = monday, timing = TodoTiming.At(Fixture.time("12:00")))
        val paper = Todo(title = "Hand in the paper", day = monday, timing = TodoTiming.AfterClass(Fixture.calculus.id))
        val milk = Todo(title = "Buy milk", day = monday)
        assertEquals(Fixture.at("2026-09-28", "12:00"), timetable.resolve(pkg).due)
        assertEquals(Fixture.at("2026-09-28", "09:40"), timetable.resolve(paper).due)
        assertEquals("Calculus", timetable.resolve(paper).session?.course?.name)
        assertNull(timetable.resolve(milk).due)
        assertFalse(timetable.resolve(milk).isMissingItsClass)

        // Physics meets in odd weeks only: on the Monday of week 6 there's no class to follow.
        val nextMonday = Todo(title = "Lab report", day = Fixture.day("2026-10-05"), timing = TodoTiming.AfterClass(Fixture.physics.id))
        assertNull(timetable.resolve(nextMonday).due)
        assertTrue(timetable.resolve(nextMonday).isMissingItsClass)
    }

    @Test
    fun theDayListsClassesAndToDosInOrder() {
        val todos = listOf(
            Todo(title = "Pick up the package", day = monday, timing = TodoTiming.At(Fixture.time("12:00"))),
            Todo(title = "Hand in the paper", day = monday, timing = TodoTiming.AfterClass(Fixture.calculus.id)),
            Todo(title = "Buy milk", day = monday),
            Todo(title = "Call home", day = monday, timing = TodoTiming.At(Fixture.time("07:30"))),
            Todo(title = "Ask about the exam", day = monday, timing = TodoTiming.AfterClass(Fixture.calculus.id), isDone = true),
            Todo(title = "Return the book", day = monday, timing = TodoTiming.At(Fixture.time("10:00"))),
            Todo(title = "Another day", day = Fixture.day("2026-09-29")),
            Todo(title = "Lab report", day = monday, timing = TodoTiming.AfterClass(Fixture.lab.id)),
        )
        val agenda = timetable.agenda(monday, todos)
        assertEquals(listOf("Buy milk", "Lab report"), agenda.anytime.map { it.todo.title })
        assertTrue(agenda.anytime[1].isMissingItsClass)
        assertEquals(
            listOf(
                "07:30 Call home",
                "Calculus\n  after: Hand in the paper\n  after: Ask about the exam",
                "10:00 Return the book", // a to-do at the start of a class comes first
                "Physics",
                "12:00 Pick up the package",
            ),
            names(agenda),
        )

        val empty = timetable.agenda(Fixture.day("2026-09-27"), todos)
        assertTrue(empty.isEmpty)
        assertFalse(timetable.agenda(Fixture.day("2026-09-29"), todos).isEmpty)
    }

    @Test
    fun onlyUnfinishedToDosStillAheadAreNotified() {
        val todos = listOf(
            Todo(title = "Earlier", day = monday, timing = TodoTiming.At(Fixture.time("07:00"))),
            Todo(title = "After Calculus", day = monday, timing = TodoTiming.AfterClass(Fixture.calculus.id)),
            Todo(title = "Done", day = monday, timing = TodoTiming.At(Fixture.time("12:00")), isDone = true),
            Todo(title = "Anytime", day = monday),
            Todo(title = "Tomorrow", day = Fixture.day("2026-09-29"), timing = TodoTiming.At(Fixture.time("08:00"))),
            Todo(title = "Noon", day = monday, timing = TodoTiming.At(Fixture.time("12:00"))),
            Todo(title = "Also 09:40", day = monday, timing = TodoTiming.At(Fixture.time("09:40"))),
        )
        val alerts = timetable.upcomingTodoAlerts(Fixture.at("2026-09-28", "08:00"), todos)
        assertEquals(listOf("After Calculus", "Also 09:40", "Noon", "Tomorrow"), alerts.map { it.todo.title })
        assertEquals(2, timetable.upcomingTodoAlerts(Fixture.at("2026-09-28", "08:00"), todos, limit = 2).size)
        // A to-do due exactly now has had its moment.
        assertEquals(listOf("Tomorrow"), timetable.upcomingTodoAlerts(Fixture.at("2026-09-28", "12:00"), todos).map { it.todo.title })
        assertTrue(Timetable.MAX_SCHEDULED_REMINDERS + Timetable.MAX_TODO_ALERTS + FocusRun.MAX_ALERTS <= 64)
    }

    @Test
    fun aClassListsItsUnfinishedToDos() {
        val calculus = timetable.sessions(monday)[0]
        val todos = listOf(
            Todo(title = "Hand in the paper", day = monday, timing = TodoTiming.AfterClass(Fixture.calculus.id)),
            Todo(title = "Done already", day = monday, timing = TodoTiming.AfterClass(Fixture.calculus.id), isDone = true),
            Todo(title = "Next week", day = Fixture.day("2026-10-05"), timing = TodoTiming.AfterClass(Fixture.calculus.id)),
            Todo(title = "After Physics", day = monday, timing = TodoTiming.AfterClass(Fixture.physics.id)),
            Todo(title = "At noon", day = monday, timing = TodoTiming.At(Fixture.time("12:00"))),
        )
        assertEquals(listOf("Hand in the paper"), timetable.todosAfter(calculus, todos).map { it.title })

        val windows = timetable.upcomingActivityWindows(Fixture.at("2026-09-28", "07:00"), leadMinutes = 10, limit = 2, todos = todos)
        assertEquals(listOf(listOf("Hand in the paper"), listOf("After Physics")), windows.map { it.todos })
    }

    @Test
    fun toDosRoundTripAsJSON() {
        val todos = listOf(
            Todo(title = "A", day = monday),
            Todo(title = "B", day = monday, timing = TodoTiming.At(Fixture.time("12:00")), isDone = true),
            Todo(title = "C", day = monday, timing = TodoTiming.AfterClass(Fixture.calculus.id)),
        )
        val serializer = ListSerializer(Todo.serializer())
        val json = CourseKitJson.encodeToString(serializer, todos)
        assertEquals(todos, CourseKitJson.decodeFromString(serializer, json))
        // Swift's Codable shapes for the enum.
        assertTrue(""""timing":{"anytime":{}}""" in json)
        assertTrue(""""timing":{"at":{"_0":"12:00"}}""" in json)
        assertTrue(""""timing":{"afterClass":{"courseID":"${Fixture.calculus.id.uuidString}"}}""" in json)
    }
}
