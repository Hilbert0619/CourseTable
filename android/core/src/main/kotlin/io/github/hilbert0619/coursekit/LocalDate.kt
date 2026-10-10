package io.github.hilbert0619.coursekit

import java.time.Instant
import java.time.ZoneId

/**
 * A calendar day with no time or time zone attached, such as the first day of term.
 *
 * Day arithmetic is plain integer math on the proleptic Gregorian calendar, so results never depend on the
 * device's time zone or daylight-saving rules.
 */
@JvmInline
value class LocalDate(
    /** Number of days since 1970-01-01 (negative before it). */
    val daysSinceEpoch: Int,
) : Comparable<LocalDate> {

    /** Out-of-range values roll over, e.g. February 30 becomes March 1 or 2. */
    constructor(year: Int, month: Int, day: Int) : this(
        (month - 1).let { zeroBasedMonth ->
            daysFromCivil(year + zeroBasedMonth.floorDiv(12), zeroBasedMonth.mod(12) + 1, 1) + day - 1
        }
    )

    val year: Int get() = civilFromDays(daysSinceEpoch).year
    val month: Int get() = civilFromDays(daysSinceEpoch).month
    val day: Int get() = civilFromDays(daysSinceEpoch).day

    // 1970-01-01 was a Thursday.
    val weekday: Weekday get() = Weekday.of((daysSinceEpoch + 3).mod(7) + 1)!!

    fun adding(days: Int): LocalDate = LocalDate(daysSinceEpoch + days)

    /** Whole days from this day to [other] (positive when [other] is later). */
    fun daysTo(other: LocalDate): Int = other.daysSinceEpoch - daysSinceEpoch

    /** The Monday of the Monday–Sunday week containing this day. */
    val startOfWeek: LocalDate get() = adding(-(weekday.rawValue - 1))

    /** The moment this day reaches [time] in [zone]. */
    fun toInstant(time: TimeOfDay = TimeOfDay.MIDNIGHT, zone: ZoneId = ZoneId.systemDefault()): Instant {
        val civil = civilFromDays(daysSinceEpoch)
        return java.time.LocalDateTime.of(civil.year, civil.month, civil.day, time.hour, time.minute)
            .atZone(zone).toInstant()
    }

    override fun compareTo(other: LocalDate): Int = daysSinceEpoch.compareTo(other.daysSinceEpoch)

    /** ISO 8601 form, e.g. `2026-09-07`. */
    override fun toString(): String {
        val civil = civilFromDays(daysSinceEpoch)
        return "${civil.year}-${civil.month.toString().padStart(2, '0')}-${civil.day.toString().padStart(2, '0')}"
    }

    companion object {
        /** Parses `YYYY-MM-DD`; rejects impossible dates such as `2026-02-30`. */
        fun parse(text: String): LocalDate? {
            val parts = text.split("-")
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val day = parts[2].toIntOrNull() ?: return null
            if (month !in 1..12 || day < 1) return null
            val date = LocalDate(year, month, day)
            return if (date.year == year && date.month == month && date.day == day) date else null
        }

        /** The calendar day that [instant] falls on in [zone]. */
        fun from(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
            val local = instant.atZone(zone).toLocalDate()
            return LocalDate(local.year, local.monthValue, local.dayOfMonth)
        }

        // Howard Hinnant's days_from_civil / civil_from_days algorithms.
        internal fun daysFromCivil(year: Int, month: Int, day: Int): Int {
            val y = if (month <= 2) year - 1 else year
            val era = (if (y >= 0) y else y - 399) / 400
            val yearOfEra = y - era * 400
            val shiftedMonth = (month + 9) % 12
            val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146_097 + dayOfEra - 719_468
        }

        internal fun civilFromDays(days: Int): Civil {
            val z = days + 719_468
            val era = (if (z >= 0) z else z - 146_096) / 146_097
            val dayOfEra = z - era * 146_097
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shiftedMonth = (5 * dayOfYear + 2) / 153
            val day = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1
            val month = if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9
            val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
            return Civil(year, month, day)
        }
    }

    internal data class Civil(val year: Int, val month: Int, val day: Int)
}
