import Foundation
import SwiftData
import CourseKit

/// To-dos and focus history (docs/todos-focus-spec.md §4): their own SwiftData file, `Planner.store`, so the
/// timetable's store is never migrated for them. Versioned from the start; a later schema adds a
/// `PlannerSchemaV2` and a migration stage.
enum PlannerSchemaV1: VersionedSchema {
    static var versionIdentifier: Schema.Version { Schema.Version(1, 0, 0) }

    static var models: [any PersistentModel.Type] { [TodoRecord.self, FocusSessionRecord.self] }

    /// A to-do. Its timing is stored as a kind plus the fields that kind uses.
    @Model
    final class TodoRecord {
        @Attribute(.unique) var id: UUID
        var title: String
        /// `yyyy-MM-dd`
        var day: String
        /// `anytime`, `at` or `afterClass`
        var timingKind: String
        /// Minutes after midnight, for `at`.
        var minutes: Int?
        /// For `afterClass`.
        var courseID: UUID?
        var isDone: Bool
        var doneAt: Date?
        var createdAt: Date

        init(_ todo: Todo, createdAt: Date = .now) {
            id = todo.id
            title = todo.title
            day = todo.day.description
            timingKind = "anytime"
            minutes = nil
            courseID = nil
            isDone = todo.isDone
            doneAt = todo.isDone ? createdAt : nil
            self.createdAt = createdAt
            setTiming(todo.timing)
        }

        var todo: Todo {
            let timing: TodoTiming = switch timingKind {
            case "at": minutes.map { TodoTiming.at(TimeOfDay(minutes: $0)) } ?? .anytime
            case "afterClass": courseID.map { TodoTiming.afterClass(courseID: $0) } ?? .anytime
            default: .anytime
            }
            return Todo(id: id, title: title, day: LocalDate(day) ?? LocalDate(createdAt), timing: timing, isDone: isDone)
        }

        func update(from todo: Todo, now: Date = .now) {
            title = todo.title
            day = todo.day.description
            if todo.isDone != isDone {
                doneAt = todo.isDone ? now : nil
            }
            isDone = todo.isDone
            setTiming(todo.timing)
        }

        private func setTiming(_ timing: TodoTiming) {
            switch timing {
            case .anytime:
                timingKind = "anytime"
                minutes = nil
                courseID = nil
            case .at(let time):
                timingKind = "at"
                minutes = time.minutes
                courseID = nil
            case .afterClass(let id):
                timingKind = "afterClass"
                minutes = nil
                courseID = id
            }
        }
    }

    /// A focus session that ended (finished or stopped).
    @Model
    final class FocusSessionRecord {
        @Attribute(.unique) var id: UUID
        var goal: String
        var startedAt: Date
        var endedAt: Date
        var focusedSeconds: Int
        var plannedSeconds: Int
        var finished: Bool

        init(id: UUID, goal: String, startedAt: Date, endedAt: Date, focusedSeconds: Int, plannedSeconds: Int, finished: Bool) {
            self.id = id
            self.goal = goal
            self.startedAt = startedAt
            self.endedAt = endedAt
            self.focusedSeconds = focusedSeconds
            self.plannedSeconds = plannedSeconds
            self.finished = finished
        }
    }
}

typealias TodoRecord = PlannerSchemaV1.TodoRecord
typealias FocusSessionRecord = PlannerSchemaV1.FocusSessionRecord

enum PlannerMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [PlannerSchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}

enum PlannerStore {
    static var schema: Schema { Schema(versionedSchema: PlannerSchemaV1.self) }

    /// The store in Application Support (`Planner.store`), or in memory for tests and UI tests.
    static func makeContainer(inMemory: Bool = false) throws -> ModelContainer {
        let configuration: ModelConfiguration
        if inMemory {
            configuration = ModelConfiguration("Planner", schema: schema, isStoredInMemoryOnly: true)
        } else {
            let folder = URL.applicationSupportDirectory
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            configuration = ModelConfiguration("Planner", schema: schema, url: folder.appending(path: "Planner.store"),
                                               cloudKitDatabase: .none)
        }
        return try ModelContainer(for: schema, migrationPlan: PlannerMigrationPlan.self, configurations: [configuration])
    }
}
