package io.github.hilbert0619.coursekit

/** A notification to deliver shortly before one class meeting. */
data class ClassReminder(
    val session: ClassSession,
    /** Minutes between the reminder and the start of class. */
    val leadMinutes: Int,
    /** When the reminder goes off. */
    val fireMoment: Moment,
) {
    /** Stable for a given course and day, so rescheduling replaces a reminder instead of duplicating it. */
    val id: String get() = "class-${session.id}"
}

/**
 * Reminders [leadMinutes] before each class after [after], earliest first, up to [limit]. Reminders whose time has
 * already passed are left out, even if the class hasn't started, and so are the classes in [excluding]
 * ([ClassSession.id]: those with a live class notification).
 */
fun Timetable.upcomingReminders(
    after: Moment,
    leadMinutes: Int,
    limit: Int = Timetable.MAX_SCHEDULED_REMINDERS,
    excluding: Set<String> = emptySet(),
): List<ClassReminder> {
    val lead = maxOf(leadMinutes, 0)
    val reminders = mutableListOf<ClassReminder>()
    var day = maxOf(after.day, semester.firstMonday)
    while (day <= semester.lastDay && reminders.size < limit) {
        for (session in sessions(day)) {
            if (session.id in excluding) continue
            val fire = session.startMoment.adding(-lead)
            if (fire > after) reminders += ClassReminder(session, lead, fire)
        }
        day = day.adding(1)
    }
    return reminders.sortedBy { it.fireMoment }.take(maxOf(limit, 0))
}
