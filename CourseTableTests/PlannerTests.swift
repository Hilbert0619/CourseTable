import Foundation
import Testing
import CourseKit
@testable import CourseTable

@MainActor
@Suite struct PlannerTests {
    let today = LocalDate(year: 2026, month: 8, day: 31)
    let calculus = Course(
        name: "Calculus", teacher: "Li", room: "A101", weekday: .monday, startPeriod: 1, periodCount: 2,
        weeks: CourseWeeks(first: 1, last: 16)
    )

    func model() throws -> PlannerModel {
        PlannerModel(container: try PlannerStore.makeContainer(inMemory: true))
    }

    @Test func toDosAreSavedChangedAndDeleted() throws {
        let planner = try model()
        let noon = Todo(title: "  Pick up the package  ", day: today, timing: .at(TimeOfDay(hour: 12, minute: 0)))
        let paper = Todo(title: "Hand in the paper", day: today, timing: .afterClass(courseID: calculus.id))
        planner.save(noon)
        planner.save(paper)
        planner.save(Todo(title: "   ", day: today))  // blank: ignored
        #expect(planner.todos.map(\.title) == ["Pick up the package", "Hand in the paper"])
        #expect(planner.todos[0].timing == .at(TimeOfDay(hour: 12, minute: 0)))
        #expect(planner.todos[1].timing == .afterClass(courseID: calculus.id))

        planner.setDone(paper.id, true)
        #expect(planner.todos[1].isDone)
        var moved = planner.todos[0]
        moved.day = today.adding(days: 1)
        moved.timing = .anytime
        planner.save(moved)
        #expect(planner.todos[0].day == today.adding(days: 1) && planner.todos[0].timing == .anytime)
        #expect(planner.todos.count == 2)

        planner.delete(noon.id)
        #expect(planner.todos.map(\.title) == ["Hand in the paper"])
        // A fresh model on the same store reads the same.
        let again = PlannerModel(container: planner.container)
        #expect(again.todos == planner.todos)
    }

    @Test func focusHistoryCountsTodaysSessions() throws {
        let planner = try model()
        let morning = today.date(at: TimeOfDay(hour: 9, minute: 0))
        let id = UUID()
        planner.recordFocus(id: id, goal: "Review", startedAt: morning, endedAt: morning.addingTimeInterval(3600),
                            focusedSeconds: 3000, plannedSeconds: 3000, finished: true)
        planner.recordFocus(id: id, goal: "Review", startedAt: morning, endedAt: morning.addingTimeInterval(3600),
                            focusedSeconds: 3000, plannedSeconds: 3000, finished: true)  // the same session again
        planner.recordFocus(id: UUID(), goal: "", startedAt: morning.addingTimeInterval(86_400), endedAt: morning.addingTimeInterval(87_000),
                            focusedSeconds: 600, plannedSeconds: 1500, finished: false)
        #expect(planner.focusSessions.count == 2)
        #expect(planner.focusSessions.first?.finished == false)  // newest first
        #expect(planner.focusedSeconds(on: today) == 3000)
        #expect(planner.focusedSeconds(on: today.adding(days: 1)) == 600)
    }

    @Test func toDoNotificationsSayWhen() throws {
        let timetable = Timetable(semester: Semester(startingOn: today), courses: [calculus])
        let noon = timetable.resolve(Todo(title: "Pick up the package", day: today, timing: .at(TimeOfDay(hour: 12, minute: 0))))
        let paper = timetable.resolve(Todo(title: "Hand in the paper", day: today, timing: .afterClass(courseID: calculus.id)))
        let payload = try #require(TodoReminders.payload(for: noon))
        #expect(payload.title == "Pick up the package" && payload.body == "12:00")
        #expect(payload.id == "todo-\(noon.id.uuidString)")
        #expect(payload.fireDate == DateComponents(year: 2026, month: 8, day: 31, hour: 12, minute: 0))
        let after = try #require(TodoReminders.payload(for: paper))
        #expect(after.body.hasSuffix("Calculus · 09:40") || after.body.contains("Calculus"))
        #expect(after.fireDate.hour == 9 && after.fireDate.minute == 40)
        #expect(TodoReminders.payload(for: timetable.resolve(Todo(title: "Any", day: today))) == nil)
        #expect(!payload.id.hasPrefix(ClassReminders.idPrefix) && !payload.id.hasPrefix(FocusEngine.notificationPrefix))
    }

    /// Activities started by 0.3.0 have no to-dos in their content; they still decode.
    @Test func olderLiveActivityContentStillDecodes() throws {
        let old = #"{"start": 800000000, "end": 800006000}"#.data(using: .utf8)!
        let state = try JSONDecoder().decode(ClassActivityAttributes.ContentState.self, from: old)
        #expect(state.todos.isEmpty && state.todoSummary == nil)
        let full = ClassActivityAttributes.ContentState(start: state.start, end: state.end, todos: ["A", "B", "C"])
        #expect(try JSONDecoder().decode(ClassActivityAttributes.ContentState.self, from: JSONEncoder().encode(full)) == full)
        #expect(full.todoSummary == "A · B +1")
    }

    @Test func focusNotificationsNameTheNextPhase() {
        let run = FocusRun(goal: "Calculus review", plan: FocusPlan(FocusSettings(totalMinutes: 60)), startedAt: .now)
        #expect(run.plan.phases.count == 3)
        #expect(FocusReminders.text(for: 1, in: run).1.isEmpty == false)  // into the break
        #expect(FocusReminders.text(for: 2, in: run).1.contains("Calculus review"))  // back to focus
        #expect(FocusReminders.text(for: 3, in: run).1.contains("Calculus review"))  // done
        let state = FocusActivityAttributes.ContentState(run, at: run.startedAt.addingTimeInterval(60))
        #expect(state.phase == "focus" && state.block == 1 && state.blockCount == 2 && !state.isPaused)
        #expect(state.phaseEnd.timeIntervalSince(run.startedAt) == 25 * 60)
        #expect(state.sessionEnd.timeIntervalSince(run.startedAt) == 60 * 60)
        #expect(FocusSound.available.first == FocusSound.none)
    }
}
