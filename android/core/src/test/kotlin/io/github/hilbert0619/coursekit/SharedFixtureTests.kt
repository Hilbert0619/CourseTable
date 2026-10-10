package io.github.hilbert0619.coursekit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

/**
 * The shared test cases in `spec/fixtures/`, which the Swift package's tests read too
 * (`CourseKit/Tests/CourseKitTests/SharedFixtureTests.swift`): both platforms must give the same answers.
 */
class SharedFixtureTests {
    private val directory = File(System.getProperty("fixtures.dir") ?: error("The test task sets fixtures.dir"))

    private inline fun <reified T> decode(name: String): T =
        CourseKitJson.decodeFromString(kotlinx.serialization.serializer<T>(), File(directory, name).readText())

    private fun moment(text: String): Moment {
        val (day, time) = text.split(" ")
        return Moment(LocalDate.parse(day)!!, TimeOfDay.parse(time)!!)
    }

    /** Collects every mismatch, so one run shows them all. */
    private class Mismatches {
        val lines = mutableListOf<String>()
        fun <T> check(what: String, expected: T, actual: T) {
            if (expected != actual) lines += "$what: expected $expected, got $actual"
        }
        fun assertNone() = assertTrue(lines.isEmpty(), lines.joinToString("\n"))
    }

    // Import fields

    @Serializable
    class ImportFields(
        val totalWeeks: Int,
        val weekdays: List<WeekdayCase>,
        val invalidWeekdays: List<String>,
        val periods: List<PeriodCase>,
        val invalidPeriods: List<String>,
        val weeks: List<WeeksCase>,
        val invalidWeeks: List<String>,
    ) {
        @Serializable class WeekdayCase(val text: String, val weekday: Int)
        @Serializable class PeriodCase(val text: String, val runs: List<List<Int>>)
        @Serializable class WeeksCase(val text: String, val weeks: List<CourseWeeks>)
    }

    @Test
    fun importFields() {
        val fixture = decode<ImportFields>("import-fields.json")
        val mismatches = Mismatches()
        for (case in fixture.weekdays) {
            mismatches.check("weekday \"${case.text}\"", Weekday.of(case.weekday), CourseImporter.parseWeekday(case.text))
        }
        for (text in fixture.invalidWeekdays) mismatches.check("weekday \"$text\"", null, CourseImporter.parseWeekday(text))
        for (case in fixture.periods) {
            mismatches.check(
                "periods \"${case.text}\"", case.runs.map { it[0]..it[1] },
                CourseImporter.parsePeriods(case.text, PeriodSchedule.STANDARD),
            )
        }
        for (text in fixture.invalidPeriods) {
            mismatches.check("periods \"$text\"", null, CourseImporter.parsePeriods(text, PeriodSchedule.STANDARD))
        }
        for (case in fixture.weeks) {
            mismatches.check("weeks \"${case.text}\"", case.weeks, CourseImporter.parseWeeks(case.text, fixture.totalWeeks))
        }
        for (text in fixture.invalidWeeks) mismatches.check("weeks \"$text\"", null, CourseImporter.parseWeeks(text, fixture.totalWeeks))
        mismatches.assertNone()
    }

    // Import samples

    @Serializable
    class ImportSamples(val totalWeeks: Int, val samples: List<Sample>) {
        @Serializable
        class Sample(
            val name: String,
            val text: String? = null,
            val file: String? = null,
            val encoding: String? = null,
            val rowCount: Int,
            val courses: List<String>,
            val problems: List<String>,
        )
    }

    @Test
    fun importSamples() {
        val fixture = decode<ImportSamples>("import-samples.json")
        val importer = CourseImporter(PeriodSchedule.STANDARD, fixture.totalWeeks)
        val mismatches = Mismatches()
        for (sample in fixture.samples) {
            val text = if (sample.file != null) ImportedText.decode(File(directory, sample.file).readBytes()) else sample.text
            if (text == null) {
                mismatches.lines += "${sample.name}: ${sample.file} didn't decode"
                continue
            }
            val result = importer.parse(text)
            mismatches.check("${sample.name}: courses", sample.courses, result.courses.map(::summary))
            mismatches.check("${sample.name}: problems", sample.problems, result.problems.map(::describe))
            mismatches.check("${sample.name}: rows", sample.rowCount, result.rowCount)
        }
        mismatches.assertNone()
    }

