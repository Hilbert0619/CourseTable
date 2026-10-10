package io.github.hilbert0619.coursetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseTemplate
import io.github.hilbert0619.coursekit.PeriodSchedule
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursetable.StudyHourApp
import io.github.hilbert0619.coursetable.data.Repository
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the screens show. `null` until the database has been read. */
data class AppData(
    val timetable: Timetable,
    val templates: List<CourseTemplate>,
    val todos: List<Todo>,
)

/** Everything the screens can change. The view model saves it; screenshot tests pass a stand-in. */
interface AppActions {
    fun saveCourse(course: Course)
    fun deleteCourse(id: UUID)
    fun addCourses(courses: List<Course>, replacingExisting: Boolean)
    fun saveSemester(semester: Semester)
    fun savePeriods(periods: PeriodSchedule)
    fun addTemplates(templates: List<CourseTemplate>)
    fun removeTemplates(ids: Set<UUID>)
    fun saveTodo(todo: Todo)
    fun setTodoDone(id: UUID, isDone: Boolean)
    fun deleteTodo(id: UUID)

    /** Does nothing: for previews and screenshots. */
    object None : AppActions {
        override fun saveCourse(course: Course) = Unit
        override fun deleteCourse(id: UUID) = Unit
        override fun addCourses(courses: List<Course>, replacingExisting: Boolean) = Unit
        override fun saveSemester(semester: Semester) = Unit
        override fun savePeriods(periods: PeriodSchedule) = Unit
        override fun addTemplates(templates: List<CourseTemplate>) = Unit
        override fun removeTemplates(ids: Set<UUID>) = Unit
        override fun saveTodo(todo: Todo) = Unit
        override fun setTodoDone(id: UUID, isDone: Boolean) = Unit
        override fun deleteTodo(id: UUID) = Unit
    }
}

class AppViewModel(private val repository: Repository) : ViewModel(), AppActions {
    val data: StateFlow<AppData?> = combine(repository.timetable, repository.templates, repository.todos, ::AppData)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { repository.ensureSettings() }
    }

    private fun save(block: suspend Repository.() -> Unit) {
        viewModelScope.launch { repository.block() }
    }

    override fun saveCourse(course: Course) = save { saveCourse(course) }
    override fun deleteCourse(id: UUID) = save { deleteCourse(id) }
    override fun addCourses(courses: List<Course>, replacingExisting: Boolean) = save { addCourses(courses, replacingExisting) }
    override fun saveSemester(semester: Semester) = save { saveSemester(semester) }
    override fun savePeriods(periods: PeriodSchedule) = save { savePeriods(periods) }
    override fun addTemplates(templates: List<CourseTemplate>) = save { addTemplates(templates) }
    override fun removeTemplates(ids: Set<UUID>) = save { removeTemplates(ids) }
    override fun saveTodo(todo: Todo) = save { saveTodo(todo) }
    override fun setTodoDone(id: UUID, isDone: Boolean) = save { setTodoDone(id, isDone) }
    override fun deleteTodo(id: UUID) = save { deleteTodo(id) }

    companion object {
        val Factory = viewModelFactory {
            initializer { AppViewModel((this[APPLICATION_KEY] as StudyHourApp).repository) }
        }
    }
}
