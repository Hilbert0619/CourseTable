/// The start and end time of one class period (节).
public struct ClassPeriod: Hashable, Codable, Sendable {
    public var start: TimeOfDay
    public var end: TimeOfDay

    public init(start: TimeOfDay, end: TimeOfDay) {
        self.start = start
        self.end = end
    }

    public var durationMinutes: Int { end.minutes - start.minutes }
}

/// The school's bell schedule: period 1, period 2, … with their times.
public struct PeriodSchedule: Hashable, Codable, Sendable {
    /// `periods[0]` is period 1.
    public var periods: [ClassPeriod]

    public static let maxPeriods = 16

    public init(periods: [ClassPeriod]) {
        self.periods = periods
    }

    /// A typical 12-period university day: four morning, four afternoon, four evening periods.
    public static let standard = PeriodSchedule(periods: [
        ("08:00", "08:45"), ("08:55", "09:40"), ("10:00", "10:45"), ("10:55", "11:40"),
        ("14:00", "14:45"), ("14:55", "15:40"), ("16:00", "16:45"), ("16:55", "17:40"),
        ("19:00", "19:45"), ("19:55", "20:40"), ("20:50", "21:35"), ("21:45", "22:30"),
    ].map { ClassPeriod(start: TimeOfDay($0.0)!, end: TimeOfDay($0.1)!) })

    public var count: Int { periods.count }

    /// Period `number` (1-based), if it exists.
    public func period(_ number: Int) -> ClassPeriod? {
        periods.indices.contains(number - 1) ? periods[number - 1] : nil
    }

    /// Start of the first period to end of the last, for a class occupying `numbers`.
    public func timeSpan(of numbers: ClosedRange<Int>) -> (start: TimeOfDay, end: TimeOfDay)? {
        guard let first = period(numbers.lowerBound), let last = period(numbers.upperBound) else { return nil }
        return (first.start, last.end)
    }

    /// A new period after the last one, keeping its length and a 10-minute break.
    public func appendingPeriod() -> PeriodSchedule {
        guard periods.count < Self.maxPeriods else { return self }
        let last = periods.last ?? ClassPeriod(start: TimeOfDay(hour: 7, minute: 15), end: TimeOfDay(hour: 7, minute: 50))
        let start = last.end.adding(minutes: 10)
        var copy = self
        copy.periods.append(ClassPeriod(start: start, end: start.adding(minutes: max(last.durationMinutes, 5))))
        return copy
    }

    /// Problems that make the schedule unusable or ambiguous.
    public var issues: [PeriodScheduleIssue] {
        guard !periods.isEmpty else { return [.empty] }
        var issues: [PeriodScheduleIssue] = []
        for (index, period) in periods.enumerated() {
            let number = index + 1
            if period.end <= period.start {
                issues.append(.endsBeforeStart(period: number))
            }
            if index > 0, period.start < periods[index - 1].end {
                issues.append(.overlapsPrevious(period: number))
            }
        }
        return issues
    }
}

public enum PeriodScheduleIssue: Hashable, Sendable {
    case empty
    case endsBeforeStart(period: Int)
    case overlapsPrevious(period: Int)
}
