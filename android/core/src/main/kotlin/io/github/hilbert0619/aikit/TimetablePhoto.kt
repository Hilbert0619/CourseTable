package io.github.hilbert0619.aikit

import io.github.hilbert0619.coursekit.ClassPeriod
import io.github.hilbert0619.coursekit.PeriodSchedule
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.vocabkit.isSwiftNewline
import io.github.hilbert0619.vocabkit.splitWhere
import io.github.hilbert0619.vocabkit.swiftLines
import io.github.hilbert0619.vocabkit.trimmingWhitespaces
import io.github.hilbert0619.vocabkit.trimmingWhitespacesAndNewlines
import kotlin.math.abs

/**
 * A timetable from a picture (docs/ai-agent-spec.md §2): either the picture goes to a model that reads pictures, or the
 * phone reads its text with the position of each piece and a text model rebuilds the grid. Either way the answer is the
 * CSV import format, which goes into the import preview.
 */
object TimetablePhoto {
    /** A piece of text the phone found in a picture. Positions are fractions of the picture (0–1) from its top left corner. */
    data class TextBox(val text: String, val x: Double, val y: Double, val width: Double, val height: Double)

    /** The most text sent for one picture. */
    const val MAX_OCR_CHARACTERS = 12_000

    /** What the position lines mean, before them in a prompt. */
    internal const val OCR_EXPLANATION =
        "The phone read this text from a timetable photo. Each line is one text box: [x y w h] are its left edge, " +
            "top edge, width and height as fractions of the picture (0–1, from the top left), so boxes in one column " +
            "have similar x and boxes in one row similar y. Column headers are usually the weekdays (星期一… or Mon…), " +
            "row headers the class periods (第1-2节) or times. A cell may hold several lines, and sometimes two courses."

    /**
     * The boxes as lines like `[x 0.12 y 0.31 w 0.08 h 0.02] 高等数学`, top to bottom and left to right within a row
     * (boxes whose middles are within half a box height count as one row).
     */
    fun ocrText(boxes: List<TextBox>): String {
        val cleaned = boxes.mapNotNull { box ->
            val text = box.text.swiftLines().joinToString(" ").trimmingWhitespaces()
            if (text.isEmpty()) null else box.copy(text = text)
        }
        val rows = mutableListOf<MutableList<TextBox>>()
        for (box in cleaned.sortedBy { it.y + it.height / 2 }) {
            val middle = box.y + box.height / 2
            val last = rows.lastOrNull()?.lastOrNull()
            if (last != null && abs((last.y + last.height / 2) - middle) <= maxOf(last.height, box.height) / 2) {
                rows.last() += box
            } else {
                rows += mutableListOf(box)
            }
        }
        val text = StringBuilder()
        var count = 0
        for (box in rows.flatMap { row -> row.sortedBy { it.x } }) {
            val line = "[x ${fraction(box.x)} y ${fraction(box.y)} w ${fraction(box.width)} h ${fraction(box.height)}] ${box.text}"
            val length = line.characterCount
            if (count + length + 1 > MAX_OCR_CHARACTERS) break
            if (text.isNotEmpty()) {
                text.append('\n')
                count += 1
            }
            text.append(line)
            count += length
        }
        return text.toString()
    }

    /** Two decimals, e.g. `0.07`. */
    internal fun fraction(value: Double): String {
        val hundredths = (value.coerceIn(0.0, 1.0) * 100).roundSwift()
        return if (hundredths == 100) "1.00" else "0.${if (hundredths < 10) "0" else ""}$hundredths"
    }

    /** Swift `rounded()`: halves away from zero. */
    private fun Double.roundSwift(): Int = if (this >= 0) kotlin.math.floor(this + 0.5).toInt() else -kotlin.math.floor(-this + 0.5).toInt()

    /** What the model wrote: the CSV, and the period times when it found them. */
    data class Reading(val csv: String, val periods: PeriodSchedule?)

    /**
     * Reads the answer to [AIPrompts.timetableFromPhoto]: the ```csv block (or the first code block, or the whole answer)
     * and a ```periods block. `null` when there is no CSV at all.
     */
    fun reading(answer: String): Reading? {
        val blocks = codeBlocks(answer)
        val block = blocks.firstOrNull { it.first == "csv" } ?: blocks.firstOrNull { it.first != "periods" }
        val csv = (block?.second ?: if (blocks.isEmpty()) answer else null)?.trimmingWhitespacesAndNewlines()
        if (csv.isNullOrEmpty()) return null
        val periods = blocks.firstOrNull { it.first == "periods" }?.let { periodSchedule(it.second) }
        return Reading(csv, periods)
    }

    private fun codeBlocks(text: String): List<Pair<String, String>> {
        val blocks = mutableListOf<Pair<String, String>>()
        var language: String? = null
        val lines = mutableListOf<String>()
        for (line in text.splitWhere(omittingEmpty = false, ::isSwiftNewline)) {
            val trimmed = line.trimmingWhitespaces()
            if (trimmed.startsWith("```")) {
                val open = language
                if (open != null) {
                    blocks += open to lines.joinToString("\n")
                    language = null
                    lines.clear()
                } else {
                    language = trimmed.substring(3).trimmingWhitespaces().lowercase()
                }
            } else if (language != null) {
                lines += line
            }
        }
        val open = language
        if (open != null && lines.isNotEmpty()) blocks += open to lines.joinToString("\n")
        return blocks
    }

    /** Lines `number,start,end`: periods 1, 2, … in order, each ending after it starts and before the next starts. */
    internal fun periodSchedule(text: String): PeriodSchedule? {
        val periods = mutableListOf<ClassPeriod>()
        for (line in text.splitWhere(omittingEmpty = false, ::isSwiftNewline)) {
            val fields = line.replace("，", ",").split(",").filter { it.isNotEmpty() }.map { it.trimmingWhitespaces() }
            if (fields.size != 3) continue
            val number = fields[0].toIntOrNull() ?: continue
            val start = TimeOfDay.parse(fields[1])
            val end = TimeOfDay.parse(fields[2])
            if (number != periods.size + 1 || start == null || end == null || start >= end) return null
            if (periods.lastOrNull()?.let { it.end <= start } == false) return null
            periods += ClassPeriod(start, end)
        }
        if (periods.size < 2 || periods.size > PeriodSchedule.MAX_PERIODS) return null
        return PeriodSchedule(periods)
    }
}
