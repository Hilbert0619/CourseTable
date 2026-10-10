package io.github.hilbert0619.coursekit

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** A column of the course import format. */
enum class ImportColumn(val rawValue: String) {
    NAME("name"), TEACHER("teacher"), ROOM("room"), WEEKDAY("weekday"), PERIODS("periods"), WEEKS("weeks");

    companion object {
        /** Columns every row needs. A missing `weeks` value means the whole term. */
        val REQUIRED: List<ImportColumn> = listOf(NAME, WEEKDAY, PERIODS)

        /** Column order assumed when the text has no header row; also the order [CourseCSV] exports. */
        val DEFAULT_ORDER: List<ImportColumn> = listOf(NAME, TEACHER, ROOM, WEEKDAY, PERIODS, WEEKS)

        /** Header names recognised for each column, compared after lowercasing and removing spaces, `_` and `-`. */
        internal val ALIASES: Map<ImportColumn, List<String>> = mapOf(
            NAME to listOf("name", "course", "coursename", "subject", "title", "课程", "课程名", "课程名称", "科目", "名称"),
            TEACHER to listOf("teacher", "instructor", "lecturer", "教师", "老师", "任课教师", "授课教师", "教师姓名"),
            ROOM to listOf("room", "location", "classroom", "place", "venue", "教室", "地点", "上课地点", "上课教室"),
            WEEKDAY to listOf("weekday", "day", "dayofweek", "星期", "星期几", "周几", "上课星期"),
            PERIODS to listOf("periods", "period", "time", "节次", "节", "上课节次", "时间", "上课时间"),
            WEEKS to listOf("weeks", "week", "周次", "周数", "上课周次", "起止周", "周"),
        )

        fun matching(header: String): ImportColumn? {
            val key = header.foldingFullWidth.lowercase().filter { !it.isWhitespace() && it != '_' && it != '-' }
            return entries.firstOrNull { ALIASES[it].orEmpty().contains(key) }
        }
    }
}

/** Something wrong with one line of imported text. */
data class ImportProblem(
    /** 1-based line number in the imported text. */
    val line: Int,
    val kind: Kind,
) {
    sealed interface Kind {
        /** The header row has no column for these required fields. */
        data class MissingColumns(val columns: List<ImportColumn>) : Kind

        /** A required field is empty. */
        data class MissingValue(val column: ImportColumn) : Kind

        /** A field that couldn't be understood, with its text. */
        data class InvalidValue(val column: ImportColumn, val text: String) : Kind

        /** Periods beyond the last period of the day. */
        data class PeriodsOutOfRange(val text: String, val periodsPerDay: Int) : Kind
    }
}

data class CourseImportResult(
    /** Parsed courses, all with colour index 0 (see [Timetable.planningImport]). */
    val courses: List<Course> = emptyList(),
    val problems: List<ImportProblem> = emptyList(),
    /** Data rows read, not counting the header, blank lines and `#` comments. */
    val rowCount: Int = 0,
)

/**
 * Reads courses from CSV or tab-separated text, e.g. a spreadsheet paste or a file exported from a school system.
 *
 * - The delimiter is detected (tab, comma, semicolon or full-width comma).
 * - The first row is a header if it names at least two known columns, in English or Chinese (`name`/`课程`,
 *   `teacher`/`教师`, `room`/`教室`, `weekday`/`星期`, `periods`/`节次`, `weeks`/`周次`), in any order. Without a
 *   header the columns are [ImportColumn.DEFAULT_ORDER].
 * - Weekdays: `1`–`7`, `Mon`/`Monday`, `周一`/`星期一`/`一`, `周日`/`星期天`.
 * - Periods: `3`, `1-2`, `第1-2节`, `1,2`, or a time range like `08:00-09:40` that is matched against the bell schedule.
 * - Weeks: `1-16`, `1-16周`, `1-15单`, `2-16(双)`, `1-8,10-16`, `1,3,5`, `odd`; empty means the whole term. Week
 *   lists that don't fit one range become several courses.
 */
class CourseImporter(val periods: PeriodSchedule, val totalWeeks: Int) {
    constructor(timetable: Timetable) : this(timetable.periods, timetable.semester.totalWeeks)

