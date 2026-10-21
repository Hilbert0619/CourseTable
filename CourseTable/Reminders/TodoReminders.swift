import Foundation
import OSLog
import UserNotifications
import CourseKit

/// A notification when a to-do is due: at its time, or when its class ends. Only the next
/// `Timetable.maxTodoAlerts` are scheduled; `Alerts.sync` renews them.
enum TodoReminders {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "TodoReminders")
    static let idPrefix = "todo-"

    /// e.g. title "Pick up the package", body "12:00" or "After Calculus · 09:40".
    static func payload(for todo: ResolvedTodo) -> ClassReminders.Payload? {
        guard let due = todo.due else { return nil }
        return ClassReminders.Payload(
            id: idPrefix + todo.id.uuidString,
            title: todo.todo.title,
            body: when(todo) ?? due.time.description,
            fireDate: DateComponents(year: due.day.year, month: due.day.month, day: due.day.day, hour: due.time.hour, minute: due.time.minute)
        )
    }

    /// "12:00", "After Calculus · 09:40", or `nil` for a to-do without a time.
    static func when(_ todo: ResolvedTodo) -> String? {
        guard let due = todo.due else { return nil }
        if let session = todo.session {
            return "\(String(localized: "After \(session.course.name)")) · \(due.time)"
        }
        return due.time.description
    }

    /// Replaces the scheduled to-do notifications with ones for the next unfinished to-dos.
    static func sync(timetable: Timetable, todos: [Todo], now: Date = .now) async {
        let center = UNUserNotificationCenter.current()
        await ClassReminders.removePending(withPrefix: idPrefix)
        guard await ClassReminders.isAllowed() else { return }
        for payload in timetable.upcomingTodoAlerts(after: Moment(now), todos: todos).compactMap(payload(for:)) {
            guard !Task.isCancelled else { return }
            let content = UNMutableNotificationContent()
            content.title = payload.title
            content.body = payload.body
            content.sound = .default
            content.threadIdentifier = "todos"
            let trigger = UNCalendarNotificationTrigger(dateMatching: payload.fireDate, repeats: false)
            do {
                try await center.add(UNNotificationRequest(identifier: payload.id, content: content, trigger: trigger))
            } catch {
                logger.error("Scheduling to-do \(payload.id) failed: \(error.localizedDescription)")
            }
        }
    }
}

/// Keeps every alert in step with the timetable, the to-dos and the settings: class Live Activities first,
/// then a notification for every class, then the to-dos. `RootView` runs it whenever any of
/// them change and each time the app comes to the foreground.
enum Alerts {
    /// What `RootView` asks for; a new value cancels the previous sync.
    struct SyncRequest: Equatable, Sendable {
        var timetable: Timetable
        var remindersEnabled: Bool
        var liveActivitiesEnabled: Bool
        var leadMinutes: Int
        var todos: [Todo]
        /// Only sync while the app is in the foreground.
        var isActive: Bool
    }

    static func sync(_ request: SyncRequest, now: Date = .now) async {
        guard request.isActive, !UITestLaunch.isActive else { return }
        // Let a burst of edits settle; a newer request cancels this one.
        try? await Task.sleep(for: .seconds(1))
        guard !Task.isCancelled else { return }
        // With a reminder sounding, a scheduled activity starts silently next to it.
        var remindersSound = false
        if request.remindersEnabled {
            remindersSound = await ClassReminders.isAllowed()
        }
        await ClassLiveActivities.sync(
            timetable: request.timetable, enabled: request.liveActivitiesEnabled, leadMinutes: request.leadMinutes,
            todos: request.todos, quietAlert: remindersSound, now: now
        )
        guard !Task.isCancelled else { return }
        await ClassReminders.sync(
            timetable: request.timetable, enabled: request.remindersEnabled, leadMinutes: request.leadMinutes, now: now
        )
        guard !Task.isCancelled else { return }
        await TodoReminders.sync(timetable: request.timetable, todos: request.todos, now: now)
    }
}
