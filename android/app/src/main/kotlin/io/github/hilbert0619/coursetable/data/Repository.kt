package io.github.hilbert0619.coursetable.data

import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseTemplate
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.PeriodSchedule
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Reads and changes the stored data in core types. Every change is saved at once; [onChange] runs after each one (the
 * app refreshes its widget and alerts there).
 */
class Repository(
    private val database: AppDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    var onChange: suspend () -> Unit = {},
) {
    private val dao = database.dao()

    private fun today() = LocalDate.from(clock.instant(), clock.zone)
    private var lastStamp = 0L

    /** The time for a new row's `createdAt`: never the same twice, so rows keep the order they were added in. */
    @Synchronized
    private fun now(): Long = maxOf(clock.millis(), lastStamp + 1).also { lastStamp = it }

    /** The term, bell schedule and courses. Before the settings row exists, the term starts this week. */
    val timetable: Flow<Timetable> = combine(dao.settings(), dao.courses()) { settings, courses ->
        Timetable(
            semester = settings?.semester(today()) ?: Semester(today()),
            periods = settings?.periods ?: PeriodSchedule.STANDARD,
            courses = courses.map { it.toCourse() },
        )
    }

    /** The course list, in the order the courses were typed. */
    val templates: Flow<List<CourseTemplate>> = dao.templates().map { list -> list.map { it.toTemplate() } }

    /** Every to-do, oldest first. */
    val todos: Flow<List<Todo>> = dao.todos().map { list -> list.map { it.toTodo() } }

    /** Every ended focus session, newest first (Focus → All Sessions lists them all). */
    val focusSessions: Flow<List<FocusSessionRecord>> = dao.allFocusSessions().map { list -> list.map { it.toRecord() } }

    suspend fun currentTimetable(): Timetable = timetable.first()

    /** Creates the settings row with defaults (term starting this week) on first launch. */
    suspend fun ensureSettings() {
        if (dao.currentSettings() == null) dao.upsertSettings(SettingsEntity.from(Semester(today()), PeriodSchedule.STANDARD))
    }

    // Courses

    /** Adds [course], or saves the changes to the one with its id. Names are trimmed. */
    suspend fun saveCourse(course: Course) {
        val cleaned = course.copy(name = course.name.trim(), teacher = course.teacher.trim(), room = course.room.trim())
        val createdAt = dao.course(course.id.toString())?.createdAt ?: now()
        dao.upsertCourse(CourseEntity.from(cleaned, createdAt))
        onChange()
    }

    suspend fun deleteCourse(id: UUID) {
        dao.deleteCourse(id.toString())
        onChange()
    }

    /** Adds imported courses in their order, after removing every course if [replacingExisting]. */
    suspend fun addCourses(courses: List<Course>, replacingExisting: Boolean = false) {
        val entities = courses.map { course -> CourseEntity.from(course, now()) }
        if (replacingExisting) dao.replaceCourses(entities) else dao.insertCourses(entities)
        onChange()
    }

    // Settings

    suspend fun saveSemester(semester: Semester) {
        val periods = dao.currentSettings()?.periods ?: PeriodSchedule.STANDARD
        dao.upsertSettings(SettingsEntity.from(semester, periods))
        onChange()
    }

    suspend fun savePeriods(periods: PeriodSchedule) {
        val semester = dao.currentSettings()?.semester(today()) ?: Semester(today())
        dao.upsertSettings(SettingsEntity.from(semester, periods))
        onChange()
    }

    // Course list

    /** Adds course-list entries, keeping their order. */
    suspend fun addTemplates(templates: List<CourseTemplate>) {
        dao.insertTemplates(templates.map { template -> CourseTemplateEntity.from(template, now()) })
        onChange()
    }

    suspend fun removeTemplates(ids: Set<UUID>) {
        dao.deleteTemplates(ids.map { it.toString() })
        onChange()
    }

    // To-dos

    /** Adds [todo], or saves the changes to the one with its id. The title is trimmed (at most 200 characters). */
    suspend fun saveTodo(todo: Todo) {
        val title = todo.title.trim()
        if (title.isEmpty()) return
        val cleaned = todo.copy(title = title.take(200))
        val existing = dao.todo(todo.id.toString())
        val doneAt = when {
            existing == null -> if (cleaned.isDone) now() else null
            existing.isDone == cleaned.isDone -> existing.doneAt
            cleaned.isDone -> now()
            else -> null
        }
        dao.upsertTodo(TodoEntity.from(cleaned, existing?.createdAt ?: now(), doneAt))
        onChange()
    }

    suspend fun setTodoDone(id: UUID, isDone: Boolean) {
        val existing = dao.todo(id.toString()) ?: return
        if (existing.isDone == isDone) return
        saveTodo(existing.toTodo().copy(isDone = isDone))
    }

    suspend fun deleteTodo(id: UUID) {
        dao.deleteTodo(id.toString())
        onChange()
    }

    /** Settings → Storage: deletes every ticked-off to-do. */
    suspend fun deleteCompletedTodos() {
        dao.deleteCompletedTodos()
        onChange()
    }

    // Focus history

    /** Saves a session that ended; a second record of the same session is ignored. */
    suspend fun recordFocus(session: FocusSessionRecord) {
        dao.insertFocusSession(FocusSessionEntity.from(session))
    }

    suspend fun deleteFocusSessions(ids: Collection<UUID>) {
        if (ids.isNotEmpty()) dao.deleteFocusSessions(ids.map { it.toString() })
    }

    /** Focus → All Sessions → Clear History, and Settings → Storage. */
    suspend fun deleteAllFocusSessions() {
        dao.deleteAllFocusSessions()
    }

    /** Demo data for screenshots: a term in its fifth week, a few courses on today and tomorrow, and to-dos. */
    suspend fun insertDemoData(chinese: Boolean) {
        val today = today()
        dao.upsertSettings(SettingsEntity.from(Semester(today.adding(-28)), PeriodSchedule.STANDARD))
        val courses = DemoData.courses(today, chinese)
        addCourses(courses)
        DemoData.todos(today, courses, chinese).forEach { todo ->
            val created = now()
            dao.upsertTodo(TodoEntity.from(todo, created, if (todo.isDone) created else null))
        }
        onChange()
    }

    companion object {
        /** The sessions the Assistant looks at (iOS `PlannerModel.recentSessionLimit`). */
        const val RECENT_FOCUS_SESSIONS = 50
    }
}
