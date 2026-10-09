import Foundation

/// When a to-do is due.
public enum TodoTiming: Hashable, Codable, Sendable {
    /// Some time that day; no notification.
    case anytime
    /// At a time of its day.
    case at(TimeOfDay)
    /// When that course's class ends on its day.
    case afterClass(courseID: UUID)
}

/// Something to do on a given day, e.g. "Pick up the package" at 12:00 or after Calculus.
public struct Todo: Hashable, Identifiable, Codable, Sendable {
    public var id: UUID
    public var title: String
    public var day: LocalDate
    public var timing: TodoTiming
    public var isDone: Bool

    public init(id: UUID = UUID(), title: String, day: LocalDate, timing: TodoTiming = .anytime, isDone: Bool = false) {
        self.id = id
        self.title = title
        self.day = day
        self.timing = timing
        self.isDone = isDone
    }
}

/// A to-do with its time worked out from the timetable.
public struct ResolvedTodo: Hashable, Identifiable, Sendable {
    public var todo: Todo
    /// The class it follows, when that class meets on the to-do's day.
    public var session: ClassSession?
    /// When it's due: its time, or the end of its class. `nil` for "anytime" and for a class that doesn't meet
    /// that day (any more).
    public var due: Moment?

    public var id: UUID { todo.id }

    /// Tied to a class that doesn't meet on its day, e.g. after the timetable was edited.
    public var isMissingItsClass: Bool {
        if case .afterClass = todo.timing { return session == nil }
        return false
    }
}

/// One day's classes and to-dos, in the order they happen.
public struct DayAgenda: Hashable, Sendable {
    public enum Item: Hashable, Identifiable, Sendable {
        /// A class, with the to-dos due when it ends.
        case session(ClassSession, after: [ResolvedTodo])
        /// A to-do at a time.
        case todo(ResolvedTodo)

        public var id: String {
            switch self {
            case .session(let session, _): "session-\(session.id)"
            case .todo(let todo): "todo-\(todo.id.uuidString)"
            }
        }
    }

    public var day: LocalDate
    /// To-dos without a time, and those whose class doesn't meet that day.
    public var anytime: [ResolvedTodo]
    /// Classes and timed to-dos by time; at the same time, a to-do comes before a class.
    public var items: [Item]

    public var isEmpty: Bool { anytime.isEmpty && items.isEmpty }
}

extension Timetable {
    /// Notifications kept for to-dos (iOS keeps 64 per app: classes 40, to-dos 12, focus 10, 2 spare).
    public static let maxTodoAlerts = 12

    public func resolve(_ todo: Todo) -> ResolvedTodo {
        switch todo.timing {
        case .anytime:
            return ResolvedTodo(todo: todo, session: nil, due: nil)
        case .at(let time):
            return ResolvedTodo(todo: todo, session: nil, due: Moment(day: todo.day, time: time))
        case .afterClass(let courseID):
            let session = sessions(on: todo.day).first { $0.course.id == courseID }
            return ResolvedTodo(todo: todo, session: session, due: session?.endMoment)
        }
    }

    /// The classes of `day` with its to-dos (`todos` may hold other days too; they keep their order).
    public func agenda(on day: LocalDate, todos: [Todo]) -> DayAgenda {
        let resolved = todos.filter { $0.day == day }.map(resolve)
        let anytime = resolved.filter { $0.due == nil }
        var entries: [(time: TimeOfDay, rank: Int, item: DayAgenda.Item)] = []
        for (index, todo) in resolved.enumerated() {
            if let due = todo.due, todo.session == nil {
                entries.append((due.time, index, .todo(todo)))
            }
        }
        for (index, session) in sessions(on: day).enumerated() {
            let after = resolved.filter { $0.session?.id == session.id }
            entries.append((session.start, resolved.count + index, .session(session, after: after)))
        }
        let items = entries.sorted { ($0.time, $0.rank) < ($1.time, $1.rank) }.map(\.item)
        return DayAgenda(day: day, anytime: anytime, items: items)
    }

    /// The unfinished to-dos due after `moment`, earliest first, up to `limit`: the ones to notify.
    public func upcomingTodoAlerts(after moment: Moment, todos: [Todo], limit: Int = maxTodoAlerts) -> [ResolvedTodo] {
        let due = todos.filter { !$0.isDone }.map(resolve).filter { ($0.due ?? moment) > moment }
        let ordered = due.enumerated().sorted { ($0.element.due!, $0.offset) < ($1.element.due!, $1.offset) }.map(\.element)
        return Array(ordered.prefix(max(limit, 0)))
    }

    /// The unfinished to-dos due when `session` ends, in their order.
    public func todos(after session: ClassSession, in todos: [Todo]) -> [Todo] {
        todos.filter { todo in
            guard !todo.isDone, todo.day == session.day, case .afterClass(let courseID) = todo.timing else { return false }
            return courseID == session.course.id
        }
    }
}
