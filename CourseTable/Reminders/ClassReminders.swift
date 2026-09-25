import Foundation
import OSLog
import UserNotifications
import CourseKit

/// Reminder preferences. They're per device, so they live in `UserDefaults` (`@AppStorage`), not SwiftData.
enum ReminderSettings {
    static let enabledKey = "reminders.enabled"
    static let leadMinutesKey = "reminders.leadMinutes"
    static let defaultLeadMinutes = 10
    static let leadChoices = [0, 5, 10, 15, 20, 30, 45, 60]

    static func label(forLeadMinutes minutes: Int) -> String {
        minutes == 0 ? String(localized: "At start time") : String(localized: "\(minutes) minutes before")
    }
}

/// Local notifications shortly before each class.
///
/// iOS keeps at most 64 pending notifications per app, so only the next
/// `Timetable.maxScheduledReminders` classes are scheduled. The app schedules them again whenever
/// it comes to the foreground and whenever the timetable or the reminder settings change.
enum ClassReminders {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "Reminders")

    /// What `RootView` asks for; a new value cancels the previous sync.
    struct SyncRequest: Equatable, Sendable {
        var timetable: Timetable
        var enabled: Bool
        var leadMinutes: Int
        /// Only sync while the app is in the foreground.
        var isActive: Bool
    }

    /// One notification's text and time, before it becomes a `UNNotificationRequest`.
    struct Payload: Hashable, Sendable {
        var id: String
        var title: String
        var body: String
        var fireDate: DateComponents
    }

    /// e.g. title "Calculus", body "Starts in 10 min · 08:00–09:40 · A101 · Li".
    static func payload(for reminder: ClassReminder) -> Payload {
        let session = reminder.session
        let when = reminder.leadMinutes == 0
            ? String(localized: "Starting now")
            : String(localized: "Starts in \(reminder.leadMinutes) min")
        let fire = reminder.fireMoment
        return Payload(
            id: reminder.id,
            title: session.course.name,
            body: [when, session.timeRange, session.details].filter { !$0.isEmpty }.joined(separator: " · "),
            fireDate: DateComponents(
                year: fire.day.year, month: fire.day.month, day: fire.day.day,
                hour: fire.time.hour, minute: fire.time.minute
            )
        )
    }

    /// Asks for permission to show notifications (only prompts the first time). `true` if allowed.
    static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    /// Whether the user has turned notifications off for the app in Settings.
    static func isDenied() async -> Bool {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .denied
    }

    private static func isAllowed() async -> Bool {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        return [.authorized, .provisional, .ephemeral].contains(status)
    }

    /// Replaces the scheduled reminders with ones for the upcoming classes, or removes them all when
    /// reminders are off or not allowed.
    static func sync(_ request: SyncRequest, now: Date = .now) async {
        guard request.isActive, !UITestLaunch.isActive else { return }
        // Let a burst of edits settle; a newer request cancels this one.
        try? await Task.sleep(for: .seconds(1))
        guard !Task.isCancelled else { return }

        let center = UNUserNotificationCenter.current()
        guard request.enabled, await isAllowed() else {
            center.removeAllPendingNotificationRequests()
            return
        }
        let reminders = request.timetable.upcomingReminders(after: Moment(now), leadMinutes: request.leadMinutes)
        center.removeAllPendingNotificationRequests()
        for payload in reminders.map(payload(for:)) {
            guard !Task.isCancelled else { return }
            let content = UNMutableNotificationContent()
            content.title = payload.title
            content.body = payload.body
            content.sound = .default
            content.threadIdentifier = "class-reminders"
            let trigger = UNCalendarNotificationTrigger(dateMatching: payload.fireDate, repeats: false)
            do {
                try await center.add(UNNotificationRequest(identifier: payload.id, content: content, trigger: trigger))
            } catch {
                logger.error("Scheduling reminder \(payload.id) failed: \(error.localizedDescription)")
            }
        }
    }
}
