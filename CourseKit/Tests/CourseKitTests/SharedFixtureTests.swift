import Foundation
import Testing
@testable import CourseKit

/// The shared test cases in `spec/fixtures/`, which the Android port's tests read too
/// (`android/core/src/test/kotlin/.../SharedFixtureTests.kt`): both platforms must give the same answers.
enum SharedFixtures {
    /// `spec/fixtures/` in the repository, found from this file's path.
    static let directory = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()  // CourseKitTests
        .deletingLastPathComponent()  // Tests
        .deletingLastPathComponent()  // CourseKit
        .deletingLastPathComponent()  // the repository
        .appendingPathComponent("spec")
        .appendingPathComponent("fixtures")

    static func data(_ name: String) throws -> Data {
        try Data(contentsOf: directory.appendingPathComponent(name))
    }

    static func decode<T: Decodable>(_ type: T.Type, from name: String) throws -> T {
        try JSONDecoder().decode(T.self, from: data(name))
    }

    /// `"2026-09-28 07:00"`
    static func moment(_ text: String) -> Moment {
        let parts = text.split(separator: " ").map(String.init)
        return Moment(day: LocalDate(parts[0])!, time: TimeOfDay(parts[1])!)
    }
}

@Suite struct SharedFixtureTests {
    // MARK: - Import fields

    struct ImportFields: Decodable {
        struct WeekdayCase: Decodable { var text: String; var weekday: Int }
        struct PeriodCase: Decodable { var text: String; var runs: [[Int]] }
        struct WeeksCase: Decodable { var text: String; var weeks: [CourseWeeks] }

        var totalWeeks: Int
        var weekdays: [WeekdayCase]
        var invalidWeekdays: [String]
        var periods: [PeriodCase]
        var invalidPeriods: [String]
        var weeks: [WeeksCase]
        var invalidWeeks: [String]
    }

    @Test func importFields() throws {
        let fixture = try SharedFixtures.decode(ImportFields.self, from: "import-fields.json")
        for item in fixture.weekdays {
            #expect(CourseImporter.parseWeekday(item.text) == Weekday(rawValue: item.weekday), "weekday \"\(item.text)\"")
        }
        for text in fixture.invalidWeekdays {
            #expect(CourseImporter.parseWeekday(text) == nil, "weekday \"\(text)\"")
        }
        for item in fixture.periods {
            let expected = item.runs.map { $0[0]...$0[1] }
            #expect(CourseImporter.parsePeriods(item.text, schedule: .standard) == expected, "periods \"\(item.text)\"")
        }
        for text in fixture.invalidPeriods {
            #expect(CourseImporter.parsePeriods(text, schedule: .standard) == nil, "periods \"\(text)\"")
        }
        for item in fixture.weeks {
            #expect(CourseImporter.parseWeeks(item.text, totalWeeks: fixture.totalWeeks) == item.weeks, "weeks \"\(item.text)\"")
        }
        for text in fixture.invalidWeeks {
            #expect(CourseImporter.parseWeeks(text, totalWeeks: fixture.totalWeeks) == nil, "weeks \"\(text)\"")
        }
    }

    // MARK: - Import samples

    struct ImportSamples: Decodable {
        struct Sample: Decodable {
            var name: String
            var text: String?
            var file: String?
            var encoding: String?
            var rowCount: Int
            var courses: [String]
            var problems: [String]
        }

        var totalWeeks: Int
        var samples: [Sample]
    }

    @Test func importSamples() throws {
        let fixture = try SharedFixtures.decode(ImportSamples.self, from: "import-samples.json")
        let importer = CourseImporter(periods: .standard, totalWeeks: fixture.totalWeeks)
        for sample in fixture.samples {
            var text = sample.text ?? ""
            if let file = sample.file {
                guard let decoded = ImportedText.decode(try SharedFixtures.data(file)) else {
                    // GB18030 needs Apple's Foundation (see ImportedText.decode); everything else decodes everywhere.
                    #if canImport(Darwin)
                    Issue.record("\(sample.name): \(file) didn't decode")
                    #else
                    if sample.encoding != "gb18030" { Issue.record("\(sample.name): \(file) didn't decode") }
                    #endif
                    continue
                }
                text = decoded
            }
            let result = importer.parse(text)
            #expect(result.courses.map(Self.summary) == sample.courses, "\(sample.name): courses")
            #expect(result.problems.map(Self.describe) == sample.problems, "\(sample.name): problems")
            #expect(result.rowCount == sample.rowCount, "\(sample.name): rows")
        }
    }

