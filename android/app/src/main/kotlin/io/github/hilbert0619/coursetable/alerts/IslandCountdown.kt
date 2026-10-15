package io.github.hilbert0619.coursetable.alerts

import java.time.Duration
import java.time.Instant

/**
 * A countdown as whole minutes, for the islands that don't run the notification's chronometer ([LiveUpdates]): the
 * text shows "25 min" and the notification is posted again each time the number changes.
 */
object IslandCountdown {
    /** Minutes left until [target], rounded up ("1 min" in the last minute); 0 once it has passed. */
    fun minutesLeft(target: Instant, now: Instant): Long {
        val millis = Duration.between(now, target).toMillis()
        return if (millis <= 0) 0 else (millis + 59_999) / 60_000
    }

    /**
     * When [minutesLeft] next changes (always after [now]), or `null` in the last minute: at [target] the notification
     * switches or ends, and its own alarm posts it again.
     */
    fun nextChange(target: Instant, now: Instant): Instant? {
        val left = minutesLeft(target, now)
        return if (left <= 1) null else target.minusSeconds((left - 1) * 60)
    }
}
