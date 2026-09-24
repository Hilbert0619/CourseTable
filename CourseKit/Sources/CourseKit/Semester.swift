/// A term: when teaching week 1 starts and how many weeks it runs.
///
/// Weeks run Monday–Sunday. Week 1 is the week containing the chosen start day, so picking a
/// Wednesday still makes that whole Monday–Sunday week "week 1".
public struct Semester: Hashable, Sendable {
    /// Monday of week 1.
    public private(set) var firstMonday: LocalDate
    public var totalWeeks: Int

    public static let defaultTotalWeeks = 20
    public static let maxWeeks = 30

    public init(startingOn startDay: LocalDate, totalWeeks: Int = Semester.defaultTotalWeeks) {
        self.firstMonday = startDay.startOfWeek
        self.totalWeeks = max(1, totalWeeks)
    }

    /// The start day as shown to the user (always the Monday of week 1).
    public var startDate: LocalDate {
        get { firstMonday }
        set { firstMonday = newValue.startOfWeek }
    }

    /// Sunday of the last week.
    public var lastDay: LocalDate { firstMonday.adding(days: totalWeeks * 7 - 1) }

    public var weeks: ClosedRange<Int> { 1...totalWeeks }

    /// Teaching-week number of `day`. Zero or negative before term, above `totalWeeks` after it.
    public func week(containing day: LocalDate) -> Int {
        firstMonday.days(to: day).floorDiv(7) + 1
    }

    public func status(on day: LocalDate) -> SemesterStatus {
        let current = week(containing: day)
        if current < 1 { return .notStarted(daysUntilStart: day.days(to: firstMonday)) }
        if current > totalWeeks { return .finished }
        return .inProgress(week: current)
    }

    /// The week to show by default on `day`: the current week, clamped into the term.
    public func displayWeek(on day: LocalDate) -> Int {
        min(max(week(containing: day), 1), totalWeeks)
    }

    /// The date of `weekday` in teaching week `week`.
    public func date(of weekday: Weekday, inWeek week: Int) -> LocalDate {
        firstMonday.adding(days: (week - 1) * 7 + weekday.rawValue - 1)
    }
}

public enum SemesterStatus: Hashable, Sendable {
    case notStarted(daysUntilStart: Int)
    case inProgress(week: Int)
    case finished

    public var week: Int? {
        if case .inProgress(let week) = self { return week }
        return nil
    }
}

extension Semester: Codable {
    private enum CodingKeys: String, CodingKey { case firstMonday, totalWeeks }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            startingOn: try container.decode(LocalDate.self, forKey: .firstMonday),
            totalWeeks: try container.decode(Int.self, forKey: .totalWeeks)
        )
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(firstMonday, forKey: .firstMonday)
        try container.encode(totalWeeks, forKey: .totalWeeks)
    }
}
