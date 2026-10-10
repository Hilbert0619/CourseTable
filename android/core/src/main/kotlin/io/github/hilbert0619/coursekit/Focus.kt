package io.github.hilbert0619.coursekit

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a focus session is laid out: its length (breaks included) and the Pomodoro rhythm. */
@Serializable
data class FocusSettings(
    val totalMinutes: Int = 120,
    /** Off: one straight countdown. */
    val usesBreaks: Boolean = true,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    /** A long break after every this many focus blocks. */
    val blocksBeforeLongBreak: Int = 4,
) {
    companion object {
        val TOTAL_RANGE = 15..240
        val FOCUS_RANGE = 10..90
        val SHORT_BREAK_RANGE = 1..30
        val LONG_BREAK_RANGE = 5..60
    }
}

/** One stretch of a focus session. */
@Serializable
data class FocusPhase(
    val kind: Kind,
    val seconds: Int,
    /** 1 for the first focus block, 2 for the second…; for a break, the block before it. */
    val block: Int,
) {
    @Serializable
    enum class Kind {
        @SerialName("focus") FOCUS,
        @SerialName("shortBreak") SHORT_BREAK,
        @SerialName("longBreak") LONG_BREAK,
    }

    val isBreak: Boolean get() = kind != Kind.FOCUS
}

/**
 * The phases of a session, filling its length exactly. It never ends with a break: when what's left after a block is
 * too short for a break and at least 5 minutes of focus, the last block takes it; otherwise the last block is
 * shortened so the session ends on time.
 */
@Serializable
data class FocusPlan(val phases: List<FocusPhase>) {
    constructor(settings: FocusSettings) : this(phases(settings))

    val totalSeconds: Int get() = phases.sumOf { it.seconds }
    val focusSeconds: Int get() = phases.filter { !it.isBreak }.sumOf { it.seconds }
    val blockCount: Int get() = phases.count { !it.isBreak }

    /** When each phase starts, in seconds from the start of the session. */
    val offsets: List<Int> get() = phases.runningFold(0) { sum, phase -> sum + phase.seconds }.dropLast(1)

    companion object {
        /** The shortest focus block the plan adds after a break. */
        const val SHORTEST_BLOCK_SECONDS = 5 * 60

        private fun phases(settings: FocusSettings): List<FocusPhase> {
            val total = settings.totalMinutes.coerceIn(FocusSettings.TOTAL_RANGE) * 60
            if (!settings.usesBreaks) return listOf(FocusPhase(FocusPhase.Kind.FOCUS, total, 1))
            val focus = settings.focusMinutes.coerceIn(FocusSettings.FOCUS_RANGE) * 60
            val shortBreak = settings.shortBreakMinutes.coerceIn(FocusSettings.SHORT_BREAK_RANGE) * 60
            val longBreak = settings.longBreakMinutes.coerceIn(FocusSettings.LONG_BREAK_RANGE) * 60
            val every = maxOf(settings.blocksBeforeLongBreak, 1)
            val phases = mutableListOf<FocusPhase>()
            var left = total
            var block = 0
            while (left > 0) {
                block++
                val length = minOf(focus, left)
                phases += FocusPhase(FocusPhase.Kind.FOCUS, length, block)
                left -= length
                if (left <= 0) break
                val isLong = block % every == 0
                val pause = if (isLong) longBreak else shortBreak
                if (left < pause + SHORTEST_BLOCK_SECONDS) {
                    // Not enough for a break and another block: the last block runs to the end.
                    phases[phases.lastIndex] = phases.last().let { it.copy(seconds = it.seconds + left) }
                    break
                }
                phases += FocusPhase(if (isLong) FocusPhase.Kind.LONG_BREAK else FocusPhase.Kind.SHORT_BREAK, pause, block)
                left -= pause
            }
            return phases
        }
    }
}

/** Where a running session stands at some moment. Times are in seconds. */
data class FocusStatus(
    /** Index into [FocusPlan.phases]; the last phase once the session is over. */
    val phaseIndex: Int,
    val phase: FocusPhase,
    /** Session time so far (pauses don't count, skipped breaks do). */
    val elapsed: Double,
    val phaseElapsed: Double,
    val phaseRemaining: Double,
    /** Focus time so far, without breaks. */
    val focused: Double,
    val isPaused: Boolean,
    val isFinished: Boolean,
)

