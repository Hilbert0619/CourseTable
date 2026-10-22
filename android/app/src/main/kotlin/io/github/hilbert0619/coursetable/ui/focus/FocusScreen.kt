@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.hilbert0619.coursekit.FocusPlan
import io.github.hilbert0619.coursekit.FocusRun
import io.github.hilbert0619.coursekit.FocusSettings
import io.github.hilbert0619.coursekit.FocusStatus
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
import io.github.hilbert0619.coursetable.ui.components.CardShape
import io.github.hilbert0619.coursetable.ui.components.GlassButton
import io.github.hilbert0619.coursetable.ui.components.GlassScope
import io.github.hilbert0619.coursetable.ui.components.LocalGlass
import io.github.hilbert0619.coursetable.ui.components.SwipeDeleteBackground
import io.github.hilbert0619.coursetable.ui.components.glass
import io.github.hilbert0619.coursetable.ui.components.glassCard
import io.github.hilbert0619.coursetable.ui.components.rowBackground
import io.github.hilbert0619.coursetable.ui.components.screenBackground
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
import java.util.UUID
import kotlinx.coroutines.delay

/**
 * The Focus tab (iOS `FocusView`): set up a session (a plan card with quick lengths, goal, Pomodoro rhythm, sound,
 * wallpaper), run it (on a photo, in full screen if you like), and see and tidy the history.
 */
