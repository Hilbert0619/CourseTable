import Foundation
import Testing
@testable import CourseKit

@Suite struct TodoTests {
    let timetable = Fixture.timetable
    let monday = Fixture.day("2026-09-28")  // week 5: Calculus 08:00–09:40, Physics 10:00–11:40

    func names(_ agenda: DayAgenda) -> [String] {
        agenda.items.map { item in
            switch item {
            case .session(let session, let after):
                ([session.course.name] + after.map { "  after: \($0.todo.title)" }).joined(separator: "\n")
            case .todo(let todo):
                "\(todo.due!.time) \(todo.todo.title)"
            }
        }
    }

    @Test func timesComeFromTheClockOrTheClass() {
        let package = Todo(title: "Pick up the package", day: monday, timing: .at(TimeOfDay("12:00")!))
        let paper = Todo(title: "Hand in the paper", day: monday, timing: .afterClass(courseID: Fixture.calculus.id))
        let milk = Todo(title: "Buy milk", day: monday)
        #expect(timetable.resolve(package).due == Fixture.at("2026-09-28", "12:00"))
        #expect(timetable.resolve(paper).due == Fixture.at("2026-09-28", "09:40"))
        #expect(timetable.resolve(paper).session?.course.name == "Calculus")
        #expect(timetable.resolve(milk).due == nil)
        #expect(!timetable.resolve(milk).isMissingItsClass)

        // Physics meets in odd weeks only: on the Monday of week 6 there's no class to follow.
        let nextMonday = Todo(title: "Lab report", day: Fixture.day("2026-10-05"), timing: .afterClass(courseID: Fixture.physics.id))
        #expect(timetable.resolve(nextMonday).due == nil)
        #expect(timetable.resolve(nextMonday).isMissingItsClass)
    }

    @Test func theDayListsClassesAndToDosInOrder() {
        let todos = [
            Todo(title: "Pick up the package", day: monday, timing: .at(TimeOfDay("12:00")!)),
            Todo(title: "Hand in the paper", day: monday, timing: .afterClass(courseID: Fixture.calculus.id)),
            Todo(title: "Buy milk", day: monday),
            Todo(title: "Call home", day: monday, timing: .at(TimeOfDay("07:30")!)),
            Todo(title: "Ask about the exam", day: monday, timing: .afterClass(courseID: Fixture.calculus.id), isDone: true),
            Todo(title: "Return the book", day: monday, timing: .at(TimeOfDay("10:00")!)),
            Todo(title: "Another day", day: Fixture.day("2026-09-29")),
            Todo(title: "Lab report", day: monday, timing: .afterClass(courseID: Fixture.lab.id)),
        ]
        let agenda = timetable.agenda(on: monday, todos: todos)
        #expect(agenda.anytime.map(\.todo.title) == ["Buy milk", "Lab report"])
        #expect(agenda.anytime[1].isMissingItsClass)
        #expect(names(agenda) == [
            "07:30 Call home",
            "Calculus\n  after: Hand in the paper\n  after: Ask about the exam",
            "10:00 Return the book",  // a to-do at the start of a class comes first
            "Physics",
            "12:00 Pick up the package",
        ])

        let empty = timetable.agenda(on: Fixture.day("2026-09-27"), todos: todos)
        #expect(empty.isEmpty)
        #expect(!timetable.agenda(on: Fixture.day("2026-09-29"), todos: todos).isEmpty)
    }

    @Test func onlyUnfinishedToDosStillAheadAreNotified() {
        let todos = [
            Todo(title: "Earlier", day: monday, timing: .at(TimeOfDay("07:00")!)),
            Todo(title: "After Calculus", day: monday, timing: .afterClass(courseID: Fixture.calculus.id)),
            Todo(title: "Done", day: monday, timing: .at(TimeOfDay("12:00")!), isDone: true),
            Todo(title: "Anytime", day: monday),
            Todo(title: "Tomorrow", day: Fixture.day("2026-09-29"), timing: .at(TimeOfDay("08:00")!)),
            Todo(title: "Noon", day: monday, timing: .at(TimeOfDay("12:00")!)),
            Todo(title: "Also 09:40", day: monday, timing: .at(TimeOfDay("09:40")!)),
        ]
        let alerts = timetable.upcomingTodoAlerts(after: Fixture.at("2026-09-28", "08:00"), todos: todos)
        #expect(alerts.map(\.todo.title) == ["After Calculus", "Also 09:40", "Noon", "Tomorrow"])
        #expect(timetable.upcomingTodoAlerts(after: Fixture.at("2026-09-28", "08:00"), todos: todos, limit: 2).count == 2)
        // A to-do due exactly now has had its moment.
        #expect(timetable.upcomingTodoAlerts(after: Fixture.at("2026-09-28", "12:00"), todos: todos).map(\.todo.title) == ["Tomorrow"])
        #expect(Timetable.maxScheduledReminders + Timetable.maxTodoAlerts + FocusRun.maxAlerts <= 64)
    }

    @Test func aClassListsItsUnfinishedToDos() {
        let calculus = timetable.sessions(on: monday)[0]
        let todos = [
            Todo(title: "Hand in the paper", day: monday, timing: .afterClass(courseID: Fixture.calculus.id)),
            Todo(title: "Done already", day: monday, timing: .afterClass(courseID: Fixture.calculus.id), isDone: true),
            Todo(title: "Next week", day: Fixture.day("2026-10-05"), timing: .afterClass(courseID: Fixture.calculus.id)),
            Todo(title: "After Physics", day: monday, timing: .afterClass(courseID: Fixture.physics.id)),
            Todo(title: "At noon", day: monday, timing: .at(TimeOfDay("12:00")!)),
        ]
        #expect(timetable.todos(after: calculus, in: todos).map(\.title) == ["Hand in the paper"])

        let windows = timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 10, limit: 2, todos: todos)
        #expect(windows.map(\.todos) == [["Hand in the paper"], ["After Physics"]])
    }

    @Test func toDosRoundTripAsJSON() throws {
        let todos = [
            Todo(title: "A", day: monday),
            Todo(title: "B", day: monday, timing: .at(TimeOfDay("12:00")!), isDone: true),
            Todo(title: "C", day: monday, timing: .afterClass(courseID: Fixture.calculus.id)),
        ]
        let data = try JSONEncoder().encode(todos)
        #expect(try JSONDecoder().decode([Todo].self, from: data) == todos)
    }
}
