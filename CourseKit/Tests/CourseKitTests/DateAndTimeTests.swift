import Foundation
import Testing
@testable import CourseKit

@Suite struct LocalDateTests {
    @Test func epochIsThursdayJanuaryFirst1970() {
        let epoch = LocalDate(daysSinceEpoch: 0)
        #expect(epoch == LocalDate(year: 1970, month: 1, day: 1))
        #expect(epoch.weekday == .thursday)
    }

    @Test(arguments: [
        ("2026-09-24", Weekday.thursday),
        ("2026-09-28", .monday),
        ("2024-02-29", .thursday),
        ("2000-01-01", .saturday),
        ("1969-12-31", .wednesday),
    ])
    func weekdayOfKnownDates(text: String, expected: Weekday) throws {
        let date = try #require(LocalDate(text))
        #expect(date.weekday == expected)
    }

    @Test func civilConversionRoundTripsOverCenturies() {
        for days in stride(from: -200_000, through: 200_000, by: 37) {
            let date = LocalDate(daysSinceEpoch: days)
            #expect(date.daysSinceEpoch == days)
            #expect((1...12).contains(date.month))
        }
    }

    @Test func consecutiveDaysAreOneApart() {
        var date = LocalDate(year: 2023, month: 12, day: 25)
        for _ in 0..<800 {
            let next = date.adding(days: 1)
            #expect(date.days(to: next) == 1)
            #expect(next > date)
            date = next
        }
        #expect(date == LocalDate(year: 2026, month: 3, day: 4))
    }

    @Test func outOfRangeComponentsRollOver() {
        #expect(LocalDate(year: 2026, month: 2, day: 30) == LocalDate(year: 2026, month: 3, day: 2))
        #expect(LocalDate(year: 2026, month: 13, day: 1) == LocalDate(year: 2027, month: 1, day: 1))
        #expect(LocalDate(year: 2026, month: 1, day: 0) == LocalDate(year: 2025, month: 12, day: 31))
        #expect(LocalDate(year: 2026, month: 0, day: 15) == LocalDate(year: 2025, month: 12, day: 15))
    }

    @Test func parsingRejectsImpossibleDates() {
        #expect(LocalDate("2024-02-29") != nil)
        #expect(LocalDate("2000-02-29") != nil)
        #expect(LocalDate("2026-02-29") == nil)
        #expect(LocalDate("1900-02-29") == nil)
        #expect(LocalDate("2026-13-01") == nil)
        #expect(LocalDate("2026-9") == nil)
        #expect(LocalDate("hello") == nil)
    }

    @Test func descriptionIsZeroPaddedISO() {
        #expect(LocalDate(year: 2026, month: 9, day: 7).description == "2026-09-07")
        #expect(LocalDate("2026-09-07")?.description == "2026-09-07")
    }

    @Test func startOfWeekIsMonday() {
        let thursday = LocalDate(year: 2026, month: 9, day: 24)
        #expect(thursday.startOfWeek == LocalDate(year: 2026, month: 9, day: 21))
        let sunday = LocalDate(year: 2026, month: 9, day: 27)
        #expect(sunday.startOfWeek == LocalDate(year: 2026, month: 9, day: 21))
        let monday = LocalDate(year: 2026, month: 9, day: 21)
        #expect(monday.startOfWeek == monday)
    }

    @Test func convertsFromDateInGivenTimeZone() {
        let shanghai = Calendar.fixed(hoursFromGMT: 8)
        let epoch = Date(timeIntervalSince1970: 0)
        #expect(LocalDate(epoch, calendar: shanghai) == LocalDate(year: 1970, month: 1, day: 1))
        #expect(TimeOfDay(epoch, calendar: shanghai) == TimeOfDay(hour: 8, minute: 0))

        let lateEvening = Date(timeIntervalSince1970: -4 * 3600)  // 1969-12-31 20:00 UTC
        #expect(LocalDate(lateEvening, calendar: shanghai) == LocalDate(year: 1970, month: 1, day: 1))
        #expect(LocalDate(lateEvening, calendar: .fixed(hoursFromGMT: 0)) == LocalDate(year: 1969, month: 12, day: 31))
    }

    @Test func dateAtTimeRoundTrips() {
        let calendar = Calendar.fixed(hoursFromGMT: 8)
        let moment = Moment(day: LocalDate(year: 2026, month: 9, day: 24), time: TimeOfDay(hour: 14, minute: 55))
        let date = moment.date(calendar: calendar)
        #expect(Moment(date, calendar: calendar) == moment)
    }
}

@Suite struct TimeOfDayTests {
    @Test func parsesAndFormats() throws {
        let time = try #require(TimeOfDay("8:05"))
        #expect(time.hour == 8)
        #expect(time.minute == 5)
        #expect(time.description == "08:05")
        #expect(TimeOfDay("23:59")?.minutes == 23 * 60 + 59)
    }

    @Test(arguments: ["24:00", "12:60", "7", "07:5", "ab:cd", "-1:00", ""])
    func rejectsInvalidText(text: String) {
        #expect(TimeOfDay(text) == nil)
    }

    @Test func wrapsAroundMidnight() {
        #expect(TimeOfDay(hour: 23, minute: 50).adding(minutes: 20) == TimeOfDay(hour: 0, minute: 10))
        #expect(TimeOfDay(minutes: -10) == TimeOfDay(hour: 23, minute: 50))
    }

    @Test func ordersByTime() {
        #expect(TimeOfDay(hour: 8, minute: 0) < TimeOfDay(hour: 8, minute: 1))
        let early = Moment(day: LocalDate(year: 2026, month: 9, day: 24), time: TimeOfDay(hour: 23, minute: 0))
        let late = Moment(day: LocalDate(year: 2026, month: 9, day: 25), time: TimeOfDay(hour: 1, minute: 0))
        #expect(early < late)
    }
}

extension Calendar {
    static func fixed(hoursFromGMT hours: Int) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: hours * 3600)!
        return calendar
    }
}
