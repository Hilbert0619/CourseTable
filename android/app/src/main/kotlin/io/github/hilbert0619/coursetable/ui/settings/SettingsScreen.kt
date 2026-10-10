@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseCSV
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursetable.BuildConfig
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.DayPickerDialog
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.StepperRow
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.coursetable.ui.timetable.ImportScreen
import java.io.File

@Composable
fun SettingsScreen(timetable: Timetable, actions: AppActions, modifier: Modifier = Modifier) {
    var editingPeriods by rememberSaveable { mutableStateOf(false) }
    if (editingPeriods) {
        BackHandler { editingPeriods = false }
        PeriodScheduleEditor(timetable.periods, actions, onBack = { editingPeriods = false }, modifier = modifier)
    } else {
        SettingsList(timetable, actions, onEditPeriods = { editingPeriods = true }, modifier = modifier)
    }
}

@Composable
private fun SettingsList(timetable: Timetable, actions: AppActions, onEditPeriods: () -> Unit, modifier: Modifier) {
    val names = rememberNames()
    val context = LocalContext.current
    val today = LocalNowSource.current.today()
    val semester = timetable.semester
    var pickingStart by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(title = stringResource(R.string.term), footer = stringResource(R.string.term_footer)) {
                ValueRow(stringResource(R.string.first_day_of_term), names.mediumDate(semester.startDate), onClick = { pickingStart = true })
                RowDivider()
                StepperRow(stringResource(R.string.weeks_in_term), semester.totalWeeks, 1..Semester.MAX_WEEKS, valueText = "${semester.totalWeeks}") {
                    actions.saveSemester(semester.withTotalWeeks(it))
                }
                RowDivider()
                ValueRow(stringResource(R.string.tab_today), names.status(semester.status(today)))
            }

            Section(title = stringResource(R.string.class_periods)) {
                ValueRow(stringResource(R.string.period_times), "${timetable.periods.count}", onClick = onEditPeriods)
            }

            Section(title = stringResource(R.string.courses)) {
                ActionRow(R.drawable.ic_import, stringResource(R.string.import_courses_ellipsis)) { importing = true }
                RowDivider()
                ActionRow(R.drawable.ic_export, stringResource(R.string.export_csv), enabled = timetable.courses.isNotEmpty()) {
                    shareCsv(context, timetable.courses)
                }
            }

            Section(title = stringResource(R.string.about)) {
                ValueRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
            }
        }
    }

    if (pickingStart) {
        DayPickerDialog(semester.startDate, onPick = { actions.saveSemester(semester.withStartDate(it)) }, onDismiss = { pickingStart = false })
    }
    if (importing) ImportScreen(timetable, actions, onDismiss = { importing = false })
}

/** The courses as a CSV file (UTF-8 with a byte-order mark, for Excel), offered to the share sheet. */
private fun shareCsv(context: Context, courses: List<Course>) {
    val folder = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(folder, EXPORT_FILE_NAME)
    file.writeBytes(CourseCSV.exportData(courses))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

const val EXPORT_FILE_NAME = "StudyHour.csv"
