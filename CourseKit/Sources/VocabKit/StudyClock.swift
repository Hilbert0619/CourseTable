import Foundation
import CourseKit

/// Study days. A study day starts at `dayStart` (04:00 by default) instead of midnight, so late-night
/// study still counts for the evening before. Days are numbered like `LocalDate.daysSinceEpoch`: the
/// study day that starts on 2026-09-30 at 04:00 is the number of 2026-09-30.
public struct StudyClock: Sendable {
    public var calendar: Calendar
    public var dayStart: TimeOfDay

    public static let defaultDayStart = TimeOfDay(hour: 4, minute: 0)

    public init(calendar: Calendar = .current, dayStart: TimeOfDay = defaultDayStart) {
        self.calendar = calendar
        self.dayStart = dayStart
    }

    /// The study day `date` belongs to.
    public func day(of date: Date) -> Int {
        let calendarDay = LocalDate(date, calendar: calendar).daysSinceEpoch
        return TimeOfDay(date, calendar: calendar) < dayStart ? calendarDay - 1 : calendarDay
    }

    /// When study day `day` begins.
    public func start(of day: Int) -> Date {
        LocalDate(daysSinceEpoch: day).date(at: dayStart, calendar: calendar)
    }

    /// When study day `day` ends (the next one begins).
    public func end(of day: Int) -> Date {
        start(of: day + 1)
    }

    /// The calendar date a study day is named after.
    public func date(of day: Int) -> LocalDate {
        LocalDate(daysSinceEpoch: day)
    }
}
