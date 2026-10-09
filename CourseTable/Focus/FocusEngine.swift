import ActivityKit
import Foundation
import Observation
import OSLog
import UserNotifications
import CourseKit

/// The focus timer (docs/todos-focus-spec.md): the setup the person last used, and the session in progress.
///
/// The run is a `CourseKit.FocusRun` saved in `UserDefaults`, so the app, the Live Activity's buttons
/// (`FocusControl`, possibly in a freshly launched app) and a relaunch all continue the same session. While
/// the app runs (in the foreground, or in the background while a sound plays) a ticker updates the Live
/// Activity at each switch between focus and break; notifications cover the switches when it doesn't.
@MainActor
@Observable
final class FocusEngine {
    static let shared = FocusEngine()

    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "Focus")
    private static let runKey = "focus.run"
    private static let settingsKey = "focus.settings"
    private static let goalKey = "focus.goal"
    private static let soundKey = "focus.sound"
    private static let volumeKey = "focus.volume"
    nonisolated static let notificationPrefix = "focus-"

    /// The session in progress, if any.
    private(set) var run: FocusRun?
    /// The last session that ended, for the "done" note on the Focus tab.
    private(set) var lastEnded: FocusSessionSummary?

    var settings: FocusSettings { didSet { save(settings, Self.settingsKey) } }
    var goal: String { didSet { UserDefaults.standard.set(goal, forKey: Self.goalKey) } }
    var sound: FocusSound {
        didSet {
            UserDefaults.standard.set(sound.rawValue, forKey: Self.soundKey)
            if run != nil || previewing { player.play(sound) }
        }
    }
    var volume: Double {
        didSet {
            UserDefaults.standard.set(volume, forKey: Self.volumeKey)
            player.volume = Float(volume)
        }
    }
    /// Playing the chosen sound on the setup screen.
    private(set) var previewing = false

    /// Where history goes (`CourseTableApp` sets it at launch).
    var planner: PlannerModel?

    private let player = SoundPlayer()
    @ObservationIgnored private var ticker: Task<Void, Never>?

    private init() {
        let defaults = UserDefaults.standard
        settings = Self.load(FocusSettings.self, Self.settingsKey) ?? FocusSettings()
        goal = defaults.string(forKey: Self.goalKey) ?? ""
        sound = defaults.string(forKey: Self.soundKey).flatMap { FocusSound(rawValue: $0) } ?? .rain
        volume = defaults.object(forKey: Self.volumeKey) as? Double ?? 0.6
        run = UITestLaunch.isActive ? nil : Self.load(FocusRun.self, Self.runKey)
        player.volume = Float(volume)
    }

    var plan: FocusPlan { FocusPlan(settings) }

    // MARK: - Session

    func start(now: Date = .now) {
        stopPreview()
        let run = FocusRun(goal: goal.trimmingCharacters(in: .whitespacesAndNewlines), plan: plan, startedAt: now)
        self.run = run
        persist()
        player.play(sound)
        Task {
            await FocusLiveActivity.start(run, now: now)
            await FocusReminders.sync(run, now: now)
        }
        startTicker()
        if !UITestLaunch.isActive {
            Task { _ = await ClassReminders.requestAuthorization() }
        }
    }

    func togglePause(now: Date = .now) {
        guard let run else { return }
        run.isPaused ? resume(now: now) : pause(now: now)
    }

    func pause(now: Date = .now) {
        guard var run, !run.isPaused else { return }
        run.pause(at: now)
        apply(run, now: now)
        player.stop()
    }

    func resume(now: Date = .now) {
        guard var run, run.isPaused else { return }
        run.resume(at: now)
        apply(run, now: now)
        player.play(sound)
    }

    func skipBreak(now: Date = .now) {
        guard var run else { return }
        run.skipBreak(at: now)
        apply(run, now: now)
    }

    /// Ends the session early; it's kept in the history as stopped.
    func stop(now: Date = .now) {
        guard let run else { return }
        finish(run, at: now, finished: false)
    }

    /// Catches up after time passed without the app running: ends a finished session, refreshes the rest.
    /// `RootView` calls it whenever the app comes to the foreground.
    func refresh(now: Date = .now) {
        guard let run else { return }
        if run.status(at: now).isFinished {
            finish(run, at: now, finished: true)
        } else {
            Task { await FocusLiveActivity.update(run, now: now) }
            startTicker()
        }
    }

    private func apply(_ run: FocusRun, now: Date) {
        self.run = run
        persist()
        Task {
            await FocusLiveActivity.update(run, now: now)
            await FocusReminders.sync(run, now: now)
        }
        startTicker()
    }

    private func finish(_ run: FocusRun, at date: Date, finished: Bool) {
        let status = run.status(at: date)
        let ended = finished ? run.startedAt.addingTimeInterval(TimeInterval(run.plan.totalSeconds) + run.pausedSeconds - run.skippedSeconds) : date
        planner?.recordFocus(
            id: run.id, goal: run.goal, startedAt: run.startedAt, endedAt: min(ended, date), focusedSeconds: Int(status.focused),
            plannedSeconds: run.plan.focusSeconds, finished: finished
        )
        lastEnded = FocusSessionSummary(id: run.id, goal: run.goal, startedAt: run.startedAt, endedAt: min(ended, date),
                                        focusedSeconds: Int(status.focused), finished: finished)
        self.run = nil
        persist()
        ticker?.cancel()
        ticker = nil
        player.stop()
        Task {
            await FocusLiveActivity.end()
            await ClassReminders.removePending(withPrefix: Self.notificationPrefix)
        }
    }

    /// Wakes up at each switch to update the Live Activity, and at the end to finish.
    private func startTicker() {
        ticker?.cancel()
        ticker = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, let run = self.run, !run.isPaused else { return }
                let now = Date.now
                guard let next = run.upcomingSwitches(at: now, limit: 1).first else {
                    self.refresh(now: now)
                    return
                }
                try? await Task.sleep(for: .seconds(max(next.date.timeIntervalSince(now), 0.5)))
                guard !Task.isCancelled else { return }
                if let current = self.run, current.status(at: .now).isFinished {
                    self.finish(current, at: .now, finished: true)
                    return
                }
                if let current = self.run {
                    await FocusLiveActivity.update(current, now: .now)
                }
            }
        }
    }

    // MARK: - Sound preview

    func togglePreview() {
        if previewing {
            stopPreview()
        } else {
            previewing = true
            player.play(sound)
        }
    }

    func stopPreview() {
        guard previewing else { return }
        previewing = false
        if run == nil { player.stop() }
    }

    // MARK: - Saving

    private func persist() {
        if let run {
            save(run, Self.runKey)
        } else {
            UserDefaults.standard.removeObject(forKey: Self.runKey)
        }
    }

    private func save<T: Encodable>(_ value: T, _ key: String) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    private static func load<T: Decodable>(_ type: T.Type, _ key: String) -> T? {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(type, from: $0) }
    }

    // MARK: - UI tests

    /// `-focusDemo`: a session that started 7 minutes ago, without sound.
    func startDemo() {
        goal = Locale.current.language.languageCode?.identifier == "zh" ? "高数复习" : "Calculus review"
        settings = FocusSettings(totalMinutes: 150)
        sound = .none
        start(now: Date(timeIntervalSinceNow: -7 * 60))
    }
}