    private fun summary(course: Course): String = with(course) {
        "$name|$teacher|$room|${weekday.rawValue}|$startPeriod-$endPeriod|${weeks.first}-${weeks.last} ${weeks.parity.name.lowercase()}"
    }

    private fun describe(problem: ImportProblem): String = "${problem.line} " + when (val kind = problem.kind) {
        is ImportProblem.Kind.MissingColumns -> "missingColumns " + kind.columns.joinToString(",") { it.rawValue }
        is ImportProblem.Kind.MissingValue -> "missingValue ${kind.column.rawValue}"
        is ImportProblem.Kind.InvalidValue -> "invalidValue ${kind.column.rawValue} ${kind.text}"
        is ImportProblem.Kind.PeriodsOutOfRange -> "periodsOutOfRange ${kind.text} ${kind.periodsPerDay}"
    }

    // Focus plans

    @Serializable
    class FocusPlans(val plans: List<Case>) {
        @Serializable class Case(val settings: FocusSettings, val layout: List<String>)
    }

    @Test
    fun focusPlans() {
        val mismatches = Mismatches()
        for (case in decode<FocusPlans>("focus-plans.json").plans) {
            val layout = FocusPlan(case.settings).phases.map { phase ->
                when (phase.kind) {
                    FocusPhase.Kind.FOCUS -> "F${phase.seconds / 60}"
                    FocusPhase.Kind.SHORT_BREAK -> "b${phase.seconds / 60}"
                    FocusPhase.Kind.LONG_BREAK -> "L${phase.seconds / 60}"
                }
            }
            mismatches.check("plan for ${case.settings}", case.layout, layout)
        }
        mismatches.assertNone()
    }

    // Reminders, windows, agenda, to-do alerts

    @Serializable
    class TimetableAlerts(
        val timetable: Timetable,
        val todos: List<Todo>,
        val reminders: List<AlertCase>,
        val windows: List<AlertCase>,
        val agenda: List<AgendaCase>,
        val todoAlerts: List<AlertCase>,
    ) {
        @Serializable class AlertCase(val after: String, val leadMinutes: Int = 0, val limit: Int, val expected: List<String>)
        @Serializable class AgendaCase(val day: LocalDate, val anytime: List<String>, val items: List<String>)
    }

    @Test
    fun timetableAlerts() {
        val fixture = decode<TimetableAlerts>("timetable-alerts.json")
        val timetable = fixture.timetable
        val mismatches = Mismatches()
        for (case in fixture.reminders) {
            val reminders = timetable.upcomingReminders(moment(case.after), case.leadMinutes, case.limit)
            mismatches.check(
                "reminders after ${case.after}, ${case.leadMinutes} min", case.expected,
                reminders.map { "${it.session.course.name} ${it.fireMoment.day} ${it.fireMoment.time}" },
            )
        }
        for (case in fixture.windows) {
            val windows = timetable.upcomingActivityWindows(moment(case.after), case.leadMinutes, case.limit, fixture.todos)
            mismatches.check(
                "windows after ${case.after}, ${case.leadMinutes} min", case.expected,
                windows.map { window ->
                    "${window.session.course.name} ${window.showMoment.day} ${window.showMoment.time}–${window.session.end}" +
                        if (window.todos.isEmpty()) "" else " after: " + window.todos.joinToString(", ")
                },
            )
        }
        for (case in fixture.agenda) {
            val agenda = timetable.agenda(case.day, fixture.todos)
            mismatches.check("anytime on ${case.day}", case.anytime, agenda.anytime.map { it.todo.title })
            mismatches.check(
                "agenda on ${case.day}", case.items,
                agenda.items.map { item ->
                    when (item) {
                        is DayAgenda.Item.Session -> item.session.course.name +
                            if (item.after.isEmpty()) "" else " after: " + item.after.joinToString(", ") { it.todo.title }
                        is DayAgenda.Item.TodoItem -> "${item.todo.due!!.time} ${item.todo.todo.title}"
                    }
                },
            )
        }
        for (case in fixture.todoAlerts) {
            val alerts = timetable.upcomingTodoAlerts(moment(case.after), fixture.todos, case.limit)
            mismatches.check("to-do alerts after ${case.after}", case.expected, alerts.map { it.todo.title })
        }
        mismatches.assertNone()
    }
}
