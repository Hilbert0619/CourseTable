/// When one class shows as a Live Activity (Dynamic Island, Lock Screen): from the reminder time,
/// `leadMinutes` before it starts, until it ends.
public struct ClassActivityWindow: Hashable, Identifiable, Sendable {
    public var session: ClassSession
    /// When the activity appears.
    public var showMoment: Moment

    public init(session: ClassSession, showMoment: Moment) {
        self.session = session
        self.showMoment = showMoment
    }

    /// Stable for a given course and day.
    public var id: String { session.id }

    /// Everything the activity displays. An activity whose signature differs from its class's window
    /// shows outdated details (the room or the time was edited) and is replaced.
    public var signature: String {
        let course = session.course
        return [session.id, course.name, course.room, course.teacher, "\(course.colorIndex)", "\(session.start)", "\(session.end)"]
            .joined(separator: "|")
    }

    /// The signature plus the time it appears: each key is created at most once, so an activity the
    /// person closed doesn't come back, while a new lead time schedules a new one.
    public var key: String { "\(signature)@\(showMoment.day) \(showMoment.time)" }

    public func isOpen(at moment: Moment) -> Bool {
        showMoment <= moment && moment < session.endMoment
    }
}

extension Timetable {
    /// Live Activities the app keeps at once, running and scheduled. iOS limits them per app.
    public static let maxClassActivities = 4

    /// The windows that haven't ended by `moment`, including one that is showing now, in the order
    /// they appear, up to `limit`.
    public func upcomingActivityWindows(after moment: Moment, leadMinutes: Int, limit: Int = maxClassActivities) -> [ClassActivityWindow] {
        let lead = max(leadMinutes, 0)
        var windows: [ClassActivityWindow] = []
        var day = max(moment.day, semester.firstMonday)
        while day <= semester.lastDay, windows.count < limit {
            for session in sessions(on: day) where moment < session.endMoment {
                windows.append(ClassActivityWindow(session: session, showMoment: session.startMoment.adding(minutes: -lead)))
            }
            day = day.adding(days: 1)
        }
        let ordered = windows.sorted { ($0.showMoment, $0.session.startMoment, $0.id) < ($1.showMoment, $1.session.startMoment, $1.id) }
        return Array(ordered.prefix(max(limit, 0)))
    }
}

/// A class Live Activity that exists now: showing, or scheduled to start (iOS 26).
public struct ExistingClassActivity: Hashable, Sendable {
    /// The system's identifier for the activity.
    public var id: String
    public var signature: String
    public var showMoment: Moment
    /// Scheduled but not started yet.
    public var isPending: Bool

    public init(id: String, signature: String, showMoment: Moment, isPending: Bool) {
        self.id = id
        self.signature = signature
        self.showMoment = showMoment
        self.isPending = isPending
    }

    /// Whether this activity stands for `window`: same details, and for one not started yet, the
    /// same time to appear.
    func matches(_ window: ClassActivityWindow) -> Bool {
        signature == window.signature && (!isPending || showMoment == window.showMoment)
    }
}

/// What to change so the Live Activities match the wanted windows.
public struct ClassActivityPlan: Hashable, Sendable {
    /// Activities to end now (`ExistingClassActivity.id`): finished, no longer wanted, outdated, or duplicates.
    public var end: [String]
    /// Windows open now that need an activity started.
    public var startNow: [ClassActivityWindow]
    /// Later windows to schedule (only where the system can start an activity on its own, iOS 26).
    public var schedule: [ClassActivityWindow]

    /// - Parameters:
    ///   - existing: the app's class activities, showing or scheduled.
    ///   - wanted: usually `Timetable.upcomingActivityWindows`; empty when the feature is off.
    ///   - created: keys (`ClassActivityWindow.key`) of every activity created before, so one that ended
    ///     early (closed by the person) isn't created again.
    ///   - canSchedule: whether activities can be scheduled to start later.
    public init(existing: [ExistingClassActivity], wanted: [ClassActivityWindow], created: Set<String>, now: Moment, canSchedule: Bool) {
        var end: [String] = []
        var covered: Set<String> = []
        for activity in existing {
            if let window = wanted.first(where: { activity.matches($0) && !covered.contains($0.id) }) {
                covered.insert(window.id)
            } else {
                end.append(activity.id)
            }
        }
        var startNow: [ClassActivityWindow] = []
        var schedule: [ClassActivityWindow] = []
        for window in wanted where !covered.contains(window.id) && !created.contains(window.key) && now < window.session.endMoment {
            if window.isOpen(at: now) {
                startNow.append(window)
            } else if canSchedule {
                schedule.append(window)
            }
        }
        self.end = end
        self.startNow = startNow
        self.schedule = schedule
    }
}
