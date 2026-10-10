package io.github.hilbert0619.coursekit

import java.util.UUID

/** A course without a time slot: the name, room and teacher typed into the course list, ready to be placed on the grid. */
data class CourseTemplate(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val room: String = "",
    val teacher: String = "",
    /** Index into the app's colour palette. */
    val colorIndex: Int = 0,
) {
    /** Same name, room and teacher. */
    fun hasSameDetails(other: CourseTemplate): Boolean = name == other.name && room == other.room && teacher == other.teacher

    /** Whether [course] has this template's name, room and teacher. */
    fun describes(course: Course): Boolean = name == course.name && room == course.room && teacher == course.teacher

    companion object {
        /** The name, room and teacher of an existing course. */
        fun from(course: Course): CourseTemplate =
            CourseTemplate(course.id, course.name, course.room, course.teacher, course.colorIndex)

        /**
         * Reads a typed course list: one course per line as name, room, teacher.
         *
         * Fields are separated by tabs, commas (`,` `，`), semicolons, `|` or `、`. If a line has none of those, spaces
         * separate the fields, and with more than three words the last two are the room and teacher (so "College
         * English B204 Wang" works). Room and teacher are optional. Blank lines, `#` comments and lines without a name
         * are skipped; repeated lines are kept once.
         */
        fun parseList(text: String): List<CourseTemplate> {
            val templates = mutableListOf<CourseTemplate>()
            for (rawLine in text.split(CSV.NEWLINE)) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val fields = fields(line)
                val name = fields.firstOrNull()
                if (name.isNullOrEmpty()) continue
                val template = CourseTemplate(
                    name = name,
                    room = fields.getOrElse(1) { "" },
                    teacher = fields.getOrElse(2) { "" },
                )
                if (templates.none { it.hasSameDetails(template) }) templates += template
            }
            return templates
        }

        private val SEPARATORS = setOf('\t', ',', '，', ';', '；', '|', '、')

        private fun fields(line: String): List<String> {
            if (line.any { it in SEPARATORS }) {
                return line.split(*SEPARATORS.toCharArray()).map { it.trim() }
            }
            val words = line.splitWhere { it.isWhitespace() }
            if (words.size <= 3) return words
            return listOf(words.dropLast(2).joinToString(" ")) + words.takeLast(2)
        }
    }
}

/**
 * The courses to offer when placing: [saved] (the course list) first, then one template for each name/room/teacher
 * combination on the timetable that the list doesn't already cover, in slot order. A template taken from a course
 * reuses that course's id and colour.
 */
fun Timetable.placementTemplates(saved: List<CourseTemplate>): List<CourseTemplate> {
    val templates = saved.toMutableList()
    for (course in courses.sortedWith(Timetable.SLOT_ORDER)) {
        if (templates.none { it.describes(course) }) templates += CourseTemplate.from(course)
    }
    return templates
}

/**
 * Colours for templates about to join the course list: the colour of a course or saved template with the same name,
 * otherwise the least-used palette colour.
 */
fun Timetable.assigningColors(new: List<CourseTemplate>, saved: List<CourseTemplate>): List<CourseTemplate> {
    val colorByName = mutableMapOf<String, Int>()
    for ((name, color) in courses.map { it.name to it.colorIndex } + saved.map { it.name to it.colorIndex }) {
        colorByName.putIfAbsent(name, color)
    }
    val used = (courses.map { it.colorIndex } + saved.map { it.colorIndex }).toMutableList()
    return new.map { template ->
        val color = colorByName[template.name]
            ?: Course.leastUsedColorIndex(used).also { colorByName[template.name] = it }
        used += color
        template.copy(colorIndex = color)
    }
}

/**
 * The course created by placing [template] in the slot at [period] on [weekday].
 *
 * It runs for the whole term (odd or even weeks if [parity] says so and the term has such a week). [periodCount] is
 * cut short to fit the day, and further if that avoids running into another course; a clash in the starting period
 * itself is left for the editor to flag.
 */
fun Timetable.placing(
    template: CourseTemplate,
    weekday: Weekday,
    period: Int,
    periodCount: Int,
    parity: WeekParity = WeekParity.EVERY,
): Course {
    var weeks = CourseWeeks(1, semester.totalWeeks, parity)
    if (weeks.all.isEmpty()) weeks = weeks.copy(parity = WeekParity.EVERY)
    val start = period.coerceIn(1, maxOf(periods.count, 1))
    val fitsDay = periodCount.coerceAtLeast(1).coerceAtMost(maxOf(periods.count - start + 1, 1))
    val course = Course(
        name = template.name, teacher = template.teacher, room = template.room, weekday = weekday,
        startPeriod = start, periodCount = fitsDay, weeks = weeks, colorIndex = template.colorIndex,
    )
    val clashFree = (fitsDay downTo 1).firstOrNull { length -> clashes(course.copy(periodCount = length)).isEmpty() }
    return if (clashFree != null) course.copy(periodCount = clashFree) else course
}
