@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.ClassSession
import io.github.hilbert0619.coursekit.CourseBlockStyle
import io.github.hilbert0619.coursekit.DayAgenda
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.ResolvedTodo
import io.github.hilbert0619.coursekit.SessionStatus
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Todo
import io.github.hilbert0619.coursekit.TodoTiming
import io.github.hilbert0619.coursekit.agenda
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.focus.FocusText
import io.github.hilbert0619.coursetable.ui.ai.AssistantButton
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.Names
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.GlassButton
import io.github.hilbert0619.coursetable.ui.components.GlassIconButton
import io.github.hilbert0619.coursetable.ui.components.GlassScope
import io.github.hilbert0619.coursetable.ui.components.LocalGlass
import io.github.hilbert0619.coursetable.ui.components.SwipeDeleteBackground
import io.github.hilbert0619.coursetable.ui.components.glassCard
import io.github.hilbert0619.coursetable.ui.components.rowBackground
import io.github.hilbert0619.coursetable.ui.components.screenBackground
import io.github.hilbert0619.coursetable.ui.components.topBarFrosting
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.currentMoment
import io.github.hilbert0619.coursetable.ui.details
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette
import io.github.hilbert0619.coursetable.ui.timeRange
import kotlinx.coroutines.launch

/** What the to-do editor is showing. */
sealed interface TodoEditorTarget {
    data class New(val day: LocalDate) : TodoEditorTarget
    data class Edit(val todo: Todo) : TodoEditorTarget
}

/** One day's classes and to-dos, today by default; the arrows switch days. */
@Composable
fun TodayScreen(timetable: Timetable, todos: List<Todo>, actions: AppActions, modifier: Modifier = Modifier) {
    val now = currentMoment()
    // The day shown, as days since 1970; `null` follows the clock.
    var pickedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    val day = pickedDay?.let { LocalDate(daysSinceEpoch = it) } ?: now.day
    var editing by remember { mutableStateOf<TodoEditorTarget?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = screenBackground(),
        topBar = {
            TopAppBar(
                modifier = Modifier.topBarFrosting(),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = screenBackground(), scrolledContainerColor = screenBackground()),
                title = { Text(stringResource(R.string.tab_today)) },
                actions = {
                    AssistantButton()
                    IconButton(onClick = { editing = TodoEditorTarget.New(day) }) {
                        AppIcon(R.drawable.ic_add, stringResource(R.string.add_todo))
                    }
                },
            )
        },
    ) { padding ->
        GlassScope {
            TodayContent(
                timetable = timetable, todos = todos, now = now, day = day,
                pickDay = { picked -> pickedDay = if (picked == now.day) null else picked.daysSinceEpoch },
                edit = { editing = it },
                actions = actions,
                modifier = Modifier.padding(padding),
            )
        }
    }

    editing?.let { target ->
        TodoEditor(timetable, target, actions, onDismiss = { editing = null })
    }
}

