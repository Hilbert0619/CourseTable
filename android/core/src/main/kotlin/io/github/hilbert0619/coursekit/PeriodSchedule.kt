package io.github.hilbert0619.coursekit

import kotlinx.serialization.Serializable

/** The start and end time of one class period (节). */
@Serializable
data class ClassPeriod(val start: TimeOfDay, val end: TimeOfDay) {
    val durationMinutes: Int get() = end.minutes - start.minutes
}

/** A start and an end time, e.g. of a class spanning several periods. */
data class TimeSpan(val start: TimeOfDay, val end: TimeOfDay)

/** The school's bell schedule: period 1, period 2, … with their times. */
@Serializable
data class PeriodSchedule(
    /** `periods[0]` is period 1. */
    val periods: List<ClassPeriod>,
) {
    val count: Int get() = periods.size

    /** Period [number] (1-based), if it exists. */
    fun period(number: Int): ClassPeriod? = periods.getOrNull(number - 1)

    /** Start of the first period to end of the last, for a class occupying [numbers]. */
    fun timeSpan(numbers: IntRange): TimeSpan? {
        val first = period(numbers.first) ?: return null
        val last = period(numbers.last) ?: return null
        return TimeSpan(first.start, last.end)
    }

    /**
     * Where [time] falls on a timetable grid with one row per period, for the current-time line: 0 is the top of
     * period 1 and [count] the bottom of the last. Inside a period it moves through the row in proportion; during a
     * break it rests on the line between the two periods. Null before the first period starts or after the last ends.
     */
    fun gridPosition(time: TimeOfDay): Double? {
        val first = periods.firstOrNull() ?: return null
        val last = periods.last()
        if (time < first.start || time > last.end) return null
        periods.forEachIndexed { index, period ->
            if (time < period.start) return index.toDouble()
            if (time <= period.end) {
                val length = maxOf(period.durationMinutes, 1).toDouble()
                return index + (time.minutes - period.start.minutes) / length
            }
        }
        return periods.size.toDouble()
    }

    /** A new period after the last one, keeping its length and a 10-minute break. */
    fun appendingPeriod(): PeriodSchedule {
        if (periods.size >= MAX_PERIODS) return this
        val last = periods.lastOrNull() ?: ClassPeriod(TimeOfDay(7, 15), TimeOfDay(7, 50))
        val start = last.end.adding(10)
        return copy(periods = periods + ClassPeriod(start, start.adding(maxOf(last.durationMinutes, 5))))
    }

    /** Problems that make the schedule unusable or ambiguous. */
    val issues: List<PeriodScheduleIssue>
        get() {
            if (periods.isEmpty()) return listOf(PeriodScheduleIssue.Empty)
            val issues = mutableListOf<PeriodScheduleIssue>()
            periods.forEachIndexed { index, period ->
                val number = index + 1
                if (period.end <= period.start) issues += PeriodScheduleIssue.EndsBeforeStart(number)
                if (index > 0 && period.start < periods[index - 1].end) issues += PeriodScheduleIssue.OverlapsPrevious(number)
            }
            return issues
        }

    companion object {
        const val MAX_PERIODS = 16

        /** A typical 12-period university day: four morning, four afternoon, four evening periods. */
        val STANDARD = PeriodSchedule(
            listOf(
                "08:00" to "08:45", "08:55" to "09:40", "10:00" to "10:45", "10:55" to "11:40",
                "14:00" to "14:45", "14:55" to "15:40", "16:00" to "16:45", "16:55" to "17:40",
                "19:00" to "19:45", "19:55" to "20:40", "20:50" to "21:35", "21:45" to "22:30",
            ).map { (start, end) -> ClassPeriod(TimeOfDay.parse(start)!!, TimeOfDay.parse(end)!!) },
        )
    }
}

sealed interface PeriodScheduleIssue {
    data object Empty : PeriodScheduleIssue
    data class EndsBeforeStart(val period: Int) : PeriodScheduleIssue
    data class OverlapsPrevious(val period: Int) : PeriodScheduleIssue
}
