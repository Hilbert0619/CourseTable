import Testing
@testable import CourseKit

/// The timetable's current-time line: where a time falls on the period rows.
@Suite struct GridPositionTests {
    let standard = PeriodSchedule.standard

    func position(_ text: String, in schedule: PeriodSchedule? = nil) -> Double? {
        (schedule ?? standard).gridPosition(of: TimeOfDay(text)!)
    }

    @Test func insideAPeriodTheLineMovesThroughItsRow() {
        #expect(position("08:00") == 0)
        #expect(abs(position("08:15")! - 1.0 / 3) < 1e-9)
        #expect(position("08:45") == 1)
        #expect(abs(position("10:30")! - (2 + 30.0 / 45)) < 1e-9)
    }

    @Test func duringABreakTheLineRestsBetweenPeriods() {
        #expect(position("08:50") == 1)
        #expect(position("08:55") == 1)
        #expect(position("09:50") == 2)
        // Lunch, after period 4.
        #expect(position("12:30") == 4)
        #expect(position("18:30") == 8)
    }

    @Test func noLineOutsideTheDay() {
        #expect(position("07:59") == nil)
        #expect(position("22:30") == 12)
        #expect(position("22:31") == nil)
        #expect(position("08:00", in: PeriodSchedule(periods: [])) == nil)
    }

    @Test func aZeroLengthPeriodDoesNotDivideByZero() {
        let odd = PeriodSchedule(periods: [ClassPeriod(start: TimeOfDay("08:00")!, end: TimeOfDay("08:00")!),
                                           ClassPeriod(start: TimeOfDay("09:00")!, end: TimeOfDay("09:40")!)])
        #expect(position("08:00", in: odd) == 0)
        #expect(position("08:30", in: odd) == 1)
        #expect(position("09:20", in: odd) == 1.5)
    }
}
