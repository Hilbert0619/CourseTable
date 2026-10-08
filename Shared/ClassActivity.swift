import ActivityKit
import AppIntents
import CourseKit
import Foundation

/// One class as a Live Activity (Dynamic Island, Lock Screen): it appears at the reminder time, counts
/// down to the start, then shows the class in progress until it ends. Nothing changes after it starts,
/// so the content only carries the times; the layout switches at the start through the stale date.
struct ClassActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var start: Date
        var end: Date
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
        self.init(start: window.session.startMoment.date(calendar: calendar), end: window.session.endMoment.date(calendar: calendar))
    }

    /// The content as shown: it becomes "stale" when the class starts, which switches the layout from
    /// the countdown to the class in progress. A later class is more relevant, so once a class is over
    /// (its activity can't end itself), the next one takes the Dynamic Island.
    var content: ActivityContent<Self> {
        ActivityContent(state: self, staleDate: start, relevanceScore: start.timeIntervalSince1970 / 60)
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
