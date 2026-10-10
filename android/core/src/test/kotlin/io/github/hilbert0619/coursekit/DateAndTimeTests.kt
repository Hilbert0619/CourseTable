package io.github.hilbert0619.coursekit

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class LocalDateTests {
    @Test
    fun epochIsThursdayJanuaryFirst1970() {
        val epoch = LocalDate(daysSinceEpoch = 0)
        assertEquals(LocalDate(1970, 1, 1), epoch)
        assertEquals(Weekday.THURSDAY, epoch.weekday)
    }

    @ParameterizedTest
    @CsvSource("2026-09-24, THURSDAY", "2026-09-28, MONDAY", "2024-02-29, THURSDAY", "2000-01-01, SATURDAY", "1969-12-31, WEDNESDAY")
    fun weekdayOfKnownDates(text: String, expected: Weekday) {
        val date = assertNotNull(LocalDate.parse(text))
        assertEquals(expected, date.weekday)
    }

    @Test
    fun civilConversionRoundTripsOverCenturies() {
        for (days in -200_000..200_000 step 37) {
            val date = LocalDate(daysSinceEpoch = days)
            val roundTripped = LocalDate(date.year, date.month, date.day)
            assertEquals(days, roundTripped.daysSinceEpoch)
            assertTrue(date.month in 1..12)
        }
    }

    @Test
    fun consecutiveDaysAreOneApart() {
        var date = LocalDate(2023, 12, 25)
        repeat(800) {
            val next = date.adding(1)
            assertEquals(1, date.daysTo(next))
            assertTrue(next > date)
            date = next
        }
        assertEquals(LocalDate(2026, 3, 4), date)
    }

    @Test
    fun outOfRangeComponentsRollOver() {
        assertEquals(LocalDate(2026, 3, 2), LocalDate(2026, 2, 30))
        assertEquals(LocalDate(2027, 1, 1), LocalDate(2026, 13, 1))
        assertEquals(LocalDate(2025, 12, 31), LocalDate(2026, 1, 0))
        assertEquals(LocalDate(2025, 12, 15), LocalDate(2026, 0, 15))
    }

    @Test
    fun parsingRejectsImpossibleDates() {
        assertNotNull(LocalDate.parse("2024-02-29"))
        assertNotNull(LocalDate.parse("2000-02-29"))
        assertNull(LocalDate.parse("2026-02-29"))
        assertNull(LocalDate.parse("1900-02-29"))
        assertNull(LocalDate.parse("2026-13-01"))
        assertNull(LocalDate.parse("2026-9"))
        assertNull(LocalDate.parse("hello"))
    }

    @Test
    fun descriptionIsZeroPaddedISO() {
        assertEquals("2026-09-07", LocalDate(2026, 9, 7).toString())
        assertEquals("2026-09-07", LocalDate.parse("2026-09-07")?.toString())
    }

    @Test
    fun startOfWeekIsMonday() {
        val thursday = LocalDate(2026, 9, 24)
        assertEquals(LocalDate(2026, 9, 21), thursday.startOfWeek)
        val sunday = LocalDate(2026, 9, 27)
        assertEquals(LocalDate(2026, 9, 21), sunday.startOfWeek)
        val monday = LocalDate(2026, 9, 21)
        assertEquals(monday, monday.startOfWeek)
    }

    @Test
    fun convertsFromDateInGivenTimeZone() {
        val shanghai = ZoneOffset.ofHours(8)
        val epoch = Instant.ofEpochSecond(0)
        assertEquals(LocalDate(1970, 1, 1), LocalDate.from(epoch, shanghai))
        assertEquals(TimeOfDay(8, 0), TimeOfDay.from(epoch, shanghai))

        val lateEvening = Instant.ofEpochSecond(-4 * 3600L) // 1969-12-31 20:00 UTC
        assertEquals(LocalDate(1970, 1, 1), LocalDate.from(lateEvening, shanghai))
        assertEquals(LocalDate(1969, 12, 31), LocalDate.from(lateEvening, ZoneOffset.UTC))
    }

    @Test
    fun dateAtTimeRoundTrips() {
        val zone = ZoneOffset.ofHours(8)
        val moment = Moment(LocalDate(2026, 9, 24), TimeOfDay(14, 55))
        val instant = moment.toInstant(zone)
        assertEquals(moment, Moment.from(instant, zone))
    }
}

class TimeOfDayTests {
    @Test
    fun parsesAndFormats() {
        val time = assertNotNull(TimeOfDay.parse("8:05"))
        assertEquals(8, time.hour)
        assertEquals(5, time.minute)
        assertEquals("08:05", time.toString())
        assertEquals(23 * 60 + 59, TimeOfDay.parse("23:59")?.minutes)
    }

    @ParameterizedTest
    @ValueSource(strings = ["24:00", "12:60", "7", "07:5", "ab:cd", "-1:00", ""])
    fun rejectsInvalidText(text: String) {
        assertNull(TimeOfDay.parse(text))
    }

    @Test
    fun wrapsAroundMidnight() {
        assertEquals(TimeOfDay(0, 10), TimeOfDay(23, 50).adding(20))
        assertEquals(TimeOfDay(23, 50), TimeOfDay(minutes = -10))
    }

    @Test
    fun ordersByTime() {
        assertTrue(TimeOfDay(8, 0) < TimeOfDay(8, 1))
        val early = Moment(LocalDate(2026, 9, 24), TimeOfDay(23, 0))
        val late = Moment(LocalDate(2026, 9, 25), TimeOfDay(1, 0))
        assertTrue(early < late)
    }
}
