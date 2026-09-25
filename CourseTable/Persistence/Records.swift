import Foundation
import SwiftData
import CourseKit

/// A stored course. Mirrors `CourseKit.Course` field by field so SwiftData can query and migrate it.
@Model
final class CourseRecord {
    @Attribute(.unique) var id: UUID
    var name: String
    var teacher: String
    var room: String
    var weekday: Int
    var startPeriod: Int
    var periodCount: Int
    var firstWeek: Int
    var lastWeek: Int
    var parity: String
    var colorIndex: Int
    var createdAt: Date

    init(_ course: Course) {
        id = course.id
        name = course.name
        teacher = course.teacher
        room = course.room
        weekday = course.weekday.rawValue
        startPeriod = course.startPeriod
        periodCount = course.periodCount
        firstWeek = course.weeks.first
        lastWeek = course.weeks.last
        parity = course.weeks.parity.rawValue
        colorIndex = course.colorIndex
        createdAt = .now
    }

    var course: Course {
        Course(
            id: id,
            name: name,
            teacher: teacher,
            room: room,
            weekday: Weekday(rawValue: weekday) ?? .monday,
            startPeriod: startPeriod,
            periodCount: periodCount,
            weeks: CourseWeeks(first: firstWeek, last: lastWeek, parity: WeekParity(rawValue: parity) ?? .every),
            colorIndex: colorIndex
        )
    }

    func update(from course: Course) {
        name = course.name
        teacher = course.teacher
        room = course.room
        weekday = course.weekday.rawValue
        startPeriod = course.startPeriod
        periodCount = course.periodCount
        firstWeek = course.weeks.first
        lastWeek = course.weeks.last
        parity = course.weeks.parity.rawValue
        colorIndex = course.colorIndex
    }
}

/// An entry in the course list: a course typed as name, room and teacher, waiting to be placed on
/// the grid. Placing it creates `CourseRecord`s; the entry stays so the course can be placed again.
@Model
final class CourseTemplateRecord {
    @Attribute(.unique) var id: UUID
    var name: String
    var room: String
    var teacher: String
    var colorIndex: Int
    var createdAt: Date

    init(_ template: CourseTemplate, createdAt: Date = .now) {
        id = template.id
        name = template.name
        room = template.room
        teacher = template.teacher
        colorIndex = template.colorIndex
        self.createdAt = createdAt
    }

    var template: CourseTemplate {
        CourseTemplate(id: id, name: name, room: room, teacher: teacher, colorIndex: colorIndex)
    }
}

/// App-wide settings: the term and the bell schedule. There is exactly one row.
@Model
final class SettingsRecord {
    /// Monday of week 1, as `YYYY-MM-DD`.
    var semesterStart: String
    var totalWeeks: Int
    /// JSON-encoded `PeriodSchedule`.
    var periodsData: Data

    init(semester: Semester, periods: PeriodSchedule) {
        semesterStart = semester.firstMonday.description
        totalWeeks = semester.totalWeeks
        periodsData = (try? JSONEncoder().encode(periods)) ?? Data()
    }

    var semester: Semester {
        get {
            Semester(startingOn: LocalDate(semesterStart) ?? LocalDate(.now), totalWeeks: totalWeeks)
        }
        set {
            semesterStart = newValue.firstMonday.description
            totalWeeks = newValue.totalWeeks
        }
    }

    var periods: PeriodSchedule {
        get { (try? JSONDecoder().decode(PeriodSchedule.self, from: periodsData)) ?? .standard }
        set { periodsData = (try? JSONEncoder().encode(newValue)) ?? periodsData }
    }
}
