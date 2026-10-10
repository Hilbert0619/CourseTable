package io.github.hilbert0619.coursekit

/** Day of the week, Monday first (`rawValue` 1 = Monday … 7 = Sunday), as on a Chinese timetable. */
enum class Weekday(val rawValue: Int) {
    MONDAY(1), TUESDAY(2), WEDNESDAY(3), THURSDAY(4), FRIDAY(5), SATURDAY(6), SUNDAY(7);

    val isWeekend: Boolean get() = this == SATURDAY || this == SUNDAY

    /** `java.util.Calendar` numbering, where 1 = Sunday … 7 = Saturday. */
    val calendarWeekday: Int get() = if (this == SUNDAY) 1 else rawValue + 1

    companion object {
        fun of(rawValue: Int): Weekday? = entries.firstOrNull { it.rawValue == rawValue }

        fun ofCalendarWeekday(calendarWeekday: Int): Weekday? =
            if (calendarWeekday in 1..7) of(if (calendarWeekday == 1) 7 else calendarWeekday - 1) else null
    }
}
