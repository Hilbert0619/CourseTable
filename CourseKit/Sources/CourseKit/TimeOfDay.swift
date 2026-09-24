import Foundation

/// A wall-clock time within a day, to the minute (00:00 … 23:59).
public struct TimeOfDay: Hashable, Comparable, Sendable {
    /// Minutes since midnight, `0..<1440`.
    public let minutes: Int

    public static let midnight = TimeOfDay(minutes: 0)

    /// Values outside a day wrap around, so 24:10 becomes 00:10.
    public init(minutes: Int) {
        self.minutes = minutes.floorMod(24 * 60)
    }

    public init(hour: Int, minute: Int) {
        self.init(minutes: hour * 60 + minute)
    }

    /// The time of day of `date` in `calendar`'s time zone.
    public init(_ date: Date, calendar: Calendar = .current) {
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        self.init(hour: parts.hour ?? 0, minute: parts.minute ?? 0)
    }

    public var hour: Int { minutes / 60 }
    public var minute: Int { minutes % 60 }

    public func adding(minutes delta: Int) -> TimeOfDay {
        TimeOfDay(minutes: minutes + delta)
    }

    public static func < (lhs: TimeOfDay, rhs: TimeOfDay) -> Bool { lhs.minutes < rhs.minutes }
}

extension TimeOfDay: CustomStringConvertible, LosslessStringConvertible {
    /// 24-hour form, e.g. `08:05`.
    public var description: String {
        let h = hour < 10 ? "0\(hour)" : "\(hour)"
        let m = minute < 10 ? "0\(minute)" : "\(minute)"
        return "\(h):\(m)"
    }

    /// Parses `H:MM` or `HH:MM` (24-hour).
    public init?(_ description: String) {
        let parts = description.split(separator: ":", omittingEmptySubsequences: false)
        guard parts.count == 2, parts[1].count == 2,
              let hour = Int(parts[0]), let minute = Int(parts[1]),
              (0..<24).contains(hour), (0..<60).contains(minute)
        else { return nil }
        self.init(hour: hour, minute: minute)
    }
}

extension TimeOfDay: Codable {
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let text = try container.decode(String.self)
        guard let time = TimeOfDay(text) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid time \"\(text)\"; expected HH:MM")
        }
        self = time
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}

/// A local day plus a time on it: "now" as far as the timetable is concerned.
public struct Moment: Hashable, Comparable, Sendable {
    public var day: LocalDate
    public var time: TimeOfDay

    public init(day: LocalDate, time: TimeOfDay) {
        self.day = day
        self.time = time
    }

    public init(_ date: Date, calendar: Calendar = .current) {
        self.init(day: LocalDate(date, calendar: calendar), time: TimeOfDay(date, calendar: calendar))
    }

    public func date(calendar: Calendar = .current) -> Date {
        day.date(at: time, calendar: calendar)
    }

    public static func < (lhs: Moment, rhs: Moment) -> Bool {
        (lhs.day, lhs.time) < (rhs.day, rhs.time)
    }
}
