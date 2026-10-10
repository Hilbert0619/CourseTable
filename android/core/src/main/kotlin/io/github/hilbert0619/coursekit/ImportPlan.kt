package io.github.hilbert0619.coursekit

/** What importing a list of courses would do to a timetable. */
data class ImportPlan(
    /** Courses that will be added, with colours assigned. */
    val added: List<Course>,
    /** Imported courses that are already in the timetable (or repeated in the import); skipped. */
    val duplicates: List<Course>,
    /** Whether the existing courses are removed first. */
    val replacesExisting: Boolean,
    /** The timetable after the import. */
    val timetable: Timetable,
)

/**
 * Plans adding [imported] to this timetable (or replacing its courses).
 *
 * Courses already present are skipped. A course whose name matches an existing or earlier imported course reuses its
 * colour; other names get the least-used colour.
 */
fun Timetable.planningImport(imported: List<Course>, replacingExisting: Boolean = false): ImportPlan {
    val kept = if (replacingExisting) emptyList() else courses
    val added = mutableListOf<Course>()
    val duplicates = mutableListOf<Course>()
    val colorByName = mutableMapOf<String, Int>()
    for (course in kept) colorByName.putIfAbsent(course.name, course.colorIndex)

    for (course in imported) {
        if ((kept + added).any { it.isSameClass(course) }) {
            duplicates += course
            continue
        }
        val color = colorByName[course.name] ?: Course.suggestedColorIndex(kept + added).also { colorByName[course.name] = it }
        added += course.copy(colorIndex = color)
    }
    return ImportPlan(added, duplicates, replacingExisting, copy(courses = kept + added))
}

/** Courses as CSV, in the column order the importer assumes when there is no header. */
object CourseCSV {
    fun export(courses: List<Course>): String {
        val header = ImportColumn.DEFAULT_ORDER.joinToString(",") { it.rawValue }
        val rows = courses.sortedWith(Timetable.SLOT_ORDER).map { course ->
            ImportColumn.DEFAULT_ORDER.joinToString(",") { CSV.escape(field(it, course)) }
        }
        return (listOf(header) + rows).joinToString("") { it + "\r\n" }
    }

    /** UTF-8 with a byte-order mark, so Excel shows Chinese text correctly. */
    fun exportData(courses: List<Course>): ByteArray =
        byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + export(courses).toByteArray(Charsets.UTF_8)

    private fun field(column: ImportColumn, course: Course): String = when (column) {
        ImportColumn.NAME -> course.name
        ImportColumn.TEACHER -> course.teacher
        ImportColumn.ROOM -> course.room
        ImportColumn.WEEKDAY -> course.weekday.rawValue.toString()
        ImportColumn.PERIODS -> if (course.periodCount > 1) "${course.startPeriod}-${course.endPeriod}" else "${course.startPeriod}"
        ImportColumn.WEEKS -> weeksText(course.weeks)
    }

    private fun weeksText(weeks: CourseWeeks): String {
        val range = if (weeks.first == weeks.last) "${weeks.first}" else "${weeks.first}-${weeks.last}"
        return when (weeks.parity) {
            WeekParity.EVERY -> range
            WeekParity.ODD -> "$range odd"
            WeekParity.EVEN -> "$range even"
        }
    }

    /** A sample to paste into the import screen, with English headers. */
    val ENGLISH_EXAMPLE = """
        name,teacher,room,weekday,periods,weeks
        Advanced Mathematics,Li,A101,Mon,1-2,1-16
        College English,Wang,B204,Tue,3-4,1-16
        Physics Lab,Zhao,Lab 3,Thu,5-7,1-15 odd
        Data Structures,Chen,C305,Fri,1-2,"1-8,10-16"
    """.trimIndent()

    /** The same sample with Chinese headers and values. */
    val CHINESE_EXAMPLE = """
        课程,教师,教室,星期,节次,周次
        高等数学,李老师,A101,周一,1-2,1-16周
        大学英语,王老师,B204,周二,3-4,1-16周
        物理实验,赵老师,实验楼3,周四,5-7,1-15单周
        数据结构,陈老师,C305,周五,1-2,"1-8,10-16周"
    """.trimIndent()
}
