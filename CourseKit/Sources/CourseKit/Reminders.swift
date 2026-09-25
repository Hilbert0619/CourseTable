/// A notification to deliver shortly before one class meeting.
public struct ClassReminder: Hashable, Identifiable, Sendable {
    public var session: ClassSession
    /// Minutes between the reminder and the start of class.
    public var leadMinutes: Int
    /// When the reminder goes off.
    public var fireMoment: Moment

    /// Stable for a given course and day, so rescheduling replaces a reminder instead of duplicating it.
    public var id: String { "class-\(session.id)" }
}

extension Timetable {
    /// iOS keeps at most 64 pending local notifications per app; leave a little room.
    public static let maxScheduledReminders = 60

    /// Reminders `leadMinutes` before each class after `moment`, earliest first, up to `limit`.
    /// Reminders whose time has already passed are left out, even if the class hasn't started.
    public func upcomingReminders(after moment: Moment, leadMinutes: Int, limit: Int = maxScheduledReminders) -> [ClassReminder] {
        let lead = max(leadMinutes, 0)
        var reminders: [ClassReminder] = []
        var day = max(moment.day, semester.firstMonday)
        while day <= semester.lastDay, reminders.count < limit {
            for session in sessions(on: day) {
                let fire = session.startMoment.adding(minutes: -lead)
                if fire > moment {
                    reminders.append(ClassReminder(session: session, leadMinutes: lead, fireMoment: fire))
                }
            }
            day = day.adding(days: 1)
        }
        return Array(reminders.sorted { $0.fireMoment < $1.fireMoment }.prefix(max(limit, 0)))
    }
}

extension Moment {
    /// The moment `minutes` later (earlier if negative), crossing midnight as needed.
    public func adding(minutes: Int) -> Moment {
        let total = day.daysSinceEpoch * 1440 + time.minutes + minutes
        return Moment(day: LocalDate(daysSinceEpoch: total.floorDiv(1440)), time: TimeOfDay(minutes: total.floorMod(1440)))
    }
}
