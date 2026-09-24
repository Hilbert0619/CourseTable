import Foundation

/// A calendar day with no time or time zone attached, such as the first day of term.
///
/// Day arithmetic is plain integer math on the proleptic Gregorian calendar, so results never
/// depend on the device's time zone or daylight-saving rules.
public struct LocalDate: Hashable, Comparable, Sendable {
    public let year: Int
    public let month: Int
    public let day: Int

    /// Out-of-range values roll over, e.g. February 30 becomes March 1 or 2.
    public init(year: Int, month: Int, day: Int) {
        let zeroBasedMonth = month - 1
        let normalizedYear = year + zeroBasedMonth.floorDiv(12)
        let normalizedMonth = zeroBasedMonth.floorMod(12) + 1
        self.init(daysSinceEpoch: Self.daysFromCivil(normalizedYear, normalizedMonth, 1) + day - 1)
    }

    /// Creates the date `days` days after 1970-01-01.
    public init(daysSinceEpoch days: Int) {
        (year, month, day) = Self.civilFromDays(days)
    }

    /// The calendar day that `date` falls on in `calendar`'s time zone.
    public init(_ date: Date, calendar: Calendar = .current) {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 1970, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    /// Number of days since 1970-01-01 (negative before it).
    public var daysSinceEpoch: Int { Self.daysFromCivil(year, month, day) }

    public var weekday: Weekday {
        // 1970-01-01 was a Thursday.
        Weekday(rawValue: (daysSinceEpoch + 3).floorMod(7) + 1)!
    }

    public func adding(days: Int) -> LocalDate {
        LocalDate(daysSinceEpoch: daysSinceEpoch + days)
    }

    /// Whole days from `self` to `other` (positive when `other` is later).
    public func days(to other: LocalDate) -> Int {
        other.daysSinceEpoch - daysSinceEpoch
    }

    /// The Monday of the Monday–Sunday week containing this day.
    public var startOfWeek: LocalDate {
        adding(days: -(weekday.rawValue - 1))
    }

    /// The moment this day reaches `time` in `calendar`'s time zone.
    public func date(at time: TimeOfDay = .midnight, calendar: Calendar = .current) -> Date {
        let parts = DateComponents(year: year, month: month, day: day, hour: time.hour, minute: time.minute)
        return calendar.date(from: parts) ?? Date(timeIntervalSince1970: TimeInterval(daysSinceEpoch) * 86_400)
    }

    public static func < (lhs: LocalDate, rhs: LocalDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    // Howard Hinnant's days_from_civil / civil_from_days algorithms.
    static func daysFromCivil(_ year: Int, _ month: Int, _ day: Int) -> Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yearOfEra = y - era * 400
        let shiftedMonth = (month + 9) % 12
        let dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
        let dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }

    static func civilFromDays(_ days: Int) -> (year: Int, month: Int, day: Int) {
        let z = days + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let dayOfEra = z - era * 146_097
        let yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        let dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        let shiftedMonth = (5 * dayOfYear + 2) / 153
        let day = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1
        let month = shiftedMonth < 10 ? shiftedMonth + 3 : shiftedMonth - 9
        let year = yearOfEra + era * 400 + (month <= 2 ? 1 : 0)
        return (year, month, day)
    }
}

extension LocalDate: CustomStringConvertible, LosslessStringConvertible {
    /// ISO 8601 form, e.g. `2026-09-07`.
    public var description: String {
        let y = String(year)
        let m = month < 10 ? "0\(month)" : "\(month)"
        let d = day < 10 ? "0\(day)" : "\(day)"
        return "\(y)-\(m)-\(d)"
    }

    /// Parses `YYYY-MM-DD`; rejects impossible dates such as `2026-02-30`.
    public init?(_ description: String) {
        let parts = description.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3,
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2]),
              (1...12).contains(month), day >= 1
        else { return nil }
        let date = LocalDate(year: year, month: month, day: day)
        guard date.year == year, date.month == month, date.day == day else { return nil }
        self = date
    }
}

extension LocalDate: Codable {
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let text = try container.decode(String.self)
        guard let date = LocalDate(text) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid date \"\(text)\"; expected YYYY-MM-DD")
        }
        self = date
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}

extension Int {
    /// Division rounding toward negative infinity.
    func floorDiv(_ divisor: Int) -> Int {
        let q = self / divisor
        return (self % divisor != 0 && (self < 0) != (divisor < 0)) ? q - 1 : q
    }

    /// Remainder with the sign of the divisor, so `(-1).floorMod(7) == 6`.
    func floorMod(_ divisor: Int) -> Int {
        let r = self % divisor
        return r != 0 && (r < 0) != (divisor < 0) ? r + divisor : r
    }
}
