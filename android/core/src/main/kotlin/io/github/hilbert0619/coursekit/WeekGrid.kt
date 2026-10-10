package io.github.hilbert0619.coursekit

import java.util.UUID

/**
 * Where a course block sits in the weekly grid.
 *
 * Clashing courses on the same day share the column side by side: [lane] is the block's position (0-based) and
 * [laneCount] how many lanes its group of overlapping blocks needs.
 */
data class GridPlacement(
    val course: Course,
    val lane: Int,
    val laneCount: Int,
    /** Whether the course meets in the week shown; `false` only when other weeks are included. */
    val isInWeek: Boolean = true,
) {
    val id: UUID get() = course.id
    val weekday: Weekday get() = course.weekday
    val periods: IntRange get() = course.periods
}

/**
 * Block placements for the weekly grid of teaching week [inWeek].
 *
 * With [includingOtherWeeks], every course that meets at some point in the term is laid out, and those not meeting
 * in [inWeek] have `isInWeek == false` (shown dimmed).
 */
fun Timetable.gridPlacements(inWeek: Int, includingOtherWeeks: Boolean = false): List<GridPlacement> {
    val shown = if (includingOtherWeeks) courses.filter { it.weeks.all.isNotEmpty() } else courses(inWeek)
    val byDay = shown.groupBy { it.weekday }
    return Weekday.entries.flatMap { layOut(byDay[it].orEmpty()) }
        .map { it.copy(isInWeek = it.course.meets(inWeek)) }
}

/** Greedy interval colouring, then each cluster of overlapping blocks shares one lane count. */
internal fun layOut(dayCourses: List<Course>): List<GridPlacement> {
    val sorted = dayCourses.sortedWith(Timetable.SLOT_ORDER)
    val placements = mutableListOf<GridPlacement>()
    val cluster = mutableListOf<GridPlacement>()
    val laneEnds = mutableListOf<Int>() // last period occupied in each lane of the current cluster
    var clusterEnd = Int.MIN_VALUE

    fun closeCluster() {
        val lanes = laneEnds.size
        placements += cluster.map { it.copy(laneCount = lanes) }
        cluster.clear()
        laneEnds.clear()
    }

    for (course in sorted) {
        if (course.startPeriod > clusterEnd) closeCluster()
        val lane = laneEnds.indexOfFirst { it < course.startPeriod }.takeIf { it >= 0 } ?: laneEnds.size
        if (lane == laneEnds.size) laneEnds += course.endPeriod else laneEnds[lane] = course.endPeriod
        cluster += GridPlacement(course, lane, laneCount = 1)
        clusterEnd = maxOf(clusterEnd, course.endPeriod)
    }
    closeCluster()
    return placements
}
