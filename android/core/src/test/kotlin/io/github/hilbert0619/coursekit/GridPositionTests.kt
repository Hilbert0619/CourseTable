package io.github.hilbert0619.coursekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The timetable's current-time line: where a time falls on the period rows. */
class GridPositionTests {
    private val standard = PeriodSchedule.STANDARD

    private fun position(text: String, schedule: PeriodSchedule = standard): Double? =
        schedule.gridPosition(TimeOfDay.parse(text)!!)

    @Test
    fun insideAPeriodTheLineMovesThroughItsRow() {
        assertEquals(0.0, position("08:00"))
        assertTrue(abs(position("08:15")!! - 1.0 / 3) < 1e-9)
        assertEquals(1.0, position("08:45"))
        assertTrue(abs(position("10:30")!! - (2 + 30.0 / 45)) < 1e-9)
    }

    @Test
    fun duringABreakTheLineRestsBetweenPeriods() {
        assertEquals(1.0, position("08:50"))
        assertEquals(1.0, position("08:55"))
        assertEquals(2.0, position("09:50"))
        // Lunch, after period 4.
        assertEquals(4.0, position("12:30"))
        assertEquals(8.0, position("18:30"))
    }

    @Test
    fun noLineOutsideTheDay() {
        assertNull(position("07:59"))
        assertEquals(12.0, position("22:30"))
        assertNull(position("22:31"))
        assertNull(position("08:00", PeriodSchedule(emptyList())))
    }

    @Test
    fun aZeroLengthPeriodDoesNotDivideByZero() {
        val odd = PeriodSchedule(
            listOf(
                ClassPeriod(TimeOfDay.parse("08:00")!!, TimeOfDay.parse("08:00")!!),
                ClassPeriod(TimeOfDay.parse("09:00")!!, TimeOfDay.parse("09:40")!!),
            ),
        )
        assertEquals(0.0, position("08:00", odd))
        assertEquals(1.0, position("08:30", odd))
        assertEquals(1.5, position("09:20", odd))
    }
}
