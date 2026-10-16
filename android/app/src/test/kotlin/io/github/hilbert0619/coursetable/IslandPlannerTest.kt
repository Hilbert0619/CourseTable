package io.github.hilbert0619.coursetable

import io.github.hilbert0619.coursetable.island.Bounds
import io.github.hilbert0619.coursetable.island.IslandGeometry
import io.github.hilbert0619.coursetable.island.IslandItem
import io.github.hilbert0619.coursetable.island.IslandKind
import io.github.hilbert0619.coursetable.island.IslandPlanner
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the Countdown Island shows and where it goes (plain JVM). */
class IslandPlannerTest {
    private val now = Instant.parse("2026-09-28T01:15:30Z")

    private fun item(key: String, kind: IslandKind, minutes: Long?, started: Boolean = false, title: String = key) =
        IslandItem(key, kind, title, "", minutes?.let { now.plusSeconds(it * 60) }, started, color = 0)

    private val classSoon = item("1:class:a", IslandKind.CLASS, 8)
    private val classLater = item("1:class:b", IslandKind.CLASS, 40, started = true)
    private val focus = item("10:", IslandKind.FOCUS, 20)
    private val paused = item("10:", IslandKind.FOCUS, null)

    @Test
    fun aRunningSessionComesFirstThenTheSoonestEndThenAPausedSession() {
        assertEquals(listOf(focus, classSoon, classLater), IslandPlanner.order(listOf(classLater, classSoon, focus)))
        assertEquals(listOf(classSoon, paused), IslandPlanner.order(listOf(paused, classSoon)))
    }

    @Test
    fun thePillShowsWholeMinutesLeftOrPaused() {
        val minutes = { n: Int -> "$n min" }
        val target = now.plusSeconds(24 * 60 + 10)
        assertEquals("25 min", IslandPlanner.compactText(focus.copy(target = target), now, minutes, "Paused"))
        assertEquals("1 min", IslandPlanner.compactText(focus.copy(target = now.plusSeconds(5)), now, minutes, "Paused"))
        assertEquals("Paused", IslandPlanner.compactText(paused, now, minutes, "Paused"))
    }

    @Test
    fun theCardShowsTheTimeToTheSecond() {
        assertEquals("24:59", IslandPlanner.clock(now.plusSeconds(24 * 60 + 59), now))
        assertEquals("25:00", IslandPlanner.clock(now.plusMillis(24 * 60_000L + 59_001), now))
        assertEquals("1:05:00", IslandPlanner.clock(now.plusSeconds(65 * 60), now))
        assertEquals("0:00", IslandPlanner.clock(now.minusSeconds(3), now))
    }

    @Test
    fun theIslandIsReadAgainWhenAMinuteChanges() {
        // 8 minutes left: "8 min" becomes "7 min" a minute later; in the last minute, at the end.
        assertEquals(now.plusSeconds(60), IslandPlanner.nextChange(listOf(classLater, classSoon), now))
        val lastMinute = item("x", IslandKind.TEST, null).copy(target = now.plusSeconds(30))
        assertEquals(now.plusSeconds(30), IslandPlanner.nextChange(listOf(lastMinute), now))
        assertNull(IslandPlanner.nextChange(listOf(paused), now))
    }

    @Test
    fun hidingLastsUntilACountdownStartsEndsOrSwitches() {
        val phase = IslandPlanner.phase(listOf(classSoon, focus))
        // The minutes going down don't count.
        assertEquals(phase, IslandPlanner.phase(listOf(classSoon.copy(target = now.plusSeconds(60)), focus)))
        assertNotEquals(phase, IslandPlanner.phase(listOf(classSoon.copy(started = true), focus)))
        assertNotEquals(phase, IslandPlanner.phase(listOf(classSoon, paused)))
        assertNotEquals(phase, IslandPlanner.phase(listOf(classSoon, focus.copy(title = "Break"))))
        assertNotEquals(phase, IslandPlanner.phase(listOf(focus)))
    }

    // Where it goes: a 1260-pixel-wide screen at density 3 (the iQOO Neo 9's size).

    @Test
    fun aCenteredCameraHoleSitsInTheMiddleOfThePill() {
        val geometry = IslandGeometry.of(1260, Bounds(588, 36, 672, 120), statusBarHeight = 120, density = 3f)
        assertEquals(630, geometry.centerX)
        assertEquals(36, geometry.top)
        assertEquals(84, geometry.height)
        assertEquals(84 + 30, geometry.cameraWidth)
        assertEquals(120, geometry.cameraBottom)
    }

    @Test
    fun aSmallHoleGetsAPillAtLeast28dpTallAroundIt() {
        val geometry = IslandGeometry.of(1260, Bounds(610, 40, 650, 80), statusBarHeight = 120, density = 3f)
        assertEquals(84, geometry.height)
        assertEquals(18, geometry.top)
    }

    @Test
    fun aCornerHoleOrNoneGetsAPillCenteredInTheStatusBar() {
        val corner = IslandGeometry.of(1260, Bounds(60, 30, 140, 110), statusBarHeight = 120, density = 3f)
        assertEquals(630, corner.centerX)
        assertEquals(108, corner.height)
        assertEquals(6, corner.top)
        assertEquals(24, corner.cameraWidth)
        val none = IslandGeometry.of(1260, null, statusBarHeight = 0, density = 3f)
        assertEquals(84, none.height)
        assertEquals(0, none.top)
    }
}
