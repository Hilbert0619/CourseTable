@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.ClassSession
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
import io.github.hilbert0619.coursetable.ui.ai.AssistantButton
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.Names
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.currentMoment
import io.github.hilbert0619.coursetable.ui.details
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette
import io.github.hilbert0619.coursetable.ui.timeRange

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
        topBar = {
            TopAppBar(
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
        TodayContent(
            timetable = timetable, todos = todos, now = now, day = day,
            pickDay = { picked -> pickedDay = if (picked == now.day) null else picked.daysSinceEpoch },
            edit = { editing = it },
            actions = actions,
            modifier = Modifier.padding(padding),
        )
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
            Section {
                DayHeader(day, isToday, names.status(timetable.semester.status(day)), names, onPick = pickDay, today = now.day)
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
                            FilledTonalButton(onClick = { edit(TodoEditorTarget.New(day)) }) { Text(stringResource(R.string.add_todo)) }
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

        val next = nowAndNext.next
        if (isToday && next != null && next.day != now.day) {
            item("next") {
                Section(title = stringResource(R.string.next_class)) {
                    SessionRow(next, SessionStatus.UPCOMING, names, isNext = false, showsDay = true)
                }
            }
        }
    }
}

/** The date, the term week, and the buttons to move between days. */
@Composable
private fun DayHeader(day: LocalDate, isToday: Boolean, status: String, names: Names, onPick: (LocalDate) -> Unit, today: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPick(day.adding(-1)) }) { AppIcon(R.drawable.ic_chevron_left, stringResource(R.string.previous_day)) }
        Column(Modifier.weight(1f)) {
            Text(names.fullDate(day), style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isToday) {
            TextButton(onClick = { onPick(today) }) { Text(stringResource(R.string.tab_today)) }
        }
        IconButton(onClick = { onPick(day.adding(1)) }) { AppIcon(R.drawable.ic_chevron_right, stringResource(R.string.next_day)) }
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
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                AppIcon(R.drawable.ic_delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        val done = todo.todo.isDone
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable { edit(TodoEditorTarget.Edit(todo.todo)) }
                .padding(start = if (isUnderItsClass) 22.dp else 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { actions.setTodoDone(todo.id, !done) }) {
                AppIcon(
                    if (done) R.drawable.ic_todo_done else R.drawable.ic_todo_open,
                    stringResource(if (done) R.string.mark_not_done else R.string.mark_done),
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
