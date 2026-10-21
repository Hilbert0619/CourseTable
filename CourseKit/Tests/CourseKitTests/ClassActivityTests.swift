import Foundation
import Testing
@testable import CourseKit

@Suite struct ClassActivityTests {
    let timetable = Fixture.timetable

    func summary(_ windows: [ClassActivityWindow]) -> [String] {
        windows.map { "\($0.session.course.name) \($0.showMoment.day) \($0.showMoment.time)–\($0.session.end)" }
    }

    func existing(_ window: ClassActivityWindow, id: String? = nil, pending: Bool = false, todos: [String] = []) -> ExistingClassActivity {
        ExistingClassActivity(id: id ?? window.id, signature: window.signature, showMoment: window.showMoment, isPending: pending, todos: todos)
    }

    // MARK: - Windows

    @Test func windowsRunFromTheReminderTimeToTheEndOfClass() {
        let windows = timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 10)
        #expect(summary(windows) == [
            "Calculus 2026-09-28 07:50–09:40",
            "Physics 2026-09-28 09:50–11:40",
            "Lab 2026-10-01 13:50–16:45",
            "Seminar 2026-10-02 18:50–20:40",
        ])
        #expect(windows.map(\.leadMinutes) == [10, 10, 10, 10])
        #expect(windows.count == Timetable.maxClassActivities)
        #expect(windows[0].isOpen(at: Fixture.at("2026-09-28", "07:50")))
        #expect(windows[0].isOpen(at: Fixture.at("2026-09-28", "09:39")))
        #expect(!windows[0].isOpen(at: Fixture.at("2026-09-28", "07:49")))
        #expect(!windows[0].isOpen(at: Fixture.at("2026-09-28", "09:40")))
    }

    @Test func aClassInProgressStillHasItsWindow() {
        let during = timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "09:00"), leadMinutes: 10, limit: 2)
        #expect(summary(during) == ["Calculus 2026-09-28 07:50–09:40", "Physics 2026-09-28 09:50–11:40"])
        #expect(during[0].isOpen(at: Fixture.at("2026-09-28", "09:00")))
        let after = timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "09:40"), leadMinutes: 10, limit: 1)
        #expect(summary(after) == ["Physics 2026-09-28 09:50–11:40"])
    }

    /// With a long lead time, the next class waits for the one in progress to end, so the two never
    /// show at once; the same window is worked out after the first class has ended.
    @Test func theNextClassAppearsWhenTheOneBeforeEnds() {
        let morning = timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 30, limit: 2)
        #expect(summary(morning) == ["Calculus 2026-09-28 07:30–09:40", "Physics 2026-09-28 09:40–11:40"])
        #expect(morning.map(\.leadMinutes) == [30, 20])
        let later = timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "09:45"), leadMinutes: 30, limit: 1)
        #expect(later == [morning[1]])

        // A class that overlaps another (a clash) doesn't wait for it.
        let tutorial = Course(name: "Tutorial", weekday: .monday, startPeriod: 2, periodCount: 2, weeks: CourseWeeks(first: 1, last: 16))
        let clash = Timetable(semester: Fixture.semester, courses: [Fixture.calculus, tutorial, Fixture.physics])
        #expect(summary(clash.upcomingActivityWindows(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 30, limit: 3)) == [
            "Calculus 2026-09-28 07:30–09:40", "Tutorial 2026-09-28 08:25–10:45", "Physics 2026-09-28 09:40–11:40",
        ])
    }

    @Test func windowsStopAtTheLimitAndTheEndOfTerm() {
        #expect(timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 10, limit: 0).isEmpty)
        #expect(timetable.upcomingActivityWindows(after: Fixture.at("2026-08-01", "12:00"), leadMinutes: 10, limit: 100).count == 59)
        #expect(summary(timetable.upcomingActivityWindows(after: Fixture.at("2026-12-28", "00:00"), leadMinutes: 10))
            == ["Seminar 2027-01-01 18:50–20:40"])
        #expect(timetable.upcomingActivityWindows(after: Fixture.at("2027-01-04", "00:00"), leadMinutes: 10).isEmpty)
    }

    @Test func aWindowCanOpenTheEveningBefore() {
        let midnight = PeriodSchedule(periods: [ClassPeriod(start: TimeOfDay("00:10")!, end: TimeOfDay("00:50")!)])
        let owl = Course(name: "Astronomy", weekday: .monday, startPeriod: 1, periodCount: 1, weeks: CourseWeeks(first: 1, last: 18))
        let night = Timetable(semester: Fixture.semester, periods: midnight, courses: [owl])
        let windows = night.upcomingActivityWindows(after: Fixture.at("2026-08-30", "23:55"), leadMinutes: 20, limit: 1)
        #expect(summary(windows) == ["Astronomy 2026-08-30 23:50–00:50"])
        #expect(windows[0].leadMinutes == 20)
        #expect(windows[0].isOpen(at: Fixture.at("2026-08-30", "23:55")))
        #expect(windows[0].isOpen(at: Fixture.at("2026-08-31", "00:20")))
    }

    @Test func signaturesFollowWhatIsShown() {
        let windows = timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 10, limit: 1)
        let later = timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 15, limit: 1)
        #expect(windows[0].id == later[0].id)
        #expect(windows[0].signature == later[0].signature)
        #expect(windows[0].showMoment != later[0].showMoment)

        var moved = Fixture.calculus
        moved.room = "A202"
        let edited = Timetable(semester: Fixture.semester, courses: [moved])
            .upcomingActivityWindows(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 10, limit: 1)
        #expect(edited[0].id == windows[0].id)
        #expect(edited[0].signature != windows[0].signature)
    }

    // MARK: - Plan

    @Test func nothingExistsYet() {
        let now = Fixture.at("2026-09-28", "07:55")
        let wanted = timetable.upcomingActivityWindows(after: now, leadMinutes: 10)
        let plan = ClassActivityPlan(existing: [], wanted: wanted, created: [], now: now, canSchedule: true)
        #expect(plan.end.isEmpty && plan.forget.isEmpty)
        #expect(summary(plan.startNow) == ["Calculus 2026-09-28 07:50–09:40"])
        #expect(plan.schedule == Array(wanted.dropFirst()))

        let older = ClassActivityPlan(existing: [], wanted: wanted, created: [], now: now, canSchedule: false)
        #expect(older.startNow == plan.startNow)
        #expect(older.schedule.isEmpty)
    }

    @Test func matchingActivitiesAreKept() {
        let now = Fixture.at("2026-09-28", "08:30")
        let wanted = timetable.upcomingActivityWindows(after: now, leadMinutes: 10)
        let current = wanted.map { existing($0, pending: $0.showMoment > now) }
        let plan = ClassActivityPlan(existing: current, wanted: wanted, created: Set(wanted.map(\.signature)), now: now, canSchedule: true)
        #expect(plan == ClassActivityPlan(existing: current, wanted: wanted, created: [], now: now, canSchedule: true))
        #expect(plan.end.isEmpty && plan.forget.isEmpty && plan.startNow.isEmpty && plan.schedule.isEmpty)
    }

    @Test func finishedAndUnwantedActivitiesEnd() {
        let morning = timetable.upcomingActivityWindows(after: Fixture.at("2026-09-28", "07:55"), leadMinutes: 10, limit: 2)
        let now = Fixture.at("2026-09-28", "10:00")
        let wanted = timetable.upcomingActivityWindows(after: now, leadMinutes: 10)
        let plan = ClassActivityPlan(existing: morning.map { existing($0) }, wanted: wanted, created: Set(morning.map(\.signature)),
                                     now: now, canSchedule: true)
        // Calculus is over; Physics is showing.
        #expect(plan.end == [morning[0].id])
        #expect(plan.forget.isEmpty)
        #expect(plan.startNow.isEmpty)
        #expect(summary(plan.schedule) == [
            "Lab 2026-10-01 13:50–16:45", "Seminar 2026-10-02 18:50–20:40", "Calculus 2026-10-05 07:50–09:40",
        ])

        let off = ClassActivityPlan(existing: morning.map { existing($0) }, wanted: [], created: [], now: now, canSchedule: true)
        #expect(off.end == morning.map(\.id))
        #expect(off.forget.isEmpty && off.startNow.isEmpty && off.schedule.isEmpty)
    }

    @Test func aClosedActivityIsNotCreatedAgain() {
        let now = Fixture.at("2026-09-28", "08:00")
        let wanted = timetable.upcomingActivityWindows(after: now, leadMinutes: 10, limit: 1)
        let plan = ClassActivityPlan(existing: [], wanted: wanted, created: [wanted[0].signature], now: now, canSchedule: true)
        #expect(plan.startNow.isEmpty && plan.schedule.isEmpty && plan.end.isEmpty)

        // Not at a new reminder time either.
        let fifteen = timetable.upcomingActivityWindows(after: now, leadMinutes: 15, limit: 1)
        let moved = ClassActivityPlan(existing: [], wanted: fifteen, created: [wanted[0].signature], now: now, canSchedule: true)
        #expect(moved.startNow.isEmpty && moved.schedule.isEmpty)
    }

    @Test func editedDetailsReplaceTheActivity() {
        let now = Fixture.at("2026-09-28", "08:00")
        let before = timetable.upcomingActivityWindows(after: now, leadMinutes: 10, limit: 1)
        var moved = Fixture.calculus
        moved.room = "A202"
        let after = Timetable(semester: Fixture.semester, courses: [moved]).upcomingActivityWindows(after: now, leadMinutes: 10, limit: 1)
        let plan = ClassActivityPlan(existing: [existing(before[0], id: "old")], wanted: after, created: [before[0].signature],
                                     now: now, canSchedule: true)
        #expect(plan.end == ["old"])
        #expect(plan.startNow.map(\.signature) == [after[0].signature])
    }

    @Test func aNewLeadTimeReschedulesOnlyActivitiesNotStartedYet() {
        let now = Fixture.at("2026-09-28", "08:00")
        let ten = timetable.upcomingActivityWindows(after: now, leadMinutes: 10, limit: 2)
        let fifteen = timetable.upcomingActivityWindows(after: now, leadMinutes: 15, limit: 2)
        let current = [existing(ten[0], id: "showing"), existing(ten[1], id: "scheduled", pending: true)]
        let plan = ClassActivityPlan(existing: current, wanted: fifteen, created: Set(ten.map(\.signature)), now: now, canSchedule: true)
        #expect(plan.end == ["scheduled"])
        #expect(plan.forget == [ten[1].signature])
        #expect(plan.startNow.isEmpty)
        #expect(summary(plan.schedule) == ["Physics 2026-09-28 09:45–11:40"])
    }

    /// Scheduled activities ended because the feature is off (or Live Activities are off in Settings)
    /// never showed, so they're forgotten and come back when it's on again.
    @Test func scheduledActivitiesEndedBeforeTheyShowAreForgotten() {
        let now = Fixture.at("2026-09-28", "07:00")
        let wanted = timetable.upcomingActivityWindows(after: now, leadMinutes: 10, limit: 2)
        let scheduled = wanted.map { existing($0, pending: true) }
        let off = ClassActivityPlan(existing: scheduled, wanted: [], created: Set(wanted.map(\.signature)), now: now, canSchedule: true)
        #expect(off.end == wanted.map(\.id))
        #expect(off.forget == Set(wanted.map(\.signature)))

        let on = ClassActivityPlan(existing: [], wanted: wanted, created: [], now: now, canSchedule: true)
        #expect(on.schedule == wanted)
    }

    /// New to-dos: a showing activity is updated, a scheduled one is replaced (it never showed).
    @Test func changedToDosUpdateOrReplace() {
        let now = Fixture.at("2026-09-28", "08:00")
        let before = timetable.upcomingActivityWindows(after: now, leadMinutes: 10, limit: 2)
        let monday = Fixture.day("2026-09-28")
        let todos = [
            Todo(title: "Hand in the paper", day: monday, timing: .afterClass(courseID: Fixture.calculus.id)),
            Todo(title: "Return the book", day: monday, timing: .afterClass(courseID: Fixture.physics.id)),
        ]
        let after = timetable.upcomingActivityWindows(after: now, leadMinutes: 10, limit: 2, todos: todos)
        let current = [existing(before[0], id: "showing"), existing(before[1], id: "scheduled", pending: true)]
        let plan = ClassActivityPlan(existing: current, wanted: after, created: Set(before.map(\.signature)), now: now, canSchedule: true)
        #expect(plan.update == [ClassActivityUpdate(id: "showing", window: after[0])])
        #expect(plan.end == ["scheduled"])
        #expect(plan.forget == [before[1].signature])
        #expect(plan.schedule == [after[1]])
        #expect(plan.schedule[0].todos == ["Return the book"])

        // Unchanged to-dos: nothing to do.
        let same = [existing(after[0], id: "showing", todos: after[0].todos), existing(after[1], id: "scheduled", pending: true, todos: after[1].todos)]
        let quiet = ClassActivityPlan(existing: same, wanted: after, created: Set(after.map(\.signature)), now: now, canSchedule: true)
        #expect(quiet.update.isEmpty && quiet.end.isEmpty && quiet.schedule.isEmpty)
    }

    /// The classes with an activity, and those whose activity was closed (Android sends no notification for them;
    /// iOS notifies every class since 0.7.1, as iOS doesn't always start a scheduled activity).
    @Test func coveredClassesHaveOrHadAnActivity() {
        let now = Fixture.at("2026-09-28", "07:00")
        let wanted = timetable.upcomingActivityWindows(after: now, leadMinutes: 10)
        let plan = ClassActivityPlan(existing: [existing(wanted[1], id: "physics", pending: true)], wanted: wanted,
                                     created: [wanted[1].signature, wanted[2].signature], now: now, canSchedule: true)
        // Physics is scheduled, Lab was closed; Calculus and Seminar are to be scheduled (the app adds them).
        #expect(plan.covered == [wanted[1].id, wanted[2].id])
        #expect(plan.schedule == [wanted[0], wanted[3]])
    }

    /// iOS 26 starts scheduled activities on its own, but not always (users saw none, 2026-10): one that is gone
    /// although nobody saw it is created again, scheduled if its window is ahead, started if it's open.
    @Test func aScheduledActivityGoneUnseenIsCreatedAgain() {
        let evening = Fixture.at("2026-09-27", "21:00")
        let wanted = timetable.upcomingActivityWindows(after: evening, leadMinutes: 10, limit: 2)
        let signatures = Set(wanted.map(\.signature))
        let later = ClassActivityPlan(existing: [], wanted: wanted, created: signatures, scheduled: signatures, now: evening, canSchedule: true)
        #expect(later.forget == signatures)
        #expect(later.covered.isEmpty)
        #expect(later.schedule == wanted)

        let inClass = Fixture.at("2026-09-28", "07:55")
        let open = ClassActivityPlan(existing: [], wanted: wanted, created: signatures, scheduled: signatures, now: inClass, canSchedule: true)
        #expect(summary(open.startNow) == ["Calculus 2026-09-28 07:50–09:40"])
        #expect(open.schedule == [wanted[1]])
    }

    /// Still scheduled: kept. Showing: seen, and once it's gone (closed, or ended by iOS) it isn't created again.
    @Test func aScheduledActivitySeenShowingStaysClosedWhenGone() {
        let now = Fixture.at("2026-09-28", "08:00")
        let wanted = timetable.upcomingActivityWindows(after: now, leadMinutes: 10, limit: 2)
        let signatures = Set(wanted.map(\.signature))
        let current = [existing(wanted[0], id: "showing"), existing(wanted[1], id: "scheduled", pending: true)]
        let plan = ClassActivityPlan(existing: current, wanted: wanted, created: signatures, scheduled: signatures, now: now, canSchedule: true)
        #expect(plan.seen == [wanted[0].signature])
        #expect(plan.forget.isEmpty && plan.end.isEmpty && plan.startNow.isEmpty && plan.schedule.isEmpty)
        #expect(plan.covered == Set(wanted.map(\.id)))

        // The app moved Calculus out of `scheduled`; then it was closed.
        let closed = ClassActivityPlan(existing: [current[1]], wanted: wanted, created: signatures, scheduled: [wanted[1].signature],
                                       now: now, canSchedule: true)
        #expect(closed.startNow.isEmpty && closed.schedule.isEmpty && closed.forget.isEmpty)
        #expect(closed.covered == Set(wanted.map(\.id)))
    }

    @Test func duplicatesAreEnded() {
        let now = Fixture.at("2026-09-28", "08:00")
        let wanted = timetable.upcomingActivityWindows(after: now, leadMinutes: 10, limit: 1)
        let plan = ClassActivityPlan(existing: [existing(wanted[0], id: "a"), existing(wanted[0], id: "b", pending: true)],
                                     wanted: wanted, created: [wanted[0].signature], now: now, canSchedule: true)
        #expect(plan.end == ["b"])
        // "a" still stands for the class, so it stays remembered.
        #expect(plan.forget.isEmpty)
    }
}