@Composable
private fun TodayContent(
    timetable: Timetable,
    todos: List<Todo>,
    now: Moment,
    day: LocalDate,
    pickDay: (LocalDate) -> Unit,
    edit: (TodoEditorTarget) -> Unit,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val names = rememberNames()
    val isToday = day == now.day
    val agenda = timetable.agenda(day, todos)
    val nowAndNext = timetable.currentAndNext(now)

    fun status(session: ClassSession): SessionStatus = when {
        isToday -> session.status(now)
        day < now.day -> SessionStatus.FINISHED
        else -> SessionStatus.UPCOMING
    }

    LazyColumn(modifier.fillMaxSize()) {
        item("header") {
            DayHeader(day, isToday, names.status(timetable.semester.status(day)), names, onPick = pickDay, today = now.day)
        }

        if (isToday && timetable.courses.isNotEmpty()) {
            item("nowNext") {
                NowNextCard(now, nowAndNext.current, nowAndNext.next, openTodos = todos.count { it.day == now.day && !it.isDone })
            }
        }

        if (agenda.anytime.isNotEmpty()) {
            item("anytime") {
                Section(title = stringResource(R.string.any_time)) {
                    agenda.anytime.forEachIndexed { index, todo ->
                        if (index > 0) RowDivider()
                        TodoRow(todo, timetable, names, actions, edit)
                    }
                }
            }
        }

        if (agenda.items.isEmpty()) {
            if (agenda.anytime.isEmpty()) {
                item("empty") {
                    Section {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AppIcon(R.drawable.ic_empty_day, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(if (isToday) R.string.no_classes_today else R.string.no_classes_on_this_day),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            GlassButton(onClick = { edit(TodoEditorTarget.New(day)) }) { Text(stringResource(R.string.add_todo)) }
                        }
                    }
                }
            }
        } else {
            item("schedule") {
                Section(title = stringResource(R.string.schedule)) {
                    agenda.items.forEachIndexed { index, item ->
                        if (index > 0) RowDivider()
                        when (item) {
                            is DayAgenda.Item.Session -> {
                                SessionRow(
                                    item.session, status(item.session), names,
                                    isNext = isToday && item.session.id == nowAndNext.next?.id && nowAndNext.current == null,
                                )
                                item.after.forEach { todo -> TodoRow(todo, timetable, names, actions, edit, isUnderItsClass = true) }
                            }
                            is DayAgenda.Item.TodoItem -> TodoRow(item.todo, timetable, names, actions, edit)
                        }
                    }
                }
            }
        }
    }
}

