import Foundation
import Testing
@testable import CourseKit

/// The To-Dos and Focus widgets' data (`PlannerSnapshot`).
@Suite struct PlannerSnapshotTests {
    let timetable = Fixture.timetable
    let monday = Fixture.day("2026-09-28")  // week 5: Calculus 08:00–09:40, Physics 10:00–11:40

    var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Shanghai")!
        return calendar
    }

    func date(_ day: String, _ time: String) -> Date {
        Fixture.at(day, time).date(calendar: calendar)
    }

    func titles(_ list: TodoWidgetList) -> [String] {
        list.items.map { ($0.todo.isDone ? "✓ " : $0.isOverdue ? "! " : "") + $0.todo.title }
    }

    // MARK: - To-dos

    @Test func unfinishedByTimeThenAnytimeThenDone() {
        let snapshot = PlannerSnapshot(todos: [
            Todo(title: "Buy milk", day: monday),
            Todo(title: "Pick up the package", day: monday, timing: .at(TimeOfDay("12:00")!)),
            Todo(title: "Return the book", day: monday, timing: .at(TimeOfDay("07:30")!), isDone: true),
            Todo(title: "Hand in the paper", day: monday, timing: .afterClass(courseID: Fixture.calculus.id)),
            Todo(title: "Call home", day: monday),
            Todo(title: "Tomorrow's thing", day: monday.adding(days: 1)),
        ], focusDays: [], run: nil)
        let list = snapshot.todoList(at: Fixture.at("2026-09-28", "09:00"), timetable: timetable)
        #expect(titles(list) == ["Hand in the paper", "Pick up the package", "Buy milk", "Call home", "✓ Return the book"])
        #expect(list.remaining == 4)
        #expect(list.done == 1)
        #expect(list.items.first?.session?.course.name == "Calculus")
        #expect(list.items.first?.due == Fixture.at("2026-09-28", "09:40"))

        // At 09:40 the paper is due: overdue from then on.
        let later = snapshot.todoList(at: Fixture.at("2026-09-28", "09:40"), timetable: timetable)
        #expect(titles(later).prefix(2) == ["! Hand in the paper", "Pick up the package"])
    }

    @Test func withoutATimetableAClassToDoHasNoTime() {
        let snapshot = PlannerSnapshot(todos: [
            Todo(title: "Hand in the paper", day: monday, timing: .afterClass(courseID: Fixture.calculus.id)),
            Todo(title: "Pick up the package", day: monday, timing: .at(TimeOfDay("12:00")!)),
        ], focusDays: [], run: nil)
        let list = snapshot.todoList(at: Fixture.at("2026-09-28", "13:00"), timetable: nil)
        #expect(titles(list) == ["! Pick up the package", "Hand in the paper"])
        #expect(list.items.last?.due == nil)
    }

    @Test func theTimelineTurnsAtEachTimeAndAtMidnight() {
        let snapshot = PlannerSnapshot(todos: [
            Todo(title: "Hand in the paper", day: monday, timing: .afterClass(courseID: Fixture.calculus.id)),
            Todo(title: "Pick up the package", day: monday, timing: .at(TimeOfDay("12:00")!)),
            Todo(title: "Same time", day: monday, timing: .at(TimeOfDay("12:00")!)),
            Todo(title: "Done", day: monday, timing: .at(TimeOfDay("15:00")!), isDone: true),
            Todo(title: "Buy milk", day: monday),
        ], focusDays: [], run: nil)
        let now = Fixture.at("2026-09-28", "10:00")
        #expect(snapshot.todoTimelineMoments(from: now, timetable: timetable) == [
            now, Fixture.at("2026-09-28", "12:00"), Fixture.at("2026-09-29", "00:00"),
        ])
    }

    @Test func theAppKeepsWhatTheWidgetsNeed() {
        let sessions: [(startedAt: Date, focusedSeconds: Int)] = [
            (date("2026-09-28", "09:00"), 1500),
            (date("2026-09-28", "23:30"), 600),
            (date("2026-09-27", "20:00"), 0),
            (date("2025-01-01", "10:00"), 900),
        ]
        let snapshot = PlannerSnapshot(
            todos: [Todo(title: "Old", day: monday.adding(days: -2)), Todo(title: "Yesterday", day: monday.adding(days: -1)),
                    Todo(title: "Later", day: monday.adding(days: 5))],
            focusSessions: sessions, run: nil, today: monday, calendar: calendar
        )
        #expect(snapshot.todos.map(\.title) == ["Yesterday", "Later"])
        #expect(snapshot.focusDays == [FocusDay(day: monday, seconds: 2100)])
        let decoded = try? PlannerSnapshot.decode(from: snapshot.encoded())
        #expect(decoded == snapshot)
    }

    @Test func aNewerSnapshotIsRefused() throws {
        var snapshot = PlannerSnapshot(todos: [], focusDays: [], run: nil)
        snapshot.version = PlannerSnapshot.currentVersion + 1
        #expect(throws: SnapshotError.unsupportedVersion(snapshot.version)) {
            try PlannerSnapshot.decode(from: snapshot.encoded())
        }
    }

    // MARK: - Focus

    @Test func todayAndTheStreak() {
        let days = ["2026-09-24", "2026-09-25", "2026-09-26", "2026-09-27"].map { FocusDay(day: Fixture.day($0), seconds: 1500) }
            + [FocusDay(day: Fixture.day("2026-09-22"), seconds: 1500), FocusDay(day: Fixture.day("2026-09-23"), seconds: 30)]
        let snapshot = PlannerSnapshot(todos: [], focusDays: days, run: nil)
        // Nothing yet today: the streak up to yesterday still shows (a 30-second day breaks it).
        let morning = snapshot.focusState(at: date("2026-09-28", "08:00"), calendar: calendar)
        #expect(morning.todaySeconds == 0)
        #expect(morning.streakDays == 4)
        #expect(morning.run == nil)
        // A day missed: no streak.
        #expect(snapshot.focusState(at: date("2026-09-29", "08:00"), calendar: calendar).streakDays == 0)

        var withToday = snapshot
        withToday.focusDays.append(FocusDay(day: monday, seconds: 600))
        let evening = withToday.focusState(at: date("2026-09-28", "20:00"), calendar: calendar)
        #expect(evening.todaySeconds == 600)
        #expect(evening.streakDays == 5)
    }

    @Test func theSessionInProgressCounts() {
        let run = FocusRun(goal: "Calculus", plan: FocusPlan(FocusSettings(totalMinutes: 60)), startedAt: date("2026-09-28", "09:00"))
        let snapshot = PlannerSnapshot(todos: [], focusDays: [FocusDay(day: monday, seconds: 600)], run: run)
        let during = snapshot.focusState(at: date("2026-09-28", "09:10"), calendar: calendar)
        #expect(during.todaySeconds == 600 + 600)
        #expect(during.streakDays == 1)
        #expect(during.run == run)
        #expect(during.status?.phase.kind == .focus)
        // Over (the app hasn't recorded it yet): the widget is idle, the time still counts.
        let after = snapshot.focusState(at: date("2026-09-28", "10:30"), calendar: calendar)
        #expect(after.run == nil)
        #expect(after.todaySeconds == 600 + 55 * 60)
    }

    @Test func theFocusTimelineFollowsTheSession() {
        let start = date("2026-09-28", "09:00")
        var run = FocusRun(goal: "", plan: FocusPlan(FocusSettings(totalMinutes: 60)), startedAt: start)  // F25 b5 F30
        let snapshot = PlannerSnapshot(todos: [], focusDays: [], run: run)
        let now = start.addingTimeInterval(10 * 60)
        let midnight = date("2026-09-29", "00:00")
        #expect(snapshot.focusTimelineDates(from: now, calendar: calendar) == [
            now, start.addingTimeInterval(25 * 60), start.addingTimeInterval(30 * 60), start.addingTimeInterval(60 * 60), midnight,
        ])
        run.pause(at: now)
        let paused = PlannerSnapshot(todos: [], focusDays: [], run: run)
        #expect(paused.focusTimelineDates(from: now, calendar: calendar) == [now, midnight])
        #expect(PlannerSnapshot(todos: [], focusDays: [], run: nil).focusTimelineDates(from: now, calendar: calendar) == [now, midnight])
    }
}
