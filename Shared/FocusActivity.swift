import ActivityKit
import AppIntents
import CourseKit
import Foundation

/// A running focus session as a Live Activity (Dynamic Island, Lock Screen), like the system timer: the
/// phase, the time left in it, Pause/Resume and Stop. The app updates it at every switch while it runs
/// (playing a sound keeps it running); the stale date marks the end of the phase otherwise.
struct FocusActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        /// `FocusPhase.Kind` raw value: `focus`, `shortBreak` or `longBreak`.
        var phase: String
        var block: Int
        var blockCount: Int
        var phaseStart: Date
        /// When the phase ends; while paused, when it would end if resumed now.
        var phaseEnd: Date
        /// While paused, the time left in the phase (the timers stand still).
        var pausedRemaining: TimeInterval?
        var sessionEnd: Date

        var isPaused: Bool { pausedRemaining != nil }
        var isBreak: Bool { phase != FocusPhase.Kind.focus.rawValue }
    }

    var goal: String
}

extension FocusActivityAttributes.ContentState {
    /// Where `run` stands at `date`.
    init(_ run: FocusRun, at date: Date) {
        let status = run.status(at: date)
        let phaseEnd = date.addingTimeInterval(status.phaseRemaining)
        self.init(
            phase: status.phase.kind.rawValue, block: status.phase.block, blockCount: run.plan.blockCount,
            phaseStart: phaseEnd.addingTimeInterval(-TimeInterval(status.phase.seconds)), phaseEnd: phaseEnd,
            pausedRemaining: status.isPaused ? status.phaseRemaining : nil,
            sessionEnd: date.addingTimeInterval(TimeInterval(run.plan.totalSeconds) - status.elapsed)
        )
    }

    /// Stale when the phase ends (unless paused). Above the class activities, so it holds the Dynamic Island.
    var content: ActivityContent<Self> {
        ActivityContent(state: self, staleDate: isPaused ? nil : phaseEnd, relevanceScore: Date.now.timeIntervalSince1970 / 60 + 100_000)
    }
}

/// Pause or Resume on the focus Live Activity; runs in the app (`FocusControl`).
struct ToggleFocusPauseIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Pause or Resume Focus"
    static let isDiscoverable = false

    init() {}

    func perform() async throws -> some IntentResult {
        await FocusControl.togglePause()
        return .result()
    }
}

/// Stop on the focus Live Activity; runs in the app (`FocusControl`).
struct StopFocusIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Stop Focus"
    static let isDiscoverable = false

    init() {}

    func perform() async throws -> some IntentResult {
        await FocusControl.stop()
        return .result()
    }
}