/** The date, the term week, and the buttons to move between days. */
@Composable
private fun DayHeader(day: LocalDate, isToday: Boolean, status: String, names: Names, onPick: (LocalDate) -> Unit, today: LocalDate) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (LocalGlass.current != null) 8.dp else 0.dp),
    ) {
        GlassIconButton(onClick = { onPick(day.adding(-1)) }) { AppIcon(R.drawable.ic_chevron_left, stringResource(R.string.previous_day)) }
        Column(Modifier.weight(1f)) {
            Text(names.fullDate(day), style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isToday) {
            if (LocalGlass.current != null) {
                GlassButton(onClick = { onPick(today) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(stringResource(R.string.tab_today)) }
            } else {
                TextButton(onClick = { onPick(today) }) { Text(stringResource(R.string.tab_today)) }
            }
        }
        GlassIconButton(onClick = { onPick(day.adding(1)) }) { AppIcon(R.drawable.ic_chevron_right, stringResource(R.string.next_day)) }
    }
}

/**
 * Today's first card: the class in progress (time left, progress) or the next one (when, where), tinted in that
 * course's soft colour, and the to-dos still open today (iOS `NowNextCard`).
 */
@Composable
private fun NowNextCard(now: Moment, current: ClassSession?, next: ClassSession?, openTodos: Int) {
    val session = current ?: next
    val tint = session?.let { CoursePalette.block(it.course.colorIndex, CourseBlockStyle.SOFT) }
    val ink = tint?.text ?: MaterialTheme.colorScheme.onSurface
    // Glass tinted in the course's soft colour on the wallpaper, the soft colour itself on the paper.
    Surface(
        color = Color.Transparent,
        contentColor = ink,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).glassCard(tint?.fill)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (session != null) {
                val color = CoursePalette.color(session.course.colorIndex)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text(nowNextHeadline(session, current, now).uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(session.course.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    (listOf(session.timeRange) + listOf(session.details).filter { it.isNotEmpty() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(0.85f),
                )
                if (current != null) {
                    val length = maxOf(session.end.minutes - session.start.minutes, 1)
                    val progress = ((now.time.minutes - session.start.minutes).toFloat() / length).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        color = color,
                        trackColor = ink.copy(alpha = 0.15f),
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(R.drawable.ic_moon, null, Modifier.size(22.dp), tint = ink)
                    Text(stringResource(R.string.no_more_classes_today), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (openTodos > 0) {
                Row(
                    Modifier.padding(top = 2.dp).alpha(0.85f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppIcon(R.drawable.ic_checklist, null, Modifier.size(16.dp), tint = ink)
                    Text(pluralStringResource(R.plurals.todos_left_today, openTodos, openTodos), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** "Now · 25 min left", "Next · in 1 hr, 5 min", "Next · Tomorrow", "Next · Monday". */
@Composable
private fun nowNextHeadline(session: ClassSession, current: ClassSession?, now: Moment): String {
    val text = FocusText(LocalContext.current.resources)
    val names = rememberNames()
    return when {
        session == current -> stringResource(R.string.now_time_left, text.duration(maxOf(session.end.minutes - now.time.minutes, 0) * 60))
        session.day == now.day -> stringResource(R.string.next_in, text.duration(maxOf(session.start.minutes - now.time.minutes, 0) * 60))
        session.day == now.day.adding(1) -> stringResource(R.string.next_tomorrow)
        else -> stringResource(R.string.next_on_day, names.full(session.course.weekday))
    }
}

/** A class: its colour, name, time, periods, room and teacher; "Now" or "Next" on the right. */
@Composable
fun SessionRow(session: ClassSession, status: SessionStatus, names: Names, isNext: Boolean, showsDay: Boolean = false) {
    val color = CoursePalette.color(session.course.colorIndex)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (status == SessionStatus.FINISHED) 0.45f else 1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.course.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (showsDay) "${names.full(session.course.weekday)} ${session.timeRange}" else session.timeRange,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                names.periods(session.course.startPeriod, session.course.endPeriod),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.details.isNotEmpty()) {
                Text(session.details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (status == SessionStatus.IN_PROGRESS) {
            Badge(stringResource(R.string.now), color)
        } else if (isNext) {
            Badge(stringResource(R.string.next), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.15f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** A to-do: tick it off with the circle, tap to edit, swipe left to delete. */
@Composable
private fun TodoRow(
    todo: ResolvedTodo,
    timetable: Timetable,
    names: Names,
    actions: AppActions,
    edit: (TodoEditorTarget) -> Unit,
    isUnderItsClass: Boolean = false,
) = key(todo.id) {
    // Keyed by the to-do, so a swiped-away row's state never carries over to the row that takes its place.
    val dismiss = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) actions.deleteTodo(todo.id)
    }
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = { SwipeDeleteBackground(dismiss) },
    ) {
        val done = todo.todo.isDone
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val bounce = remember { Animatable(1f) }
        Row(
            Modifier.fillMaxWidth().background(rowBackground())
                .clickable { edit(TodoEditorTarget.Edit(todo.todo)) }
                .padding(start = if (isUnderItsClass) 22.dp else 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                actions.setTodoDone(todo.id, !done)
                if (!done) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch {
                        bounce.animateTo(1.3f, tween(110))
                        bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                    }
                }
            }) {
                AppIcon(
                    if (done) R.drawable.ic_todo_done else R.drawable.ic_todo_open,
                    stringResource(if (done) R.string.mark_not_done else R.string.mark_done),
                    Modifier.scale(bounce.value),
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    todo.todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                todoDetail(todo, timetable, isUnderItsClass)?.let { detail ->
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (todo.isMissingItsClass) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "12:00", "After class · 09:40", "After Calculus · 09:40", or a note that its class isn't on that day. */
@Composable
private fun todoDetail(todo: ResolvedTodo, timetable: Timetable, isUnderItsClass: Boolean): String? {
    if (todo.isMissingItsClass) {
        val courseId = (todo.todo.timing as? TodoTiming.AfterClass)?.courseId
        val course = timetable.courses.firstOrNull { it.id == courseId }
        return if (course != null) stringResource(R.string.no_class_on_day, course.name) else stringResource(R.string.its_class_removed)
    }
    val due = todo.due ?: return null
    if (isUnderItsClass) return "${stringResource(R.string.after_class)} · ${due.time}"
    val session = todo.session
    return if (session != null) "${stringResource(R.string.after_course, session.course.name)} · ${due.time}" else due.time.toString()
}
