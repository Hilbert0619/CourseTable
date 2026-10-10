package io.github.hilbert0619.coursekit

/**
 * When one class shows as a live notification (the iOS Live Activity; an ongoing notification on Android): from the
 * reminder time, `leadMinutes` before it starts, until it ends. If the class before it ends later than that, it
 * appears when that one ends instead, so the class in progress keeps the spot until it's over.
 */
data class ClassActivityWindow(
    val session: ClassSession,
    /** When the activity appears. */
    val showMoment: Moment,
    /** The unfinished to-dos due when the class ends ("After class: …"). */
    val todos: List<String> = emptyList(),
) {
    /** Stable for a given course and day. */
    val id: String get() = session.id

    /**
     * Everything the activity displays. An activity whose signature differs from its class's window shows outdated
     * details (the room or the time was edited) and is replaced.
     */
    val signature: String
        get() {
            val course = session.course
            return listOf(session.id, course.name, course.room, course.teacher, "${course.colorIndex}", "${session.start}", "${session.end}")
                .joinToString("|")
        }

    /** Minutes from appearing to the start of class. */
    val leadMinutes: Int
        get() = (session.startMoment.day.daysSinceEpoch - showMoment.day.daysSinceEpoch) * 1440 +
            session.start.minutes - showMoment.time.minutes

    fun isOpen(at: Moment): Boolean = showMoment <= at && at < session.endMoment
}

/**
 * The windows that haven't ended by [after], including one that is showing now, in the order they appear, up to
 * [limit], each with its class's "after" to-dos from [todos].
 */
fun Timetable.upcomingActivityWindows(
    after: Moment,
    leadMinutes: Int,
    limit: Int = Timetable.MAX_CLASS_ACTIVITIES,
    todos: List<Todo> = emptyList(),
): List<ClassActivityWindow> {
    val lead = maxOf(leadMinutes, 0)
    val windows = mutableListOf<ClassActivityWindow>()
    var day = maxOf(after.day, semester.firstMonday)
    while (day <= semester.lastDay && windows.size < limit) {
        // Finished classes count too: they decide when the next one appears.
        val daySessions = sessions(day)
        for (session in daySessions) {
            if (after >= session.endMoment) continue
            var show = session.startMoment.adding(-lead)
            val previousEnd = daySessions.map { it.endMoment }.filter { it <= session.startMoment }.maxOrNull()
            if (previousEnd != null && previousEnd > show) show = previousEnd
            windows += ClassActivityWindow(session, show, todosAfter(session, todos).map { it.title })
        }
        day = day.adding(1)
    }
    return windows
        .sortedWith(compareBy<ClassActivityWindow>({ it.showMoment }, { it.session.startMoment }, { it.id }))
        .take(maxOf(limit, 0))
}

/** A live class notification that exists now: showing, or scheduled to start. */
data class ExistingClassActivity(
    /** The system's identifier for the activity. */
    val id: String,
    val signature: String,
    val showMoment: Moment,
    /** Scheduled but not started yet. */
    val isPending: Boolean,
    /** The to-dos it shows. */
    val todos: List<String> = emptyList(),
) {
    /**
     * Whether this activity stands for [window]: same details, and for one not started yet, the same time to appear
     * and the same to-dos (a showing one gets its to-dos updated instead).
     */
    internal fun matches(window: ClassActivityWindow): Boolean =
        signature == window.signature && (!isPending || (showMoment == window.showMoment && todos == window.todos))
}

/** An activity that stays but shows other to-dos now. */
data class ClassActivityUpdate(
    /** [ExistingClassActivity.id] */
    val id: String,
    val window: ClassActivityWindow,
)

/**
 * What to change so the live class notifications match the wanted windows.
 *
 * Each class gets one at most: the app remembers the signature of every one it created ([of]'s `created`) until the
 * class is over, so one the person closed doesn't come back. Only one that never showed (still scheduled) and is ended
 * here is forgotten ([forget]), so its class can be scheduled again, e.g. at a new reminder time.
 */
data class ClassActivityPlan(
    /** Activities to end now ([ExistingClassActivity.id]): finished, no longer wanted, outdated, or duplicates. */
    val end: List<String>,
    /** Signatures to remove from the created ones: scheduled activities ended before they started. */
    val forget: Set<String>,
    /** Showing activities whose to-dos changed. */
    val update: List<ClassActivityUpdate>,
    /** Classes ([ClassActivityWindow.id]) that keep an activity, or had one: no notification for them. */
    val covered: Set<String>,
    /** Windows open now that need an activity started. */
    val startNow: List<ClassActivityWindow>,
    /** Later windows to schedule (only where the system can start one on its own). */
    val schedule: List<ClassActivityWindow>,
) {
    companion object {
        /**
         * @param existing the app's class activities, showing or scheduled.
         * @param wanted usually [Timetable.upcomingActivityWindows]; empty when the feature is off.
         * @param created signatures ([ClassActivityWindow.signature]) of the activities created before.
         * @param canSchedule whether activities can be scheduled to start later.
         */
        fun of(
            existing: List<ExistingClassActivity>,
            wanted: List<ClassActivityWindow>,
            created: Set<String>,
            now: Moment,
            canSchedule: Boolean,
        ): ClassActivityPlan {
            val end = mutableListOf<String>()
            val update = mutableListOf<ClassActivityUpdate>()
            val endedPending = mutableSetOf<String>()
            val kept = mutableSetOf<String>()
            val covered = mutableSetOf<String>()
            for (activity in existing) {
                val window = wanted.firstOrNull { activity.matches(it) && it.id !in covered }
                if (window != null) {
                    covered += window.id
                    kept += activity.signature
                    if (activity.todos != window.todos) update += ClassActivityUpdate(activity.id, window)
                } else {
                    end += activity.id
                    if (activity.isPending) endedPending += activity.signature
                }
            }
            val forget = endedPending - kept
            val remembered = created - forget
            for (window in wanted) {
                if (window.id !in covered && window.signature in remembered) covered += window.id
            }
            val startNow = mutableListOf<ClassActivityWindow>()
            val schedule = mutableListOf<ClassActivityWindow>()
            for (window in wanted) {
                if (window.id in covered || now >= window.session.endMoment) continue
                if (window.isOpen(now)) {
                    startNow += window
                } else if (canSchedule) {
                    schedule += window
                }
            }
            return ClassActivityPlan(end, forget, update, covered, startNow, schedule)
        }
    }
}
