package io.github.hilbert0619.coursetable.ui.today

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.aikit.TodoDraft
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.ai.LocalAI
import io.github.hilbert0619.coursetable.ui.ai.TodoQuickAddSection
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.ChoiceRow
import io.github.hilbert0619.coursetable.ui.components.DayPickerDialog
import io.github.hilbert0619.coursetable.ui.components.EditorScaffold
import io.github.hilbert0619.coursetable.ui.components.EditorDialog
import io.github.hilbert0619.coursetable.ui.components.MenuRow
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.TimePickerDialog
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.coursetable.ui.rememberNotificationRequest
import io.github.hilbert0619.coursetable.ui.timeRange
import java.util.UUID

private enum class TodoKind { ANYTIME, AT, AFTER_CLASS }

/** Adds or edits a to-do: what, which day, and when (any time, at a time, or after one of that day's classes). */
@Composable
fun TodoEditor(timetable: Timetable, target: TodoEditorTarget, actions: AppActions, onDismiss: () -> Unit) {
    EditorDialog(onDismiss) { TodoEditorContent(timetable, target, actions, onDismiss) }
}

@Composable
fun TodoEditorContent(timetable: Timetable, target: TodoEditorTarget, actions: AppActions, onDismiss: () -> Unit) {
    val names = rememberNames()
    val nowSource = LocalNowSource.current
    val original = when (target) {
        is TodoEditorTarget.New -> Todo(title = "", day = target.day)
        is TodoEditorTarget.Edit -> target.todo
    }
    // The next full hour.
    val suggestedTime = remember { TimeOfDay(minOf(nowSource.moment().time.hour + 1, 23), 0) }
    var title by remember { mutableStateOf(original.title) }
    var day by remember { mutableStateOf(original.day) }
    var kind by remember {
        mutableStateOf(
            when (original.timing) {
                TodoTiming.Anytime -> TodoKind.ANYTIME
                is TodoTiming.At -> TodoKind.AT
                is TodoTiming.AfterClass -> TodoKind.AFTER_CLASS
            },
        )
    }
    var time by remember { mutableStateOf((original.timing as? TodoTiming.At)?.time ?: suggestedTime) }
    var courseId by remember { mutableStateOf<UUID?>((original.timing as? TodoTiming.AfterClass)?.courseId) }
    var pickingDay by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val sessions = timetable.sessions(day)
    // A class that doesn't meet on the chosen day is replaced by that day's first class.
    LaunchedEffect(kind, day) {
        if (kind == TodoKind.AFTER_CLASS && sessions.none { it.course.id == courseId }) courseId = sessions.firstOrNull()?.course?.id
    }
    val timing: TodoTiming? = when (kind) {
        TodoKind.ANYTIME -> TodoTiming.Anytime
        TodoKind.AT -> TodoTiming.At(time)
        TodoKind.AFTER_CLASS -> courseId?.takeIf { id -> sessions.any { it.course.id == id } }?.let { TodoTiming.AfterClass(it) }
    }
    val isNew = target is TodoEditorTarget.New
    val focus = remember { FocusRequester() }
    val askForNotifications = rememberNotificationRequest()
    val ai = LocalAI.current
    val aiReady = ai?.state?.collectAsStateWithLifecycle()?.value?.isSetUp == true
    LaunchedEffect(Unit) { if (isNew && !aiReady) runCatching { focus.requestFocus() } }
    val resources = LocalContext.current.resources

    /** Fills in the fields from the AI's draft; the class is set before the day, so the check that follows keeps it. */
    fun applyDraft(draft: TodoDraft): String? {
        title = draft.title
        when (val drafted = draft.timing) {
            TodoTiming.Anytime -> {
                day = draft.day
                kind = TodoKind.ANYTIME
            }
            is TodoTiming.At -> {
                time = drafted.time
                day = draft.day
                kind = TodoKind.AT
            }
            is TodoTiming.AfterClass -> if (timetable.sessions(draft.day).any { it.course.id == drafted.courseId }) {
                courseId = drafted.courseId
                day = draft.day
                kind = TodoKind.AFTER_CLASS
            } else {
                day = draft.day
                kind = TodoKind.ANYTIME
                val name = timetable.courses.firstOrNull { it.id == drafted.courseId }?.name.orEmpty()
                return resources.getString(R.string.ai_todo_no_class, name)
            }
        }
        return draft.unknownCourse?.let { resources.getString(R.string.ai_todo_unknown_course, it) }
    }

    EditorScaffold(
        title = stringResource(if (isNew) R.string.new_todo else R.string.edit_todo),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = title.isNotBlank() && timing != null,
        onConfirm = {
            if (timing != null) {
                actions.saveTodo(original.copy(title = title, day = day, timing = timing))
                // A timed to-do comes with a notification.
                if (timing != TodoTiming.Anytime) askForNotifications()
                onDismiss()
            }
        },
    ) {
        if (isNew && ai != null && aiReady) TodoQuickAddSection(ai, timetable, ::applyDraft)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.what_to_do)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
        )
        Section(
            footer = if (kind != TodoKind.ANYTIME) stringResource(R.string.todo_notification_footer) else null,
        ) {
            ValueRow(stringResource(R.string.date), names.mediumDate(day), onClick = { pickingDay = true })
            RowDivider()
            Text(
                stringResource(R.string.when_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
            ChoiceRow(
                options = TodoKind.entries,
                selected = kind,
                label = {
                    stringResource(
                        when (it) {
                            TodoKind.ANYTIME -> R.string.any_time
                            TodoKind.AT -> R.string.at_a_time
                            TodoKind.AFTER_CLASS -> R.string.after_a_class
                        },
                    )
                },
                onSelect = { kind = it },
            )
            when (kind) {
                TodoKind.ANYTIME -> Unit
                TodoKind.AT -> {
                    RowDivider()
                    ValueRow(stringResource(R.string.time), time.toString(), onClick = { pickingTime = true })
                }
                TodoKind.AFTER_CLASS -> {
                    RowDivider()
                    if (sessions.isEmpty()) {
                        ValueRow(stringResource(R.string.no_classes_on_this_day), "")
                    } else {
                        val selected = sessions.firstOrNull { it.course.id == courseId } ?: sessions.first()
                        MenuRow(
                            label = stringResource(R.string.class_label),
                            selected = selected,
                            options = sessions,
                            optionLabel = { "${it.course.name} · ${it.timeRange}" },
                            onSelect = { courseId = it.course.id },
                        )
                    }
                }
            }
        }
        if (!isNew) {
            Section {
                ActionRow(R.drawable.ic_delete, stringResource(R.string.delete_todo), color = MaterialTheme.colorScheme.error) {
                    actions.deleteTodo(original.id)
                    onDismiss()
                }
            }
        }
    }

    if (pickingDay) DayPickerDialog(day, onPick = { day = it }, onDismiss = { pickingDay = false })
    if (pickingTime) TimePickerDialog(time, onPick = { time = it }, onDismiss = { pickingTime = false })
}
