package io.github.hilbert0619.coursetable.ui.words

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.ConfirmDialog
import io.github.hilbert0619.coursetable.ui.components.DayPickerDialog
import io.github.hilbert0619.coursetable.ui.components.MenuRow
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.StepperRow
import io.github.hilbert0619.coursetable.ui.components.SwitchRow
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.components.WarningRow
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.vocabkit.SessionMode
import io.github.hilbert0619.vocabkit.store.VocabSettings
import kotlinx.coroutines.launch

/** Study settings, sync, and the account (iOS `VocabSettingsView`). */
@Composable
fun VocabSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val controller = LocalWordsController.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val names = rememberNames()
    val text = WordsText(LocalContext.current.resources)
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingSignOut by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var pickingExamDate by remember { mutableStateOf(false) }
    var retention by remember(settings.desiredRetention) { mutableFloatStateOf(settings.desiredRetention.toFloat()) }
    fun change(transform: (VocabSettings) -> VocabSettings) {
        controller.launch { model.updateSettings(transform) }
    }
    // Upload changed settings when leaving the screen (not on every tap of the stepper).
    DisposableEffect(Unit) { onDispose { controller.launch { syncNow() } } }

    WordsScaffold(stringResource(R.string.study_settings), onBack, modifier) { content ->
        Column(content.verticalScroll(rememberScrollState())) {
            Section(title = stringResource(R.string.study), footer = stringResource(R.string.day_start_footer)) {
                StepperRow(stringResource(R.string.new_words_a_day), settings.dailyNewWords, 0..200, valueText = "${settings.dailyNewWords}", step = 5) { value ->
                    change { it.copy(dailyNewWords = value) }
                }
                RowDivider()
                MenuRow(stringResource(R.string.review_questions), settings.reviewMode, SessionMode.entries, { modeName(it) }) { mode ->
                    change { it.copy(reviewMode = mode) }
                }
                RowDivider()
                MenuRow(stringResource(R.string.new_day_starts_at), settings.dayStart, (0..6).map { TimeOfDay(it, 0) }, { it.toString() }) { time ->
                    change { it.copy(dayStart = time) }
                }
            }

            Section(title = stringResource(R.string.exam), footer = stringResource(R.string.exam_date_footer)) {
                SwitchRow(stringResource(R.string.exam_date), settings.examDate != null) { on ->
                    change { it.copy(examDate = if (on) it.examDate ?: defaultExamDate(state.today(controller.now())) else null) }
                }
                settings.examDate?.let { examDate ->
                    RowDivider()
                    ValueRow(stringResource(R.string.date), names.mediumDate(examDate), onClick = { pickingExamDate = true })
                }
            }

            Section(title = stringResource(R.string.advanced), footer = stringResource(R.string.target_recall_footer)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row {
                        Text(stringResource(R.string.target_recall), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(text.percent(retention.toDouble()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = retention,
                        onValueChange = { retention = it },
                        onValueChangeFinished = {
                            val rounded = Math.round(retention * 100) / 100.0
                            change { it.copy(desiredRetention = rounded) }
                        },
                        valueRange = 0.80f..0.95f,
                        steps = 14,
                    )
                }
            }

            Section(title = stringResource(R.string.sync)) { SyncStatusRow() }

            Section(title = stringResource(R.string.account), footer = stringResource(R.string.account_footer)) {
                controller.vocab.account?.let { account ->
                    ValueRow(stringResource(R.string.signed_in_as), account.email)
                    RowDivider()
                }
                ActionRow(null, stringResource(R.string.sign_out), enabled = !working) { confirmingSignOut = true }
                RowDivider()
                ActionRow(null, stringResource(R.string.delete_account_ellipsis), enabled = !working, color = MaterialTheme.colorScheme.error) {
                    confirmingDelete = true
                }
            }

            error?.let { Section { WarningRow(it, blocking = true) } }

            Section(title = stringResource(R.string.about)) {
                Text(
                    stringResource(R.string.words_credits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }

    if (pickingExamDate) {
        DayPickerDialog(settings.examDate ?: defaultExamDate(state.today(controller.now())), onPick = { day ->
            change { it.copy(examDate = day) }
        }, onDismiss = { pickingExamDate = false })
    }
    if (confirmingSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.sign_out_question),
            text = if (state.unsentCount > 0) pluralStringResource(R.plurals.unsent_answers_warning, state.unsentCount, state.unsentCount) else null,
            confirmLabel = stringResource(R.string.sign_out),
            onConfirm = {
                working = true
                scope.launch {
                    try {
                        controller.vocab.signOut()
                    } finally {
                        working = false
                    }
                }
            },
            onDismiss = { confirmingSignOut = false },
        )
    }
    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_account_question),
            text = stringResource(R.string.delete_account_message),
            confirmLabel = stringResource(R.string.delete_account),
            onConfirm = {
                working = true
                scope.launch {
                    try {
                        controller.vocab.deleteAccount()
                    } catch (failure: Exception) {
                        error = text.error(failure)
                    } finally {
                        working = false
                    }
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun modeName(mode: SessionMode): String = stringResource(
    when (mode) {
        SessionMode.RECOGNITION -> R.string.mode_recall
        SessionMode.CHOICE -> R.string.mode_choice
        SessionMode.SPELLING -> R.string.mode_spelling
        SessionMode.MIXED -> R.string.mode_mixed
    },
)

/** Late December, when the national 考研 exam is usually held. */
private fun defaultExamDate(today: Int): LocalDate {
    val day = LocalDate(daysSinceEpoch = today)
    val year = if (day.month == 12 && day.day > 20) day.year + 1 else day.year
    return LocalDate(year, 12, 20)
}
