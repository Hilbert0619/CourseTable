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
import io.github.hilbert0619.coursetable.alerts.AlertNotifications
import io.github.hilbert0619.coursetable.alerts.ReminderSettings
import io.github.hilbert0619.coursetable.alerts.SystemSettings
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.ai.AISettingsScreen
import io.github.hilbert0619.coursetable.ui.ai.LocalAI
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.DayPickerDialog
import io.github.hilbert0619.coursetable.ui.components.LocalAppearance
import io.github.hilbert0619.coursetable.ui.components.MenuRow
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.StepperRow
import io.github.hilbert0619.coursetable.ui.components.SwitchRow
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.components.WarningRow
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.coursetable.ui.rememberNotificationRequest
import io.github.hilbert0619.coursetable.ui.rememberSystemState
import io.github.hilbert0619.coursetable.ui.timetable.ImportScreen
import java.io.File

@Composable
fun SettingsScreen(
    timetable: Timetable,
    reminders: ReminderSettings,
    actions: AppActions,
    modifier: Modifier = Modifier,
    focusSessionCount: Int = 0,
    completedTodoCount: Int = 0,
) {
    var editingPeriods by rememberSaveable { mutableStateOf(false) }
    var showingReliable by rememberSaveable { mutableStateOf(false) }
    var showingAI by rememberSaveable { mutableStateOf(false) }
    var showingAppearance by rememberSaveable { mutableStateOf(false) }
    var showingStorage by rememberSaveable { mutableStateOf(false) }
    val ai = LocalAI.current
    when {
        showingAppearance -> {
            BackHandler { showingAppearance = false }
            AppearanceScreen(onBack = { showingAppearance = false }, modifier = modifier)
        }
        showingStorage -> {
            BackHandler { showingStorage = false }
            StorageScreen(focusSessionCount, completedTodoCount, actions, onBack = { showingStorage = false }, modifier = modifier)
        }
        editingPeriods -> {
            BackHandler { editingPeriods = false }
            PeriodScheduleEditor(timetable.periods, actions, onBack = { editingPeriods = false }, modifier = modifier)
        }
        showingReliable -> {
            BackHandler { showingReliable = false }
            ReliableRemindersScreen(actions, onBack = { showingReliable = false }, modifier = modifier)
        }
        showingAI && ai != null -> {
            BackHandler { showingAI = false }
            AISettingsScreen(ai, onBack = { showingAI = false }, modifier = modifier)
        }
        else -> SettingsList(
            timetable, reminders, actions, onEditPeriods = { editingPeriods = true }, onShowReliable = { showingReliable = true },
            onShowAI = { showingAI = true }, onShowAppearance = { showingAppearance = true }, onShowStorage = { showingStorage = true },
            modifier = modifier,
        )
    }
}

@Composable
private fun SettingsList(
    timetable: Timetable,
    reminders: ReminderSettings,
    actions: AppActions,
    onEditPeriods: () -> Unit,
    onShowReliable: () -> Unit,
    onShowAI: () -> Unit,
    onShowAppearance: () -> Unit,
    onShowStorage: () -> Unit,
    modifier: Modifier,
) {
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

            // The look in use, and the way to change it (iOS `AppearanceSection`).
            val glass = LocalAppearance.current.settings.isGlass
            Section(footer = stringResource(R.string.appearance_footer)) {
                ValueRow(
                    stringResource(R.string.appearance),
                    stringResource(if (glass) R.string.style_glass else R.string.style_classic),
                    onClick = onShowAppearance,
                )
            }

            ReminderSection(reminders, actions, onShowReliable)

            LocalAI.current?.let { ai ->
                val aiState by ai.state.collectAsStateWithLifecycle()
                Section(footer = stringResource(R.string.ai_settings_footer)) {
                    ValueRow(stringResource(R.string.ai_help), aiState.defaultConnection?.name ?: stringResource(R.string.status_off), onClick = onShowAI)
                }
            }

            Section(footer = stringResource(R.string.storage_footer)) {
                ActionRow(R.drawable.ic_storage, stringResource(R.string.storage), onClick = onShowStorage)
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

/** Class reminders and the live class notification (iOS `ReminderSection`), and the way to Reliable Reminders. */
@Composable
private fun ReminderSection(reminders: ReminderSettings, actions: AppActions, onShowReliable: () -> Unit) {
    val context = LocalContext.current
    val allowed = rememberSystemState { AlertNotifications.canNotify(context) }
    val askForNotifications = rememberNotificationRequest()
    val footer = stringResource(R.string.reminders_footer_reminders) + "\n\n" + stringResource(R.string.reminders_footer_live)
    Section(title = stringResource(R.string.reminders), footer = footer) {
        SwitchRow(stringResource(R.string.class_reminders), reminders.remindersEnabled) { on ->
            actions.setRemindersEnabled(on)
            if (on) askForNotifications()
        }
        RowDivider()
        SwitchRow(stringResource(R.string.live_notification), reminders.liveEnabled) { on ->
            actions.setLiveNotificationsEnabled(on)
            if (on) askForNotifications()
        }
        if (reminders.remindersEnabled || reminders.liveEnabled) {
            RowDivider()
            MenuRow(stringResource(R.string.remind_me), reminders.leadMinutes, ReminderSettings.LEAD_CHOICES, { leadLabel(it) }) {
                actions.setLeadMinutes(it)
            }
            if (!allowed) {
                RowDivider()
                WarningRow(stringResource(R.string.notifications_off))
                ActionRow(R.drawable.ic_notifications, stringResource(R.string.open_settings)) { SystemSettings.openNotificationSettings(context) }
            }
        }
        RowDivider()
        ActionRow(R.drawable.ic_alarm, stringResource(R.string.reliable_reminders), onClick = onShowReliable)
    }
}

/** "At start time", "10 minutes before" */
@Composable
private fun leadLabel(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.at_start_time) else stringResource(R.string.minutes_before, minutes)

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
