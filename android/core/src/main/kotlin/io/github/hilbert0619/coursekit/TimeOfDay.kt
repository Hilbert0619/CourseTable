package io.github.hilbert0619.coursekit

import java.time.Instant
import java.time.ZoneId

/** A wall-clock time within a day, to the minute (00:00 … 23:59). Values outside a day wrap around. */
class TimeOfDay(minutes: Int) : Comparable<TimeOfDay> {
    /** Minutes since midnight, `0 until 1440`. */
    val minutes: Int = minutes.mod(24 * 60)

    constructor(hour: Int, minute: Int) : this(hour * 60 + minute)

    val hour: Int get() = minutes / 60
    val minute: Int get() = minutes % 60

    fun adding(minutes: Int): TimeOfDay = TimeOfDay(this.minutes + minutes)

    override fun compareTo(other: TimeOfDay): Int = minutes.compareTo(other.minutes)
    override fun equals(other: Any?): Boolean = other is TimeOfDay && other.minutes == minutes
    override fun hashCode(): Int = minutes

    /** 24-hour form, e.g. `08:05`. */
    override fun toString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        val MIDNIGHT = TimeOfDay(0)

        /** Parses `H:MM` or `HH:MM` (24-hour). */
        fun parse(text: String): TimeOfDay? {
            val parts = text.split(":")
            if (parts.size != 2 || parts[1].length != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return TimeOfDay(hour, minute)
        }

        /** The time of day of [instant] in [zone]. */
        fun from(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): TimeOfDay {
            val local = instant.atZone(zone)
            return TimeOfDay(local.hour, local.minute)
        }
    }
}

/** A local day plus a time on it: "now" as far as the timetable is concerned. */
data class Moment(val day: LocalDate, val time: TimeOfDay) : Comparable<Moment> {
    fun toInstant(zone: ZoneId = ZoneId.systemDefault()): Instant = day.toInstant(time, zone)

    override fun compareTo(other: Moment): Int = compareValuesBy(this, other, { it.day }, { it.time })

    companion object {
        fun from(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Moment =
            Moment(LocalDate.from(instant, zone), TimeOfDay.from(instant, zone))
    }
}
