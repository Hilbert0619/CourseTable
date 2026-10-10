package io.github.hilbert0619.coursekit

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which weeks inside a course's week range it actually meets. */
@Serializable
enum class WeekParity {
    @SerialName("every") EVERY,
    @SerialName("odd") ODD,
    @SerialName("even") EVEN;

    fun matches(week: Int): Boolean = when (this) {
        EVERY -> true
        ODD -> week % 2 != 0
        EVEN -> week % 2 == 0
    }
}

/** The teaching weeks a course meets in, e.g. "weeks 1–16, odd weeks only". */
@Serializable
data class CourseWeeks(val first: Int, val last: Int, val parity: WeekParity = WeekParity.EVERY) {
    operator fun contains(week: Int): Boolean = week >= first && week <= last && parity.matches(week)

    /** Every week the course meets, in order. */
    val all: List<Int> get() = if (first <= last) (first..last).filter(parity::matches) else emptyList()

    companion object {
        /**
         * The fewest ranges this greedy pass finds that cover exactly [weeks]: runs of consecutive weeks become
         * "every week" ranges, runs stepping by two become odd- or even-week ranges.
         */
        fun segments(covering: Set<Int>): List<CourseWeeks> {
            val sorted = covering.sorted()
            val segments = mutableListOf<CourseWeeks>()
            var index = 0
            while (index < sorted.size) {
                var consecutiveEnd = index
                while (consecutiveEnd + 1 < sorted.size && sorted[consecutiveEnd + 1] == sorted[consecutiveEnd] + 1) consecutiveEnd++
                var alternateEnd = index
                while (alternateEnd + 1 < sorted.size && sorted[alternateEnd + 1] == sorted[alternateEnd] + 2) alternateEnd++

                val first = sorted[index]
                if (alternateEnd > consecutiveEnd) {
                    val parity = if (first % 2 == 0) WeekParity.EVEN else WeekParity.ODD
                    segments += CourseWeeks(first, sorted[alternateEnd], parity)
                    index = alternateEnd + 1
                } else {
                    segments += CourseWeeks(first, sorted[consecutiveEnd])
                    index = consecutiveEnd + 1
                }
            }
            return segments
        }
    }
}

/** One recurring class in the timetable. */
@Serializable
data class Course(
    @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    val name: String,
    val teacher: String = "",
    val room: String = "",
    @Serializable(with = WeekdaySerializer::class) val weekday: Weekday,
    /** First period (1-based). */
    val startPeriod: Int,
    /** Number of consecutive periods, at least 1. */
    val periodCount: Int = 2,
    val weeks: CourseWeeks,
    /** Index into the app's colour palette. */
    val colorIndex: Int = 0,
) {
    val endPeriod: Int get() = startPeriod + maxOf(periodCount, 1) - 1

    val periods: IntRange get() = startPeriod..endPeriod

    fun meets(inWeek: Int): Boolean = inWeek in weeks

    /** True if both courses would be in the same slot in at least one week. */
    fun clashes(other: Course): Boolean {
        if (other.id == id || other.weekday != weekday) return false
        if (other.startPeriod > endPeriod || startPeriod > other.endPeriod) return false
        val firstShared = maxOf(weeks.first, other.weeks.first)
        val lastShared = minOf(weeks.last, other.weeks.last)
        if (firstShared > lastShared) return false
        return (firstShared..lastShared).any { meets(it) && other.meets(it) }
    }

    /** Problems with this course given the bell schedule and term length. */
    fun issues(periodsPerDay: Int, totalWeeks: Int): List<CourseIssue> {
        val issues = mutableListOf<CourseIssue>()
        if (name.isBlank()) issues += CourseIssue.MissingName
        if (startPeriod < 1 || periodCount < 1 || endPeriod > periodsPerDay) {
            issues += CourseIssue.PeriodsOutOfRange(periodsPerDay)
        }
        if (weeks.first < 1 || weeks.last < weeks.first) {
            issues += CourseIssue.InvalidWeekRange
        } else if (weeks.all.isEmpty()) {
            issues += CourseIssue.NoMatchingWeeks
        } else if (weeks.last > totalWeeks) {
            issues += CourseIssue.WeeksBeyondSemester(totalWeeks)
        }
        return issues
    }

    /** The same class in the same slot: equal name, teacher, room, day, periods and weeks. Identity and colour are ignored. */
    fun isSameClass(other: Course): Boolean {
        return name == other.name && teacher == other.teacher && room == other.room && weekday == other.weekday &&
            startPeriod == other.startPeriod && periodCount == other.periodCount && weeks.all == other.weeks.all
    }

    companion object {
        const val PALETTE_SIZE = 10

        /** The palette colour used least by [existing] courses (lowest index on ties). */
        fun suggestedColorIndex(avoiding: List<Course>): Int = leastUsedColorIndex(avoiding.map { it.colorIndex })

        /** The palette colour appearing least often in [used] (lowest index on ties). */
        fun leastUsedColorIndex(among: List<Int>): Int {
            val usage = IntArray(PALETTE_SIZE)
            for (index in among) if (index in usage.indices) usage[index]++
            return usage.indices.minByOrNull { usage[it] } ?: 0
        }
    }
}

sealed interface CourseIssue {
    data object MissingName : CourseIssue
    data class PeriodsOutOfRange(val periodsPerDay: Int) : CourseIssue
    data object InvalidWeekRange : CourseIssue

    /** E.g. "week 2 only, odd weeks": the course would never meet. */
    data object NoMatchingWeeks : CourseIssue

    /** The course runs past the end of term; allowed, but worth a warning. */
    data class WeeksBeyondSemester(val totalWeeks: Int) : CourseIssue

    /** Whether the course can't be saved until this is fixed. */
    val isBlocking: Boolean get() = this !is WeeksBeyondSemester
}