    static func summary(_ course: Course) -> String {
        "\(course.name)|\(course.teacher)|\(course.room)|\(course.weekday.rawValue)|\(course.startPeriod)-\(course.endPeriod)|"
            + "\(course.weeks.first)-\(course.weeks.last) \(course.weeks.parity.rawValue)"
    }

    static func describe(_ problem: ImportProblem) -> String {
        let detail = switch problem.kind {
        case .missingColumns(let columns): "missingColumns " + columns.map(\.rawValue).joined(separator: ",")
        case .missingValue(let column): "missingValue \(column.rawValue)"
        case .invalidValue(let column, let text): "invalidValue \(column.rawValue) \(text)"
        case .periodsOutOfRange(let text, let periodsPerDay): "periodsOutOfRange \(text) \(periodsPerDay)"
        }
        return "\(problem.line) \(detail)"
    }

    // MARK: - Focus plans

    struct FocusPlans: Decodable {
        struct Case: Decodable { var settings: FocusSettings; var layout: [String] }
        var plans: [Case]
    }

    @Test func focusPlans() throws {
        for item in try SharedFixtures.decode(FocusPlans.self, from: "focus-plans.json").plans {
            let layout = FocusPlan(item.settings).phases.map { phase in
                switch phase.kind {
                case .focus: "F\(phase.seconds / 60)"
                case .shortBreak: "b\(phase.seconds / 60)"
                case .longBreak: "L\(phase.seconds / 60)"
                }
            }
            #expect(layout == item.layout, "plan for \(item.settings)")
        }
    }

    // MARK: - Reminders, windows, agenda, to-do alerts

    struct TimetableAlerts: Decodable {
        struct AlertCase: Decodable { var after: String; var leadMinutes: Int?; var limit: Int; var expected: [String] }
        struct AgendaCase: Decodable { var day: LocalDate; var anytime: [String]; var items: [String] }

        var timetable: Timetable
        var todos: [Todo]
        var reminders: [AlertCase]
        var windows: [AlertCase]
        var agenda: [AgendaCase]
        var todoAlerts: [AlertCase]
    }

    @Test func timetableAlerts() throws {
        let fixture = try SharedFixtures.decode(TimetableAlerts.self, from: "timetable-alerts.json")
        let timetable = fixture.timetable
        for item in fixture.reminders {
            let reminders = timetable.upcomingReminders(
                after: SharedFixtures.moment(item.after), leadMinutes: item.leadMinutes ?? 0, limit: item.limit
            )
            #expect(reminders.map { "\($0.session.course.name) \($0.fireMoment.day) \($0.fireMoment.time)" } == item.expected,
                    "reminders after \(item.after), \(item.leadMinutes ?? 0) min")
        }
        for item in fixture.windows {
            let windows = timetable.upcomingActivityWindows(
                after: SharedFixtures.moment(item.after), leadMinutes: item.leadMinutes ?? 0, limit: item.limit, todos: fixture.todos
            )
            let summary = windows.map { window in
                "\(window.session.course.name) \(window.showMoment.day) \(window.showMoment.time)–\(window.session.end)"
                    + (window.todos.isEmpty ? "" : " after: " + window.todos.joined(separator: ", "))
            }
            #expect(summary == item.expected, "windows after \(item.after), \(item.leadMinutes ?? 0) min")
        }
        for item in fixture.agenda {
            let agenda = timetable.agenda(on: item.day, todos: fixture.todos)
            #expect(agenda.anytime.map(\.todo.title) == item.anytime, "anytime on \(item.day)")
            let items = agenda.items.map { entry in
                switch entry {
                case .session(let session, let after):
                    session.course.name + (after.isEmpty ? "" : " after: " + after.map(\.todo.title).joined(separator: ", "))
                case .todo(let todo):
                    "\(todo.due!.time) \(todo.todo.title)"
                }
            }
            #expect(items == item.items, "agenda on \(item.day)")
        }
        for item in fixture.todoAlerts {
            let alerts = timetable.upcomingTodoAlerts(after: SharedFixtures.moment(item.after), todos: fixture.todos, limit: item.limit)
            #expect(alerts.map(\.todo.title) == item.expected, "to-do alerts after \(item.after)")
        }
    }
}
