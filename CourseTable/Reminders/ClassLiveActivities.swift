import ActivityKit
import BackgroundTasks
import Foundation
import OSLog
import UserNotifications
import CourseKit

/// Live Activity preferences, per device like the reminder settings.
enum LiveActivitySettings {
    static let enabledKey = "liveActivities.enabled"
    static let defaultEnabled = true
}

/// Each class as a Live Activity in the Dynamic Island and on the Lock Screen, from the reminder time
/// (`ReminderSettings.leadMinutesKey`) until it ends (`ClassActivityWidget` draws it).
///
/// On iOS 26 the next few classes are scheduled ahead, and the system starts each one at its time even when
/// the app isn't running, but not always (users saw none, 2026-10). So every class also gets its reminder
/// notification (`Alerts.sync`), and while that is on, a scheduled activity starts silently next to it. One
/// that is gone although nobody saw it is created again the next time the app is open. Earlier versions can
/// only start one while the app is open, so there a class's activity appears when the app comes to the
/// foreground during its window, e.g. by tapping the class reminder. Without a server push an activity can't
/// end itself: it has a Close button, the app ends finished ones whenever it comes to the foreground or iOS
/// gives it a background refresh, and iOS ends any after eight hours.
enum ClassLiveActivities {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "LiveActivities")

    /// The background app refresh that ends activities after their class (listed in Info.plist).
    static let cleanupTaskID = "io.github.hilbert0619.CourseTable.classActivities"
    /// A silent sound in the app bundle, for the alert of an activity that starts with a class reminder.
    static let silentSound = "Silence.wav"
    /// Signatures of the test activities from Settings → Reminders, which the sync leaves alone.
    private static let testPrefix = "test|"
    /// Not starting with `ClassReminders.idPrefix`: a sync replaces those.
    private static let testReminderID = "test-class-reminder"

    /// Whether the system can start an activity at a later date on its own.
    static var canSchedule: Bool {
        if #available(iOS 26.0, *) {
            return true
        }
        return false
    }

    /// Ends, starts, schedules and updates activities so they match the upcoming classes and their to-dos.
    /// `quietAlert`: a class reminder sounds at the same moment, so scheduled activities start silently.
    static func sync(timetable: Timetable, enabled: Bool, leadMinutes: Int, todos: [Todo], quietAlert: Bool, now: Date = .now) async {
        var created = CreatedActivities.load()
        created.removeFinished(before: now)
        if !enabled {
            // Turning the switch on again shows the class in its window once more.
            created.removeAll()
        }
        var ended: Set<String> = []
        if created.quietAlert != quietAlert {
            if created.quietAlert == nil {
                // From 0.7.0, which didn't keep track of what showed: whatever isn't there now may never have.
                let live = Set(classActivities().map(\.attributes.signature))
                created.scheduled.formUnion(created.signatures.subtracting(live))
            }
            // Scheduled activities keep the alert they were made with: replace them.
            for activity in classActivities() where isPending(activity) {
                await activity.end(nil, dismissalPolicy: .immediate)
                ended.insert(activity.id)
                created.forget([activity.attributes.signature])
            }
            created.quietAlert = quietAlert
        }
        let moment = Moment(now)
        let wanted = enabled && ActivityAuthorizationInfo().areActivitiesEnabled
            ? timetable.upcomingActivityWindows(after: moment, leadMinutes: leadMinutes, todos: todos)
            : []
        let activities = classActivities().filter { !ended.contains($0.id) }
        let existing = activities.map { activity in
            ExistingClassActivity(id: activity.id, signature: activity.attributes.signature, showMoment: Moment(activity.attributes.showDate),
                                  isPending: isPending(activity), todos: activity.content.state.todos)
        }
        let plan = ClassActivityPlan(existing: existing, wanted: wanted, created: created.signatures, scheduled: created.scheduled,
                                     now: moment, canSchedule: canSchedule)
        created.markSeen(plan.seen)

        for activity in activities where plan.end.contains(activity.id) {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        for change in plan.update {
            if let activity = activities.first(where: { $0.id == change.id }) {
                await activity.update(ClassActivityAttributes.ContentState(change.window).content(now: now))
            }
        }
        created.forget(plan.forget)
        for window in plan.startNow {
            do {
                _ = try Activity<ClassActivityAttributes>.request(
                    attributes: ClassActivityAttributes(window), content: ClassActivityAttributes.ContentState(window).content(now: now)
                )
                created.insert(window, scheduled: false)
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
                        pushType: nil, style: .standard,
                        alertConfiguration: alert(title: window.session.course.name,
                                                  body: ClassReminders.body(for: window.session, leadMinutes: window.leadMinutes),
                                                  quiet: quietAlert),
                        start: window.showMoment.date()
                    )
                    created.insert(window, scheduled: true)
                } catch {
                    // Most likely the system's limit on activities: the rest are scheduled next time (their
                    // reminders come anyway).
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

    /// Settings → Reminders → Test: a pretend class whose Live Activity iOS starts on its own in a minute, with its
    /// reminder when class reminders are on, just as a real class would get them. Returns when it appears.
    @available(iOS 26.0, *)
    static func scheduleTest(withReminder: Bool, now: Date = .now) async throws -> Date {
        for activity in Activity<ClassActivityAttributes>.activities where activity.attributes.signature.hasPrefix(testPrefix) {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        let show = now.addingTimeInterval(60)
        let start = show.addingTimeInterval(2 * 60)
        let end = start.addingTimeInterval(10 * 60)
        let name = String(localized: "Test Class")
        let startTime = TimeOfDay(start).description
        let endTime = TimeOfDay(end).description
        let body = [String(localized: "Starts in \(2) min"), "\(startTime)–\(endTime)", "A101"].joined(separator: " · ")
        let attributes = ClassActivityAttributes(
            signature: testPrefix + UUID().uuidString, courseName: name, room: "A101", teacher: "", colorIndex: 2,
            startTime: startTime, endTime: endTime, showDate: show
        )
        let state = ClassActivityAttributes.ContentState(start: start, end: end)
        _ = try Activity<ClassActivityAttributes>.request(
            attributes: attributes, content: state.content(now: now), pushType: nil, style: .standard,
            alertConfiguration: alert(title: name, body: body, quiet: withReminder), start: show
        )
        if withReminder {
            let content = UNMutableNotificationContent()
            content.title = name
            content.body = body
            content.sound = .default
            content.threadIdentifier = "class-reminders"
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: show.timeIntervalSince(now), repeats: false)
            try await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: testReminderID, content: content, trigger: trigger))
        }
        scheduleCleanup(now: now)
        return show
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

    /// The live activities for classes: not the tests from Settings, which end by themselves after their class.
    private static func classActivities() -> [Activity<ClassActivityAttributes>] {
        Activity<ClassActivityAttributes>.activities.filter { isLive($0) && !$0.attributes.signature.hasPrefix(testPrefix) }
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
    /// the activity itself (expanded in the Dynamic Island); Apple Watch shows this text. `quiet` when the class
    /// reminder sounds at the same moment, so there's one sound.
    @available(iOS 26.0, *)
    private static func alert(title: String, body: String, quiet: Bool) -> AlertConfiguration {
        AlertConfiguration(
            title: LocalizedStringResource(stringLiteral: title),
            body: LocalizedStringResource(stringLiteral: body),
            sound: quiet ? .named(silentSound) : .default
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
        let state = ClassActivityAttributes.ContentState(start: start, end: end, todos: [chinese ? "交论文" : "Hand in the paper"])
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
/// Its signature stays in `CreatedActivities`, now as seen, so the class doesn't get another activity.
enum ClassActivityCloser {
    static func close(signature: String) async {
        for activity in Activity<ClassActivityAttributes>.activities where activity.attributes.signature == signature {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        var created = CreatedActivities.load()
        created.markSeen([signature])
        created.save()
    }
}

/// Signatures of the activities the app has created (`ClassActivityWindow.signature`) with the end of
/// their class, so each class gets one activity at most; kept in `UserDefaults` until the class is over.
/// Those scheduled to start on their own stay in `scheduled` until the app sees them showing: if one of
/// them is gone before that, iOS never showed it, and the class gets it again.
private struct CreatedActivities {
    private static let defaultsKey = "liveActivities.created"
    private static let scheduledKey = "liveActivities.scheduled"
    private static let quietAlertKey = "liveActivities.quietAlert"
    private var ends: [String: Double]
    var scheduled: Set<String>
    /// Whether the scheduled activities start silently (a class reminder comes with them); `nil` before 0.7.1.
    var quietAlert: Bool?

    static func load() -> CreatedActivities {
        let defaults = UserDefaults.standard
        return CreatedActivities(
            ends: defaults.dictionary(forKey: defaultsKey) as? [String: Double] ?? [:],
            scheduled: Set(defaults.stringArray(forKey: scheduledKey) ?? []),
            quietAlert: defaults.object(forKey: quietAlertKey) as? Bool
        )
    }

    func save() {
        let defaults = UserDefaults.standard
        defaults.set(ends, forKey: Self.defaultsKey)
        defaults.set(Array(scheduled.intersection(ends.keys)), forKey: Self.scheduledKey)
        defaults.set(quietAlert, forKey: Self.quietAlertKey)
    }

    var signatures: Set<String> { Set(ends.keys) }

    mutating func insert(_ window: ClassActivityWindow, scheduled isScheduled: Bool) {
        ends[window.signature] = window.session.endMoment.date().timeIntervalSinceReferenceDate
        if isScheduled {
            scheduled.insert(window.signature)
        } else {
            scheduled.remove(window.signature)
        }
    }

    mutating func markSeen(_ signatures: Set<String>) {
        scheduled.subtract(signatures)
    }

    mutating func forget(_ signatures: Set<String>) {
        for signature in signatures {
            ends[signature] = nil
        }
        scheduled.subtract(signatures)
    }

    mutating func removeFinished(before date: Date) {
        ends = ends.filter { $0.value > date.timeIntervalSinceReferenceDate }
        scheduled.formIntersection(ends.keys)
    }

    mutating func removeAll() {
        ends = [:]
        scheduled = []
    }
}
