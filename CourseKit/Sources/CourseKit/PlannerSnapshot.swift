import Foundation

/// The to-dos and focus time the app writes for the To-Dos and Focus widgets, beside `ScheduleSnapshot`
/// (a file of its own, since the planner and the timetable change at different times).
public struct PlannerSnapshot: Hashable, Codable, Sendable {
    public static let currentVersion = 1
    /// To-dos kept from this many days before today, so a widget left alone over a few days still has them.
    public static let todoDaysBack = 1
    /// Focus days kept, enough for any streak a widget shows.
    public static let focusDaysBack = 400

    public var version: Int
    /// To-dos from yesterday on, in the app's order (oldest first).
    public var todos: [Todo]
    /// Focused time per day (days without focus left out), oldest first.
    public var focusDays: [FocusDay]
    /// The focus session in progress, not yet in `focusDays`.
    public var run: FocusRun?

    public init(todos: [Todo], focusDays: [FocusDay], run: FocusRun?) {
        self.version = Self.currentVersion
        self.todos = todos
        self.focusDays = focusDays
        self.run = run
    }

    /// What the app has, trimmed to what the widgets need on `today`.
    public init(todos: [Todo], focusSessions: [(startedAt: Date, focusedSeconds: Int)], run: FocusRun?, today: LocalDate,
                calendar: Calendar = .current) {
        let firstTodoDay = today.adding(days: -Self.todoDaysBack)
        let firstFocusDay = today.adding(days: -Self.focusDaysBack)
        var seconds: [LocalDate: Int] = [:]
        for session in focusSessions where session.focusedSeconds > 0 {
            let day = LocalDate(session.startedAt, calendar: calendar)
            if day >= firstFocusDay { seconds[day, default: 0] += session.focusedSeconds }
        }
        self.init(
            todos: todos.filter { $0.day >= firstTodoDay },
            focusDays: seconds.map { FocusDay(day: $0.key, seconds: $0.value) }.sorted { $0.day < $1.day },
            run: run
        )
    }

    public func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(self)
    }

    public static func decode(from data: Data) throws -> PlannerSnapshot {
        let snapshot = try JSONDecoder().decode(PlannerSnapshot.self, from: data)
        guard snapshot.version <= currentVersion else {
            throw SnapshotError.unsupportedVersion(snapshot.version)
        }
        return snapshot
    }
}

/// Focused time on one day.
public struct FocusDay: Hashable, Codable, Sendable {
    public var day: LocalDate
    public var seconds: Int

    public init(day: LocalDate, seconds: Int) {
        self.day = day
        self.seconds = seconds
    }
}

// MARK: - To-dos

/// One row of the To-Dos widget.
public struct TodoWidgetItem: Hashable, Identifiable, Sendable {
    public var todo: Todo
    /// The class it follows, when that class meets on its day.
    public var session: ClassSession?
    /// Its time, or the end of its class; `nil` for "anytime".
    public var due: Moment?
    /// Not done and its time has passed.
    public var isOverdue: Bool

    public var id: UUID { todo.id }
}

/// A day's to-dos as the widget lists them: the unfinished ones by time (those without a time last), then
/// the done ones in the same order.
public struct TodoWidgetList: Hashable, Sendable {
    public var day: LocalDate
    public var items: [TodoWidgetItem]

    public var remaining: Int { items.filter { !$0.todo.isDone }.count }
    public var done: Int { items.filter(\.todo.isDone).count }
    public var isEmpty: Bool { items.isEmpty }
}

extension PlannerSnapshot {
    /// The to-dos of `now`'s day. Without a timetable, to-dos after a class have no time.
    public func todoList(at now: Moment, timetable: Timetable?) -> TodoWidgetList {
        let resolved = todos.filter { $0.day == now.day }.enumerated().map { index, todo in
            let item = timetable?.resolve(todo) ?? ResolvedTodo(todo: todo, session: nil, due: Self.ownTime(of: todo))
            return (index: index, item: TodoWidgetItem(
                todo: todo, session: item.session, due: item.due,
                isOverdue: !todo.isDone && (item.due.map { $0 <= now } ?? false)
            ))
        }
        let ordered = resolved.sorted { a, b in
            let aKey = (a.item.todo.isDone ? 1 : 0, a.item.due == nil ? 1 : 0)
            let bKey = (b.item.todo.isDone ? 1 : 0, b.item.due == nil ? 1 : 0)
            if aKey != bKey { return aKey < bKey }
            if let aDue = a.item.due, let bDue = b.item.due, aDue != bDue { return aDue < bDue }
            return a.index < b.index
        }
        return TodoWidgetList(day: now.day, items: ordered.map(\.item))
    }

    /// When the To-Dos widget changes on its own: now, each unfinished to-do's time later that day (it turns
    /// overdue), and the next midnight (a new day's list).
    public func todoTimelineMoments(from now: Moment, timetable: Timetable?) -> [Moment] {
        let dues = todoList(at: now, timetable: timetable).items
            .filter { !$0.todo.isDone }
            .compactMap(\.due)
            .filter { $0 > now && $0.day == now.day }
        let midnight = Moment(day: now.day.adding(days: 1), time: TimeOfDay(hour: 0, minute: 0))
        return [now] + Array(Set(dues)).sorted() + [midnight]
    }

    private static func ownTime(of todo: Todo) -> Moment? {
        if case .at(let time) = todo.timing { return Moment(day: todo.day, time: time) }
        return nil
    }
}

// MARK: - Focus

/// What the Focus widget shows at a moment.
public struct FocusWidgetState: Hashable, Sendable {
    /// Focused today, the session in progress included.
    public var todaySeconds: Int
    /// Days in a row with focus up to today (or up to yesterday, while today has none yet).
    public var streakDays: Int
    /// The session in progress and where it stands; `nil` when none runs (or it has finished).
    public var run: FocusRun?
    public var status: FocusStatus?
}

extension PlannerSnapshot {
    /// A day counts towards the streak with at least this much focus.
    public static let streakMinimumSeconds = 60

    public func focusState(at date: Date, calendar: Calendar = .current) -> FocusWidgetState {
        let today = LocalDate(date, calendar: calendar)
        var seconds: [LocalDate: Int] = [:]
        for day in focusDays { seconds[day.day, default: 0] += day.seconds }
        var running: (FocusRun, FocusStatus)?
        if let run {
            let status = run.status(at: date)
            seconds[LocalDate(run.startedAt, calendar: calendar), default: 0] += Int(status.focused)
            if !status.isFinished { running = (run, status) }
        }
        let counts = { (day: LocalDate) in (seconds[day] ?? 0) >= Self.streakMinimumSeconds }
        var day = counts(today) ? today : today.adding(days: -1)
        var streak = 0
        while counts(day) {
            streak += 1
            day = day.adding(days: -1)
        }
        return FocusWidgetState(todaySeconds: seconds[today] ?? 0, streakDays: streak, run: running?.0, status: running?.1)
    }

    /// When the Focus widget changes on its own: now, each switch between focus and break and the end of the
    /// session in progress (unless it's paused), and the next midnight (today's time starts again).
    public func focusTimelineDates(from now: Date, calendar: Calendar = .current) -> [Date] {
        var dates = [now]
        if let run, !run.isPaused, !run.status(at: now).isFinished {
            // The switches end with the end of the session.
            dates += run.upcomingSwitches(at: now, limit: 50).map(\.date)
        }
        let tomorrow = LocalDate(now, calendar: calendar).adding(days: 1)
        dates.append(tomorrow.date(at: TimeOfDay(hour: 0, minute: 0), calendar: calendar))
        return Array(Set(dates)).sorted()
    }
}
