/// When one class shows as a Live Activity (Dynamic Island, Lock Screen): from the reminder time,
/// `leadMinutes` before it starts, until it ends. If the class before it ends later than that, it
/// appears when that one ends instead, so the class in progress keeps the Dynamic Island until it's over.
public struct ClassActivityWindow: Hashable, Identifiable, Sendable {
    public var session: ClassSession
    /// When the activity appears.
    public var showMoment: Moment
    /// The unfinished to-dos due when the class ends ("After class: …").
    public var todos: [String]

    public init(session: ClassSession, showMoment: Moment, todos: [String] = []) {
        self.session = session
        self.showMoment = showMoment
        self.todos = todos
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

    /// Minutes from appearing to the start of class.
    public var leadMinutes: Int {
        (session.startMoment.day.daysSinceEpoch - showMoment.day.daysSinceEpoch) * 1440
            + session.start.minutes - showMoment.time.minutes
    }

    public func isOpen(at moment: Moment) -> Bool {
        showMoment <= moment && moment < session.endMoment
    }
}

extension Timetable {
    /// Live Activities the app keeps at once, running and scheduled. iOS limits them per app and per
    /// device, and scheduled ones count, so take only a few and leave room for other apps.
    public static let maxClassActivities = 4

    /// The windows that haven't ended by `moment`, including one that is showing now, in the order
    /// they appear, up to `limit`, each with its class's "after" to-dos from `todos`.
    public func upcomingActivityWindows(after moment: Moment, leadMinutes: Int, limit: Int = maxClassActivities,
                                        todos: [Todo] = []) -> [ClassActivityWindow] {
        let lead = max(leadMinutes, 0)
        var windows: [ClassActivityWindow] = []
        var day = max(moment.day, semester.firstMonday)
        while day <= semester.lastDay, windows.count < limit {
            // Finished classes count too: they decide when the next one appears.
            let daySessions = sessions(on: day)
            for session in daySessions where moment < session.endMoment {
                var show = session.startMoment.adding(minutes: -lead)
                let previousEnd = daySessions.map(\.endMoment).filter { $0 <= session.startMoment }.max()
                if let previousEnd, previousEnd > show {
                    show = previousEnd
                }
                let after = self.todos(after: session, in: todos).map(\.title)
                windows.append(ClassActivityWindow(session: session, showMoment: show, todos: after))
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
    /// The to-dos it shows.
    public var todos: [String]

    public init(id: String, signature: String, showMoment: Moment, isPending: Bool, todos: [String] = []) {
        self.id = id
        self.signature = signature
        self.showMoment = showMoment
        self.isPending = isPending
        self.todos = todos
    }

    /// Whether this activity stands for `window`: same details, and for one not started yet, the
    /// same time to appear and the same to-dos (a showing one gets its to-dos updated instead).
    func matches(_ window: ClassActivityWindow) -> Bool {
        signature == window.signature && (!isPending || (showMoment == window.showMoment && todos == window.todos))
    }
}

/// An activity that stays but shows other to-dos now.
public struct ClassActivityUpdate: Hashable, Sendable {
    /// `ExistingClassActivity.id`
    public var id: String
    public var window: ClassActivityWindow
}

/// What to change so the Live Activities match the wanted windows.
///
/// Each class gets one activity at most: the app remembers the signature of every activity it
/// created (`created`) until the class is over, so one the person closed doesn't come back. Only an
/// activity that never showed is forgotten (`forget`), so its class gets one again: a scheduled one
/// ended here (e.g. for a new reminder time), and a scheduled one that is gone although nobody saw it
/// (`scheduled`): iOS didn't start it, or dropped it before it started.
public struct ClassActivityPlan: Hashable, Sendable {
    /// Activities to end now (`ExistingClassActivity.id`): finished, no longer wanted, outdated, or duplicates.
    public var end: [String]
    /// Signatures to remove from the created ones: scheduled activities ended before they started, or gone
    /// without being seen.
    public var forget: Set<String>
    /// Scheduled signatures whose activity is showing now: from now on they count as seen, so the app doesn't
    /// start them again once they're gone (closed, or ended by iOS).
    public var seen: Set<String>
    /// Showing activities whose to-dos changed.
    public var update: [ClassActivityUpdate]
    /// Classes (`ClassActivityWindow.id`) that keep an activity, or had one that showed. The app adds the
    /// ones it starts or schedules.
    public var covered: Set<String>
    /// Windows open now that need an activity started.
    public var startNow: [ClassActivityWindow]
    /// Later windows to schedule (only where the system can start an activity on its own, iOS 26).
    public var schedule: [ClassActivityWindow]

    /// - Parameters:
    ///   - existing: the app's class activities, showing or scheduled.
    ///   - wanted: usually `Timetable.upcomingActivityWindows`; empty when the feature is off.
    ///   - created: signatures (`ClassActivityWindow.signature`) of the activities created before.
    ///   - scheduled: those of `created` that were scheduled to start on their own and haven't been seen
    ///     showing; if one of them is gone, nobody saw it.
    ///   - canSchedule: whether activities can be scheduled to start later.
    public init(existing: [ExistingClassActivity], wanted: [ClassActivityWindow], created: Set<String>, scheduled: Set<String> = [],
                now: Moment, canSchedule: Bool) {
        var end: [String] = []
        var update: [ClassActivityUpdate] = []
        var endedPending: Set<String> = []
        var kept: Set<String> = []
        var covered: Set<String> = []
        for activity in existing {
            if let window = wanted.first(where: { activity.matches($0) && !covered.contains($0.id) }) {
                covered.insert(window.id)
                kept.insert(activity.signature)
                if activity.todos != window.todos {
                    update.append(ClassActivityUpdate(id: activity.id, window: window))
                }
            } else {
                end.append(activity.id)
                if activity.isPending {
                    endedPending.insert(activity.signature)
                }
            }
        }
        var forget = endedPending.subtracting(kept)
        let remembered = created.subtracting(forget)
        let seen = scheduled.intersection(existing.filter { !$0.isPending }.map(\.signature))
        var startNow: [ClassActivityWindow] = []
        var schedule: [ClassActivityWindow] = []
        for window in wanted where !covered.contains(window.id) && remembered.contains(window.signature) {
            if scheduled.contains(window.signature) && !seen.contains(window.signature) {
                // Scheduled, and gone without anyone seeing it: create it again.
                forget.insert(window.signature)
            } else {
                covered.insert(window.id)
            }
        }
        for window in wanted where !covered.contains(window.id) && now < window.session.endMoment {
            if window.isOpen(at: now) {
                startNow.append(window)
            } else if canSchedule {
                schedule.append(window)
            }
        }
        self.end = end
        self.forget = forget
        self.seen = seen
        self.update = update
        self.covered = covered
        self.startNow = startNow
        self.schedule = schedule
    }
}
