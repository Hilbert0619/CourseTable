import Foundation
import Observation
import OSLog
import SwiftData
import CourseKit

/// A finished or stopped focus session, as the Focus tab lists it.
struct FocusSessionSummary: Hashable, Identifiable, Sendable {
    var id: UUID
    var goal: String
    var startedAt: Date
    var endedAt: Date
    var focusedSeconds: Int
    var finished: Bool
}

/// To-dos and focus history, from `Planner.store`. Views read the arrays and call the methods; every change
/// is saved at once.
@MainActor
@Observable
final class PlannerModel {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "Planner")
    private static let recentSessionLimit = 50

    /// Every to-do, oldest first (the order they're shown in within a group).
    private(set) var todos: [Todo] = []
    /// Ended focus sessions, newest first.
    private(set) var focusSessions: [FocusSessionSummary] = []
    /// Set when the store couldn't be opened and an in-memory one is used instead.
    let storeProblem: String?

    let container: ModelContainer
    private var context: ModelContext { container.mainContext }

    init(container: ModelContainer, storeProblem: String? = nil) {
        self.container = container
        self.storeProblem = storeProblem
        reload()
    }

    static func makeForLaunch() -> PlannerModel {
        do {
            return PlannerModel(container: try PlannerStore.makeContainer(inMemory: UITestLaunch.isActive))
        } catch {
            logger.error("Opening Planner.store failed: \(error.localizedDescription)")
            return PlannerModel(container: try! PlannerStore.makeContainer(inMemory: true), storeProblem: error.localizedDescription)
        }
    }

    static func preview(courses: [Course] = [], today: LocalDate = LocalDate(.now)) -> PlannerModel {
        let model = PlannerModel(container: try! PlannerStore.makeContainer(inMemory: true))
        model.insertDemoData(courses: courses, today: today)
        return model
    }

    func reload() {
        let records = (try? context.fetch(FetchDescriptor<TodoRecord>(sortBy: [SortDescriptor(\.createdAt)]))) ?? []
        todos = records.map(\.todo)
        var recent = FetchDescriptor<FocusSessionRecord>(sortBy: [SortDescriptor(\.startedAt, order: .reverse)])
        recent.fetchLimit = Self.recentSessionLimit
        focusSessions = ((try? context.fetch(recent)) ?? []).map {
            FocusSessionSummary(id: $0.id, goal: $0.goal, startedAt: $0.startedAt, endedAt: $0.endedAt,
                                focusedSeconds: $0.focusedSeconds, finished: $0.finished)
        }
    }

    // MARK: - To-dos

    /// Adds `todo`, or saves the changes to the one with its id.
    func save(_ todo: Todo) {
        let title = todo.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return }
        var cleaned = todo
        cleaned.title = String(title.prefix(200))
        if let record = record(for: todo.id) {
            record.update(from: cleaned)
        } else {
            context.insert(TodoRecord(cleaned))
        }
        commit()
    }

    func setDone(_ id: UUID, _ isDone: Bool) {
        guard let record = record(for: id), record.isDone != isDone else { return }
        var todo = record.todo
        todo.isDone = isDone
        record.update(from: todo)
        commit()
    }

    func delete(_ id: UUID) {
        guard let record = record(for: id) else { return }
        context.delete(record)
        commit()
    }

    private func record(for id: UUID) -> TodoRecord? {
        try? context.fetch(FetchDescriptor<TodoRecord>(predicate: #Predicate { $0.id == id })).first
    }

    // MARK: - Focus history

    func recordFocus(id: UUID, goal: String, startedAt: Date, endedAt: Date, focusedSeconds: Int, plannedSeconds: Int, finished: Bool) {
        guard (try? context.fetch(FetchDescriptor<FocusSessionRecord>(predicate: #Predicate { $0.id == id })).first) == nil else { return }
        context.insert(FocusSessionRecord(id: id, goal: goal, startedAt: startedAt, endedAt: endedAt,
                                          focusedSeconds: focusedSeconds, plannedSeconds: plannedSeconds, finished: finished))
        commit()
    }

    /// Focus time of the sessions that started on `day`.
    func focusedSeconds(on day: LocalDate) -> Int {
        focusSessions.filter { LocalDate($0.startedAt) == day }.reduce(0) { $0 + $1.focusedSeconds }
    }

    private func commit() {
        do {
            try context.save()
        } catch {
            Self.logger.error("Saving Planner.store failed: \(error.localizedDescription)")
        }
        reload()
    }

    // MARK: - Demo

    /// A few to-dos for today, for previews and UI tests (`-demoData`): one at a time, one after the first
    /// class of the day, one for any time, one done.
    func insertDemoData(courses: [Course], today: LocalDate) {
        let chinese = Locale.current.language.languageCode?.identifier == "zh"
        let first = courses.filter { $0.weekday == today.weekday }.min { $0.startPeriod < $1.startPeriod }
        var demo = [
            Todo(title: chinese ? "取快递" : "Pick up the package", day: today, timing: .at(TimeOfDay(hour: 12, minute: 10))),
            Todo(title: chinese ? "买牛奶" : "Buy milk", day: today),
            Todo(title: chinese ? "还图书馆的书" : "Return the library book", day: today, timing: .at(TimeOfDay(hour: 7, minute: 30)), isDone: true),
        ]
        if let first {
            demo.insert(Todo(title: chinese ? "交论文" : "Hand in the paper", day: today, timing: .afterClass(courseID: first.id)), at: 0)
        }
        for (index, todo) in demo.enumerated() {
            context.insert(TodoRecord(todo, createdAt: Date(timeIntervalSinceNow: Double(index - demo.count))))
        }
        commit()
    }
}
