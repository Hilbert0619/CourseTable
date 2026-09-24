/// Day of the week, numbered Monday = 1 … Sunday = 7 (ISO 8601), the way timetables are laid out.
public enum Weekday: Int, CaseIterable, Codable, Comparable, Identifiable, Sendable {
    case monday = 1, tuesday, wednesday, thursday, friday, saturday, sunday

    public var id: Int { rawValue }

    public var isWeekend: Bool { self == .saturday || self == .sunday }

    /// Foundation `Calendar` numbering, where 1 = Sunday … 7 = Saturday.
    public var calendarWeekday: Int { self == .sunday ? 1 : rawValue + 1 }

    public init?(calendarWeekday: Int) {
        guard (1...7).contains(calendarWeekday) else { return nil }
        self.init(rawValue: calendarWeekday == 1 ? 7 : calendarWeekday - 1)
    }

    public static func < (lhs: Weekday, rhs: Weekday) -> Bool { lhs.rawValue < rhs.rawValue }
}
