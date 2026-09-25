import Foundation
import SwiftData
import Testing
import CourseKit
@testable import CourseTable

@Suite struct ClassReminderPayloadTests {
    let calculus = Course(
        name: "Calculus", teacher: "Li", room: "A101", weekday: .monday, startPeriod: 1, periodCount: 2,
        weeks: CourseWeeks(first: 1, last: 16)
    )

    func firstReminder(leadMinutes: Int) throws -> ClassReminder {
        let timetable = Timetable(semester: Semester(startingOn: LocalDate(year: 2026, month: 8, day: 31)), courses: [calculus])
        let now = Moment(day: LocalDate(year: 2026, month: 8, day: 31), time: TimeOfDay(hour: 7, minute: 0))
        return try #require(timetable.upcomingReminders(after: now, leadMinutes: leadMinutes, limit: 1).first)
    }

    @Test func payloadDescribesTheClass() throws {
        let reminder = try firstReminder(leadMinutes: 10)
        let payload = ClassReminders.payload(for: reminder)
        #expect(payload.id == reminder.id)
        #expect(payload.title == "Calculus")
        #expect(payload.body.hasSuffix("08:00–09:40 · A101 · Li"))
        #expect(payload.body.contains("10"))
        #expect(payload.fireDate == DateComponents(year: 2026, month: 8, day: 31, hour: 7, minute: 50))
    }

    @Test func zeroLeadFiresAtTheStart() throws {
        let payload = ClassReminders.payload(for: try firstReminder(leadMinutes: 0))
        #expect(payload.fireDate == DateComponents(year: 2026, month: 8, day: 31, hour: 8, minute: 0))
        #expect(payload.body.hasSuffix("08:00–09:40 · A101 · Li"))
    }

    @Test func leadChoicesHaveDistinctLabels() {
        let labels = ReminderSettings.leadChoices.map(ReminderSettings.label(forLeadMinutes:))
        #expect(Set(labels).count == labels.count)
        #expect(ReminderSettings.leadChoices.contains(ReminderSettings.defaultLeadMinutes))
    }
}

@Suite struct ImportAppTests {
    @Test func decodesGB18030FromExcel() throws {
        let gb18030 = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(
            CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue)
        ))
        let text = "课程,星期,节次,周次\n高等数学,周一,1-2,1-16周"
        let data = try #require(text.data(using: gb18030))
        #expect(String(data: data, encoding: .utf8) == nil)
        let decoded = try #require(ImportedText.decode(data))
        #expect(decoded == text)
        #expect(CourseImporter(periods: .standard, totalWeeks: 16).parse(decoded).courses.first?.name == "高等数学")
    }

    @Test func everyProblemHasALineNumberedMessage() {
        let kinds: [ImportProblem.Kind] = [
            .missingColumns([.weekday, .periods]),
            .missingValue(.name),
            .invalidValue(.weeks, "abc"),
            .periodsOutOfRange("13-14", periodsPerDay: 12),
        ]
        let messages = kinds.map { ImportProblem(line: 7, kind: $0).message }
        #expect(Set(messages).count == kinds.count)
        #expect(messages.allSatisfy { $0.contains("7") && $0.count > 10 })
        #expect(messages[2].contains("abc"))
        #expect(Set(ImportColumn.allCases.map(\.displayName)).count == ImportColumn.allCases.count)
    }

    @Test func weekSummaries() {
        #expect(CourseWeeks(first: 1, last: 16).summary == "Weeks 1–16")
        #expect(CourseWeeks(first: 5, last: 5).summary == "Week 5")
        #expect(CourseWeeks(first: 1, last: 15, parity: .odd).summary == "Weeks 1–15 · Odd weeks")
    }

    @MainActor
    @Test func importedCoursesAreStored() throws {
        let container = try Persistence.makeContainer(inMemory: true)
        let context = container.mainContext
        let settings = Persistence.settings(in: context, today: LocalDate(year: 2026, month: 9, day: 1))
        let timetable = Persistence.timetable(settings: settings, courses: [])
        let result = CourseImporter(for: timetable).parse(CourseCSV.englishExample)
        let plan = timetable.planningImport(of: result.courses)
        for course in plan.added {
            context.insert(CourseRecord(course))
        }
        try context.save()
        let records = try context.fetch(FetchDescriptor<CourseRecord>())
        #expect(records.count == 5)
        #expect(Set(records.map(\.name)) == ["Advanced Mathematics", "College English", "Physics Lab", "Data Structures"])
    }
}
