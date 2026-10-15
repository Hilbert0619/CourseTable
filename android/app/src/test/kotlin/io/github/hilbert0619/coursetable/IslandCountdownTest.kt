package io.github.hilbert0619.coursetable

import io.github.hilbert0619.coursetable.alerts.IslandCountdown
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The minutes an island shows in place of the countdown, and when they change (plain JVM). */
class IslandCountdownTest {
    private val target = Instant.parse("2026-09-28T01:40:00Z")

    @Test
    fun minutesAreRoundedUp() {
        assertEquals(25L, IslandCountdown.minutesLeft(target, target.minusSeconds(24 * 60 + 10)))
        assertEquals(24L, IslandCountdown.minutesLeft(target, target.minusSeconds(24 * 60)))
        assertEquals(1L, IslandCountdown.minutesLeft(target, target.minusMillis(1)))
        assertEquals(0L, IslandCountdown.minutesLeft(target, target))
        assertEquals(0L, IslandCountdown.minutesLeft(target, target.plusSeconds(5)))
    }

    @Test
    fun theNumberChangesOnEachWholeMinuteBeforeTheTarget() {
        assertEquals(target.minusSeconds(24 * 60), IslandCountdown.nextChange(target, target.minusSeconds(24 * 60 + 10)))
        // Right on a minute: the next one.
        assertEquals(target.minusSeconds(23 * 60), IslandCountdown.nextChange(target, target.minusSeconds(24 * 60)))
        assertEquals(target.minusSeconds(60), IslandCountdown.nextChange(target, target.minusSeconds(90)))
        // The last minute ends at the target, where the notification's own alarm posts it again.
        assertNull(IslandCountdown.nextChange(target, target.minusSeconds(60)))
        assertNull(IslandCountdown.nextChange(target, target.minusSeconds(30)))
        assertNull(IslandCountdown.nextChange(target, target.plusSeconds(30)))
    }
}