    fun parse(text: String): CourseImportResult {
        val body = text.removePrefix("﻿")
        val records = CSV.records(body, CSV.detectDelimiter(body))
            .map { record -> record.copy(fields = record.fields.map { it.trim() }) }
            .filter { record -> !record.fields.all { it.isEmpty() } && !isComment(record) }
        val first = records.firstOrNull() ?: return CourseImportResult()

        var columns: List<ImportColumn?> = ImportColumn.DEFAULT_ORDER
        var rows = records
        val header = headerColumns(first.fields)
        if (header != null) {
            val missing = ImportColumn.REQUIRED.filter { it !in header }
            if (missing.isNotEmpty()) {
                return CourseImportResult(problems = listOf(ImportProblem(first.line, ImportProblem.Kind.MissingColumns(missing))))
            }
            columns = header
            rows = records.drop(1)
        }

        val courses = mutableListOf<Course>()
        val problems = mutableListOf<ImportProblem>()
        for (row in rows) {
            val (rowCourses, rowProblems) = courses(row, columns)
            courses += rowCourses
            problems += rowProblems
        }
        return CourseImportResult(courses, problems, rows.size)
    }

    private fun courses(row: CSV.Record, columns: List<ImportColumn?>): Pair<List<Course>, List<ImportProblem>> {
        fun value(column: ImportColumn): String {
            val index = columns.indexOf(column)
            return if (index >= 0) row.fields.getOrElse(index) { "" } else ""
        }
        val problems = mutableListOf<ImportProblem>()
        fun report(kind: ImportProblem.Kind) {
            problems += ImportProblem(row.line, kind)
        }

        val name = value(ImportColumn.NAME)
        if (name.isEmpty()) report(ImportProblem.Kind.MissingValue(ImportColumn.NAME))

        val weekdayText = value(ImportColumn.WEEKDAY)
        val weekday = parseWeekday(weekdayText)
        if (weekdayText.isEmpty()) {
            report(ImportProblem.Kind.MissingValue(ImportColumn.WEEKDAY))
        } else if (weekday == null) {
            report(ImportProblem.Kind.InvalidValue(ImportColumn.WEEKDAY, weekdayText))
        }

        val periodsText = value(ImportColumn.PERIODS)
        val periodRuns = parsePeriods(periodsText, periods)
        if (periodsText.isEmpty()) {
            report(ImportProblem.Kind.MissingValue(ImportColumn.PERIODS))
        } else if (periodRuns != null) {
            if (periodRuns.any { it.first < 1 || it.last > periods.count }) {
                report(ImportProblem.Kind.PeriodsOutOfRange(periodsText, periods.count))
            }
        } else {
            report(ImportProblem.Kind.InvalidValue(ImportColumn.PERIODS, periodsText))
        }

        val weeksText = value(ImportColumn.WEEKS)
        val weeks = parseWeeks(weeksText, totalWeeks)
        if (weeks == null) report(ImportProblem.Kind.InvalidValue(ImportColumn.WEEKS, weeksText))

        if (problems.isNotEmpty() || weekday == null || periodRuns == null || weeks == null) return emptyList<Course>() to problems
        val courses = periodRuns.flatMap { run ->
            weeks.map { weekRange ->
                Course(
                    name = name, teacher = value(ImportColumn.TEACHER), room = value(ImportColumn.ROOM), weekday = weekday,
                    startPeriod = run.first, periodCount = run.last - run.first + 1, weeks = weekRange,
                )
            }
        }
        return courses to emptyList()
    }