/** A switch into phase [index] (or the end of the session when `index == phases.size`) at [date]. */
data class FocusSwitch(val index: Int, val date: Instant)

/**
 * A focus session in progress: the plan, when it started, and its pauses. Kept as a value so the app, its
 * notification buttons and a relaunch all work from the same saved state. Changes return a new run.
 */
@Serializable
data class FocusRun(
    @Serializable(with = UuidSerializer::class) val id: UUID = UUID.randomUUID(),
    val goal: String,
    val plan: FocusPlan,
    @Serializable(with = ReferenceDateSerializer::class) val startedAt: Instant,
    /** Time spent in pauses that have ended, in seconds. */
    val pausedSeconds: Double = 0.0,
    /** The current pause, if paused. */
    @Serializable(with = ReferenceDateSerializer::class) val pausedAt: Instant? = null,
    /** Break time skipped so far, in seconds: it counts as elapsed. */
    val skippedSeconds: Double = 0.0,
) {
    val isPaused: Boolean get() = pausedAt != null

    /** Session time at [date]: wall time since the start, minus pauses, plus skipped breaks; at most the plan's length. */
    fun elapsed(date: Instant): Double {
        val end = pausedAt ?: date
        val raw = secondsBetween(startedAt, end) - pausedSeconds + skippedSeconds
        return raw.coerceIn(0.0, plan.totalSeconds.toDouble())
    }

    fun status(date: Instant): FocusStatus {
        val elapsed = elapsed(date)
        val offsets = plan.offsets
        var index = 0
        while (index + 1 < plan.phases.size && offsets[index + 1] <= elapsed) index++
        val phase = plan.phases[index]
        val phaseElapsed = minOf(elapsed - offsets[index], phase.seconds.toDouble())
        var focused = 0.0
        for ((offset, item) in offsets.zip(plan.phases)) {
            if (!item.isBreak) focused += (elapsed - offset).coerceIn(0.0, item.seconds.toDouble())
        }
        return FocusStatus(
            phaseIndex = index, phase = phase, elapsed = elapsed, phaseElapsed = phaseElapsed,
            phaseRemaining = phase.seconds - phaseElapsed, focused = focused, isPaused = isPaused,
            isFinished = elapsed >= plan.totalSeconds,
        )
    }

    /** When the session ends if nothing changes; `null` while paused. */
    fun endDate(date: Instant): Instant? = if (isPaused) null else date.plusSeconds(plan.totalSeconds - elapsed(date))

    /**
     * The upcoming switches (into phase `index`, or the end when `index == phases.size`) with their dates, earliest
     * first, up to [limit]; none while paused. These are the notifications to schedule.
     */
    fun upcomingSwitches(date: Instant, limit: Int = MAX_ALERTS): List<FocusSwitch> {
        if (isPaused) return emptyList()
        val elapsed = elapsed(date)
        val starts = plan.offsets.map { it.toDouble() } + plan.totalSeconds.toDouble()
        return starts.withIndex()
            .filter { (index, start) -> index > 0 && start > elapsed }
            .map { (index, start) -> FocusSwitch(index, date.plusSeconds(start - elapsed)) }
            .take(maxOf(limit, 0))
    }

    fun pause(date: Instant): FocusRun = if (pausedAt != null) this else copy(pausedAt = date)

    fun resume(date: Instant): FocusRun {
        val pausedAt = pausedAt ?: return this
        return copy(pausedSeconds = pausedSeconds + maxOf(secondsBetween(pausedAt, date), 0.0), pausedAt = null)
    }

    /** Ends the current break now; nothing during focus. */
    fun skipBreak(date: Instant): FocusRun {
        val status = status(date)
        if (!status.phase.isBreak || status.isFinished) return this
        return copy(skippedSeconds = skippedSeconds + status.phaseRemaining)
    }

    companion object {
        /** Notifications kept for a focus session (see [Timetable.MAX_SCHEDULED_REMINDERS]). */
        const val MAX_ALERTS = 10
    }
}
