import Foundation
import SwiftData
import Testing
import CourseKit
@testable import CourseTable

@MainActor
@Suite struct CourseListStoreTests {
    @Test func templateRecordRoundTrips() {
        let template = CourseTemplate(name: "高等数学", room: "A101", teacher: "李老师", colorIndex: 3)
        let record = CourseTemplateRecord(template)
        #expect(record.template == template)
        #expect(record.id == template.id)
    }

    @Test func typedListIsStoredInOrderAndCanBePlaced() throws {
        let container = try Persistence.makeContainer(inMemory: true)
        let context = container.mainContext
        let settings = Persistence.settings(in: context, today: LocalDate(year: 2026, month: 9, day: 1))
        let timetable = Persistence.timetable(settings: settings, courses: [])

        let typed = timetable.assigningColors(
            to: CourseTemplate.parseList("高等数学 A101 李老师\n大学英语 B204 王老师\n体育"), saved: []
        )
        let now = Date.now
        for (index, template) in typed.enumerated() {
            context.insert(CourseTemplateRecord(template, createdAt: now.addingTimeInterval(Double(index) / 1000)))
        }
        try context.save()

        let stored = try context.fetch(FetchDescriptor<CourseTemplateRecord>(sortBy: [SortDescriptor(\.createdAt)]))
        #expect(stored.map(\.name) == ["高等数学", "大学英语", "体育"])
        #expect(Set(stored.map(\.colorIndex)).count == 3)

        let course = timetable.placing(stored[0].template, on: .monday, at: 1, periodCount: 2)
        context.insert(CourseRecord(course))
        try context.save()

        let after = Persistence.timetable(settings: settings, courses: try context.fetch(FetchDescriptor<CourseRecord>()))
        #expect(after.courses.map(\.name) == ["高等数学"])
        #expect(after.courses.first?.room == "A101")
        #expect(after.courses.first?.colorIndex == stored[0].colorIndex)
        // The placed course is covered by its list entry, so the tray still shows three courses.
        #expect(after.placementTemplates(saved: stored.map(\.template)).count == 3)
    }
}
