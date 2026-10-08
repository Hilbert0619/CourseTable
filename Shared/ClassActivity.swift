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

    /// `ClassActivityWindow.signature`: which class, and everything shown about it.
    var signature: String
    /// `ClassActivityWindow.key`, for the Close button.
    var key: String
    var courseName: String
    var room: String
    var teacher: String
    var colorIndex: Int
    /// When it appears: the reminder time.
    var showDate: Date
}

extension ClassActivityAttributes {
    init(_ window: ClassActivityWindow, calendar: Calendar = .current) {
        let course = window.session.course
        self.init(
            signature: window.signature, key: window.key, courseName: course.name, room: course.room,
            teacher: course.teacher, colorIndex: course.colorIndex, showDate: window.showMoment.date(calendar: calendar)
        )
    }

    /// Room and teacher, whichever are filled in, e.g. "A101 · Li".
    var details: String {
        [room, teacher].filter { !$0.isEmpty }.joined(separator: " · ")
    }
}

extension ClassActivityAttributes.ContentState {
    init(_ window: ClassActivityWindow, calendar: Calendar = .current) {
        self.init(start: window.session.startMoment.date(calendar: calendar), end: window.session.endMoment.date(calendar: calendar))
    }

    /// The content as shown: it becomes "stale" when the class starts, which switches the layout from
    /// the countdown to the class in progress.
    var content: ActivityContent<Self> {
        ActivityContent(state: self, staleDate: start)
    }
}

/// The Close button on a class Live Activity. The system runs it in the app, which ends the activity
/// (`ClassActivityCloser`); the app doesn't create one for that class again.
struct EndClassActivityIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Close"
    static let isDiscoverable = false

    @Parameter(title: "Class")
    var key: String

    init() {}

    init(key: String) {
        self.key = key
    }

    func perform() async throws -> some IntentResult {
        await ClassActivityCloser.close(key: key)
        return .result()
    }
}
