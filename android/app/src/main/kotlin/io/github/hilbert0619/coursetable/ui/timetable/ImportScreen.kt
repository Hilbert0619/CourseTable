package io.github.hilbert0619.coursetable.ui.timetable

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseCSV
import io.github.hilbert0619.coursekit.CourseImporter
import io.github.hilbert0619.coursekit.ImportedText
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.planningImport
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.Names
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.ConfirmDialog
import io.github.hilbert0619.coursetable.ui.components.EditorDialog
import io.github.hilbert0619.coursetable.ui.components.EditorScaffold
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.TabsAsSpaces
import io.github.hilbert0619.coursetable.ui.components.WarningRow
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette

/** Paste CSV or spreadsheet rows (or open a file), preview the courses, then add them. */
@Composable
fun ImportScreen(timetable: Timetable, actions: AppActions, onDismiss: () -> Unit) {
    EditorDialog(onDismiss) { ImportContent(timetable, actions, onDismiss) }
}

@Composable
fun ImportContent(timetable: Timetable, actions: AppActions, onDismiss: () -> Unit, initialText: String = "") {
    val names = rememberNames()
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }
    var replacesExisting by remember { mutableStateOf(false) }
    var confirmingReplace by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val result = CourseImporter(timetable).parse(text)
    val plan = timetable.planningImport(result.courses, replacesExisting)
    val tooLarge = stringResource(R.string.file_too_large)
    val unreadable = stringResource(R.string.file_unreadable)
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            when (val loaded = readText(context, uri)) {
                is FileText.Text -> {
                    text = loaded.text
                    fileError = null
                }
                FileText.TooLarge -> fileError = tooLarge
                FileText.Unreadable -> fileError = unreadable
            }
        }
    }

    fun importNow() {
        actions.addCourses(plan.added, plan.replacesExisting)
        onDismiss()
    }

    EditorScaffold(
        title = stringResource(R.string.import_courses),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.import_action),
        confirmEnabled = plan.added.isNotEmpty(),
        onConfirm = { if (plan.replacesExisting && timetable.courses.isNotEmpty()) confirmingReplace = true else importNow() },
    ) {
        Section(title = stringResource(R.string.paste_csv), footer = stringResource(R.string.import_footer)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 6,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = TabsAsSpaces,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
            ActionRow(R.drawable.ic_file_open, stringResource(R.string.choose_file)) {
                chooseFile.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
            }
            RowDivider()
            ActionRow(null, stringResource(R.string.insert_example)) {
                text = if (names.locale.language == "zh") CourseCSV.CHINESE_EXAMPLE else CourseCSV.ENGLISH_EXAMPLE
            }
        }

        fileError?.let { error -> Section { WarningRow(error) } }

        Section(footer = stringResource(R.string.replace_existing_footer)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.replace_existing), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = replacesExisting, onCheckedChange = { replacesExisting = it })
            }
        }

        if (text.isNotBlank()) {
            Section(
                title = pluralStringResource(R.plurals.courses_to_add, plan.added.size, plan.added.size),
                footer = if (plan.duplicates.isNotEmpty()) stringResource(R.string.duplicates_skipped, plan.duplicates.size) else null,
            ) {
                if (plan.added.isEmpty()) {
                    Text(
                        stringResource(R.string.no_courses_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                plan.added.forEachIndexed { index, course ->
                    if (index > 0) RowDivider()
                    ImportedCourseRow(course, plan.timetable, names)
                }
            }
            if (result.problems.isNotEmpty()) {
                Section(title = stringResource(R.string.lines_not_imported)) {
                    result.problems.forEach { WarningRow(names.problem(it)) }
                }
            }
        }
    }

    if (confirmingReplace) {
        ConfirmDialog(
            title = stringResource(R.string.replace_all_courses),
            text = pluralStringResource(R.plurals.deletes_courses, timetable.courses.size, timetable.courses.size),
            confirmLabel = stringResource(R.string.replace),
            onConfirm = ::importNow,
            onDismiss = { confirmingReplace = false },
        )
    }
}

@Composable
private fun ImportedCourseRow(course: Course, timetable: Timetable, names: Names) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(CoursePalette.color(course.colorIndex), RoundedCornerShape(3.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${names.full(course.weekday)} · ${names.periods(course)} · ${names.weeks(course.weeks)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val details = listOf(course.room, course.teacher).filter { it.isNotEmpty() }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            timetable.issues(course).forEach { Text(names.issue(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            timetable.clashes(course).forEach { Text(names.clash(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

internal sealed interface FileText {
    data class Text(val text: String) : FileText
    data object TooLarge : FileText
    data object Unreadable : FileText
}

/** The file's text if it is small enough and decodes (UTF-8, UTF-16 with a byte-order mark, or GB18030). */
internal fun readText(context: Context, uri: Uri): FileText {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(ImportedText.MAX_BYTES + 1)
            var count = 0
            while (count < buffer.size) {
                val read = stream.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            buffer.copyOf(count)
        }
    }.getOrNull() ?: return FileText.Unreadable
    if (bytes.size > ImportedText.MAX_BYTES) return FileText.TooLarge
    return ImportedText.decode(bytes)?.let { FileText.Text(it) } ?: FileText.Unreadable
}
