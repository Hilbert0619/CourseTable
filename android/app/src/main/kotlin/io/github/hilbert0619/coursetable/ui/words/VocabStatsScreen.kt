package io.github.hilbert0619.coursetable.ui.words

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.focus.FocusText
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.javaDate
import io.github.hilbert0619.vocabkit.DayStats
import io.github.hilbert0619.vocabkit.VocabStats
import java.time.format.DateTimeFormatter

private val NEW_BLUE = Color(0xFF2F6FDB)

/** Statistics from the stored answers and cards only (iOS `VocabStatsView`); each threshold is named next to its number. */
@Composable
fun VocabStatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val controller = LocalWordsController.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val text = WordsText(resources)
    val focusText = FocusText(resources)
    val today = state.today(controller.now())
    val days = VocabStats.series(state.events, today, count = 30)
    val todayStats = days.lastOrNull() ?: DayStats(today)
    val streak = VocabStats.streak(state.events, today)
    val buckets = VocabStats.buckets(state.cards.values, today)
    val book = VocabStats.progress(state.bookWords, state.cards, today)
    val locale = resources.configuration.locales[0]
    val dayFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md"), locale)
    fun label(day: Int): String = dayFormat.format(state.clock.date(day).javaDate)

    WordsScaffold(stringResource(R.string.statistics), onBack, modifier) { content ->
        Column(content.verticalScroll(rememberScrollState())) {
            Section(title = stringResource(R.string.tab_today)) {
                ValueRow(stringResource(R.string.new_words), "${todayStats.newWords}")
                RowDivider()
                ValueRow(stringResource(R.string.words_reviewed), "${todayStats.reviewedWords}")
                RowDivider()
                ValueRow(stringResource(R.string.accuracy), text.percent(todayStats.accuracy))
                RowDivider()
                ValueRow(stringResource(R.string.time_label), focusText.duration(todayStats.durationMs / 1000))
                RowDivider()
                ValueRow(stringResource(R.string.study_streak), pluralDays(streak.days))
            }

            val chartDescription = stringResource(R.string.words_a_day)
            Section(title = chartDescription) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WordsChart(days, today, ::label, Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = chartDescription })
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(NEW_BLUE, stringResource(R.string.legend_new))
                        Legend(GOOD_GREEN, stringResource(R.string.legend_reviewed))
                    }
                }
            }

            val studied = days.filter { it.studied }
            if (studied.size >= 2) {
                Section(title = stringResource(R.string.accuracy), footer = stringResource(R.string.accuracy_footer)) {
                    AccuracyChart(days, Modifier.fillMaxWidth().height(140.dp).padding(16.dp), text::percent)
                }
            }

            Section(title = stringResource(R.string.learned_words), footer = stringResource(R.string.buckets_footer, VocabStats.APPROACHING_DAYS)) {
                ValueRow(stringResource(R.string.needs_review), "${buckets.needsReview}")
                RowDivider()
                ValueRow(stringResource(R.string.coming_up), "${buckets.approaching}")
                RowDivider()
                ValueRow(stringResource(R.string.stable), "${buckets.stable}")
            }

            state.currentBook?.let { current ->
                Section(title = current.name, footer = stringResource(R.string.book_progress_footer, VocabStats.MASTERED_STABILITY.toInt())) {
                    ValueRow(stringResource(R.string.words_label), "${book.total}")
                    RowDivider()
                    ValueRow(stringResource(R.string.learned), "${book.learned}")
                    RowDivider()
                    ValueRow(stringResource(R.string.mastered), "${book.mastered}")
                    RowDivider()
                    ValueRow(stringResource(R.string.due_today), "${book.due}")
                    LinearProgressIndicator(
                        progress = { book.learned.toFloat() / maxOf(book.total, 1) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** New and reviewed words a day, stacked; a date under every seventh day, the last one six days before today. */
@Composable
private fun WordsChart(days: List<DayStats>, today: Int, label: (Int) -> String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maximum = maxOf(days.maxOfOrNull { it.newWords + it.reviewedWords } ?: 0, 1)
    Canvas(modifier) {
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val slot = size.width / days.size
        val barWidth = slot * 0.7f
        // A line and a date every week, behind the bars.
        days.forEachIndexed { index, day ->
            if ((today - day.day) % 7 == 6) {
                val center = index * slot + slot / 2
                drawLine(grid, Offset(center, 0f), Offset(center, chartHeight), strokeWidth = 0.5.dp.toPx())
                val text = measurer.measure(label(day.day), labelStyle)
                val x = (center - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
                drawText(text, topLeft = Offset(x, chartHeight + 2.dp.toPx()))
            }
        }
        drawLine(grid, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())
        days.forEachIndexed { index, day ->
            val x = index * slot + (slot - barWidth) / 2
            val reviewed = chartHeight * day.reviewedWords / maximum
            val new = chartHeight * day.newWords / maximum
            if (day.newWords > 0) drawRect(NEW_BLUE, Offset(x, chartHeight - new), Size(barWidth, new))
            if (day.reviewedWords > 0) drawRect(GOOD_GREEN, Offset(x, chartHeight - new - reviewed), Size(barWidth, reviewed))
        }
        drawText(measurer, "$maximum", Offset(0f, 0f), labelStyle)
    }
}

/** Accuracy on the days studied, with 0%, 50% and 100% lines. */
@Composable
private fun AccuracyChart(days: List<DayStats>, modifier: Modifier, percent: (Double?) -> String) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val left = 36.dp.toPx()
        val width = size.width - left
        val slot = width / days.size
        // Room above 100% and below 0% for the labels, which sit centred on their lines.
        val top = 8.dp.toPx()
        val height = size.height - 2 * top
        fun y(fraction: Double) = top + height * (1 - fraction).toFloat()
        for (fraction in listOf(0.0, 0.5, 1.0)) {
            drawLine(grid, Offset(left, y(fraction)), Offset(size.width, y(fraction)), strokeWidth = 0.5.dp.toPx())
            val text = measurer.measure(percent(fraction), labelStyle)
            drawText(text, topLeft = Offset(0f, y(fraction) - text.size.height / 2f))
        }
        val points = days.mapIndexedNotNull { index, day ->
            day.accuracy?.let { Offset(left + index * slot + slot / 2, y(it)) }
        }
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (point in points.drop(1)) lineTo(point.x, point.y)
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        }
        for (point in points) drawCircle(line, radius = 3.dp.toPx(), center = point)
    }
}
