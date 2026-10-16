package io.github.hilbert0619.coursetable.ai

import io.github.hilbert0619.aikit.AgentChange
import io.github.hilbert0619.aikit.AgentWorld
import io.github.hilbert0619.aikit.FocusSnapshot
import io.github.hilbert0619.aikit.VocabSnapshot
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.plusSeconds
import io.github.hilbert0619.coursetable.focus.FocusActions
import io.github.hilbert0619.coursetable.focus.FocusState
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.AppData
import io.github.hilbert0619.vocabkit.store.VocabApp
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Undoing a confirmed change, while the chat is open. */
sealed interface AgentUndo {
    data class DeleteTodo(val id: UUID) : AgentUndo
    data class RestoreTodo(val todo: Todo) : AgentUndo
    data class DeleteCourses(val ids: List<UUID>) : AgentUndo
}

/**
 * What the Assistant's tools read and change (iOS `AgentContext`): the app's data and actions, the focus timer and the
 * Words account, as the screen last saw them.
 */
class AgentContext(
    val data: AppData,
    val actions: AppActions,
    val focus: FocusState,
    val focusActions: FocusActions,
    /** `null` where the Words tab isn't available. */
    val vocab: VocabApp?,
    val clock: Clock = Clock.systemDefaultZone(),
) {
    val timetable: Timetable get() = data.timetable

    fun world(now: Instant = clock.instant()): AgentWorld {
        val zone = clock.zone
        val running = focus.run?.let { run ->
            FocusSnapshot.Running(
                goal = run.goal,
                startedAt = run.startedAt,
                endsAt = run.startedAt.plusSeconds(run.plan.totalSeconds + run.pausedSeconds - run.skippedSeconds),
                isPaused = run.isPaused,
            )
        }
        val sessions = data.focusSessions.map { FocusSnapshot.Session(it.goal, it.startedAt, it.focusedSeconds, it.finished) }
        return AgentWorld(
            today = LocalDate.from(now, zone),
            now = TimeOfDay.from(now, zone),
            timetable = data.timetable,
            todos = data.todos,
            focus = FocusSnapshot(focus.settings, sessions, running),
            vocab = vocabSnapshot(now),
        )
    }

    private fun vocabSnapshot(now: Instant): VocabSnapshot? {
        val vocab = vocab ?: return null
        if (vocab.account == null) return null
        val state = vocab.model.state.value
        return VocabSnapshot(
            bookName = state.currentBook?.name.orEmpty(),
            book = state.bookWords,
            entries = state.words + state.bookEntries,
            cards = state.cards,
            events = state.events,
            plan = state.plan(now),
            exam = state.examCountdown(now),
            newWordsPerDay = state.settings.dailyNewWords,
            desiredRetention = state.settings.desiredRetention,
            today = state.clock.day(now),
            scheduler = state.scheduler,
        )
    }

    /** Makes a confirmed change (not courses: those go through the import preview). */
    fun apply(change: AgentChange): AgentUndo? = when (change) {
        is AgentChange.AddTodo -> {
            actions.saveTodo(change.todo)
            AgentUndo.DeleteTodo(change.todo.id)
        }
        is AgentChange.UpdateTodo -> {
            actions.saveTodo(change.after)
            AgentUndo.RestoreTodo(change.before)
        }
        is AgentChange.DeleteTodo -> {
            actions.deleteTodo(change.todo.id)
            AgentUndo.RestoreTodo(change.todo)
        }
        is AgentChange.StartFocus -> {
            focusActions.setGoal(change.goal)
            focusActions.setSettings(change.settings)
            focusActions.start()
            null
        }
        is AgentChange.AddCourses -> null
    }

    fun undo(undo: AgentUndo) {
        when (undo) {
            is AgentUndo.DeleteTodo -> actions.deleteTodo(undo.id)
            is AgentUndo.RestoreTodo -> actions.saveTodo(undo.todo)
            is AgentUndo.DeleteCourses -> undo.ids.forEach(actions::deleteCourse)
        }
    }
}
