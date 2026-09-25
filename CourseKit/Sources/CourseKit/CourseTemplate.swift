import Foundation

/// A course without a time slot: the name, room and teacher typed into the course list, ready to be
/// placed on the weekly grid.
public struct CourseTemplate: Identifiable, Hashable, Sendable {
    public var id: UUID
    public var name: String
    public var room: String
    public var teacher: String
    /// Index into the app's colour palette.
    public var colorIndex: Int

    public init(id: UUID = UUID(), name: String, room: String = "", teacher: String = "", colorIndex: Int = 0) {
        self.id = id
        self.name = name
        self.room = room
        self.teacher = teacher
        self.colorIndex = colorIndex
    }

    /// The name, room and teacher of an existing course.
    public init(_ course: Course) {
        self.init(id: course.id, name: course.name, room: course.room, teacher: course.teacher, colorIndex: course.colorIndex)
    }

    /// Same name, room and teacher.
    public func hasSameDetails(as other: CourseTemplate) -> Bool {
        name == other.name && room == other.room && teacher == other.teacher
    }

    /// Whether `course` has this template's name, room and teacher.
    public func describes(_ course: Course) -> Bool {
        name == course.name && room == course.room && teacher == course.teacher
    }
}

extension CourseTemplate {
    /// Reads a typed course list: one course per line as name, room, teacher.
    ///
    /// Fields are separated by tabs, commas (`,` `，`), semicolons, `|` or `、`. If a line has none of
    /// those, spaces separate the fields, and with more than three words the last two are the room and
    /// teacher (so "College English B204 Wang" works). Room and teacher are optional. Blank lines,
    /// `#` comments and lines without a name are skipped; repeated lines are kept once.
    public static func parseList(_ text: String) -> [CourseTemplate] {
        var templates: [CourseTemplate] = []
        for line in text.split(whereSeparator: \.isNewline) {
            let line = line.trimmingCharacters(in: .whitespaces)
            guard !line.isEmpty, !line.hasPrefix("#") else { continue }
            let fields = Self.fields(of: line)
            guard let name = fields.first, !name.isEmpty else { continue }
            let template = CourseTemplate(
                name: name,
                room: fields.count > 1 ? fields[1] : "",
                teacher: fields.count > 2 ? fields[2] : ""
            )
            if !templates.contains(where: { $0.hasSameDetails(as: template) }) {
                templates.append(template)
            }
        }
        return templates
    }

    private static let separators: Set<Character> = ["\t", ",", "\u{FF0C}", ";", "\u{FF1B}", "|", "、"]

    private static func fields(of line: String) -> [String] {
        if line.contains(where: { separators.contains($0) }) {
            return line.split(omittingEmptySubsequences: false, whereSeparator: { separators.contains($0) })
                .map { $0.trimmingCharacters(in: .whitespaces) }
        }
        let words = line.split(whereSeparator: \.isWhitespace).map(String.init)
        guard words.count > 3 else { return words }
        return [words.dropLast(2).joined(separator: " ")] + words.suffix(2)
    }
}

extension Timetable {
    /// The courses to offer when placing: `saved` (the course list) first, then one template for each
    /// name/room/teacher combination on the timetable that the list doesn't already cover, in slot
    /// order. A template taken from a course reuses that course's id and colour.
    public func placementTemplates(saved: [CourseTemplate]) -> [CourseTemplate] {
        var templates = saved
        for course in courses.sorted(by: Self.slotOrder) where !templates.contains(where: { $0.describes(course) }) {
            templates.append(CourseTemplate(course))
        }
        return templates
    }

    /// Colours for templates about to join the course list: the colour of a course or saved template
    /// with the same name, otherwise the least-used palette colour.
    public func assigningColors(to new: [CourseTemplate], saved: [CourseTemplate]) -> [CourseTemplate] {
        var colorByName: [String: Int] = [:]
        for (name, color) in courses.map({ ($0.name, $0.colorIndex) }) + saved.map({ ($0.name, $0.colorIndex) })
        where colorByName[name] == nil {
            colorByName[name] = color
        }
        var used = courses.map(\.colorIndex) + saved.map(\.colorIndex)
        return new.map { template in
            var template = template
            if let color = colorByName[template.name] {
                template.colorIndex = color
            } else {
                template.colorIndex = Course.leastUsedColorIndex(among: used)
                colorByName[template.name] = template.colorIndex
            }
            used.append(template.colorIndex)
            return template
        }
    }

    /// The course created by placing `template` in the slot at `period` on `weekday`.
    ///
    /// It runs for the whole term (odd or even weeks if `parity` says so and the term has such a week).
    /// `periodCount` is cut short to fit the day, and further if that avoids running into another
    /// course; a clash in the starting period itself is left for the editor to flag.
    public func placing(
        _ template: CourseTemplate, on weekday: Weekday, at period: Int, periodCount: Int, parity: WeekParity = .every
    ) -> Course {
        var weeks = CourseWeeks(first: 1, last: semester.totalWeeks, parity: parity)
        if weeks.all.isEmpty { weeks.parity = .every }
        let start = min(max(period, 1), max(periods.count, 1))
        let fitsDay = min(max(periodCount, 1), max(periods.count - start + 1, 1))
        var course = Course(
            name: template.name, teacher: template.teacher, room: template.room, weekday: weekday,
            startPeriod: start, periodCount: fitsDay, weeks: weeks, colorIndex: template.colorIndex
        )
        if let clashFree = (1...fitsDay).reversed().first(where: { length in
            var shorter = course
            shorter.periodCount = length
            return clashes(for: shorter).isEmpty
        }) {
            course.periodCount = clashFree
        }
        return course
    }
}
