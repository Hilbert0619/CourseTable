import ActivityKit
import AppIntents
import CourseKit
import Foundation

/// One class as a Live Activity (Dynamic Island, Lock Screen): it appears at the reminder time, counts
/// down to the start, then shows the class in progress until it ends. The content carries the times (the
/// layout switches at the start through the stale date) and the to-dos due after the class, which the app
/// updates when they change.
struct ClassActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var start: Date
        var end: Date
        /// Unfinished to-dos due when the class ends ("After class: …").
        var todos: [String]

        init(start: Date, end: Date, todos: [String] = []) {
            self.start = start
            self.end = end
            self.todos = todos
        }

        private enum CodingKeys: String, CodingKey {
            case start, end, todos
        }

        /// Activities from 0.3.0 have no to-dos.
        init(from decoder: any Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            start = try container.decode(Date.self, forKey: .start)
            end = try container.decode(Date.self, forKey: .end)
            todos = try container.decodeIfPresent([String].self, forKey: .todos) ?? []
        }
    }

    /// `ClassActivityWindow.signature`: which class, and everything shown about it. The Close button
    /// passes it back.
    var signature: String
    var courseName: String
    var room: String
    var teacher: String
    var colorIndex: Int
    /// "08:00" and "09:40", written like everywhere else in the app.
    var startTime: String
    var endTime: String
    /// When it appears: the reminder time, or the end of the class before.
    var showDate: Date
}

extension ClassActivityAttributes {
    init(_ window: ClassActivityWindow, calendar: Calendar = .current) {
        let session = window.session
        self.init(
            signature: window.signature, courseName: session.course.name, room: session.course.room,
            teacher: session.course.teacher, colorIndex: session.course.colorIndex, startTime: session.start.description,
            endTime: session.end.description, showDate: window.showMoment.date(calendar: calendar)
        )
    }

    /// Room and teacher, whichever are filled in, e.g. "A101 · Li".
    var details: String {
        [room, teacher].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    /// e.g. "08:00–09:40"
    var times: String { "\(startTime)–\(endTime)" }
}

extension ClassActivityAttributes.ContentState {
    init(_ window: ClassActivityWindow, calendar: Calendar = .current) {
        self.init(start: window.session.startMoment.date(calendar: calendar), end: window.session.endMoment.date(calendar: calendar),
                  todos: window.todos)
    }

    /// "Hand in the paper · Return the book +1": the first two and how many more.
    var todoSummary: String? {
        guard !todos.isEmpty else { return nil }
        let shown = todos.prefix(2).joined(separator: " · ")
        return todos.count > 2 ? "\(shown) +\(todos.count - 2)" : shown
    }

    /// The content as shown: it becomes "stale" when the class starts, which switches the layout from
    /// the countdown to the class in progress. For a class that has already started there's no stale
    /// date (with one in the past, the activity didn't show in the Simulator): the view checks the time.
    /// A later class is more relevant, so once a class is over (its activity can't end itself), the
    /// next one takes the Dynamic Island.
    func content(now: Date = .now) -> ActivityContent<Self> {
        ActivityContent(state: self, staleDate: start > now ? start : nil, relevanceScore: start.timeIntervalSince1970 / 60)
    }
}

/// The Close button on a class Live Activity. The system runs it in the app, which ends the activity
/// (`ClassActivityCloser`); the app doesn't create one for that class again.
struct EndClassActivityIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Close"
    static let isDiscoverable = false

    /// `ClassActivityAttributes.signature`
    @Parameter(title: "Class")
    var signature: String

    init() {}

    init(signature: String) {
        self.signature = signature
    }

    func perform() async throws -> some IntentResult {
        await ClassActivityCloser.close(signature: signature)
        return .result()
    }
}
