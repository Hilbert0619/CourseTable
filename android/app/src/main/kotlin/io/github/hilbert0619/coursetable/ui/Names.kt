package io.github.hilbert0619.coursetable.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.github.hilbert0619.coursekit.ClassSession
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseIssue
import io.github.hilbert0619.coursekit.CourseWeeks
import io.github.hilbert0619.coursekit.ImportColumn
import io.github.hilbert0619.coursekit.ImportProblem
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.PeriodScheduleIssue
import io.github.hilbert0619.coursekit.SemesterStatus
import io.github.hilbert0619.coursekit.WeekParity
import io.github.hilbert0619.coursekit.Weekday
import io.github.hilbert0619.coursetable.R
import java.time.format.DateTimeFormatter
import java.time.LocalDate as JavaLocalDate
import java.time.format.FormatStyle
import java.util.Locale

/** User-facing names for core values (iOS: Shared/LocalizedNames.swift and the views' `message`s). */
class Names(private val res: Resources) {
    val locale: Locale get() = res.configuration.locales[0]

    fun short(day: Weekday): String = res.getString(SHORT_DAYS[day.ordinal])
    fun full(day: Weekday): String = res.getString(FULL_DAYS[day.ordinal])

    fun parity(parity: WeekParity): String = res.getString(
        when (parity) {
            WeekParity.EVERY -> R.string.every_week
            WeekParity.ODD -> R.string.odd_weeks
            WeekParity.EVEN -> R.string.even_weeks
        },
    )

    /** e.g. "Weeks 1–16", "Week 5", "Weeks 1–15 · Odd weeks" */
    fun weeks(weeks: CourseWeeks): String {
        val range = if (weeks.first == weeks.last) week(weeks.first) else res.getString(R.string.weeks_range, weeks.first, weeks.last)
        return if (weeks.parity == WeekParity.EVERY) range else "$range · ${parity(weeks.parity)}"
    }

    fun week(number: Int): String = res.getString(R.string.week_number, number)

    /** e.g. "Periods 3–4", or "Period 5" for a single period. */
    fun periods(course: Course): String = periods(course.startPeriod, course.endPeriod)

    fun periods(first: Int, last: Int): String =
        if (last > first) res.getString(R.string.periods_range, first, last) else res.getString(R.string.period_number, first)

    /** Warning about [course], shown when it clashes with the one being edited or imported. */
    fun clash(course: Course): String =
        res.getString(R.string.clashes_with, course.name, short(course.weekday), course.startPeriod, course.endPeriod)

    fun status(status: SemesterStatus): String = when (status) {
        is SemesterStatus.NotStarted -> res.getQuantityString(R.plurals.term_starts_in, status.daysUntilStart, status.daysUntilStart)
        is SemesterStatus.InProgress -> week(status.week)
        SemesterStatus.Finished -> res.getString(R.string.term_has_ended)
    }

    fun issue(issue: CourseIssue): String = when (issue) {
        CourseIssue.MissingName -> res.getString(R.string.issue_missing_name)
        is CourseIssue.PeriodsOutOfRange -> res.getString(R.string.issue_periods_out_of_range, issue.periodsPerDay)
        CourseIssue.InvalidWeekRange -> res.getString(R.string.issue_invalid_week_range)
        CourseIssue.NoMatchingWeeks -> res.getString(R.string.issue_no_matching_weeks)
        is CourseIssue.WeeksBeyondSemester -> res.getString(R.string.issue_weeks_beyond_semester, issue.totalWeeks)
    }

    fun issue(issue: PeriodScheduleIssue): String = when (issue) {
        PeriodScheduleIssue.Empty -> res.getString(R.string.period_issue_empty)
        is PeriodScheduleIssue.EndsBeforeStart -> res.getString(R.string.period_issue_ends_before_start, issue.period)
        is PeriodScheduleIssue.OverlapsPrevious -> res.getString(R.string.period_issue_overlaps_previous, issue.period)
    }

    fun column(column: ImportColumn): String = res.getString(
        when (column) {
            ImportColumn.NAME -> R.string.column_name
            ImportColumn.TEACHER -> R.string.column_teacher
            ImportColumn.ROOM -> R.string.column_room
            ImportColumn.WEEKDAY -> R.string.column_weekday
            ImportColumn.PERIODS -> R.string.column_periods
            ImportColumn.WEEKS -> R.string.column_weeks
        },
    )

    fun problem(problem: ImportProblem): String {
        val detail = when (val kind = problem.kind) {
            is ImportProblem.Kind.MissingColumns -> res.getString(R.string.problem_missing_columns, list(kind.columns.map(::column)))
            is ImportProblem.Kind.MissingValue -> res.getString(R.string.problem_missing_value, column(kind.column))
            is ImportProblem.Kind.InvalidValue -> res.getString(R.string.problem_invalid_value, column(kind.column), kind.text)
            is ImportProblem.Kind.PeriodsOutOfRange -> res.getString(R.string.problem_periods_out_of_range, kind.text, kind.periodsPerDay)
        }
        return res.getString(R.string.problem_line, problem.line, detail)
    }

    /** "a, b and c" / "a、b和c" */
    fun list(items: List<String>): String = when {
        items.size <= 1 -> items.firstOrNull().orEmpty()
        locale.language == "zh" -> items.dropLast(1).joinToString("、") + "和" + items.last()
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    /** e.g. "Monday, September 28, 2026" / "2026年9月28日星期一" */
    fun fullDate(day: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(day.javaDate)

    /** e.g. "Sep 28, 2026" / "2026年9月28日" */
    fun mediumDate(day: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day.javaDate)

    /** The abbreviated month, e.g. "Sep" / "9月". */
    fun month(day: LocalDate): String = DateTimeFormatter.ofPattern("MMM", locale).format(day.javaDate)

    companion object {
        private val SHORT_DAYS = listOf(
            R.string.mon, R.string.tue, R.string.wed, R.string.thu, R.string.fri, R.string.sat, R.string.sun,
        )
        private val FULL_DAYS = listOf(
            R.string.monday, R.string.tuesday, R.string.wednesday, R.string.thursday, R.string.friday, R.string.saturday,
            R.string.sunday,
        )
    }
}

@Composable
fun rememberNames(): Names {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { Names(context.resources) }
}

/** The same day as a `java.time.LocalDate`. */
val LocalDate.javaDate: JavaLocalDate get() = JavaLocalDate.ofEpochDay(daysSinceEpoch.toLong())

/** e.g. "10:00–11:40" */
val ClassSession.timeRange: String get() = "$start–$end"

/** Room and teacher, whichever are filled in, e.g. "A101 · Li". */
val ClassSession.details: String get() = listOf(course.room, course.teacher).filter { it.isNotEmpty() }.joinToString(" · ")
