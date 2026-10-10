package io.github.hilbert0619.coursetable.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.hilbert0619.coursekit.ClassSession
import io.github.hilbert0619.coursekit.DaySchedule
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursetable.MainActivity
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.StudyHourApp
import io.github.hilbert0619.coursetable.ui.Names
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette
import io.github.hilbert0619.coursetable.ui.theme.DarkColors
import io.github.hilbert0619.coursetable.ui.theme.LightColors
import io.github.hilbert0619.coursetable.ui.timeRange

/** The home-screen widget: the class in progress or the next one; wider, the rest of today's classes. */
class ScheduleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as StudyHourApp).repository
        val initial = repository.currentTimetable()
        ScheduleWidgetUpdates.scheduleNext(context, initial)
        provideContent {
            val timetable by repository.timetable.collectAsState(initial)
            GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                ScheduleWidgetContent(timetable, Moment.from(java.time.Instant.now()))
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}

@Composable
private fun ScheduleWidgetContent(timetable: Timetable, now: Moment) {
    val context = LocalContext.current
    val names = Names(context.resources)
    val day = timetable.daySchedule(now.day)
    val nowAndNext = timetable.currentAndNext(now)
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground).padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (LocalSize.current.width >= ScheduleWidget.MEDIUM.width) {
            MediumSchedule(now, day, nowAndNext.current, nowAndNext.next, names)
        } else {
            SmallSchedule(now, day, nowAndNext.current, nowAndNext.next, names)
        }
    }
}

private fun timeLabel(session: ClassSession, now: Moment, names: Names): String =
    if (session.day == now.day) session.timeRange else "${names.short(session.course.weekday)} ${session.timeRange}"

/** Small: the class in progress, or else the next one. */
@Composable
private fun SmallSchedule(now: Moment, day: DaySchedule, current: ClassSession?, next: ClassSession?, names: Names) {
    val context = LocalContext.current
    val session = current ?: next
    Column(GlanceModifier.fillMaxSize()) {
        if (session != null) {
            Text(
                context.getString(if (current != null) R.string.now else R.string.next),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorProvider(CoursePalette.color(session.course.colorIndex))),
            )
            Text(
                session.course.name,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                maxLines = 3,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(timeLabel(session, now, names), style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface))
            if (session.course.room.isNotEmpty()) {
                Text(session.course.room, style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
            }
        } else {
            Text(names.status(day.semesterStatus), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurfaceVariant))
            Spacer(GlanceModifier.defaultWeight())
            Text(context.getString(R.string.no_upcoming_classes), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
        }
    }
}

/** Medium: today's classes still to come; tomorrow onwards if today is done. */
@Composable
private fun MediumSchedule(now: Moment, day: DaySchedule, current: ClassSession?, next: ClassSession?, names: Names) {
    val context = LocalContext.current
    val remaining = day.remaining(now.time)
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.tab_today),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(names.status(day.semesterStatus), style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (remaining.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                context.getString(if (day.sessions.isEmpty()) R.string.no_classes_today else R.string.no_more_classes_today),
                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
            )
            if (next != null) {
                Text(
                    "${context.getString(R.string.next)}: ${next.course.name} · ${names.short(next.course.weekday)} ${next.timeRange}",
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            remaining.take(MAX_ROWS).forEach { session -> SessionRow(session, isCurrent = session.id == current?.id) }
            if (remaining.size > MAX_ROWS) {
                Text(
                    context.getString(R.string.more_classes, remaining.size - MAX_ROWS),
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: ClassSession, isCurrent: Boolean) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(4.dp).height(16.dp).cornerRadius(2.dp).background(ColorProvider(CoursePalette.color(session.course.colorIndex)))) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(session.start.toString(), style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
        Spacer(GlanceModifier.width(6.dp))
        Text(
            session.course.name,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = GlanceTheme.colors.onSurface,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(session.course.room, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
    }
}

private const val MAX_ROWS = 4
