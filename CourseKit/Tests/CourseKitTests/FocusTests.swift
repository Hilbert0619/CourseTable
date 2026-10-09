import Foundation
import Testing
@testable import CourseKit

@Suite struct FocusTests {
    let start = Date(timeIntervalSinceReferenceDate: 800_000_000)

    func layout(_ plan: FocusPlan) -> [String] {
        plan.phases.map { phase in
            switch phase.kind {
            case .focus: "F\(phase.seconds / 60)"
            case .shortBreak: "b\(phase.seconds / 60)"
            case .longBreak: "L\(phase.seconds / 60)"
            }
        }
    }

    func minutes(_ value: Double) -> Date { start.addingTimeInterval(value * 60) }

    // MARK: - Plan

    @Test func aSessionIsFilledWithBlocksAndBreaks() {
        let plan = FocusPlan(FocusSettings(totalMinutes: 150))
        #expect(layout(plan) == ["F25", "b5", "F25", "b5", "F25", "b5", "F25", "L15", "F20"])
        #expect(plan.totalSeconds == 150 * 60)
        #expect(plan.focusSeconds == 120 * 60)
        #expect(plan.blockCount == 5)
        #expect(plan.phases.map(\.block) == [1, 1, 2, 2, 3, 3, 4, 4, 5])
        #expect(plan.offsets.prefix(3) == [0, 25 * 60, 30 * 60])
    }

    @Test func itNeverEndsWithABreakOrATinyBlock() {
        // 30 minutes: a break would leave no time to focus, so the block takes it all.
        #expect(layout(FocusPlan(FocusSettings(totalMinutes: 30))) == ["F30"])
        // 34: a break and 4 minutes is too little; 35 leaves a 5-minute block.
        #expect(layout(FocusPlan(FocusSettings(totalMinutes: 34))) == ["F34"])
        #expect(layout(FocusPlan(FocusSettings(totalMinutes: 35))) == ["F25", "b5", "F5"])
        #expect(layout(FocusPlan(FocusSettings(totalMinutes: 60))) == ["F25", "b5", "F30"])
        for total in stride(from: 15, through: 240, by: 5) {
            let plan = FocusPlan(FocusSettings(totalMinutes: total))
            #expect(plan.totalSeconds == total * 60)
            #expect(plan.phases.first?.kind == .focus && plan.phases.last?.kind == .focus)
            #expect(plan.phases.filter { !$0.isBreak }.allSatisfy { $0.seconds >= 5 * 60 })
        }
    }

    @Test func settingsShapeThePlan() {
        #expect(layout(FocusPlan(FocusSettings(totalMinutes: 150, usesBreaks: false))) == ["F150"])
        let custom = FocusSettings(totalMinutes: 120, focusMinutes: 50, shortBreakMinutes: 10, longBreakMinutes: 20, blocksBeforeLongBreak: 2)
        // After block 2 a long break (20) would leave 10 minutes: block 2 runs to the end instead.
        #expect(layout(FocusPlan(custom)) == ["F50", "b10", "F60"])
        let longer = FocusSettings(totalMinutes: 180, focusMinutes: 50, shortBreakMinutes: 10, longBreakMinutes: 20, blocksBeforeLongBreak: 2)
        #expect(layout(FocusPlan(longer)) == ["F50", "b10", "F50", "L20", "F50"])
        // Out-of-range values are brought into range.
        #expect(FocusPlan(FocusSettings(totalMinutes: 1)).totalSeconds == 15 * 60)
        #expect(FocusPlan(FocusSettings(totalMinutes: 999, usesBreaks: false)).totalSeconds == 240 * 60)
        #expect(layout(FocusPlan(FocusSettings(totalMinutes: 60, focusMinutes: 1, shortBreakMinutes: 0))).first == "F10")
    }

    // MARK: - Running

    @Test func statusFollowsTheClock() {
        let run = FocusRun(goal: "Calculus review", plan: FocusPlan(FocusSettings(totalMinutes: 60)), startedAt: start)
        let early = run.status(at: minutes(10))
        #expect(early.phaseIndex == 0 && early.phase.kind == .focus)
        #expect(early.phaseRemaining == 15 * 60 && early.focused == 10 * 60)
        let resting = run.status(at: minutes(27))
        #expect(resting.phase.kind == .shortBreak && resting.phaseRemaining == 3 * 60 && resting.focused == 25 * 60)
        let last = run.status(at: minutes(40))
        #expect(last.phaseIndex == 2 && last.phaseElapsed == 10 * 60 && last.focused == 35 * 60)
        let over = run.status(at: minutes(75))
        #expect(over.isFinished && over.phaseIndex == 2 && over.phaseRemaining == 0 && over.focused == 55 * 60)
        #expect(run.endDate(at: minutes(10)) == minutes(60))
        #expect(!run.status(at: start.addingTimeInterval(-5)).isFinished)
    }

    @Test func pausesMoveEverythingLater() {
        var run = FocusRun(goal: "", plan: FocusPlan(FocusSettings(totalMinutes: 60)), startedAt: start)
        run.pause(at: minutes(10))
        #expect(run.status(at: minutes(20)).elapsed == 10 * 60)
        #expect(run.status(at: minutes(20)).isPaused)
        #expect(run.endDate(at: minutes(20)) == nil)
        #expect(run.upcomingSwitches(at: minutes(20)).isEmpty)
        run.pause(at: minutes(15))  // already paused: no change
        run.resume(at: minutes(20))
        #expect(run.status(at: minutes(20)).elapsed == 10 * 60)
        #expect(run.endDate(at: minutes(20)) == minutes(70))
        run.resume(at: minutes(30))  // not paused: no change
        #expect(run.endDate(at: minutes(30)) == minutes(70))
    }

    @Test func skippingABreakStartsTheNextBlock() {
        var run = FocusRun(goal: "", plan: FocusPlan(FocusSettings(totalMinutes: 60)), startedAt: start)
        run.skipBreak(at: minutes(10))  // during focus: nothing
        #expect(run.skippedSeconds == 0)
        run.skipBreak(at: minutes(26))
        let status = run.status(at: minutes(26))
        #expect(status.phaseIndex == 2 && status.phaseElapsed == 0)
        #expect(run.endDate(at: minutes(26)) == minutes(56))
        #expect(status.focused == 25 * 60)
    }

    @Test func switchesAreTheNotificationsToSchedule() {
        let run = FocusRun(goal: "", plan: FocusPlan(FocusSettings(totalMinutes: 150)), startedAt: start)
        let switches = run.upcomingSwitches(at: minutes(20))
        #expect(switches.map(\.index) == Array(1...9))
        #expect(switches.first?.date == minutes(25))
        #expect(switches.last?.date == minutes(150))  // index 9 == phases.count: the end
        #expect(run.upcomingSwitches(at: minutes(20), limit: 3).count == 3)
        #expect(run.upcomingSwitches(at: minutes(149)).map(\.index) == [9])
        #expect(run.upcomingSwitches(at: minutes(150)).isEmpty)
        #expect(FocusRun.maxAlerts >= 2)
    }

    @Test func aRunRoundTripsAsJSON() throws {
        var run = FocusRun(goal: "高数复习", plan: FocusPlan(FocusSettings(totalMinutes: 90)), startedAt: start)
        run.pause(at: minutes(5))
        let data = try JSONEncoder().encode(run)
        #expect(try JSONDecoder().decode(FocusRun.self, from: data) == run)
    }
}
