import ActivityKit
import BackgroundTasks
import Foundation
import OSLog
import CourseKit

/// Live Activity preferences, per device like the reminder settings.
enum LiveActivitySettings {
    static let enabledKey = "liveActivities.enabled"
    static let defaultEnabled = true
}

/// Each class as a Live Activity in the Dynamic Island and on the Lock Screen, from the reminder time
/// (`ReminderSettings.leadMinutesKey`) until it ends (`ClassActivityWidget` draws it).
///
/// On iOS 26 the next few classes are scheduled ahead, and the system starts each one on time even when
/// the app isn't running. Earlier versions can only start one while the app is open, so there a class's
/// activity appears when the app comes to the foreground during its window, e.g. by tapping the class
/// reminder. Without a server push an activity can't end itself: it has a Close button, the app ends
/// finished ones whenever it comes to the foreground or iOS gives it a background refresh, and iOS ends
/// any after eight hours.
enum ClassLiveActivities {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "LiveActivities")

    /// The background app refresh that ends activities after their class (listed in Info.plist).
    static let cleanupTaskID = "io.github.hilbert0619.CourseTable.classActivities"

    struct SyncRequest: Equatable, Sendable {
        var timetable: Timetable
        var enabled: Bool
        var leadMinutes: Int
        /// Only sync while the app is in the foreground.
        var isActive: Bool
    }

    /// Whether the system can start an activity at a later date on its own.
    static var canSchedule: Bool {
        if #available(iOS 26.0, *) {
            return true
        }
        return false
    }

    /// Ends, starts and schedules activities so they match the upcoming classes.
    static func sync(_ request: SyncRequest, now: Date = .now) async {
        guard request.isActive, !UITestLaunch.isActive else { return }
        // Let a burst of edits settle; a newer request cancels this one.
        try? await Task.sleep(for: .seconds(1))
        guard !Task.isCancelled else { return }

        var created = CreatedActivities.load()
        created.removeFinished(before: now)
        if !request.enabled {
            // Turning the switch on again shows the class in its window once more.
            created.removeAll()
        }
        let moment = Moment(now)
        let wanted = request.enabled && ActivityAuthorizationInfo().areActivitiesEnabled
            ? request.timetable.upcomingActivityWindows(after: moment, leadMinutes: request.leadMinutes)
            : []
        let activities = Activity<ClassActivityAttributes>.activities.filter(isLive)
        let existing = activities.map { activity in
            ExistingClassActivity(id: activity.id, signature: activity.attributes.signature,
                                  showMoment: Moment(activity.attributes.showDate), isPending: isPending(activity))
        }
        let plan = ClassActivityPlan(existing: existing, wanted: wanted, created: created.signatures, now: moment, canSchedule: canSchedule)

        for activity in activities where plan.end.contains(activity.id) {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        created.forget(plan.forget)
        for window in plan.startNow {
            do {
                _ = try Activity<ClassActivityAttributes>.request(
                    attributes: ClassActivityAttributes(window), content: ClassActivityAttributes.ContentState(window).content(now: now)
                )
                created.insert(window)
            } catch {
                logger.error("Starting the activity for \(window.id) failed: \(error.localizedDescription)")
                break
            }
        }
        if #available(iOS 26.0, *) {
            for window in plan.schedule {
                do {
                    _ = try Activity<ClassActivityAttributes>.request(
                        attributes: ClassActivityAttributes(window), content: ClassActivityAttributes.ContentState(window).content(now: now),
                        pushType: nil, style: .standard, alertConfiguration: alert(for: window), start: window.showMoment.date()
                    )
                    created.insert(window)
                } catch {
                    // Most likely the system's limit on activities: the rest are scheduled next time.
                    logger.error("Scheduling the activity for \(window.id) failed: \(error.localizedDescription)")
                    break
                }
            }
        }
        created.save()
        scheduleCleanup(now: now)
    }

    /// Ends the activities whose class is over. Runs as a background app refresh (`cleanupTaskID`)
    /// whenever iOS allows one after a class ends.
    static func endFinished(now: Date = .now) async {
        for activity in Activity<ClassActivityAttributes>.activities where isLive(activity) && activity.content.state.end <= now {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        scheduleCleanup(now: now)
    }

    /// Asks for a background refresh once the next class with an activity has ended. iOS decides when
    /// it runs, if at all (Background App Refresh can be off), so this only tidies up sooner.
    private static func scheduleCleanup(now: Date) {
        let ends = Activity<ClassActivityAttributes>.activities.filter(isLive).map(\.content.state.end).filter { $0 > now }
        guard let next = ends.min() else {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: cleanupTaskID)
            return
        }
        let request = BGAppRefreshTaskRequest(identifier: cleanupTaskID)
        request.earliestBeginDate = next
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            logger.error("Asking for a background refresh failed: \(error.localizedDescription)")
        }
    }

    /// Showing (counting down or in class) or, on iOS 26, scheduled.
    private static func isLive(_ activity: Activity<ClassActivityAttributes>) -> Bool {
        activity.activityState == .active || activity.activityState == .stale || isPending(activity)
    }

    private static func isPending(_ activity: Activity<ClassActivityAttributes>) -> Bool {
        if #available(iOS 26.0, *) {
            return activity.activityState == .pending
        }
        return false
    }

    /// What a scheduled activity says when it starts: the same text as the class reminder. iPhone shows
    /// the activity itself (expanded in the Dynamic Island) with the sound; Apple Watch shows this text.
    @available(iOS 26.0, *)
    private static func alert(for window: ClassActivityWindow) -> AlertConfiguration {
        AlertConfiguration(
            title: LocalizedStringResource(stringLiteral: window.session.course.name),
            body: LocalizedStringResource(stringLiteral: ClassReminders.body(for: window.session, leadMinutes: window.leadMinutes)),
            sound: .default
        )
    }

    /// For UI tests (`-liveActivityDemo before|during|switch`): one class activity, counting down to a
    /// class starting in a few minutes, showing one in progress, or starting in 45 seconds (to see the
    /// layout switch). Returns why it couldn't start, if it couldn't.
    static func startDemo(_ mode: String, now: Date = .now) async -> String? {
        for activity in Activity<ClassActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        let chinese = Locale.current.language.languageCode?.identifier == "zh"
        let start = switch mode {
        case "during": now.addingTimeInterval(-25 * 60)
        case "switch": now.addingTimeInterval(45)
        default: now.addingTimeInterval(9 * 60 + 30)
        }
        let end = start.addingTimeInterval(100 * 60)
        let attributes = ClassActivityAttributes(
            signature: "demo", courseName: chinese ? "高等数学" : "Advanced Mathematics", room: "A101",
            teacher: chinese ? "李老师" : "Li", colorIndex: 5, startTime: TimeOfDay(start).description,
            endTime: TimeOfDay(end).description, showDate: start.addingTimeInterval(-10 * 60)
        )
        let state = ClassActivityAttributes.ContentState(start: start, end: end)
        do {
            _ = try Activity<ClassActivityAttributes>.request(attributes: attributes, content: state.content(now: now))
            return nil
        } catch {
            logger.error("Starting the demo activity failed: \(error.localizedDescription)")
            return "\(error)"
        }
    }
}

