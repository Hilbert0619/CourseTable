import Foundation

/// What importing a list of courses would do to a timetable.
public struct ImportPlan: Hashable, Sendable {
    /// Courses that will be added, with colours assigned.
    public var added: [Course]
    /// Imported courses that are already in the timetable (or repeated in the import); skipped.
    public var duplicates: [Course]
    /// Whether the existing courses are removed first.
    public var replacesExisting: Bool
    /// The timetable after the import.
    public var timetable: Timetable
}

extension Course {
    /// The same class in the same slot: equal name, teacher, room, day, periods and weeks.
    /// Identity and colour are ignored.
    public func isSameClass(as other: Course) -> Bool {
        name == other.name && teacher == other.teacher && room == other.room && weekday == other.weekday
            && startPeriod == other.startPeriod && periodCount == other.periodCount && weeks.all == other.weeks.all
    }
}

extension Timetable {
    /// Plans adding `imported` to this timetable (or replacing its courses).
    ///
    /// Courses already present are skipped. A course whose name matches an existing or earlier
    /// imported course reuses its colour; other names get the least-used colour.
    public func planningImport(of imported: [Course], replacingExisting: Bool = false) -> ImportPlan {
        let kept = replacingExisting ? [] : courses
        var added: [Course] = []
        var duplicates: [Course] = []
        var colorByName: [String: Int] = [:]
        for course in kept where colorByName[course.name] == nil {
            colorByName[course.name] = course.colorIndex
        }

        for var course in imported {
            if (kept + added).contains(where: { $0.isSameClass(as: course) }) {
                duplicates.append(course)
                continue
            }
            if let color = colorByName[course.name] {
                course.colorIndex = color
            } else {
                course.colorIndex = Course.suggestedColorIndex(avoiding: kept + added)
                colorByName[course.name] = course.colorIndex
            }
            added.append(course)
        }

        var result = self
        result.courses = kept + added
        return ImportPlan(added: added, duplicates: duplicates, replacesExisting: replacingExisting, timetable: result)
    }
}

/// Courses as CSV, in the column order the importer assumes when there is no header.
public enum CourseCSV {
    public static func export(_ courses: [Course]) -> String {
        let header = ImportColumn.defaultOrder.map(\.rawValue).joined(separator: ",")
        let rows = courses.sorted(by: Timetable.slotOrder).map { course in
            ImportColumn.defaultOrder.map { CSV.escape(field($0, of: course)) }.joined(separator: ",")
        }
        return ([header] + rows).map { $0 + "\r\n" }.joined()
    }

    /// UTF-8 with a byte-order mark, so Excel shows Chinese text correctly.
    public static func exportData(_ courses: [Course]) -> Data {
        Data([0xEF, 0xBB, 0xBF]) + Data(export(courses).utf8)
    }

    private static func field(_ column: ImportColumn, of course: Course) -> String {
        switch column {
        case .name: course.name
        case .teacher: course.teacher
        case .room: course.room
        case .weekday: String(course.weekday.rawValue)
        case .periods: course.periodCount > 1 ? "\(course.startPeriod)-\(course.endPeriod)" : "\(course.startPeriod)"
        case .weeks: weeksText(course.weeks)
        }
    }

    private static func weeksText(_ weeks: CourseWeeks) -> String {
        let range = weeks.first == weeks.last ? "\(weeks.first)" : "\(weeks.first)-\(weeks.last)"
        switch weeks.parity {
        case .every: return range
        case .odd: return range + " odd"
        case .even: return range + " even"
        }
    }

    /// A sample to paste into the import screen, with English headers.
    public static let englishExample = """
        name,teacher,room,weekday,periods,weeks
        Advanced Mathematics,Li,A101,Mon,1-2,1-16
        College English,Wang,B204,Tue,3-4,1-16
        Physics Lab,Zhao,Lab 3,Thu,5-7,1-15 odd
        Data Structures,Chen,C305,Fri,1-2,"1-8,10-16"
        """

    /// The same sample with Chinese headers and values.
    public static let chineseExample = """
        课程,教师,教室,星期,节次,周次
        高等数学,李老师,A101,周一,1-2,1-16周
        大学英语,王老师,B204,周二,3-4,1-16周
        物理实验,赵老师,实验楼3,周四,5-7,1-15单周
        数据结构,陈老师,C305,周五,1-2,"1-8,10-16周"
        """
}
