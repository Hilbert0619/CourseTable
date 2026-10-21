package io.github.hilbert0619.coursetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hilbert0619.coursekit.Course
import io.github.hilbert0619.coursekit.CourseBlockStyle
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursekit.Weekday
import io.github.hilbert0619.coursekit.gridPlacements
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.Names
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette
import kotlin.math.floor

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

/** The paper behind the days and period times when a photo is behind the timetable, so they stay readable. */
@Composable
private fun onPhotoBackground(isOnPhoto: Boolean): Color =
    if (isOnPhoto) MaterialTheme.colorScheme.background.copy(alpha = 0.82f) else Color.Transparent

/** The month, then each day's name and date; today highlighted. */
@Composable
fun DayHeaderRow(layout: GridLayout, timetable: Timetable, week: Int, today: LocalDate, names: Names, isOnPhoto: Boolean = false) {
    val semester = timetable.semester
    Row(Modifier.background(onPhotoBackground(isOnPhoto))) {
        Text(
            names.month(semester.date(Weekday.MONDAY, week)),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
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
 * The periods down the side, a cell for every day and period (tap an empty one to add a course there), the course
 * blocks (clashing courses share a column side by side; the class in progress outlined) and the current time in
 * today's column. With [showsAllWeeks], courses that don't meet in [week] are shown too, dimmed (while placing courses).
 */
@Composable
fun WeekGridView(
    layout: GridLayout,
    timetable: Timetable,
    week: Int,
    today: LocalDate,
    now: Moment,
    showsAllWeeks: Boolean,
    onSelectCourse: (Course) -> Unit,
    onSelectSlot: (Weekday, Int) -> Unit,
    blockStyle: CourseBlockStyle = CourseBlockStyle.SOFT,
    blockOpacity: Float = 1f,
    isOnPhoto: Boolean = false,
) {
    val periods = timetable.periods.periods
    val rowHeight = GridLayout.ROW_HEIGHT
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val todayColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    val current = timetable.currentAndNext(now).current
    Box(Modifier.width(layout.totalWidth).height(rowHeight * periods.size)) {
        Box(Modifier.width(GridLayout.TIME_COLUMN_WIDTH).fillMaxHeight().background(onPhotoBackground(isOnPhoto)))
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
            val isCurrent = placement.isInWeek && current != null && current.course.id == placement.course.id &&
                current.day == timetable.semester.date(placement.weekday, week)
            CourseBlock(
                placement.course,
                style = blockStyle,
                isCurrent = isCurrent,
                fillOpacity = blockOpacity,
                modifier = Modifier.offset(x = x + laneWidth * placement.lane + 1.5.dp, y = layout.y(placement.periods.first) + 1.5.dp)
                    .size(laneWidth - 3.dp, rowHeight * placement.periods.count() - 3.dp)
                    .alpha(if (placement.isInWeek) 1f else 0.35f),
                onClick = { onSelectCourse(placement.course) },
            )
        }

        // The current time in today's column, like Calendar's red line.
        val x = layout.x(today.weekday)
        val position = timetable.periods.gridPosition(now.time)
        if (now.day == today && x != null && timetable.semester.date(today.weekday, week) == today && position != null) {
            NowLine(layout.columnWidth, Modifier.offset(x = x - NOW_DOT / 2, y = rowHeight * position.toFloat() - NOW_DOT / 2))
        }
    }
}

private val NOW_DOT = 8.dp
private val NOW_RED = Color(0xFFFF3B30)

/** The current time across today's column: a dot at the left and a line. */
@Composable
private fun NowLine(width: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width + NOW_DOT / 2).height(NOW_DOT)) {
        Box(Modifier.size(NOW_DOT).background(NOW_RED, CircleShape))
        Box(Modifier.padding(start = NOW_DOT / 2).fillMaxWidth().height(2.dp).align(Alignment.CenterStart).background(NOW_RED))
    }
}

/**
 * A course on the grid: soft (a tint, same-hue text and a stripe in the course's colour) or solid (the colour with
 * white text), and outlined and lifted while the class is on. [fillOpacity] is below 1 over a photo (the text and
 * stripe stay solid). Small type, as day columns are narrow; long English words shrink instead of breaking.
 */
@Composable
fun CourseBlock(
    course: Course,
    modifier: Modifier = Modifier,
    style: CourseBlockStyle = CourseBlockStyle.SOFT,
    isCurrent: Boolean = false,
    fillOpacity: Float = 1f,
    onClick: (() -> Unit)? = null,
) {
    val colors = CoursePalette.block(course.colorIndex, style)
    val stripe = CoursePalette.color(course.colorIndex)
    val shape = RoundedCornerShape(6.dp)
    val leading = if (style == CourseBlockStyle.SOFT) 6.dp else 3.dp
    val lifted = if (isCurrent) Modifier.shadow(6.dp, shape, ambientColor = stripe, spotColor = stripe) else Modifier
    val outline = if (isCurrent) {
        Modifier.border(2.dp, if (style == CourseBlockStyle.SOFT) stripe else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), shape)
    } else {
        Modifier
    }
    BoxWithConstraints(
        modifier.then(lifted).clip(shape).background(colors.fill.copy(alpha = fillOpacity))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .then(outline),
    ) {
        if (style == CourseBlockStyle.SOFT) Box(Modifier.width(3.dp).fillMaxHeight().background(stripe))
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val textWidth = with(density) { (maxWidth - leading - 3.dp).toPx() }
        val nameSize = fitting(measurer, course.name, capped(11f, 14f), textWidth, FontWeight.SemiBold)
        val room = "@${course.room}"
        val roomSize = fitting(measurer, room, capped(10f, 12f), textWidth, FontWeight.Normal)
        val nowLabel = stringResource(R.string.now)
        Column(
            Modifier.fillMaxSize().padding(start = leading, end = 3.dp, top = 3.dp, bottom = 3.dp)
                .semantics(mergeDescendants = true) { if (isCurrent) stateDescription = nowLabel },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                course.name,
                color = colors.text,
                style = TextStyle(fontSize = nameSize, lineHeight = nameSize * 1.18f, fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Paragraph),
                maxLines = 5,
            )
            if (course.room.isNotEmpty()) {
                Text(
                    room,
                    color = colors.text,
                    style = TextStyle(fontSize = roomSize, lineHeight = roomSize * 1.2f, lineBreak = LineBreak.Paragraph),
                    maxLines = 2,
                )
            }
        }
    }
}

/** [base] sp growing with the font size setting, but only up to [max] sp. */
@Composable
private fun capped(base: Float, max: Float): TextUnit {
    val scale = LocalDensity.current.fontScale
    return (minOf(base * scale, max) / scale).sp
}

/**
 * The largest size up to [preferred] (at least 8 sp) at which every Latin word of [text] fits [width] pixels, so a
 * long English word shrinks instead of breaking mid-word (iOS `CourseBlock.fitting`). Chinese wraps between
 * characters anyway.
 */
private fun fitting(measurer: TextMeasurer, text: String, preferred: TextUnit, width: Float, weight: FontWeight): TextUnit {
    val words = text.split(Regex("\\s+")).filter { word -> word.isNotEmpty() && word.all { it.code < 128 } }
    if (width <= 0 || words.isEmpty()) return preferred
    val style = TextStyle(fontSize = preferred, fontWeight = weight)
    val widest = words.maxOf { measurer.measure(it, style, softWrap = false).size.width }.toFloat()
    if (widest <= width) return preferred
    // Text width grows in proportion to the size; half-point steps, a little room to spare.
    val size = floor(preferred.value * width / widest * 2) / 2 - 0.5f
    return maxOf(8f, size).sp
}
