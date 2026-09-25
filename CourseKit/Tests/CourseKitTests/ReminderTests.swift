import Foundation
import Testing
@testable import CourseKit

@Suite struct ReminderTests {
    let timetable = Fixture.timetable

    func summary(_ reminders: [ClassReminder]) -> [String] {
        reminders.map { "\($0.session.course.name) \($0.fireMoment.day) \($0.fireMoment.time)" }
    }

    @Test func momentArithmeticCrossesMidnight() {
        #expect(Fixture.at("2026-09-28", "00:05").adding(minutes: -10) == Fixture.at("2026-09-27", "23:55"))
        #expect(Fixture.at("2026-12-31", "23:50").adding(minutes: 20) == Fixture.at("2027-01-01", "00:10"))
        #expect(Fixture.at("2026-09-28", "08:00").adding(minutes: 0) == Fixture.at("2026-09-28", "08:00"))
        #expect(Fixture.at("2026-09-28", "08:00").adding(minutes: 2 * 1440) == Fixture.at("2026-09-30", "08:00"))
        #expect(Fixture.at("2026-09-28", "08:00").adding(minutes: -1441) == Fixture.at("2026-09-27", "07:59"))
    }

    @Test func remindersFollowTheWeekInOrder() {
        let reminders = timetable.upcomingReminders(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 10, limit: 6)
        #expect(summary(reminders) == [
            "Calculus 2026-09-28 07:50",  // week 5 is odd: Physics, not English
            "Physics 2026-09-28 09:50",
            "Lab 2026-10-01 13:50",
            "Seminar 2026-10-02 18:50",
            "Calculus 2026-10-05 07:50",  // week 6 is even
            "English 2026-10-05 09:50",
        ])
        #expect(reminders.allSatisfy { $0.leadMinutes == 10 })
        #expect(reminders[2].session.start == TimeOfDay(hour: 14, minute: 0))
        #expect(reminders[2].session.week == 5)
    }

    @Test func skipsRemindersWhoseTimeHasPassed() {
        let late = timetable.upcomingReminders(after: Fixture.at("2026-09-28", "07:55"), leadMinutes: 10, limit: 1)
        #expect(summary(late) == ["Physics 2026-09-28 09:50"])
        let exact = timetable.upcomingReminders(after: Fixture.at("2026-09-28", "07:50"), leadMinutes: 10, limit: 1)
        #expect(summary(exact) == ["Physics 2026-09-28 09:50"])
        let justBefore = timetable.upcomingReminders(after: Fixture.at("2026-09-28", "07:49"), leadMinutes: 10, limit: 1)
        #expect(summary(justBefore) == ["Calculus 2026-09-28 07:50"])
    }

    @Test func coversTheWholeTermUpToTheLimit() {
        let beforeTerm = Fixture.at("2026-08-01", "12:00")
        let all = timetable.upcomingReminders(after: beforeTerm, leadMinutes: 10, limit: 1000)
        // Calculus 16 + Physics 8 + English 8 + Lab 9 + Seminar 18.
        #expect(all.count == 59)
        #expect(summary(Array(all.prefix(1))) == ["Calculus 2026-08-31 07:50"])
        #expect(zip(all, all.dropFirst()).allSatisfy { $0.fireMoment < $1.fireMoment })
        #expect(Set(all.map(\.id)).count == all.count)

        #expect(timetable.upcomingReminders(after: beforeTerm, leadMinutes: 10).count == 59)
        #expect(timetable.upcomingReminders(after: beforeTerm, leadMinutes: 10, limit: 20) == Array(all.prefix(20)))
        #expect(timetable.upcomingReminders(after: beforeTerm, leadMinutes: 10, limit: 0).isEmpty)
        #expect(Timetable.maxScheduledReminders < 64)
    }

    @Test func stopsAtTheEndOfTerm() {
        let lastWeek = timetable.upcomingReminders(after: Fixture.at("2026-12-28", "00:00"), leadMinutes: 10)
        #expect(summary(lastWeek) == ["Seminar 2027-01-01 18:50"])
        #expect(timetable.upcomingReminders(after: Fixture.at("2027-01-04", "00:00"), leadMinutes: 10).isEmpty)
    }

    @Test func leadTimeOfZeroAndNegativeFireAtTheStart() {
        let atStart = timetable.upcomingReminders(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 0, limit: 1)
        #expect(summary(atStart) == ["Calculus 2026-09-28 08:00"])
        #expect(timetable.upcomingReminders(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: -5, limit: 1) == atStart)
    }

    @Test func reminderCanFireTheEveningBefore() {
        let midnight = PeriodSchedule(periods: [ClassPeriod(start: TimeOfDay("00:10")!, end: TimeOfDay("00:50")!)])
        let owl = Course(name: "Astronomy", weekday: .monday, startPeriod: 1, periodCount: 1, weeks: CourseWeeks(first: 1, last: 18))
        let night = Timetable(semester: Fixture.semester, periods: midnight, courses: [owl])
        let reminders = night.upcomingReminders(after: Fixture.at("2026-08-30", "23:00"), leadMinutes: 20, limit: 2)
        #expect(summary(reminders) == ["Astronomy 2026-08-30 23:50", "Astronomy 2026-09-06 23:50"])
        #expect(reminders[0].session.day == Fixture.day("2026-08-31"))
    }

    @Test func idsAreStablePerCourseAndDay() {
        let first = timetable.upcomingReminders(after: Fixture.at("2026-09-28", "07:00"), leadMinutes: 10, limit: 3)
        let again = timetable.upcomingReminders(after: Fixture.at("2026-09-28", "07:30"), leadMinutes: 15, limit: 3)
        #expect(first.map(\.id) == again.map(\.id))
        #expect(first[0].id == "class-\(Fixture.calculus.id.uuidString)@2026-09-28")
    }
}
