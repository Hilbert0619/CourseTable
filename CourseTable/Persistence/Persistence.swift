import Foundation
import OSLog
import SwiftData
import UserNotifications
import WidgetKit
import CourseKit

enum Persistence {
    static var schema: Schema { Schema([CourseRecord.self, SettingsRecord.self, CourseTemplateRecord.self]) }

    static func makeContainer(inMemory: Bool = false) throws -> ModelContainer {
        if !inMemory {
            // The default store lives in Application Support, which doesn't exist on first launch;
            // creating it up front avoids CoreData's noisy "Failed to stat path" recovery.
            try FileManager.default.createDirectory(at: .applicationSupportDirectory, withIntermediateDirectories: true)
        }
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)
        return try ModelContainer(for: schema, configurations: [configuration])
    }

    /// The single settings row, created with defaults (term starting this week) on first use.
    @MainActor
    @discardableResult
    static func settings(in context: ModelContext, today: LocalDate = LocalDate(.now)) -> SettingsRecord {
        if let existing = try? context.fetch(FetchDescriptor<SettingsRecord>()).first {
            return existing
        }
        let created = SettingsRecord(semester: Semester(startingOn: today), periods: .standard)
        context.insert(created)
        return created
    }

    static func timetable(settings: SettingsRecord?, courses: [CourseRecord]) -> Timetable {
        Timetable(
            semester: settings?.semester ?? Semester(startingOn: LocalDate(.now)),
            periods: settings?.periods ?? .standard,
            courses: courses.map(\.course)
        )
    }

    /// An in-memory store with a few courses, for SwiftUI previews.
    @MainActor
    static func previewContainer() -> ModelContainer {
        do {
            let container = try makeContainer(inMemory: true)
            insertDemoData(into: container.mainContext)
            return container
        } catch {
            fatalError("Preview store failed: \(error)")
        }
    }

    /// A term in its fifth week and a few courses on today's weekday and the next, for previews and UI tests.
    @MainActor
    static func insertDemoData(into context: ModelContext, today: LocalDate = LocalDate(.now)) {
        settings(in: context, today: today.adding(days: -28))
        for course in SampleCourses.all(today: today) {
            context.insert(CourseRecord(course))
        }
    }
}

/// Keeps the widget's copy of the timetable up to date.
enum WidgetSync {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "WidgetSync")

    /// UI tests leave the widgets alone, except the widget screenshots (`-widgetDemo`).
    private static var isAllowed: Bool { !UITestLaunch.isActive || UITestLaunch.wantsWidgetDemo }

    @MainActor
    static func publish(_ timetable: Timetable) {
        guard isAllowed else { return }
        guard let url = SnapshotStore.defaultURL else {
            logger.notice("App Group unavailable; widget snapshot not written")
            return
        }
        do {
            try SnapshotStore.write(ScheduleSnapshot(timetable: timetable), to: url)
            WidgetCenter.shared.reloadAllTimelines()
        } catch {
            logger.error("Writing widget snapshot failed: \(error.localizedDescription)")
        }
    }

    /// Keeps the To-Dos and Focus widgets' copy of the to-dos and focus time up to date.
    @MainActor
    static func publishPlanner(_ planner: PlannerModel) {
        guard isAllowed, let url = SnapshotStore.plannerURL else { return }
        do {
            try SnapshotStore.write(planner.widgetSnapshot(run: FocusEngine.shared.run), to: url)
            WidgetCenter.shared.reloadTimelines(ofKind: WidgetKinds.todos)
            WidgetCenter.shared.reloadTimelines(ofKind: WidgetKinds.focus)
        } catch {
            logger.error("Writing the planner snapshot failed: \(error.localizedDescription)")
        }
    }
}

/// What the To-Dos widget's checkbox does, in the app (possibly launched in the background for it).
enum TodoControl {
    static func setDone(_ id: UUID, _ isDone: Bool) async {
        await MainActor.run { PlannerModel.active?.setDone(id, isDone) }
        if isDone {
            // Ticked off: no reminder for it. Unticked, it comes back with the next sync in the app.
            UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [TodoReminders.idPrefix + id.uuidString])
        }
    }
}