    companion object {
        internal fun isComment(record: CSV.Record): Boolean {
            val first = record.fields.firstOrNull().orEmpty()
            return first.startsWith("#") || first.startsWith("//")
        }

        /**
         * The column of each field if [fields] looks like a header row (at least two known column names), otherwise
         * `null`. A column named twice counts only the first time.
         */
        internal fun headerColumns(fields: List<String>): List<ImportColumn?>? {
            val seen = mutableSetOf<ImportColumn>()
            val columns = fields.map { field ->
                val column = ImportColumn.matching(field)
                if (column != null && seen.add(column)) column else null
            }
            return if (seen.size >= 2) columns else null
        }

        // Field parsers

        private val ENGLISH_WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        private val CHINESE_WEEKDAYS = mapOf(
            '一' to Weekday.MONDAY, '二' to Weekday.TUESDAY, '三' to Weekday.WEDNESDAY, '四' to Weekday.THURSDAY,
            '五' to Weekday.FRIDAY, '六' to Weekday.SATURDAY, '日' to Weekday.SUNDAY, '天' to Weekday.SUNDAY,
            '七' to Weekday.SUNDAY,
        )

        internal fun parseWeekday(text: String): Weekday? {
            var key = text.foldingFullWidth.lowercase().filter { !it.isWhitespace() && it != '.' }
            for (prefix in listOf("星期", "礼拜", "周")) {
                if (key.startsWith(prefix)) {
                    key = key.removePrefix(prefix)
                    break
                }
            }
            swiftInt(key)?.let { return Weekday.of(it) }
            if (key.length == 1) CHINESE_WEEKDAYS[key[0]]?.let { return it }
            if (key.length < 2 || !key.all { it.isLetter() }) return null
            val index = ENGLISH_WEEKDAYS.indexOfFirst { it.startsWith(key) }
            return if (index >= 0) Weekday.of(index + 1) else null
        }

        /**
         * Runs of consecutive periods, e.g. `"1,2,5"` → `[1..2, 5..5]`. `null` if unreadable; the runs may still lie
         * outside the bell schedule.
         */
        internal fun parsePeriods(text: String, schedule: PeriodSchedule): List<IntRange>? {
            var key = text.foldingFullWidth.lowercase().normalizingDashes
            if (':' in key) return periodsCoveringTimes(key, schedule)?.let { listOf(it) }
            for (word in listOf("第", "节次", "节", "periods", "period")) key = key.replace(word, "")
            val numbers = sortedSetOf<Int>()
            for (token in key.tokens) {
                val range = parseRange(token) ?: return null
                numbers.addAll(range)
            }
            val sorted = numbers.toList()
            val first = sorted.firstOrNull() ?: return null
            val runs = mutableListOf<IntRange>()
            var runStart = first
            for (i in 1 until sorted.size) {
                if (sorted[i] != sorted[i - 1] + 1) {
                    runs += runStart..sorted[i - 1]
                    runStart = sorted[i]
                }
            }
            runs += runStart..sorted.last()
            return runs
        }

        /** The periods overlapping a time range like `08:00-09:40`, or `null` if there are none. */
        private fun periodsCoveringTimes(text: String, schedule: PeriodSchedule): IntRange? {
            val parts = text.split("-").filter { it.isNotEmpty() }.map { it.trim() }
            if (parts.size != 2) return null
            val start = TimeOfDay.parse(parts[0]) ?: return null
            val end = TimeOfDay.parse(parts[1]) ?: return null
            if (start >= end) return null
            val numbers = schedule.periods.indices
                .filter { schedule.periods[it].start < end && schedule.periods[it].end > start }
                .map { it + 1 }
            return if (numbers.isEmpty()) null else numbers.first()..numbers.last()
        }

        /**
         * Teaching weeks as one or more week ranges. Empty text means every week of the term; `null` if unreadable or
         * no week remains.
         */
        internal fun parseWeeks(text: String, totalWeeks: Int): List<CourseWeeks>? {
            var key = text.foldingFullWidth.lowercase().normalizingDashes
            for (bracket in listOf("(", ")", "[", "]", "【", "】")) key = key.replace(bracket, " ")
            for (word in listOf("第", "周次", "weeks", "week", "wks", "wk", "周", "每", "全", "every", "all")) {
                key = key.replace(word, "")
            }

            val ranges = mutableListOf<Pair<IntRange, WeekParity?>>()
            var sharedParity: WeekParity? = null
            for (token in key.tokens) {
                var rest = token
                var parity: WeekParity? = null
                for ((markers, value) in listOf(listOf("单", "odd") to WeekParity.ODD, listOf("双", "even") to WeekParity.EVEN)) {
                    for (marker in markers) {
                        if (marker !in rest) continue
                        if (parity != null && parity != value) return null
                        parity = value
                        rest = rest.replace(marker, "")
                    }
                }
                if (rest.isEmpty()) {
                    // A bare "单"/"odd" applies to every range that doesn't name its own parity.
                    if (sharedParity != null && sharedParity != parity) return null
                    sharedParity = parity
                    continue
                }
                val range = parseRange(rest) ?: return null
                if (range.first < 1 || range.last > Semester.MAX_WEEKS) return null
                ranges += range to parity
            }
            if (ranges.isEmpty()) ranges += (1..maxOf(totalWeeks, 1)) to null

            val weeks = mutableSetOf<Int>()
            for ((range, parity) in ranges) {
                val rule = parity ?: sharedParity ?: WeekParity.EVERY
                weeks += range.filter(rule::matches)
            }
            val segments = CourseWeeks.segments(weeks)
            return segments.ifEmpty { null }
        }

        /** `"3"` → `3..3`, `"1-4"` → `1..4`; `null` for anything else or a backwards range. */
        private fun parseRange(token: String): IntRange? {
            val bounds = token.split("-").map(::swiftInt)
            return when (bounds.size) {
                1 -> bounds[0]?.let { it..it }
                2 -> {
                    val low = bounds[0] ?: return null
                    val high = bounds[1] ?: return null
                    if (low <= high) low..high else null
                }
                else -> null
            }
        }
    }
}

