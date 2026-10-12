import CourseKit
import Foundation

/// A to-do the AI read from a note, for the user to check in the editor before saving.
public struct TodoDraft: Hashable, Sendable {
    public var title: String
    public var day: LocalDate
    public var timing: TodoTiming
    /// The class the note named when no course has that name (the timing is then "any time" or the clock time).
    public var unknownCourse: String?

    public var todo: Todo { Todo(title: title, day: day, timing: timing) }

    public static let maxTitleLength = 100

    /// Reads the AI's JSON answer (see `AIPrompts.todoDraft`), tolerating code fences and text around it.
    public static func parse(_ answer: String, today: LocalDate, courses: [Course]) throws -> TodoDraft {
        guard let start = answer.firstIndex(of: "{"), let end = answer.lastIndex(of: "}"), start < end,
              let raw = try? JSONDecoder().decode(Raw.self, from: Data(answer[start...end].utf8))
        else { throw AIError.unreadable }

        let title = String((raw.title ?? "").trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxTitleLength))
        guard !title.isEmpty else { throw AIError.unreadable }
        let day = raw.date.flatMap { LocalDate(String($0.trimmingCharacters(in: .whitespaces).prefix(10))) } ?? today
        let time = raw.time.flatMap { TimeOfDay($0.trimmingCharacters(in: .whitespaces)) }

        var draft = TodoDraft(title: title, day: day, timing: time.map(TodoTiming.at) ?? .anytime)
        if let name = raw.afterCourse?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty {
            if let course = match(name, in: courses) {
                draft.timing = .afterClass(courseID: course.id)
            } else {
                draft.unknownCourse = name
            }
        }
        return draft
    }

    /// The course with this name; failing that, the one whose name contains it or is contained in it; failing that,
    /// an abbreviation ("高数" → "高等数学"). Among several, the shortest name (the closest fit) wins.
    static func match(_ name: String, in courses: [Course]) -> Course? {
        let key = name.lowercased()
        let named = courses.filter { !$0.name.isEmpty }
        if let exact = named.first(where: { $0.name.lowercased() == key }) { return exact }
        let partial = named.filter { $0.name.lowercased().contains(key) || key.contains($0.name.lowercased()) }
        if let best = partial.min(by: { $0.name.count < $1.name.count }) { return best }
        guard key.count >= 2 else { return nil }
        return named.filter { isAbbreviation(key, of: $0.name.lowercased()) }.min { $0.name.count < $1.name.count }
    }

    /// `short`'s characters appear in `long` in order, starting with its first character.
    private static func isAbbreviation(_ short: String, of long: String) -> Bool {
        guard short.first == long.first else { return false }
        var rest = long[...]
        for character in short {
            guard let index = rest.firstIndex(of: character) else { return false }
            rest = rest[rest.index(after: index)...]
        }
        return true
    }

    private struct Raw: Decodable {
        var title: String?
        var date: String?
        var time: String?
        var afterCourse: String?
    }
}