/// Ends a class activity when its Close button is tapped (`EndClassActivityIntent`, run in the app).
/// Its signature stays in `CreatedActivities`, so the class doesn't get another activity.
enum ClassActivityCloser {
    static func close(signature: String) async {
        for activity in Activity<ClassActivityAttributes>.activities where activity.attributes.signature == signature {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }
}

/// Signatures of the activities the app has created (`ClassActivityWindow.signature`) with the end of
/// their class, so each class gets one activity at most; kept in `UserDefaults` until the class is over.
private struct CreatedActivities {
    private static let defaultsKey = "liveActivities.created"
    private var ends: [String: Double]

    static func load() -> CreatedActivities {
        CreatedActivities(ends: UserDefaults.standard.dictionary(forKey: defaultsKey) as? [String: Double] ?? [:])
    }

    func save() {
        UserDefaults.standard.set(ends, forKey: Self.defaultsKey)
    }

    var signatures: Set<String> { Set(ends.keys) }

    mutating func insert(_ window: ClassActivityWindow) {
        ends[window.signature] = window.session.endMoment.date().timeIntervalSinceReferenceDate
    }

    mutating func forget(_ signatures: Set<String>) {
        for signature in signatures {
            ends[signature] = nil
        }
    }

    mutating func removeFinished(before date: Date) {
        ends = ends.filter { $0.value > date.timeIntervalSinceReferenceDate }
    }

    mutating func removeAll() {
        ends = [:]
    }
}
