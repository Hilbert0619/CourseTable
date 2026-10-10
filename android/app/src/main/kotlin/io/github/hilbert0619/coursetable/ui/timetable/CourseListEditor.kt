package io.github.hilbert0619.coursetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.CourseTemplate
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.assigningColors
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.EditorDialog
import io.github.hilbert0619.coursetable.ui.components.EditorScaffold
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette

/** Type courses as "name room teacher" lines to add them to the course list, and manage the list. */
@Composable
fun CourseListEditor(
    timetable: Timetable,
    saved: List<CourseTemplate>,
    actions: AppActions,
    onAdded: (List<CourseTemplate>) -> Unit,
    onDismiss: () -> Unit,
) {
    EditorDialog(onDismiss) { CourseListContent(timetable, saved, actions, onAdded, onDismiss) }
}

@Composable
fun CourseListContent(
    timetable: Timetable,
    saved: List<CourseTemplate>,
    actions: AppActions,
    onAdded: (List<CourseTemplate>) -> Unit,
    onDismiss: () -> Unit,
    initialText: String = "",
) {
    var text by remember { mutableStateOf(initialText) }
    val typed = CourseTemplate.parseList(text).filter { new -> saved.none { it.hasSameDetails(new) } }
    val additions = timetable.assigningColors(typed, saved)

    EditorScaffold(
        title = stringResource(R.string.course_list),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.add),
        confirmEnabled = additions.isNotEmpty(),
        onConfirm = {
            actions.addTemplates(additions)
            onAdded(additions)
            onDismiss()
        },
    ) {
        Section(title = stringResource(R.string.one_course_per_line), footer = stringResource(R.string.course_list_footer)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 5,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }

        if (additions.isNotEmpty()) {
            Section(title = pluralStringResource(R.plurals.new_courses, additions.size, additions.size)) {
                additions.forEachIndexed { index, template ->
                    if (index > 0) RowDivider()
                    TemplateRow(template)
                }
            }
        }

        if (saved.isNotEmpty()) {
            Section(title = stringResource(R.string.your_course_list), footer = stringResource(R.string.course_list_remove_footer)) {
                saved.forEachIndexed { index, template ->
                    if (index > 0) RowDivider()
                    key(template.id) {
                        val dismiss = rememberSwipeToDismissBoxState()
                        LaunchedEffect(dismiss.currentValue) {
                            if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) actions.removeTemplates(setOf(template.id))
                        }
                        SwipeToDismissBox(
                            state = dismiss,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    AppIcon(R.drawable.ic_delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            },
                        ) {
                            Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { TemplateRow(template) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(template: CourseTemplate) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).height(32.dp).background(CoursePalette.color(template.colorIndex), RoundedCornerShape(3.dp)))
        Column {
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            val details = listOf(template.room, template.teacher).filter { it.isNotEmpty() }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
