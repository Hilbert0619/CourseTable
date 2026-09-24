import Foundation
import Testing
@testable import CourseKit

@Suite struct SemesterTests {
    // Term picked on a Wednesday: week 1 is the whole Monday–Sunday week around it.
    let semester = Semester(startingOn: LocalDate(year: 2026, month: 9, day: 2), totalWeeks: 18)

    @Test func startIsNormalisedToMonday() {
        #expect(semester.firstMonday == LocalDate(year: 2026, month: 8, day: 31))
        #expect(semester.startDate.weekday == .monday)
        #expect(semester.lastDay == LocalDate(year: 2027, month: 1, day: 3))
        #expect(semester.lastDay.weekday == .sunday)
    }

    @Test(arguments: [
        ("2026-08-30", 0),
        ("2026-08-31", 1),
        ("2026-09-06", 1),
        ("2026-09-07", 2),
        ("2026-09-24", 4),
        ("2027-01-03", 18),
        ("2027-01-04", 19),
        ("2026-08-23", -1),
        ("2026-08-24", 0),
    ])
    func weekNumbers(text: String, week: Int) throws {
        #expect(semester.week(containing: try #require(LocalDate(text))) == week)
    }

    @Test func status() {
        #expect(semester.status(on: LocalDate(year: 2026, month: 8, day: 28)) == .notStarted(daysUntilStart: 3))
        #expect(semester.status(on: LocalDate(year: 2026, month: 9, day: 24)) == .inProgress(week: 4))
        #expect(semester.status(on: LocalDate(year: 2027, month: 1, day: 4)) == .finished)
        #expect(SemesterStatus.inProgress(week: 4).week == 4)
        #expect(SemesterStatus.finished.week == nil)
    }

    @Test func displayWeekIsClampedIntoTerm() {
        #expect(semester.displayWeek(on: LocalDate(year: 2026, month: 7, day: 1)) == 1)
        #expect(semester.displayWeek(on: LocalDate(year: 2026, month: 9, day: 24)) == 4)
        #expect(semester.displayWeek(on: LocalDate(year: 2027, month: 3, day: 1)) == 18)
    }

    @Test func dateOfWeekdayInWeek() {
        #expect(semester.date(of: .monday, inWeek: 1) == LocalDate(year: 2026, month: 8, day: 31))
        #expect(semester.date(of: .friday, inWeek: 3) == LocalDate(year: 2026, month: 9, day: 18))
        #expect(semester.date(of: .sunday, inWeek: 18) == semester.lastDay)
    }

    @Test func settingStartDateNormalises() {
        var copy = semester
        copy.startDate = LocalDate(year: 2027, month: 2, day: 27)  // Saturday
        #expect(copy.firstMonday == LocalDate(year: 2027, month: 2, day: 22))
    }

    @Test func totalWeeksIsAtLeastOne() {
        #expect(Semester(startingOn: LocalDate(year: 2026, month: 9, day: 1), totalWeeks: 0).totalWeeks == 1)
    }

    @Test func codableNormalisesStart() throws {
        let json = #"{"firstMonday":"2026-09-02","totalWeeks":18}"#
        let decoded = try JSONDecoder().decode(Semester.self, from: Data(json.utf8))
        #expect(decoded == semester)
        let reencoded = try JSONDecoder().decode(Semester.self, from: JSONEncoder().encode(decoded))
        #expect(reencoded == semester)
    }
}

@Suite struct PeriodScheduleTests {
    let standard = PeriodSchedule.standard

    @Test func standardScheduleIsValid() {
        #expect(standard.count == 12)
        #expect(standard.issues.isEmpty)
        #expect(standard.period(1)?.start == TimeOfDay(hour: 8, minute: 0))
        #expect(standard.period(12)?.end == TimeOfDay(hour: 22, minute: 30))
        #expect(standard.period(0) == nil)
        #expect(standard.period(13) == nil)
    }

    @Test func timeSpanCoversFirstToLastPeriod() throws {
        let span = try #require(standard.timeSpan(of: 1...2))
        #expect(span.start.description == "08:00")
        #expect(span.end.description == "09:40")
        #expect(standard.timeSpan(of: 11...13) == nil)
    }

    @Test func detectsBrokenSchedules() {
        #expect(PeriodSchedule(periods: []).issues == [.empty])
        var broken = standard
        broken.periods[2].end = broken.periods[2].start  // period 3 has zero length
        broken.periods[4].start = TimeOfDay(hour: 11, minute: 0)  // period 5 starts inside period 4
        #expect(broken.issues == [.endsBeforeStart(period: 3), .overlapsPrevious(period: 5)])
    }

    @Test func appendingKeepsLengthAndBreak() throws {
        let longer = standard.appendingPeriod()
        #expect(longer.count == 13)
        let added = try #require(longer.period(13))
        #expect(added.start.description == "22:40")
        #expect(added.end.description == "23:25")
        #expect(longer.issues.isEmpty)
    }

    @Test func appendingStopsAtMaximum() {
        var schedule = PeriodSchedule(periods: [])
        for _ in 0..<(PeriodSchedule.maxPeriods + 3) { schedule = schedule.appendingPeriod() }
        #expect(schedule.count == PeriodSchedule.maxPeriods)
    }

    @Test func codableUsesReadableTimes() throws {
        let json = String(decoding: try JSONEncoder().encode(PeriodSchedule(periods: [standard.periods[0]])), as: UTF8.self)
        #expect(json.contains("08:00"))
        #expect(json.contains("08:45"))
        let decoded = try JSONDecoder().decode(PeriodSchedule.self, from: JSONEncoder().encode(standard))
        #expect(decoded == standard)
    }
}
