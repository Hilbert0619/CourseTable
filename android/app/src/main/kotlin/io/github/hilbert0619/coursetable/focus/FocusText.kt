package io.github.hilbert0619.coursetable.focus

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import io.github.hilbert0619.coursekit.FocusPhase
import io.github.hilbert0619.coursekit.FocusRun
import io.github.hilbert0619.coursetable.R
import java.util.Locale
import kotlin.math.ceil

/** The focus timer's texts, shared by the Focus tab and the notifications (iOS `FocusView`, `FocusReminders`). */
class FocusText(private val res: Resources) {
    private val locale: Locale get() = res.configuration.locales[0]

    /** "2 hr, 30 min", "25 min" / "2小时30分钟" (to the nearest minute, as iOS's abbreviated units). */
    fun duration(seconds: Int): String {
        val total = (maxOf(seconds, 0) + 30) / 60
        val hours = total / 60
        val minutes = total % 60
        val measures = buildList {
            if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
            if (minutes > 0 || hours == 0) add(Measure(minutes, MeasureUnit.MINUTE))
        }
        return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*measures.toTypedArray())
    }

    /** "Focus · Block 2 of 5", "Focus", "Short break", "Long break" */
    fun phaseTitle(run: FocusRun, phase: FocusPhase): String = when (phase.kind) {
        FocusPhase.Kind.FOCUS ->
            if (run.plan.blockCount > 1) res.getString(R.string.focus_block_of, phase.block, run.plan.blockCount) else res.getString(R.string.tab_focus)
        FocusPhase.Kind.SHORT_BREAK -> res.getString(R.string.short_break)
        FocusPhase.Kind.LONG_BREAK -> res.getString(R.string.long_break)
    }

    /** Title and text of the notification for the switch into phase [index], or the end when it's the phase count. */
    fun switchAlert(run: FocusRun, index: Int): Pair<String, String> {
        val goal = run.goal
        if (index >= run.plan.phases.size) {
            val text = listOf(duration(run.plan.focusSeconds), goal).filter { it.isNotEmpty() }.joinToString(" · ")
            return res.getString(R.string.focus_session_done) to text
        }
        val phase = run.plan.phases[index]
        val length = duration(phase.seconds)
        return when (phase.kind) {
            FocusPhase.Kind.FOCUS -> {
                val block = res.getString(R.string.block_of, phase.block, run.plan.blockCount)
                res.getString(R.string.back_to_focus) to listOf(block, length, goal).filter { it.isNotEmpty() }.joinToString(" · ")
            }
            FocusPhase.Kind.SHORT_BREAK -> res.getString(R.string.time_for_break) to length
            FocusPhase.Kind.LONG_BREAK -> res.getString(R.string.time_for_long_break) to length
        }
    }

    companion object {
        /** "24:59" or "1:05:00", rounded up so it reaches 0:00 when the phase ends. */
        fun clock(seconds: Double): String {
            val total = ceil(maxOf(seconds, 0.0)).toInt()
            val hours = total / 3600
            val minutes = total / 60 % 60
            val rest = total % 60
            return if (hours > 0) {
                String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
            } else {
                String.format(Locale.ROOT, "%d:%02d", minutes, rest)
            }
        }
    }
}
