@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hilbert0619.coursekit.FocusRun
import io.github.hilbert0619.coursekit.FocusSettings
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.data.FocusSessionRecord
import io.github.hilbert0619.coursetable.focus.FocusActions
import io.github.hilbert0619.coursetable.focus.FocusSound
import io.github.hilbert0619.coursetable.focus.FocusState
import io.github.hilbert0619.coursetable.focus.FocusText
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.ConfirmDialog
import io.github.hilbert0619.coursetable.ui.components.MenuRow
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.StepperRow
import io.github.hilbert0619.coursetable.ui.components.SwitchRow
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.currentMoment
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.coursetable.ui.rememberNotificationRequest
import java.time.Instant
import kotlinx.coroutines.delay

/** The Focus tab (iOS `FocusView`): set up a session (goal, length, Pomodoro rhythm, sound), run it, see today's focus time. */
@Composable
fun FocusScreen(state: FocusState, sessions: List<FocusSessionRecord>, actions: FocusActions, modifier: Modifier = Modifier) {
    val run = state.run
    val askForNotifications = rememberNotificationRequest()
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_focus)) }) },
        bottomBar = {
            // Always in reach, above the tab bar.
            if (run == null) {
                Surface {
                    Button(
                        onClick = {
                            actions.start()
                            askForNotifications()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 52.dp),
                    ) {
                        AppIcon(R.drawable.ic_play, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.start_focusing), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        if (run != null) {
            RunningFocus(run, state, actions, Modifier.padding(padding))
        } else {
            FocusSetup(state, sessions, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun rememberFocusText(): FocusText {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { FocusText(context.resources) }
}

private val LENGTHS = FocusSettings.TOTAL_RANGE.step(5).toList()

/** Green for breaks, as on iOS. */
private val BREAK_GREEN = Color(0xFF2E9E4F)

@Composable
private fun FocusSetup(state: FocusState, sessions: List<FocusSessionRecord>, actions: FocusActions, modifier: Modifier) {
    val text = rememberFocusText()
    val settings = state.settings
    val plan = state.plan
    val now = currentMoment()
    fun change(settings: FocusSettings) = actions.setSettings(settings)
    // A preview stops when leaving the tab.
    DisposableEffect(Unit) { onDispose { actions.stopPreview() } }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section {
            OutlinedTextField(
                value = state.goal,
                onValueChange = actions::setGoal,
                placeholder = { Text(stringResource(R.string.focus_goal_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            RowDivider()
            MenuRow(stringResource(R.string.length), settings.totalMinutes, LENGTHS, { text.duration(it * 60) }) {
                change(settings.copy(totalMinutes = it))
            }
        }

        val end = now.adding(plan.totalSeconds / 60).time
        Section(footer = stringResource(R.string.focus_plan_summary, plan.blockCount, text.duration(plan.focusSeconds), end.toString())) {
            SwitchRow(stringResource(R.string.breaks_pomodoro), settings.usesBreaks) { change(settings.copy(usesBreaks = it)) }
            if (settings.usesBreaks) {
                RowDivider()
                StepperRow(
                    stringResource(R.string.focus_block), settings.focusMinutes, FocusSettings.FOCUS_RANGE,
                    valueText = text.duration(settings.focusMinutes * 60), step = 5,
                ) { change(settings.copy(focusMinutes = it)) }
                RowDivider()
                StepperRow(
                    stringResource(R.string.short_break), settings.shortBreakMinutes, FocusSettings.SHORT_BREAK_RANGE,
                    valueText = text.duration(settings.shortBreakMinutes * 60),
                ) { change(settings.copy(shortBreakMinutes = it)) }
                RowDivider()
                StepperRow(
                    stringResource(R.string.long_break), settings.longBreakMinutes, FocusSettings.LONG_BREAK_RANGE,
                    valueText = text.duration(settings.longBreakMinutes * 60), step = 5,
                ) { change(settings.copy(longBreakMinutes = it)) }
                RowDivider()
                StepperRow(
                    stringResource(R.string.long_break_after, settings.blocksBeforeLongBreak), settings.blocksBeforeLongBreak, 2..8,
                ) { change(settings.copy(blocksBeforeLongBreak = it)) }
            }
        }

        SoundSection(state, actions)
        HistorySection(sessions, text)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SoundSection(state: FocusState, actions: FocusActions) {
    Section(footer = if (state.run == null) stringResource(R.string.sound_footer) else null) {
        MenuRow(stringResource(R.string.background_sound), state.sound, FocusSound.entries, { stringResource(it.title) }) {
            actions.setSound(it)
        }
        if (state.sound != FocusSound.NONE) {
            RowDivider()
            val volume = stringResource(R.string.volume)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_volume_down, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = state.volume,
                    onValueChange = actions::setVolume,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = volume },
                )
                AppIcon(R.drawable.ic_volume_up, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.run == null) {
                RowDivider()
                ActionRow(
                    if (state.previewing) R.drawable.ic_stop else R.drawable.ic_play,
                    stringResource(if (state.previewing) R.string.stop_preview else R.string.preview_sound),
                ) { actions.togglePreview() }
            }
        }
    }
}

/** Today's focus time and the latest sessions. */
@Composable
private fun HistorySection(sessions: List<FocusSessionRecord>, text: FocusText) {
    val nowSource = LocalNowSource.current
    val zone = nowSource.clock.zone
    val today = nowSource.today()
    val names = rememberNames()
    val todaySeconds = sessions.filter { LocalDate.from(it.startedAt, zone) == today }.sumOf { it.focusedSeconds }
    val recent = sessions.take(5)
    if (todaySeconds == 0 && recent.isEmpty()) return
    Section(title = stringResource(R.string.history)) {
        ValueRow(stringResource(R.string.focused_today), text.duration(todaySeconds))
        for (session in recent) {
            RowDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(session.goal.ifEmpty { stringResource(R.string.tab_focus) }, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${names.mediumDate(LocalDate.from(session.startedAt, zone))} ${TimeOfDay.from(session.startedAt, zone)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text.duration(session.focusedSeconds), style = MaterialTheme.typography.bodyLarge)
                AppIcon(
                    if (session.finished) R.drawable.ic_check_circle else R.drawable.ic_stop_circle,
                    stringResource(if (session.finished) R.string.finished else R.string.stopped),
                    Modifier.size(20.dp),
                    tint = if (session.finished) BREAK_GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The current time, every second (fixed in screenshots). */
@Composable
private fun currentInstant(): Instant {
    val source = LocalNowSource.current
    var now by remember(source) { mutableStateOf(source.clock.instant()) }
    if (source.ticks) {
        LaunchedEffect(source) {
            while (true) {
                delay(1000 - source.clock.millis() % 1000 + 5)
                now = source.clock.instant()
            }
        }
    }
    return now
}

/** The session in progress: the phase, a ring with the time left, and the controls. */
@Composable
private fun RunningFocus(run: FocusRun, state: FocusState, actions: FocusActions, modifier: Modifier) {
    val text = rememberFocusText()
    val now = currentInstant()
    val status = run.status(now)
    var confirmingStop by remember { mutableStateOf(false) }
    val accent = if (status.phase.isBreak) BREAK_GREEN else MaterialTheme.colorScheme.primary
    // A switch on screen: its notification and the next alarm, even if the alarm comes late.
    LaunchedEffect(status.phaseIndex, status.isFinished) { actions.refresh() }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Section {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text.phaseTitle(run, status.phase),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                val progress = (status.phaseElapsed / maxOf(status.phase.seconds, 1)).toFloat().coerceIn(0f, 1f)
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                        val width = 14.dp.toPx()
                        drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width))
                        drawArc(accent, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, style = Stroke(width, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(FocusText.clock(status.phaseRemaining), fontSize = 52.sp, fontWeight = FontWeight.SemiBold)
                        if (status.isPaused) {
                            Text(stringResource(R.string.paused), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (run.goal.isNotEmpty()) Text(run.goal, style = MaterialTheme.typography.titleMedium)
                val focused = stringResource(R.string.focused_amount, text.duration(status.focused.toInt()))
                val end = run.endDate(now)?.let { stringResource(R.string.ends_at, TimeOfDay.from(it, LocalNowSource.current.clock.zone).toString()) }
                Text(
                    listOfNotNull(focused, end).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (status.phase.isBreak && !status.isPaused) {
                    OutlinedButton(onClick = actions::skipBreak) { Text(stringResource(R.string.skip_break)) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = actions::togglePause, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                AppIcon(if (run.isPaused) R.drawable.ic_play else R.drawable.ic_pause, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (run.isPaused) R.string.resume else R.string.pause))
            }
            OutlinedButton(
                onClick = { confirmingStop = true },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                AppIcon(R.drawable.ic_stop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.stop))
            }
        }

        SoundSection(state, actions)
        Spacer(Modifier.height(8.dp))
    }

    if (confirmingStop) {
        ConfirmDialog(
            title = stringResource(R.string.stop_focus_question),
            text = stringResource(R.string.stop_focus_message),
            confirmLabel = stringResource(R.string.stop_session),
            onConfirm = actions::stop,
            onDismiss = { confirmingStop = false },
        )
    }
}
