@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.CourseTemplate
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.WeekParity
import io.github.hilbert0619.coursekit.Weekday
import io.github.hilbert0619.coursekit.placementTemplates
import io.github.hilbert0619.coursekit.placing
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.rememberNames
import java.util.UUID
import kotlin.math.abs

/** The weekly grid: switch weeks, tap a course to edit it or an empty slot to add one, or place courses from a list. */
@Composable
fun TimetableScreen(
    timetable: Timetable,
    savedTemplates: List<CourseTemplate>,
    actions: AppActions,
    modifier: Modifier = Modifier,
    startPlacing: Boolean = false,
) {
    val names = rememberNames()
    val today = LocalNowSource.current.today()
    val semester = timetable.semester
    val currentWeek = semester.displayWeek(today)
    // `null` follows the current week.
    var selectedWeek by rememberSaveable { mutableStateOf<Int?>(null) }
    val week = minOf(selectedWeek ?: currentWeek, semester.totalWeeks)
    var editor by remember { mutableStateOf<CourseEditorTarget?>(null) }
    var importing by remember { mutableStateOf(false) }
    var editingCourseList by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    // Placing mode: pick a course from the tray, then tap empty slots to add it there.
    var placing by rememberSaveable { mutableStateOf(startPlacing) }
    var placementId by remember { mutableStateOf<UUID?>(null) }
    var placementLength by rememberSaveable { mutableStateOf(2) }
    var placementParity by rememberSaveable { mutableStateOf(WeekParity.EVERY) }
    var placingOnWeekend by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val templates = timetable.placementTemplates(savedTemplates)
    val days = if (timetable.hasWeekendCourses || (placing && placingOnWeekend)) Weekday.entries else Weekday.entries.take(5)

    fun step(delta: Int) {
        selectedWeek = (week + delta).coerceIn(1, semester.totalWeeks)
    }

    fun beginPlacing() {
        placing = true
        if (templates.none { it.id == placementId }) placementId = templates.firstOrNull()?.id
        if (templates.isEmpty()) editingCourseList = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { WeekSwitcher(week, isThisWeek = week == semester.status(today).week, total = semester.totalWeeks, onPick = { selectedWeek = it }, onStep = ::step) },
                navigationIcon = {
                    if (week != currentWeek) TextButton(onClick = { selectedWeek = null }) { Text(stringResource(R.string.this_week_button)) }
                },
                actions = {
                    if (placing) {
                        TextButton(onClick = { placing = false }) { Text(stringResource(R.string.done), fontWeight = FontWeight.SemiBold) }
                    } else {
                        Box {
                            IconButton(onClick = { addMenu = true }) { AppIcon(R.drawable.ic_add, stringResource(R.string.add_course)) }
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.new_course)) },
                                    leadingIcon = { AppIcon(R.drawable.ic_new_course, null) },
                                    onClick = {
                                        addMenu = false
                                        editor = CourseEditorTarget.New(today.weekday, 1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.place_courses)) },
                                    leadingIcon = { AppIcon(R.drawable.ic_place, null) },
                                    onClick = {
                                        addMenu = false
                                        beginPlacing()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_courses_ellipsis)) },
                                    leadingIcon = { AppIcon(R.drawable.ic_import, null) },
                                    onClick = {
                                        addMenu = false
                                        importing = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (placing) {
                PlacementTray(
                    templates = templates,
                    savedIds = savedTemplates.map { it.id }.toSet(),
                    selection = placementId,
                    onSelect = { placementId = it },
                    length = placementLength,
                    onLength = { placementLength = it },
                    parity = placementParity,
                    onParity = { placementParity = it },
                    showsWeekend = if (timetable.hasWeekendCourses) null else placingOnWeekend,
                    onShowsWeekend = { placingOnWeekend = it },
                    periodsPerDay = timetable.periods.count,
                    onAddCourses = { editingCourseList = true },
                    onRemove = { id ->
                        actions.removeTemplates(setOf(id))
                        if (placementId == id) placementId = null
                    },
                )
            }
        },
    ) { padding ->
        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier.padding(padding).fillMaxSize()
                // Swiping sideways changes the week.
                .pointerInput(week) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (abs(total) > with(density) { 60.dp.toPx() }) step(if (total < 0) 1 else -1) },
                    ) { _, delta -> total += delta }
                },
        ) {
            val layout = GridLayout(maxWidth, days)
            Column(Modifier.fillMaxSize()) {
                DayHeaderRow(layout, timetable, week, today, names)
                HorizontalDivider()
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    WeekGridView(
                        layout = layout,
                        timetable = timetable,
                        week = week,
                        today = today,
                        showsAllWeeks = placing,
                        onSelectCourse = { editor = CourseEditorTarget.Edit(it) },
                        onSelectSlot = { day, period ->
                            val template = templates.firstOrNull { it.id == placementId }
                            if (placing && template != null) {
                                actions.saveCourse(timetable.placing(template, day, period, placementLength, placementParity))
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            } else {
                                editor = CourseEditorTarget.New(day, period)
                            }
                        },
                    )
                }
            }
            if (timetable.courses.isEmpty() && !placing) {
                EmptyHint(
                    onTypeList = ::beginPlacing,
                    onImport = { importing = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                )
            }
        }
    }

    editor?.let { target -> CourseEditor(timetable, target, actions, onDismiss = { editor = null }) }
    if (importing) ImportScreen(timetable, actions, onDismiss = { importing = false })
    if (editingCourseList) {
        CourseListEditor(
            timetable, savedTemplates, actions,
            onAdded = { added -> added.firstOrNull()?.let { placementId = it.id } },
            onDismiss = { editingCourseList = false },
        )
    }
}

/** ‹ Week 5 › with a menu of every week. */
@Composable
private fun WeekSwitcher(week: Int, isThisWeek: Boolean, total: Int, onPick: (Int) -> Unit, onStep: (Int) -> Unit) {
    val names = rememberNames()
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onStep(-1) }, enabled = week > 1) {
            AppIcon(R.drawable.ic_chevron_left, stringResource(R.string.previous_week))
        }
        Box {
            Column(
                Modifier.widthIn(min = 96.dp).clickable { menu = true }.padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(names.week(week), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (isThisWeek) {
                    Text(
                        stringResource(R.string.this_week),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                (1..total).forEach { number ->
                    DropdownMenuItem(
                        text = { Text(names.week(number)) },
                        trailingIcon = if (number == week) ({ AppIcon(R.drawable.ic_check, null) }) else null,
                        onClick = {
                            menu = false
                            onPick(number)
                        },
                    )
                }
            }
        }
        IconButton(onClick = { onStep(1) }, enabled = week < total) {
            AppIcon(R.drawable.ic_chevron_right, stringResource(R.string.next_week))
        }
    }
}

/** Shown on an empty timetable: how to add courses. */
@Composable
private fun EmptyHint(onTypeList: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.tap_empty_slot), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onTypeList) { Text(stringResource(R.string.type_course_list), fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onImport) { Text(stringResource(R.string.import_courses_ellipsis), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