/// What the focus Live Activity's buttons do, in the app.
enum FocusControl {
    static func togglePause() async {
        await MainActor.run { FocusEngine.shared.togglePause() }
    }

    static func stop() async {
        await MainActor.run { FocusEngine.shared.stop() }
    }
}

/// The focus session's Live Activity.
enum FocusLiveActivity {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "FocusActivity")

    static func start(_ run: FocusRun, now: Date) async {
        await end()
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        do {
            _ = try Activity<FocusActivityAttributes>.request(
                attributes: FocusActivityAttributes(goal: run.goal),
                content: FocusActivityAttributes.ContentState(run, at: now).content
            )
        } catch {
            logger.error("Starting the focus activity failed: \(error.localizedDescription)")
        }
    }

    static func update(_ run: FocusRun, now: Date) async {
        let content = FocusActivityAttributes.ContentState(run, at: now).content
        for activity in Activity<FocusActivityAttributes>.activities {
            await activity.update(content)
        }
    }

    static func end() async {
        for activity in Activity<FocusActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }
}

/// A notification at each switch between focus and break and at the end, so they come even when the app
/// isn't running; up to `FocusRun.maxAlerts`, renewed on every change.
enum FocusReminders {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "FocusReminders")

    static func sync(_ run: FocusRun, now: Date) async {
        await ClassReminders.removePending(withPrefix: FocusEngine.notificationPrefix)
        guard !UITestLaunch.isActive, await ClassReminders.isAllowed() else { return }
        let center = UNUserNotificationCenter.current()
        for change in run.upcomingSwitches(at: now) {
            let content = UNMutableNotificationContent()
            (content.title, content.body) = text(for: change.index, in: run)
            content.sound = .default
            content.threadIdentifier = "focus"
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(change.date.timeIntervalSince(now), 1), repeats: false)
            let id = "\(FocusEngine.notificationPrefix)\(run.id.uuidString)-\(change.index)"
            do {
                try await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
            } catch {
                logger.error("Scheduling focus notification \(id) failed: \(error.localizedDescription)")
            }
        }
    }

    /// Title and body for the switch into phase `index` (or the end, when `index == phases.count`).
    static func text(for index: Int, in run: FocusRun) -> (String, String) {
        let goal = run.goal
        guard index < run.plan.phases.count else {
            let focused = Duration.seconds(run.plan.focusSeconds).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated))
            return (String(localized: "Focus session done"), [focused, goal].filter { !$0.isEmpty }.joined(separator: " · "))
        }
        let phase = run.plan.phases[index]
        let length = Duration.seconds(phase.seconds).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated))
        switch phase.kind {
        case .focus:
            let block = String(localized: "Block \(phase.block) of \(run.plan.blockCount)")
            return (String(localized: "Back to focus"), [block, length, goal].filter { !$0.isEmpty }.joined(separator: " · "))
        case .shortBreak:
            return (String(localized: "Time for a break"), length)
        case .longBreak:
            return (String(localized: "Time for a long break"), length)
        }
    }
}
