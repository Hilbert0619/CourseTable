import Foundation
import OSLog
import SwiftData
import WidgetKit
import CourseKit

enum Persistence {
    static var schema: Schema { Schema([CourseRecord.self, SettingsRecord.self]) }

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
            let today = LocalDate(.now)
            settings(in: container.mainContext, today: today.adding(days: -28))  // today is in week 5
            for course in SampleCourses.all(today: today) {
                container.mainContext.insert(CourseRecord(course))
            }
            return container
        } catch {
            fatalError("Preview store failed: \(error)")
        }
    }
}

/// Keeps the widget's copy of the timetable up to date.
enum WidgetSync {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "WidgetSync")

    @MainActor
    static func publish(_ timetable: Timetable) {
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
}
