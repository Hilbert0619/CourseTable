import Foundation
import SwiftData
import Testing
import CourseKit
@testable import CourseTable

@MainActor
@Suite struct PersistenceTests {
    let calculus = Course(
        name: "Calculus", teacher: "Li", room: "A101", weekday: .wednesday, startPeriod: 3, periodCount: 2,
        weeks: CourseWeeks(first: 2, last: 16, parity: .even), colorIndex: 4
    )

    @Test func courseRecordRoundTrips() {
        let record = CourseRecord(calculus)
        #expect(record.course == calculus)

        var changed = calculus
        changed.room = "B2"
        changed.weekday = .friday
        changed.weeks.parity = .odd
        record.update(from: changed)
        #expect(record.course == changed)
        #expect(record.id == calculus.id)
    }

    @Test func settingsRecordRoundTrips() {
        let semester = Semester(startingOn: LocalDate(year: 2026, month: 9, day: 2), totalWeeks: 18)
        let record = SettingsRecord(semester: semester, periods: .standard)
        #expect(record.semester == semester)
        #expect(record.periods == .standard)

        record.semester.startDate = LocalDate(year: 2027, month: 2, day: 24)
        #expect(record.semesterStart == "2027-02-22")

        let shorter = PeriodSchedule(periods: Array(PeriodSchedule.standard.periods.prefix(8)))
        record.periods = shorter
        #expect(record.periods == shorter)
    }

    @Test func storeKeepsOneSettingsRowAndBuildsTimetable() throws {
        let container = try Persistence.makeContainer(inMemory: true)
        let context = container.mainContext

        let settings = Persistence.settings(in: context, today: LocalDate(year: 2026, month: 9, day: 24))
        #expect(settings.semester.firstMonday == LocalDate(year: 2026, month: 9, day: 21))
        #expect(Persistence.settings(in: context) === settings)

        context.insert(CourseRecord(calculus))
        try context.save()

        let records = try context.fetch(FetchDescriptor<CourseRecord>())
        #expect(try context.fetchCount(FetchDescriptor<SettingsRecord>()) == 1)
        let timetable = Persistence.timetable(settings: settings, courses: records)
        #expect(timetable.courses == [calculus])
        #expect(timetable.semester == settings.semester)
    }
}

@Suite struct SnapshotStoreTests {
    @Test func writesAndReadsSnapshot() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).json")
        defer { try? FileManager.default.removeItem(at: url) }

        let semester = Semester(startingOn: LocalDate(year: 2026, month: 9, day: 1))
        let course = Course(name: "Physics", weekday: .monday, startPeriod: 1, weeks: CourseWeeks(first: 1, last: 16))
        let snapshot = ScheduleSnapshot(timetable: Timetable(semester: semester, courses: [course]))

        try SnapshotStore.write(snapshot, to: url)
        #expect(SnapshotStore.read(from: url) == snapshot)
    }

    @Test func missingOrCorruptFileReadsAsNil() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).json")
        #expect(SnapshotStore.read(from: url) == nil)
        #expect(SnapshotStore.read(from: nil) == nil)

        try Data("not json".utf8).write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }
        #expect(SnapshotStore.read(from: url) == nil)
    }
}

@Suite struct LocalizationTests {
    @Test func everyWeekdayHasDistinctNames() {
        #expect(Set(Weekday.allCases.map(\.shortName)).count == 7)
        #expect(Set(Weekday.allCases.map(\.fullName)).count == 7)
    }

    @Test func paletteMatchesCourseKit() {
        #expect(CoursePalette.colors.count == Course.paletteSize)
    }
}
