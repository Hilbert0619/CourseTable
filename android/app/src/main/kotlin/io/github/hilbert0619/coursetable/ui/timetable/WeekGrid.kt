package io.github.hilbert0619.coursetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Weekday
import io.github.hilbert0619.coursekit.gridPlacements
import io.github.hilbert0619.coursetable.ui.Names
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette

/** Column and row geometry shared by the header and the grid. */
class GridLayout(width: Dp, val days: List<Weekday>) {
    val columnWidth: Dp = maxOf((width - TIME_COLUMN_WIDTH) / maxOf(days.size, 1), 24.dp)
    val totalWidth: Dp get() = TIME_COLUMN_WIDTH + columnWidth * days.size

    fun x(weekday: Weekday): Dp? = days.indexOf(weekday).takeIf { it >= 0 }?.let { TIME_COLUMN_WIDTH + columnWidth * it }
    fun y(period: Int): Dp = ROW_HEIGHT * (period - 1)

    companion object {
        val TIME_COLUMN_WIDTH = 40.dp
        val ROW_HEIGHT = 60.dp
    }
}

/** The month, then each day's name and date; today highlighted. */
@Composable
fun DayHeaderRow(layout: GridLayout, timetable: Timetable, week: Int, today: LocalDate, names: Names) {
    val semester = timetable.semester
    Row {
        Text(
            names.month(semester.date(Weekday.MONDAY, week)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(GridLayout.TIME_COLUMN_WIDTH).align(Alignment.CenterVertically),
        )
        layout.days.forEach { day ->
            val date = semester.date(day, week)
            val isToday = date == today
            val color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier.width(layout.columnWidth).height(40.dp)
                    .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(names.short(day), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
                Text("${date.month}/${date.day}", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

/**
 * The periods down the side, a cell for every day and period (tap an empty one to add a course there), and the
 * course blocks; clashing courses share a column side by side. With [showsAllWeeks], courses that don't meet in
 * [week] are shown too, dimmed (while placing courses).
 */
@Composable
fun WeekGridView(
    layout: GridLayout,
    timetable: Timetable,
    week: Int,
    today: LocalDate,
    showsAllWeeks: Boolean,
    onSelectCourse: (Course) -> Unit,
    onSelectSlot: (Weekday, Int) -> Unit,
) {
    val periods = timetable.periods.periods
    val rowHeight = GridLayout.ROW_HEIGHT
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val todayColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    Box(Modifier.width(layout.totalWidth).height(rowHeight * periods.size)) {
        periods.forEachIndexed { index, period ->
            Column(
                Modifier.offset(y = layout.y(index + 1)).size(GridLayout.TIME_COLUMN_WIDTH, rowHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp)
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
                Text(period.start.toString(), style = style, color = color)
                Text(period.end.toString(), style = style, color = color)
            }
        }

        layout.days.forEach { day ->
            val x = layout.x(day) ?: return@forEach
            val isToday = timetable.semester.date(day, week) == today
            periods.indices.forEach { index ->
                Box(
                    Modifier.offset(x = x, y = layout.y(index + 1)).size(layout.columnWidth, rowHeight)
                        .background(if (isToday) todayColor else Color.Transparent)
                        .clickable { onSelectSlot(day, index + 1) },
                ) {
                    Box(Modifier.fillMaxWidth().height(0.5.dp).align(Alignment.BottomCenter).background(lineColor))
                }
            }
        }

        timetable.gridPlacements(week, includingOtherWeeks = showsAllWeeks).forEach { placement ->
            val x = layout.x(placement.weekday) ?: return@forEach
            val laneWidth = layout.columnWidth / placement.laneCount
            CourseBlock(
                placement.course,
                Modifier.offset(x = x + laneWidth * placement.lane + 1.5.dp, y = layout.y(placement.periods.first) + 1.5.dp)
                    .size(laneWidth - 3.dp, rowHeight * placement.periods.count() - 3.dp)
                    .alpha(if (placement.isInWeek) 1f else 0.35f),
                onClick = { onSelectCourse(placement.course) },
            )
        }
    }
}

/** A course on the grid: its name and room in white on its colour. Small type: day columns are narrow. */
@Composable
fun CourseBlock(course: Course, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(6.dp)).background(CoursePalette.color(course.colorIndex))
            .clickable(onClick = onClick).padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            course.name,
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 5,
        )
        if (course.room.isNotEmpty()) {
            Text("@${course.room}", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2)
        }
    }
}
