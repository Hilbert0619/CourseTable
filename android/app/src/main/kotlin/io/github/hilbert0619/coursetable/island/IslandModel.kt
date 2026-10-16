package io.github.hilbert0619.coursetable.island

import io.github.hilbert0619.coursetable.alerts.IslandCountdown
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** The StudyHour notifications the island shows. */
enum class IslandKind { FOCUS, CLASS, TEST }

/** One countdown in the island, read from a StudyHour notification that is showing ([IslandSource]). */
data class IslandItem(
    /** The notification's id and tag, e.g. "1:class:…" or "10:". */
    val key: String,
    val kind: IslandKind,
    /** "Calculus", "Focus · Block 2 of 5". */
    val title: String,
    /** "08:00–09:40 · A101 · Li", "Calculus review · ends at 21:30". */
    val text: String,
    /** What it counts down to, or `null` while a focus session is paused. */
    val target: Instant?,
    /** A class in progress (counting down to its end, not its start). */
    val started: Boolean = false,
    /** The notification's color (ARGB): the course's, or focus blue / break green. */
    val color: Int,
)

/**
 * What the island shows, decided from the countdowns showing at one moment (pure, so it's tested on the JVM). The
 * pill shows the first one ([order]) as minutes left, like the brands' islands; the card shows all of them.
 */
object IslandPlanner {
    /**
     * A running focus session first (the person started it), then classes and the test by the soonest end, then a
     * paused session.
     */
    fun order(items: List<IslandItem>): List<IslandItem> =
        items.sortedWith(compareBy<IslandItem>({ rank(it) }, { it.target ?: Instant.MAX }, { it.key }))

    private fun rank(item: IslandItem): Int = when {
        item.target == null -> 2
        item.kind == IslandKind.FOCUS -> 0
        else -> 1
    }

    /** The pill's text: "25 min" ([minutes] formats the number), or [paused]. */
    fun compactText(item: IslandItem, now: Instant, minutes: (Int) -> String, paused: String): String {
        val target = item.target ?: return paused
        return minutes(IslandCountdown.minutesLeft(target, now).toInt())
    }

    /** The time left for the card: "24:59", "1:05:00" (seconds rounded up, as the notification's countdown). */
    fun clock(target: Instant, now: Instant): String {
        val millis = Duration.between(now, target).toMillis()
        val seconds = if (millis <= 0) 0 else (millis + 999) / 1000
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val rest = seconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, rest)
        }
    }

    /**
     * When the pill's text next changes: the next whole minute of a countdown, or its end in the last minute. `null`
     * when nothing counts down.
     */
    fun nextChange(items: List<IslandItem>, now: Instant): Instant? =
        items.mapNotNull { item ->
            val target = item.target ?: return@mapNotNull null
            IslandCountdown.nextChange(target, now) ?: target.takeIf { it > now }
        }.minOrNull()

    /**
     * The countdowns and their phases, without the time: hiding the island (a swipe up) lasts until this changes, i.e.
     * a countdown starts or ends, a class begins, focus switches to a break, or a session is paused or resumed.
     */
    fun phase(items: List<IslandItem>): String =
        order(items).joinToString("|") { "${it.key}/${it.title}/${it.started}/${it.target == null}" }
}

/** A rectangle in pixels (`android.graphics.Rect` isn't usable in plain JVM tests). */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
}

/**
 * Where the island goes, in pixels from the top left of the screen: centered on the camera hole and as tall as it,
 * like the brands' islands, with [cameraWidth] left free in the middle for the hole. Without a camera hole near the
 * middle of the top edge (none, a notch in a corner, before Android 9), a pill centered in the status bar.
 */
data class IslandGeometry(
    val screenWidth: Int,
    val centerX: Int,
    val top: Int,
    val height: Int,
    /** The width kept free for the camera (0 without one). */
    val cameraWidth: Int,
    /** Where the camera ends: the card's content starts below it. */
    val cameraBottom: Int,
) {
    companion object {
        /** A hole whose middle is within this share of the width around the center counts as centered. */
        private const val CENTERED = 0.2

        fun of(screenWidth: Int, cutout: Bounds?, statusBarHeight: Int, density: Float): IslandGeometry {
            fun dp(value: Int) = (value * density).toInt()
            val minHeight = dp(28)
            val maxHeight = dp(40)
            val centered = cutout != null && cutout.top < dp(48) && cutout.height > 0 &&
                kotlin.math.abs(cutout.centerX - screenWidth / 2) <= screenWidth * CENTERED
            if (centered) {
                val height = cutout!!.height.coerceIn(minHeight, maxHeight)
                val top = (cutout.top + (cutout.height - height) / 2).coerceAtLeast(0)
                return IslandGeometry(screenWidth, cutout.centerX, top, height, cutout.width + dp(10), cutout.bottom)
            }
            val bar = if (statusBarHeight > 0) statusBarHeight else dp(24)
            val height = (bar - dp(4)).coerceIn(minHeight, maxHeight)
            val top = ((bar - height) / 2).coerceAtLeast(0)
            return IslandGeometry(screenWidth, screenWidth / 2, top, height, dp(8), top)
        }
    }
}
