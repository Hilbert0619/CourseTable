package io.github.hilbert0619.coursetable.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseKitJson
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
import java.util.UUID

// The stored forms of the core types, field by field as on iOS (CourseTable/Persistence, Planner.store). Ids are
// UUID strings.

/** A course on the timetable. */
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val teacher: String,
    val room: String,
    /** 1 = Monday … 7 = Sunday */
    val weekday: Int,
    val startPeriod: Int,
    val periodCount: Int,
    val firstWeek: Int,
    val lastWeek: Int,
    /** `every`, `odd` or `even` */
    val parity: String,
    val colorIndex: Int,
    /** Epoch milliseconds; the order courses were added in. */
    val createdAt: Long,
) {
    fun toCourse() = Course(
        id = UUID.fromString(id), name = name, teacher = teacher, room = room,
        weekday = Weekday.of(weekday) ?: Weekday.MONDAY, startPeriod = startPeriod, periodCount = periodCount,
        weeks = CourseWeeks(firstWeek, lastWeek, parityOf(parity)), colorIndex = colorIndex,
    )

    companion object {
        fun from(course: Course, createdAt: Long) = CourseEntity(
            id = course.id.toString(), name = course.name, teacher = course.teacher, room = course.room,
            weekday = course.weekday.rawValue, startPeriod = course.startPeriod, periodCount = course.periodCount,
            firstWeek = course.weeks.first, lastWeek = course.weeks.last, parity = course.weeks.parity.name.lowercase(),
            colorIndex = course.colorIndex, createdAt = createdAt,
        )

        private fun parityOf(text: String) = WeekParity.entries.firstOrNull { it.name.lowercase() == text } ?: WeekParity.EVERY
    }
}

/** An entry in the course list: a course without a slot, waiting to be placed on the grid. */
@Entity(tableName = "course_templates")
data class CourseTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val room: String,
    val teacher: String,
    val colorIndex: Int,
    val createdAt: Long,
) {
    fun toTemplate() = CourseTemplate(UUID.fromString(id), name, room, teacher, colorIndex)

    companion object {
        fun from(template: CourseTemplate, createdAt: Long) = CourseTemplateEntity(
            template.id.toString(), template.name, template.room, template.teacher, template.colorIndex, createdAt,
        )
    }
}

/** The term and the bell schedule: one row (id 0). */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    /** Monday of week 1, `YYYY-MM-DD`. */
    val semesterStart: String,
    val totalWeeks: Int,
    /** The [PeriodSchedule] as JSON. */
    val periodsJson: String,
) {
    fun semester(today: LocalDate): Semester = Semester(LocalDate.parse(semesterStart) ?: today, totalWeeks)

    val periods: PeriodSchedule
        get() = runCatching { CourseKitJson.decodeFromString(PeriodSchedule.serializer(), periodsJson) }
            .getOrDefault(PeriodSchedule.STANDARD)

    companion object {
        fun from(semester: Semester, periods: PeriodSchedule) = SettingsEntity(
            semesterStart = semester.firstMonday.toString(),
            totalWeeks = semester.totalWeeks,
            periodsJson = CourseKitJson.encodeToString(PeriodSchedule.serializer(), periods),
        )
    }
}

/** A to-do; its timing is a kind plus the fields that kind uses. */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** `YYYY-MM-DD` */
    val day: String,
    /** `anytime`, `at` or `afterClass` */
    val timingKind: String,
    /** Minutes after midnight, for `at`. */
    val minutes: Int?,
    /** For `afterClass`. */
    val courseId: String?,
    val isDone: Boolean,
    /** Epoch milliseconds. */
    val doneAt: Long?,
    val createdAt: Long,
) {
    fun toTodo(): Todo {
        val timing = when (timingKind) {
            "at" -> minutes?.let { TodoTiming.At(TimeOfDay(it)) }
            "afterClass" -> courseId?.let { TodoTiming.AfterClass(UUID.fromString(it)) }
            else -> null
        } ?: TodoTiming.Anytime
        val date = LocalDate.parse(day) ?: LocalDate.from(java.time.Instant.ofEpochMilli(createdAt))
        return Todo(UUID.fromString(id), title, date, timing, isDone)
    }

    companion object {
        fun from(todo: Todo, createdAt: Long, doneAt: Long?) = TodoEntity(
            id = todo.id.toString(),
            title = todo.title,
            day = todo.day.toString(),
            timingKind = when (todo.timing) {
                TodoTiming.Anytime -> "anytime"
                is TodoTiming.At -> "at"
                is TodoTiming.AfterClass -> "afterClass"
            },
            minutes = (todo.timing as? TodoTiming.At)?.time?.minutes,
            courseId = (todo.timing as? TodoTiming.AfterClass)?.courseId?.toString(),
            isDone = todo.isDone,
            doneAt = doneAt,
            createdAt = createdAt,
        )
    }
}

/** A focus session that ended (finished or stopped). */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val goal: String,
    /** Epoch milliseconds. */
    val startedAt: Long,
    val endedAt: Long,
    val focusedSeconds: Int,
    val plannedSeconds: Int,
    val finished: Boolean,
)
