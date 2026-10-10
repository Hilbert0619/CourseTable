package io.github.hilbert0619.coursetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseWeeks
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.WeekParity
import io.github.hilbert0619.coursekit.Weekday
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.ChoiceRow
import io.github.hilbert0619.coursetable.ui.components.ConfirmDialog
import io.github.hilbert0619.coursetable.ui.components.EditorDialog
import io.github.hilbert0619.coursetable.ui.components.EditorScaffold
import io.github.hilbert0619.coursetable.ui.components.MenuRow
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.StepperRow
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.components.WarningRow
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette

/** What the course editor is showing. */
sealed interface CourseEditorTarget {
    data class New(val weekday: Weekday, val period: Int) : CourseEditorTarget
    data class Edit(val course: Course) : CourseEditorTarget
}

@Composable
fun CourseEditor(timetable: Timetable, target: CourseEditorTarget, actions: AppActions, onDismiss: () -> Unit) {
    EditorDialog(onDismiss) { CourseEditorContent(timetable, target, actions, onDismiss) }
}

@Composable
fun CourseEditorContent(timetable: Timetable, target: CourseEditorTarget, actions: AppActions, onDismiss: () -> Unit) {
    val names = rememberNames()
    val periodsPerDay = maxOf(timetable.periods.count, 1)
    val isNew = target is CourseEditorTarget.New
    var draft by remember {
        mutableStateOf(
            when (target) {
                is CourseEditorTarget.New -> Course(
                    name = "",
                    weekday = target.weekday,
                    startPeriod = target.period,
                    periodCount = minOf(2, maxOf(timetable.periods.count - target.period + 1, 1)),
                    weeks = CourseWeeks(1, timetable.semester.totalWeeks),
                    colorIndex = Course.suggestedColorIndex(timetable.courses),
                )
                is CourseEditorTarget.Edit -> target.course
            },
        )
    }
    var confirmingDelete by remember { mutableStateOf(false) }
    val issues = timetable.issues(draft)
    val clashes = timetable.clashes(draft)

    EditorScaffold(
        title = stringResource(if (isNew) R.string.new_course else R.string.edit_course),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = issues.none { it.isBlocking },
        onConfirm = {
            actions.saveCourse(draft)
            onDismiss()
        },
    ) {
        Section {
            TextField(stringResource(R.string.course_name), draft.name, placeholder = null) { draft = draft.copy(name = it) }
            TextField(stringResource(R.string.teacher), draft.teacher) { draft = draft.copy(teacher = it) }
            TextField(stringResource(R.string.room), draft.room) { draft = draft.copy(room = it) }
        }

        Section(title = stringResource(R.string.time)) {
            MenuRow(stringResource(R.string.day), draft.weekday, Weekday.entries, { names.full(it) }) { draft = draft.copy(weekday = it) }
            RowDivider()
            MenuRow(stringResource(R.string.starts_at), draft.startPeriod, (1..periodsPerDay).toList(), { names.periods(it, it) }) { start ->
                draft = draft.copy(startPeriod = start, periodCount = minOf(draft.periodCount, maxOf(periodsPerDay - start + 1, 1)))
            }
            RowDivider()
            StepperRow(pluralStringResource(R.plurals.length_periods, draft.periodCount, draft.periodCount), draft.periodCount, 1..periodsPerDay) {
                draft = draft.copy(periodCount = it)
            }
            timetable.periods.timeSpan(draft.periods)?.let { span ->
                RowDivider()
                ValueRow(stringResource(R.string.class_time), "${span.start}–${span.end}")
            }
        }

        Section(title = stringResource(R.string.weeks)) {
            StepperRow(stringResource(R.string.from_week, draft.weeks.first), draft.weeks.first, 1..Semester.MAX_WEEKS) {
                draft = draft.copy(weeks = draft.weeks.copy(first = it))
            }
            RowDivider()
            StepperRow(stringResource(R.string.to_week, draft.weeks.last), draft.weeks.last, 1..Semester.MAX_WEEKS) {
                draft = draft.copy(weeks = draft.weeks.copy(last = it))
            }
            ChoiceRow(WeekParity.entries, draft.weeks.parity, label = { names.parity(it) }) {
                draft = draft.copy(weeks = draft.weeks.copy(parity = it))
            }
        }

        Section(title = stringResource(R.string.color)) {
            ColorPickerRow(draft.colorIndex) { draft = draft.copy(colorIndex = it) }
        }

        if (issues.isNotEmpty() || clashes.isNotEmpty()) {
            Section {
                issues.forEach { WarningRow(names.issue(it), blocking = it.isBlocking) }
                clashes.forEach { WarningRow(names.clash(it)) }
            }
        }

        if (!isNew) {
            Section {
                ActionRow(R.drawable.ic_delete, stringResource(R.string.delete_course), color = MaterialTheme.colorScheme.error) {
                    confirmingDelete = true
                }
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_this_course),
            text = null,
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                actions.deleteCourse(draft.id)
                onDismiss()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun TextField(label: String, value: String, placeholder: String? = stringResource(R.string.optional), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** The ten course colours; the chosen one has a check mark. */
@Composable
private fun ColorPickerRow(selection: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
        CoursePalette.colors.forEachIndexed { index, color ->
            val label = stringResource(R.string.color_number, index + 1)
            Box(
                Modifier.weight(1f).semantics {
                    contentDescription = label
                    selected = index == selection
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(color).clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (index == selection) AppIcon(R.drawable.ic_check, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
    }
}
