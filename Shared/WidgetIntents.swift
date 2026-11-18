import ActivityKit
import AppIntents
import Foundation

/// The To-Dos widget's checkbox. As a Live Activity intent it runs in the app (`TodoControl`), which owns the
/// to-dos, then writes a new widget snapshot.
struct ToggleTodoIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Tick Off To-Do"
    static let isDiscoverable = false

    @Parameter(title: "To-Do")
    var id: String

    @Parameter(title: "Done")
    var isDone: Bool

    init() {}

    init(id: UUID, isDone: Bool) {
        self.id = id.uuidString
        self.isDone = isDone
    }

    func perform() async throws -> some IntentResult {
        if let id = UUID(uuidString: id) {
            await TodoControl.setDone(id, isDone)
        }
        return .result()
    }
}

/// The Focus widget's Start button: a 25-minute session, with its Live Activity; runs in the app (`FocusControl`).
struct StartFocusIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Start Focus"
    static let isDiscoverable = false
    static let minutes = 25

    init() {}

    func perform() async throws -> some IntentResult {
        await FocusControl.start(minutes: Self.minutes)
        return .result()
    }
}