/** Turns the bytes of an imported file into text. */
object ImportedText {
    /** Larger files are rejected before decoding; a timetable is a few kilobytes. */
    const val MAX_BYTES = 1_000_000

    /**
     * UTF-8 (with or without a byte-order mark) or UTF-16 with a byte-order mark. Text that isn't valid UTF-8 is
     * read as GB18030, which Excel on Chinese Windows uses when saving CSV (as on Apple platforms).
     */
    fun decode(data: ByteArray): String? {
        fun startsWith(vararg prefix: Int) = data.size >= prefix.size && prefix.indices.all { data[it] == prefix[it].toByte() }
        return when {
            startsWith(0xEF, 0xBB, 0xBF) -> strictly(data, 3, Charsets.UTF_8)
            startsWith(0xFF, 0xFE) -> strictly(data, 2, Charsets.UTF_16LE)
            startsWith(0xFE, 0xFF) -> strictly(data, 2, Charsets.UTF_16BE)
            else -> strictly(data, 0, Charsets.UTF_8) ?: strictly(data, 0, Charset.forName("GB18030"))
        }
    }

    private fun strictly(data: ByteArray, offset: Int, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(data, offset, data.size - offset))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

/** `Int(text)` in Swift: an optional sign and ASCII digits only. */
internal fun swiftInt(text: String): Int? {
    val digits = if (text.startsWith('+') || text.startsWith('-')) text.substring(1) else text
    if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
    return text.removePrefix("+").toIntOrNull()
}

/** Full-width ASCII forms (as typed with a Chinese input method) folded to ASCII. */
internal val String.foldingFullWidth: String
    get() = buildString(length) {
        for (char in this@foldingFullWidth) {
            append(
                when (char.code) {
                    in 0xFF01..0xFF5E -> (char.code - 0xFEE0).toChar()
                    0x3000 -> ' '
                    else -> char
                },
            )
        }
    }

/** Range dashes and words ("~", "–", "至", " to ") replaced with "-", without spaces around it. */
internal val String.normalizingDashes: String
    get() {
        var text = this
        for (dash in listOf(" to ", "~", "〜", "–", "—", "−", "至", "到")) text = text.replace(dash, "-")
        while (" -" in text || "- " in text) text = text.replace(" -", "-").replace("- ", "-")
        return text
    }

/** Pieces between commas, `、`, semicolons, slashes and whitespace, with empty pieces dropped. */
internal val String.tokens: List<String>
    get() = splitWhere { it == ',' || it == '、' || it == ';' || it == '/' || it.isWhitespace() }

/** The non-empty pieces between characters for which [isSeparator] is true. */
internal inline fun String.splitWhere(isSeparator: (Char) -> Boolean): List<String> {
    val pieces = mutableListOf<String>()
    val current = StringBuilder()
    for (char in this) {
        if (isSeparator(char)) {
            if (current.isNotEmpty()) pieces += current.toString()
            current.setLength(0)
        } else {
            current.append(char)
        }
    }
    if (current.isNotEmpty()) pieces += current.toString()
    return pieces
}
