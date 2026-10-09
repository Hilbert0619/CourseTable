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

/// Local notifications shortly before each class that has no Live Activity.
///
/// iOS keeps at most 64 pending notifications per app, so only the next
/// `Timetable.maxScheduledReminders` classes are scheduled (`Alerts.sync` does it whenever the app comes
/// to the foreground and whenever the timetable, the to-dos or the settings change).
enum ClassReminders {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "Reminders")
    /// Every class reminder's identifier starts with this (`ClassReminder.id`).
    static let idPrefix = "class-"

    /// One notification's text and time, before it becomes a `UNNotificationRequest`.
    struct Payload: Hashable, Sendable {
        var id: String
        var title: String
        var body: String
        var fireDate: DateComponents
    }

    /// e.g. title "Calculus", body "Starts in 10 min · 08:00–09:40 · A101 · Li".
    static func payload(for reminder: ClassReminder) -> Payload {
        let fire = reminder.fireMoment
        return Payload(
            id: reminder.id,
            title: reminder.session.course.name,
            body: body(for: reminder.session, leadMinutes: reminder.leadMinutes),
            fireDate: DateComponents(
                year: fire.day.year, month: fire.day.month, day: fire.day.day,
                hour: fire.time.hour, minute: fire.time.minute
            )
        )
    }

    /// e.g. "Starts in 10 min · 08:00–09:40 · A101 · Li"; also the alert of a class Live Activity.
    static func body(for session: ClassSession, leadMinutes: Int) -> String {
        let when = leadMinutes <= 0
            ? String(localized: "Starting now")
            : String(localized: "Starts in \(leadMinutes) min")
        return [when, session.timeRange, session.details].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    /// Asks for permission to show notifications (only prompts the first time). `true` if allowed.
    static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    /// Whether the user has turned notifications off for the app in Settings.
    static func isDenied() async -> Bool {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .denied
    }

    static func isAllowed() async -> Bool {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        return [.authorized, .provisional, .ephemeral].contains(status)
    }

    /// Removes the pending notifications whose identifiers start with `prefix`, leaving the other kinds.
    static func removePending(withPrefix prefix: String) async {
        let center = UNUserNotificationCenter.current()
        let ids = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(prefix) }
        center.removePendingNotificationRequests(withIdentifiers: ids)
    }

    /// Replaces the scheduled class reminders with ones for the upcoming classes not in `excluding` (those
    /// with a Live Activity), or removes them when reminders are off or not allowed.
    static func sync(timetable: Timetable, enabled: Bool, leadMinutes: Int, excluding: Set<String>, now: Date = .now) async {
        let center = UNUserNotificationCenter.current()
        await removePending(withPrefix: idPrefix)
        guard enabled, await isAllowed() else { return }
        let reminders = timetable.upcomingReminders(after: Moment(now), leadMinutes: leadMinutes, excluding: excluding)
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
