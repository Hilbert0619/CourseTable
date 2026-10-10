@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.ClassPeriod
import io.github.hilbert0619.coursekit.PeriodSchedule
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.ConfirmDialog
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.TimePickerDialog
import io.github.hilbert0619.coursetable.ui.components.WarningRow
import io.github.hilbert0619.coursetable.ui.rememberNames

/** The bell schedule: each period's start and end; add, delete (swipe left) or reset. Every change is saved. */
@Composable
fun PeriodScheduleEditor(initial: PeriodSchedule, actions: AppActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val names = rememberNames()
    var schedule by remember { mutableStateOf(initial) }
    // Each row's identity, so deleting one doesn't hand its swipe state to the next.
    var rowIds by remember { mutableStateOf(List(initial.count) { it }) }
    var nextRowId by remember { mutableStateOf(initial.count) }
    var confirmingReset by remember { mutableStateOf(false) }
    // The period and which end of it is being picked (true: start).
    var picking by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    fun update(new: PeriodSchedule, ids: List<Int>) {
        schedule = new
        rowIds = ids
        actions.savePeriods(new)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.period_times_title)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            val issues = schedule.issues
            if (issues.isNotEmpty()) {
                Section { issues.forEach { WarningRow(names.issue(it)) } }
            }

            Section(footer = stringResource(R.string.swipe_to_delete_period)) {
                schedule.periods.forEachIndexed { index, period ->
                    if (index > 0) RowDivider()
                    key(rowIds[index]) {
                        val dismiss = rememberSwipeToDismissBoxState()
                        LaunchedEffect(dismiss.currentValue) {
                            if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                update(schedule.copy(periods = schedule.periods.filterIndexed { i, _ -> i != index }), rowIds.filterIndexed { i, _ -> i != index })
                            }
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
                            PeriodRow(index + 1, period, names.periods(index + 1, index + 1), onPick = { start -> picking = index to start })
                        }
                    }
                }
            }

            Section {
                ActionRow(R.drawable.ic_add, stringResource(R.string.add_period), enabled = schedule.count < PeriodSchedule.MAX_PERIODS) {
                    val longer = schedule.appendingPeriod()
                    update(longer, rowIds + nextRowId)
                    nextRowId++
                }
                RowDivider()
                ActionRow(null, stringResource(R.string.reset_to_default), color = MaterialTheme.colorScheme.error) { confirmingReset = true }
            }
        }
    }

    picking?.let { (index, start) ->
        val period = schedule.periods.getOrNull(index)
        if (period != null) {
            TimePickerDialog(
                if (start) period.start else period.end,
                onPick = { time: TimeOfDay ->
                    val changed = if (start) period.copy(start = time) else period.copy(end = time)
                    update(schedule.copy(periods = schedule.periods.toMutableList().also { it[index] = changed }), rowIds)
                },
                onDismiss = { picking = null },
            )
        }
    }

    if (confirmingReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_periods_question),
            text = null,
            confirmLabel = stringResource(R.string.reset),
            onConfirm = {
                update(PeriodSchedule.STANDARD, List(PeriodSchedule.STANDARD.count) { nextRowId + it })
                nextRowId += PeriodSchedule.STANDARD.count
            },
            onDismiss = { confirmingReset = false },
        )
    }
}

@Composable
private fun PeriodRow(number: Int, period: ClassPeriod, label: String, onPick: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onPick(true) }) { Text(period.start.toString()) }
        Text("–")
        TextButton(onClick = { onPick(false) }) { Text(period.end.toString()) }
    }
}