@Composable
fun FocusScreen(
    state: FocusState,
    sessions: List<FocusSessionRecord>,
    actions: FocusActions,
    modifier: Modifier = Modifier,
    onDeleteSessions: (Set<UUID>) -> Unit = {},
    onClearHistory: () -> Unit = {},
    initiallyShowingHistory: Boolean = false,
) {
    var showingHistory by rememberSaveable { mutableStateOf(initiallyShowingHistory) }
    if (showingHistory) {
        BackHandler { showingHistory = false }
        FocusHistoryScreen(sessions, onDeleteSessions, onClearHistory, onBack = { showingHistory = false }, modifier = modifier)
        return
    }
    val run = state.run
    val askForNotifications = rememberNotificationRequest()
    Scaffold(
        modifier = modifier,
        containerColor = screenBackground(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = screenBackground(), scrolledContainerColor = screenBackground()),
                title = { Text(stringResource(R.string.tab_focus)) },
            )
        },
        bottomBar = {
            // Always in reach, above the tab bar; on the paper the list fades out under it, on glass it floats.
            if (run == null) {
                GlassScope {
                    StartBar(onStart = {
                        actions.start()
                        askForNotifications()
                    })
                }
            }
        },
    ) { padding ->
        GlassScope {
            if (run != null) {
                RunningFocus(run, state, actions, Modifier.padding(padding))
            } else {
                FocusSetup(state, sessions, actions, onDeleteSessions, onShowHistory = { showingHistory = true }, padding)
            }
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

/** The quick lengths on the plan card, in minutes. */
private val PRESETS = listOf(25, 50, 90, 120)

/** Green for breaks, as on iOS; a light green on a photo. */
private val BREAK_GREEN = Color(0xFF2E9E4F)
private val BREAK_GREEN_ON_PHOTO = Color(0.62f, 0.95f, 0.72f)

@Composable
private fun StartBar(onStart: () -> Unit) {
    val paper = MaterialTheme.colorScheme.background
    val fade = if (LocalGlass.current == null) Modifier.background(Brush.verticalGradient(0f to paper.copy(alpha = 0f), 0.4f to paper)) else Modifier
    Box(Modifier.fillMaxWidth().then(fade).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)) {
        GlassButton(onClick = onStart, prominent = true, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            AppIcon(R.drawable.ic_play, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.start_focusing), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FocusSetup(
    state: FocusState,
    sessions: List<FocusSessionRecord>,
    actions: FocusActions,
    onDeleteSessions: (Set<UUID>) -> Unit,
    onShowHistory: () -> Unit,
    padding: PaddingValues,
) {
    val text = rememberFocusText()
    val settings = state.settings
    var showingRhythm by rememberSaveable { mutableStateOf(false) }
    fun change(settings: FocusSettings) = actions.setSettings(settings)
    val wallpapers = LocalWallpaperActions.current
    // A preview stops when leaving the tab.
    DisposableEffect(Unit) { onDispose { actions.stopPreview() } }
    // Downloads the next session's wallpaper while you set it up, so it can show at once.
    LaunchedEffect(Unit) { wallpapers.prefetch() }

    // The list scrolls on under the Start button.
    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).verticalScroll(rememberScrollState())) {
        Section(card = true) {
            FocusPlanCard(state.plan, settings.totalMinutes, text) { change(settings.copy(totalMinutes = it)) }
        }

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

        Section(footer = if (settings.usesBreaks) stringResource(R.string.rhythm_footer) else null) {
            SwitchRow(stringResource(R.string.breaks_pomodoro), settings.usesBreaks) { change(settings.copy(usesBreaks = it)) }
            if (settings.usesBreaks) {
                RowDivider()
                // The rhythm rarely changes: folded away, its values in the row.
                RhythmRow(settings, expanded = showingRhythm, onToggle = { showingRhythm = !showingRhythm })
                if (showingRhythm) {
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
        }

        SoundSection(state, actions)
        WallpaperSection()
        HistorySection(sessions, text, onDeleteSessions, onShowHistory)
        Spacer(Modifier.height(padding.calculateBottomPadding() + 8.dp))
    }
}

/** "Rhythm  25 · 5 · 15", opening the steppers below it. */
@Composable
private fun RhythmRow(settings: FocusSettings, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.rhythm), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            "${settings.focusMinutes} · ${settings.shortBreakMinutes} · ${settings.longBreakMinutes}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        AppIcon(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The session at a glance (iOS `FocusPlanCard`): a ring with each focus block and break to scale, the length, when it
 * ends, and quick lengths to pick.
 */
@Composable
private fun FocusPlanCard(plan: FocusPlan, totalMinutes: Int, text: FocusText, onPick: (Int) -> Unit) {
    val now = currentMoment()
    val haptics = LocalHapticFeedback.current
    val end = now.adding(plan.totalSeconds / 60).time
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            PlanRing(plan, Modifier.size(84.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text.duration(plan.totalSeconds), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    pluralStringResource(R.plurals.focus_plan_summary, plan.blockCount, plan.blockCount, text.duration(plan.focusSeconds), end.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { minutes ->
                val selected = totalMinutes == minutes
                // A capsule, the chosen one in the accent colour; of glass on the wallpaper.
                val glass = LocalGlass.current != null
                Surface(
                    onClick = {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onPick(minutes)
                    },
                    shape = CircleShape,
                    color = when {
                        glass -> Color.Transparent
                        selected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    },
                    contentColor = when {
                        selected && glass -> Color.White
                        selected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f).glass(CircleShape, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else null)
                        .semantics { this.selected = selected },
                ) {
                    Text(
                        text.presetLength(minutes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** The plan as a ring: focus blocks in the accent colour, breaks in green, each to scale. */
@Composable
private fun PlanRing(plan: FocusPlan, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val stroke = 10.dp.toPx()
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(stroke))
            val total = maxOf(plan.totalSeconds, 1).toFloat()
            val gap = if (plan.phases.size > 1) 0.012f else 0f
            plan.phases.zip(plan.offsets).forEach { (phase, offset) ->
                val start = offset / total
                val end = maxOf((offset + phase.seconds) / total - gap, start + 0.002f)
                drawArc(
                    if (phase.isBreak) BREAK_GREEN else accent, startAngle = -90f + start * 360f, sweepAngle = (end - start) * 360f,
                    useCenter = false, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
            }
        }
        AppIcon(R.drawable.ic_timer, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Focus setup → Wallpaper. */
@Composable
private fun WallpaperSection() {
    val wallpapers = LocalWallpapers.current
    val actions = LocalWallpaperActions.current
    Section(footer = stringResource(R.string.wallpaper_footer)) {
        SwitchRow(stringResource(R.string.wallpaper), wallpapers.enabled) { actions.setEnabled(it) }
    }
}

/** Today's focus time and the latest sessions; swipe one to delete it, or see them all. */
@Composable
private fun HistorySection(sessions: List<FocusSessionRecord>, text: FocusText, onDelete: (Set<UUID>) -> Unit, onShowAll: () -> Unit) {
    val nowSource = LocalNowSource.current
    val zone = nowSource.clock.zone
    val today = nowSource.today()
    val todaySeconds = sessions.filter { LocalDate.from(it.startedAt, zone) == today }.sumOf { it.focusedSeconds }
    val recent = sessions.take(5)
    if (todaySeconds == 0 && recent.isEmpty()) return
    Section(title = stringResource(R.string.history), footer = stringResource(R.string.swipe_session_to_delete)) {
        ValueRow(stringResource(R.string.focused_today), text.duration(todaySeconds))
        for (session in recent) {
            RowDivider()
            SessionRow(session, text, showsDay = true, onDelete = { onDelete(setOf(session.id)) })
        }
        RowDivider()
        ValueRow(stringResource(R.string.all_sessions), "${sessions.size}", onClick = onShowAll)
    }
}

/** A focus session: its goal, when, how long, finished or stopped; swipe left to delete it. */
@Composable
private fun SessionRow(session: FocusSessionRecord, text: FocusText, showsDay: Boolean, onDelete: () -> Unit) = key(session.id) {
    val zone = LocalNowSource.current.clock.zone
    val names = rememberNames()
    // Keyed by the session, so a swiped-away row's state never carries over to the row that takes its place.
    val dismiss = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = { SwipeDeleteBackground(dismiss) },
    ) {
        Row(
            Modifier.fillMaxWidth().background(rowBackground()).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(session.goal.ifEmpty { stringResource(R.string.tab_focus) }, style = MaterialTheme.typography.bodyLarge)
                val time = TimeOfDay.from(session.startedAt, zone).toString()
                Text(
                    if (showsDay) "${names.mediumDate(LocalDate.from(session.startedAt, zone))} $time" else time,
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

/** Every focus session by day (iOS `FocusHistoryView`), to look back on or delete (one by one, or all). */
@Composable
fun FocusHistoryScreen(
    sessions: List<FocusSessionRecord>,
    onDelete: (Set<UUID>) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberFocusText()
    val names = rememberNames()
    val zone = LocalNowSource.current.clock.zone
    var confirmingClear by remember { mutableStateOf(false) }
    val days = sessions.groupBy { LocalDate.from(it.startedAt, zone) }.toSortedMap(compareByDescending { it })
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_history)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                AppIcon(R.drawable.ic_timer, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.no_focus_sessions), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.no_focus_sessions_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            days.forEach { (day, daySessions) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)) {
                        Text(names.mediumDate(day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text(text.duration(daySessions.sumOf { it.focusedSeconds }), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth()) {
                            daySessions.forEachIndexed { index, session ->
                                if (index > 0) RowDivider()
                                SessionRow(session, text, showsDay = false, onDelete = { onDelete(setOf(session.id)) })
                            }
                        }
                    }
                }
            }
            Section(footer = stringResource(R.string.swipe_session_to_delete)) {
                ActionRow(R.drawable.ic_delete, stringResource(R.string.clear_history), color = MaterialTheme.colorScheme.error) { confirmingClear = true }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmingClear) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.delete_all_focus_sessions, sessions.size, sessions.size),
            text = stringResource(R.string.clear_history_message),
            confirmLabel = stringResource(R.string.clear_history),
            onConfirm = onClear,
            onDismiss = { confirmingClear = false },
        )
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

/** "1 hr, 50 min focused · ends at 21:30" */
@Composable
private fun runSummary(run: FocusRun, status: FocusStatus, now: Instant, text: FocusText): String {
    val focused = stringResource(R.string.focused_amount, text.duration(status.focused.toInt()))
    val end = run.endDate(now)?.let { stringResource(R.string.ends_at, TimeOfDay.from(it, LocalNowSource.current.clock.zone).toString()) }
    return listOfNotNull(focused, end).joinToString(" · ")
}

/** The session in progress: the phase, a ring with the time left (on the session's photo), and the controls. */
@Composable
private fun RunningFocus(run: FocusRun, state: FocusState, actions: FocusActions, modifier: Modifier) {
    val text = rememberFocusText()
    val now = currentInstant()
    val status = run.status(now)
    val wallpapers = LocalWallpapers.current
    val wallpaperActions = LocalWallpaperActions.current
    val shown = wallpapers.shown
    val onPhoto = shown != null
    val haptics = LocalHapticFeedback.current
    var confirmingStop by remember { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var lastPhase by remember { mutableIntStateOf(status.phaseIndex) }
    // A switch on screen: its notification and the next alarm, even if the alarm comes late; a tap you can feel.
    LaunchedEffect(status.phaseIndex, status.isFinished) {
        actions.refresh()
        if (status.phaseIndex != lastPhase) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            lastPhase = status.phaseIndex
        }
    }
    // The session's wallpaper, also after a relaunch.
    LaunchedEffect(Unit) { wallpaperActions.restore() }

    // On a photo: white, and a light green for breaks; otherwise the app's colours.
    val tint = when {
        status.phase.isBreak -> if (onPhoto) BREAK_GREEN_ON_PHOTO else BREAK_GREEN
        else -> if (onPhoto) Color.White else MaterialTheme.colorScheme.primary
    }
    val secondary = if (onPhoto) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // The session's photo, else glass (the glass look) or the plain card colour.
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .then(if (shown == null) Modifier.glassCard() else Modifier.clip(CardShape)),
        ) {
            // The card draws its own background, so the photo shows as soon as it arrives.
            if (shown != null) WallpaperBackground(shown, Modifier.matchParentSize())
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val onCard = if (onPhoto) Color.White else MaterialTheme.colorScheme.onSurface
                val shadow = if (onPhoto) TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 10f)) else TextStyle()
                Text(text.phaseTitle(run, status.phase), style = MaterialTheme.typography.titleLarge.merge(shadow), fontWeight = FontWeight.SemiBold, color = tint)
                val track = if (onPhoto) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                val progress = (status.phaseElapsed / maxOf(status.phase.seconds, 1)).toFloat().coerceIn(0f, 1f)
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                        val width = 14.dp.toPx()
                        drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width))
                        drawArc(tint, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, style = Stroke(width, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(FocusText.clock(status.phaseRemaining), fontSize = 52.sp, fontWeight = FontWeight.SemiBold, color = onCard, style = shadow)
                        if (status.isPaused) {
                            Text(stringResource(R.string.paused), style = MaterialTheme.typography.titleSmall.merge(shadow), color = secondary)
                        }
                    }
                }
                if (run.goal.isNotEmpty()) Text(run.goal, style = MaterialTheme.typography.titleMedium.merge(shadow), color = onCard)
                Text(runSummary(run, status, now, text), style = MaterialTheme.typography.bodyMedium.merge(shadow), color = secondary, textAlign = TextAlign.Center)
                if (status.phase.isBreak && !status.isPaused) {
                    OutlinedButton(
                        onClick = actions::skipBreak,
                        colors = if (onPhoto) ButtonDefaults.outlinedButtonColors(contentColor = Color.White) else ButtonDefaults.outlinedButtonColors(),
                    ) { Text(stringResource(R.string.skip_break)) }
                }
                if (shown != null) WallpaperCredit(shown, wallpapers.isLoading, onAnother = wallpaperActions::showAnother)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(onClick = actions::togglePause, prominent = true, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                AppIcon(if (run.isPaused) R.drawable.ic_play else R.drawable.ic_pause, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (run.isPaused) R.string.resume else R.string.pause))
            }
            GlassButton(
                onClick = { confirmingStop = true },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                color = MaterialTheme.colorScheme.error,
            ) {
                AppIcon(R.drawable.ic_stop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.stop))
            }
            GlassButton(onClick = { fullScreen = true }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                AppIcon(R.drawable.ic_fullscreen, stringResource(R.string.full_screen), Modifier.size(20.dp))
            }
        }

        SoundSection(state, actions)
        Spacer(Modifier.height(8.dp))
    }

    if (fullScreen) FullScreenFocus(run, actions, onClose = { fullScreen = false })
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

/**
 * Full screen while focusing (iOS `FullScreenFocusView`): the photo edge to edge (or a deep ink gradient), the
 * countdown large, Pause and a way back. The screen stays on; it closes by itself when the session ends.
 */
@Composable
private fun FullScreenFocus(run: FocusRun, actions: FocusActions, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        FullScreenSystemBars()
        FullScreenFocusContent(run, actions, onClose)
    }
}

/** The full-screen window: the screen kept on, the status and navigation bars hidden (a swipe shows them). */
@Composable
private fun FullScreenSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { view.keepScreenOn = false }
    }
}

/** The full-screen countdown itself (also on its own in the screenshots). */
@Composable
fun FullScreenFocusContent(run: FocusRun, actions: FocusActions, onClose: () -> Unit) {
    val text = rememberFocusText()
    val now = currentInstant()
    val status = run.status(now)
    val wallpapers = LocalWallpapers.current
    val wallpaperActions = LocalWallpaperActions.current
    val shown = wallpapers.shown
    LaunchedEffect(status.isFinished) { if (status.isFinished) onClose() }
    val shadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 12f))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0.13f, 0.15f, 0.20f), Color(0.05f, 0.06f, 0.08f))))) {
        if (shown != null) WallpaperBackground(shown, Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    AppIcon(R.drawable.ic_fullscreen_exit, stringResource(R.string.exit_full_screen), tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(text.phaseTitle(run, status.phase), style = MaterialTheme.typography.titleLarge.merge(shadow), fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(FocusText.clock(status.phaseRemaining), fontSize = 92.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, style = shadow)
            if (status.isPaused) Text(stringResource(R.string.paused), style = MaterialTheme.typography.titleMedium.merge(shadow), color = Color.White.copy(alpha = 0.85f))
            if (run.goal.isNotEmpty()) Text(run.goal, style = MaterialTheme.typography.titleLarge.merge(shadow), color = Color.White)
            Text(runSummary(run, status, now, text), style = MaterialTheme.typography.bodyMedium.merge(shadow), color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = actions::togglePause,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 28.dp),
            ) {
                AppIcon(if (run.isPaused) R.drawable.ic_play else R.drawable.ic_pause, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (run.isPaused) R.string.resume else R.string.pause), style = MaterialTheme.typography.titleMedium)
            }
            if (shown != null) WallpaperCredit(shown, wallpapers.isLoading, onAnother = wallpaperActions::showAnother)
        }
    }
}
