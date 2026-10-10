package io.github.hilbert0619.coursekit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A term: when teaching week 1 starts and how many weeks it runs.
 *
 * Weeks run Monday–Sunday. Week 1 is the week containing the chosen start day, so picking a Wednesday still makes
 * that whole Monday–Sunday week "week 1".
 */
@Serializable(with = SemesterSerializer::class)
class Semester(startingOn: LocalDate, totalWeeks: Int = DEFAULT_TOTAL_WEEKS) {
    /** Monday of week 1. */
    val firstMonday: LocalDate = startingOn.startOfWeek
    val totalWeeks: Int = maxOf(1, totalWeeks)

    /** The start day as shown to the user (always the Monday of week 1). */
    val startDate: LocalDate get() = firstMonday

    /** The same term starting in the week of [day]. */
    fun withStartDate(day: LocalDate): Semester = Semester(day, totalWeeks)

    fun withTotalWeeks(weeks: Int): Semester = Semester(firstMonday, weeks)

    /** Sunday of the last week. */
    val lastDay: LocalDate get() = firstMonday.adding(totalWeeks * 7 - 1)

    val weeks: IntRange get() = 1..totalWeeks

    /** Teaching-week number of [day]. Zero or negative before term, above [totalWeeks] after it. */
    fun week(containing: LocalDate): Int = firstMonday.daysTo(containing).floorDiv(7) + 1

    fun status(on: LocalDate): SemesterStatus {
        val current = week(containing = on)
        if (current < 1) return SemesterStatus.NotStarted(daysUntilStart = on.daysTo(firstMonday))
        if (current > totalWeeks) return SemesterStatus.Finished
        return SemesterStatus.InProgress(week = current)
    }

    /** The week to show by default on [day]: the current week, clamped into the term. */
    fun displayWeek(on: LocalDate): Int = week(containing = on).coerceIn(1, totalWeeks)

    /** The date of [weekday] in teaching week [week]. */
    fun date(of: Weekday, inWeek: Int): LocalDate = firstMonday.adding((inWeek - 1) * 7 + of.rawValue - 1)

    override fun equals(other: Any?): Boolean =
        other is Semester && other.firstMonday == firstMonday && other.totalWeeks == totalWeeks

    override fun hashCode(): Int = firstMonday.hashCode() * 31 + totalWeeks
    override fun toString(): String = "Semester(firstMonday=$firstMonday, totalWeeks=$totalWeeks)"

    companion object {
        const val DEFAULT_TOTAL_WEEKS = 20
        const val MAX_WEEKS = 30
    }
}

sealed interface SemesterStatus {
    data class NotStarted(val daysUntilStart: Int) : SemesterStatus
    data class InProgress(override val week: Int) : SemesterStatus
    data object Finished : SemesterStatus

    val week: Int? get() = null
}

/** `{"firstMonday": "2026-08-31", "totalWeeks": 18}`; the start is normalised to a Monday when read. */
object SemesterSerializer : KSerializer<Semester> {
    @Serializable
    private class Surrogate(val firstMonday: LocalDate, val totalWeeks: Int)

    override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Semester) =
        encoder.encodeSerializableValue(Surrogate.serializer(), Surrogate(value.firstMonday, value.totalWeeks))

    override fun deserialize(decoder: Decoder): Semester =
        decoder.decodeSerializableValue(Surrogate.serializer()).let { Semester(it.firstMonday, it.totalWeeks) }
}
