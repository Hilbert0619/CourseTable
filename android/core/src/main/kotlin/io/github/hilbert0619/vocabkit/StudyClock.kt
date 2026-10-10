package io.github.hilbert0619.vocabkit

import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import java.time.Instant
import java.time.ZoneId

/**
 * Study days. A study day starts at [dayStart] (04:00 by default) instead of midnight, so late-night study still counts
 * for the evening before. Days are numbered like [LocalDate.daysSinceEpoch]: the study day that starts on 2026-09-30 at
 * 04:00 is the number of 2026-09-30.
 */
class StudyClock(val zone: ZoneId = ZoneId.systemDefault(), val dayStart: TimeOfDay = DEFAULT_DAY_START) {
    /** The study day [instant] belongs to. */
    fun day(instant: Instant): Int {
        val calendarDay = LocalDate.from(instant, zone).daysSinceEpoch
        return if (TimeOfDay.from(instant, zone) < dayStart) calendarDay - 1 else calendarDay
    }

    /** When study day [day] begins. */
    fun start(day: Int): Instant = LocalDate(daysSinceEpoch = day).toInstant(dayStart, zone)

    /** When study day [day] ends (the next one begins). */
    fun end(day: Int): Instant = start(day + 1)

    /** The calendar date a study day is named after. */
    fun date(day: Int): LocalDate = LocalDate(daysSinceEpoch = day)

    companion object {
        val DEFAULT_DAY_START = TimeOfDay(4, 0)
    }
}
