import Foundation

/// Which weeks inside a course's week range it actually meets.
public enum WeekParity: String, CaseIterable, Codable, Sendable {
    case every, odd, even

    public func matches(_ week: Int) -> Bool {
        switch self {
        case .every: true
        case .odd: week % 2 != 0
        case .even: week % 2 == 0
        }
    }
}

/// The teaching weeks a course meets in, e.g. "weeks 1–16, odd weeks only".
public struct CourseWeeks: Hashable, Codable, Sendable {
    public var first: Int
    public var last: Int
    public var parity: WeekParity

    public init(first: Int, last: Int, parity: WeekParity = .every) {
        self.first = first
        self.last = last
        self.parity = parity
    }

    public func contains(_ week: Int) -> Bool {
        week >= first && week <= last && parity.matches(week)
    }

    /// Every week the course meets, in order.
    public var all: [Int] {
        guard first <= last else { return [] }
        return (first...last).filter(parity.matches)
    }
}

/// One recurring class in the timetable.
public struct Course: Identifiable, Hashable, Codable, Sendable {
    public var id: UUID
    public var name: String
    public var teacher: String
    public var room: String
    public var weekday: Weekday
    /// First period (1-based).
    public var startPeriod: Int
    /// Number of consecutive periods, at least 1.
    public var periodCount: Int
    public var weeks: CourseWeeks
    /// Index into the app's colour palette.
    public var colorIndex: Int

    public init(
        id: UUID = UUID(),
        name: String,
        teacher: String = "",
        room: String = "",
        weekday: Weekday,
        startPeriod: Int,
        periodCount: Int = 2,
        weeks: CourseWeeks,
        colorIndex: Int = 0
    ) {
        self.id = id
        self.name = name
        self.teacher = teacher
        self.room = room
        self.weekday = weekday
        self.startPeriod = startPeriod
        self.periodCount = periodCount
        self.weeks = weeks
        self.colorIndex = colorIndex
    }

    public var endPeriod: Int { startPeriod + max(periodCount, 1) - 1 }

    public var periods: ClosedRange<Int> { startPeriod...endPeriod }

    public func meets(inWeek week: Int) -> Bool { weeks.contains(week) }

    /// True if both courses would be in the same slot in at least one week.
    public func clashes(with other: Course) -> Bool {
        guard other.id != id, other.weekday == weekday, other.periods.overlaps(periods) else { return false }
        let firstShared = max(weeks.first, other.weeks.first)
        let lastShared = min(weeks.last, other.weeks.last)
        guard firstShared <= lastShared else { return false }
        return (firstShared...lastShared).contains { meets(inWeek: $0) && other.meets(inWeek: $0) }
    }

    /// Problems with this course given the bell schedule and term length.
    public func issues(periodsPerDay: Int, totalWeeks: Int) -> [CourseIssue] {
        var issues: [CourseIssue] = []
        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            issues.append(.missingName)
        }
        if startPeriod < 1 || periodCount < 1 || endPeriod > periodsPerDay {
            issues.append(.periodsOutOfRange(periodsPerDay: periodsPerDay))
        }
        if weeks.first < 1 || weeks.last < weeks.first {
            issues.append(.invalidWeekRange)
        } else if weeks.all.isEmpty {
            issues.append(.noMatchingWeeks)
        } else if weeks.last > totalWeeks {
            issues.append(.weeksBeyondSemester(totalWeeks: totalWeeks))
        }
        return issues
    }
}

public enum CourseIssue: Hashable, Sendable {
    case missingName
    case periodsOutOfRange(periodsPerDay: Int)
    case invalidWeekRange
    /// e.g. "week 2 only, odd weeks": the course would never meet.
    case noMatchingWeeks
    /// The course runs past the end of term; allowed, but worth a warning.
    case weeksBeyondSemester(totalWeeks: Int)

    /// Whether the course can't be saved until this is fixed.
    public var isBlocking: Bool {
        switch self {
        case .weeksBeyondSemester: false
        default: true
        }
    }
}

extension Course {
    public static let paletteSize = 10

    /// The palette colour used least by `existing` courses (lowest index on ties).
    public static func suggestedColorIndex(avoiding existing: [Course]) -> Int {
        var usage = Array(repeating: 0, count: paletteSize)
        for course in existing where usage.indices.contains(course.colorIndex) {
            usage[course.colorIndex] += 1
        }
        return usage.indices.min { usage[$0] < usage[$1] } ?? 0
    }
}
