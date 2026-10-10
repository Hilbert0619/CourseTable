package io.github.hilbert0619.coursekit

import kotlinx.serialization.Serializable

/** Everything needed to answer "what's on when": the term, the bell schedule and the courses. */
@Serializable
data class Timetable(
    val semester: Semester,
    val periods: PeriodSchedule = PeriodSchedule.STANDARD,
    val courses: List<Course> = emptyList(),
) {
    /** Courses meeting in teaching week [week], ordered by day then period. */
    fun courses(inWeek: Int): List<Course> = courses.filter { it.meets(inWeek) }.sortedWith(SLOT_ORDER)

    /** Courses meeting on [weekday] of teaching week [inWeek], ordered by period. */
    fun courses(on: Weekday, inWeek: Int): List<Course> = courses(inWeek).filter { it.weekday == on }

    /** Other courses that would occupy the same slot as [course] in some week. */
    fun clashes(course: Course): List<Course> = courses.filter { course.clashes(it) }.sortedWith(SLOT_ORDER)

    fun issues(course: Course): List<CourseIssue> = course.issues(periods.count, semester.totalWeeks)

    val hasWeekendCourses: Boolean get() = courses.any { it.weekday.isWeekend }

    /** Classes on a calendar day, with concrete times. Empty outside the term. */
    fun sessions(on: LocalDate): List<ClassSession> {
        val status = semester.status(on)
        if (status !is SemesterStatus.InProgress) return emptyList()
        return courses(on.weekday, status.week).mapNotNull { course ->
            val span = periods.timeSpan(course.periods) ?: return@mapNotNull null
            ClassSession(course, on, status.week, span.start, span.end)
        }
    }

    fun daySchedule(on: LocalDate): DaySchedule = DaySchedule(on, semester.status(on), sessions(on))

    /** The class in progress at [at], or else the next one to start, looking up to [searchingDays] ahead. */
    fun currentOrNextSession(at: Moment, searchingDays: Int = 14): ClassSession? {
        for (offset in 0..maxOf(searchingDays, 0)) {
            val daySessions = sessions(at.day.adding(offset))
            val candidate = if (offset == 0) daySessions.firstOrNull { it.end > at.time } else daySessions.firstOrNull()
            if (candidate != null) return candidate
        }
        return null
    }

    /** The class in progress at [at] (if any) and the one after it. */
    fun currentAndNext(at: Moment, searchingDays: Int = 14): CurrentAndNext {
        val current = sessions(at.day).firstOrNull { it.status(at) == SessionStatus.IN_PROGRESS }
        val next = currentOrNextSession(current?.endMoment ?: at, searchingDays)
        return CurrentAndNext(current, next)
    }

    /** Moments on [day] when "current / next class" changes: every class start and end, plus the next midnight. */
    fun refreshMoments(on: LocalDate): List<Moment> {
        val times = sessions(on).flatMap { listOf(it.start, it.end) }.toSortedSet()
        return times.map { Moment(on, it) } + Moment(on.adding(1), TimeOfDay.MIDNIGHT)
    }

    /** Widget timeline points: [from] itself, then each later change today, ending at midnight. */
    fun timelineMoments(from: Moment): List<Moment> = listOf(from) + refreshMoments(from.day).filter { it > from }

    companion object {
        /**
         * How many class reminders to keep scheduled. iOS keeps at most 64 pending notifications per app: classes 40,
         * to-dos 12 ([MAX_TODO_ALERTS]), focus 10 ([FocusRun.MAX_ALERTS]), 2 spare. Android keeps the same numbers.
         */
        const val MAX_SCHEDULED_REMINDERS = 40

        /** Notifications kept for to-dos. */
        const val MAX_TODO_ALERTS = 12

        /**
         * Live class notifications kept at once, showing and scheduled (iOS limits Live Activities per app and per
         * device, and scheduled ones count).
         */
        const val MAX_CLASS_ACTIVITIES = 4

        /** By day, then first period, last period and name. */
        val SLOT_ORDER: Comparator<Course> =
            compareBy<Course>({ it.weekday.rawValue }, { it.startPeriod }, { it.endPeriod }, { it.name })
    }
}

/** One concrete meeting of a course on a specific day. */
data class ClassSession(
    val course: Course,
    val day: LocalDate,
    val week: Int,
    val start: TimeOfDay,
    val end: TimeOfDay,
) {
    val id: String get() = "${course.id.uuidString}@$day"

    val startMoment: Moment get() = Moment(day, start)
    val endMoment: Moment get() = Moment(day, end)

    fun status(at: Moment): SessionStatus = when {
        at < startMoment -> SessionStatus.UPCOMING
        at < endMoment -> SessionStatus.IN_PROGRESS
        else -> SessionStatus.FINISHED
    }
}

enum class SessionStatus { UPCOMING, IN_PROGRESS, FINISHED }

data class CurrentAndNext(val current: ClassSession?, val next: ClassSession?)

/** The classes on one day, for the "Today" screen and the widget. */
data class DaySchedule(
    val day: LocalDate,
    val semesterStatus: SemesterStatus,
    val sessions: List<ClassSession>,
) {
    fun current(at: TimeOfDay): ClassSession? = sessions.firstOrNull { it.start <= at && at < it.end }

    /** The first class that hasn't started by [after]. */
    fun next(after: TimeOfDay): ClassSession? = sessions.firstOrNull { it.start > after }

    /** Classes not yet finished at [at]. */
    fun remaining(at: TimeOfDay): List<ClassSession> = sessions.filter { it.end > at }
}
