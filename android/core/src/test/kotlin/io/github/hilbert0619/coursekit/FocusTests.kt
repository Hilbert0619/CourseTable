package io.github.hilbert0619.coursekit

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FocusTests {
    /** `Date(timeIntervalSinceReferenceDate: 800_000_000)` */
    private val start: Instant = Instant.ofEpochSecond(978_307_200L + 800_000_000L)

    private fun layout(plan: FocusPlan): List<String> = plan.phases.map { phase ->
        when (phase.kind) {
            FocusPhase.Kind.FOCUS -> "F${phase.seconds / 60}"
            FocusPhase.Kind.SHORT_BREAK -> "b${phase.seconds / 60}"
            FocusPhase.Kind.LONG_BREAK -> "L${phase.seconds / 60}"
        }
    }

    private fun minutes(value: Double): Instant = start.plusSeconds(value * 60)

    // Plan

    @Test
    fun aSessionIsFilledWithBlocksAndBreaks() {
        val plan = FocusPlan(FocusSettings(totalMinutes = 150))
        assertEquals(listOf("F25", "b5", "F25", "b5", "F25", "b5", "F25", "L15", "F20"), layout(plan))
        assertEquals(150 * 60, plan.totalSeconds)
        assertEquals(120 * 60, plan.focusSeconds)
        assertEquals(5, plan.blockCount)
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4, 5), plan.phases.map { it.block })
        assertEquals(listOf(0, 25 * 60, 30 * 60), plan.offsets.take(3))
    }

    @Test
    fun itNeverEndsWithABreakOrATinyBlock() {
        // 30 minutes: a break would leave no time to focus, so the block takes it all.
        assertEquals(listOf("F30"), layout(FocusPlan(FocusSettings(totalMinutes = 30))))
        // 34: a break and 4 minutes is too little; 35 leaves a 5-minute block.
        assertEquals(listOf("F34"), layout(FocusPlan(FocusSettings(totalMinutes = 34))))
        assertEquals(listOf("F25", "b5", "F5"), layout(FocusPlan(FocusSettings(totalMinutes = 35))))
        assertEquals(listOf("F25", "b5", "F30"), layout(FocusPlan(FocusSettings(totalMinutes = 60))))
        for (total in 15..240 step 5) {
            val plan = FocusPlan(FocusSettings(totalMinutes = total))
            assertEquals(total * 60, plan.totalSeconds)
            assertTrue(plan.phases.first().kind == FocusPhase.Kind.FOCUS && plan.phases.last().kind == FocusPhase.Kind.FOCUS)
            assertTrue(plan.phases.filter { !it.isBreak }.all { it.seconds >= 5 * 60 })
        }
    }

    @Test
    fun settingsShapeThePlan() {
        assertEquals(listOf("F150"), layout(FocusPlan(FocusSettings(totalMinutes = 150, usesBreaks = false))))
        val custom = FocusSettings(totalMinutes = 120, focusMinutes = 50, shortBreakMinutes = 10, longBreakMinutes = 20, blocksBeforeLongBreak = 2)
        // After block 2 a long break (20) would leave 10 minutes: block 2 runs to the end instead.
        assertEquals(listOf("F50", "b10", "F60"), layout(FocusPlan(custom)))
        val longer = FocusSettings(totalMinutes = 180, focusMinutes = 50, shortBreakMinutes = 10, longBreakMinutes = 20, blocksBeforeLongBreak = 2)
        assertEquals(listOf("F50", "b10", "F50", "L20", "F50"), layout(FocusPlan(longer)))
        // Out-of-range values are brought into range.
        assertEquals(15 * 60, FocusPlan(FocusSettings(totalMinutes = 1)).totalSeconds)
        assertEquals(240 * 60, FocusPlan(FocusSettings(totalMinutes = 999, usesBreaks = false)).totalSeconds)
        assertEquals("F10", layout(FocusPlan(FocusSettings(totalMinutes = 60, focusMinutes = 1, shortBreakMinutes = 0))).first())
    }

    // Running

    @Test
    fun statusFollowsTheClock() {
        val run = FocusRun(goal = "Calculus review", plan = FocusPlan(FocusSettings(totalMinutes = 60)), startedAt = start)
        val early = run.status(minutes(10.0))
        assertTrue(early.phaseIndex == 0 && early.phase.kind == FocusPhase.Kind.FOCUS)
        assertTrue(early.phaseRemaining == 15.0 * 60 && early.focused == 10.0 * 60)
        val resting = run.status(minutes(27.0))
        assertTrue(resting.phase.kind == FocusPhase.Kind.SHORT_BREAK && resting.phaseRemaining == 3.0 * 60 && resting.focused == 25.0 * 60)
        val last = run.status(minutes(40.0))
        assertTrue(last.phaseIndex == 2 && last.phaseElapsed == 10.0 * 60 && last.focused == 35.0 * 60)
        val over = run.status(minutes(75.0))
        assertTrue(over.isFinished && over.phaseIndex == 2 && over.phaseRemaining == 0.0 && over.focused == 55.0 * 60)
        assertEquals(minutes(60.0), run.endDate(minutes(10.0)))
        assertFalse(run.status(start.plusSeconds(-5.0)).isFinished)
    }

    @Test
    fun pausesMoveEverythingLater() {
        var run = FocusRun(goal = "", plan = FocusPlan(FocusSettings(totalMinutes = 60)), startedAt = start)
        run = run.pause(minutes(10.0))
        assertEquals(10.0 * 60, run.status(minutes(20.0)).elapsed)
        assertTrue(run.status(minutes(20.0)).isPaused)
        assertNull(run.endDate(minutes(20.0)))
        assertTrue(run.upcomingSwitches(minutes(20.0)).isEmpty())
        run = run.pause(minutes(15.0)) // already paused: no change
        run = run.resume(minutes(20.0))
        assertEquals(10.0 * 60, run.status(minutes(20.0)).elapsed)
        assertEquals(minutes(70.0), run.endDate(minutes(20.0)))
        run = run.resume(minutes(30.0)) // not paused: no change
        assertEquals(minutes(70.0), run.endDate(minutes(30.0)))
    }

    @Test
    fun skippingABreakStartsTheNextBlock() {
        var run = FocusRun(goal = "", plan = FocusPlan(FocusSettings(totalMinutes = 60)), startedAt = start)
        run = run.skipBreak(minutes(10.0)) // during focus: nothing
        assertEquals(0.0, run.skippedSeconds)
        run = run.skipBreak(minutes(26.0))
        val status = run.status(minutes(26.0))
        assertTrue(status.phaseIndex == 2 && status.phaseElapsed == 0.0)
        assertEquals(minutes(56.0), run.endDate(minutes(26.0)))
        assertEquals(25.0 * 60, status.focused)
    }

    @Test
    fun switchesAreTheNotificationsToSchedule() {
        val run = FocusRun(goal = "", plan = FocusPlan(FocusSettings(totalMinutes = 150)), startedAt = start)
        val switches = run.upcomingSwitches(minutes(20.0))
        assertEquals((1..9).toList(), switches.map { it.index })
        assertEquals(minutes(25.0), switches.first().date)
        assertEquals(minutes(150.0), switches.last().date) // index 9 == phases.size: the end
        assertEquals(3, run.upcomingSwitches(minutes(20.0), limit = 3).size)
        assertEquals(listOf(9), run.upcomingSwitches(minutes(149.0)).map { it.index })
        assertTrue(run.upcomingSwitches(minutes(150.0)).isEmpty())
        assertTrue(FocusRun.MAX_ALERTS >= 2)
    }

    @Test
    fun aRunRoundTripsAsJSON() {
        val run = FocusRun(goal = "高数复习", plan = FocusPlan(FocusSettings(totalMinutes = 90)), startedAt = start).pause(minutes(5.0))
        val json = CourseKitJson.encodeToString(FocusRun.serializer(), run)
        assertEquals(run, CourseKitJson.decodeFromString(FocusRun.serializer(), json))
        // Dates as Swift writes them: seconds since 2001-01-01.
        assertTrue(""""startedAt":8.0E8""" in json || """"startedAt":800000000""" in json, json)
    }
}
