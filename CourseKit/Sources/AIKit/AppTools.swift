import CourseKit
import Foundation
import VocabKit

/// The app's tools for the Assistant (docs/ai-agent-spec.md §3): their definitions, what the reading tools
/// answer, and the changes the others propose. Everything here is plain logic over a snapshot of the app's data
/// (`AgentWorld`); the app runs it, shows the proposals and applies what the user confirms. Texts are for the
/// model, in English; the app shows its own words.
public enum AppTools {
    // MARK: Definitions

    public static let listTodos = AITool(
        name: "list_todos",
        description: """
            List the user's to-dos (reminders) between two dates with their IDs, timing and whether they're done. \
            Defaults: from today, for 7 days.
            """,
        parameters: [
            AIToolParameter("from", .date, "First day"),
            AIToolParameter("to", .date, "Last day, at most 31 days after from"),
        ],
        effect: .reads)

    public static let addTodo = AITool(
        name: "add_todo",
        description: """
            Propose a new to-do. A to-do with a time or after a class is a reminder: the app notifies the user then \
            (after a class: when that class ends). The user confirms it on a card before it is added.
            """,
        parameters: [
            AIToolParameter("title", .text(maxLength: TodoDraft.maxTitleLength),
                            "What to do, short, in the user's language, without the date or time", required: true),
            AIToolParameter("date", .date, "The day", required: true),
            AIToolParameter("time", .time, "A clock time; leave out for any time of day or with afterCourse"),
            AIToolParameter("afterCourse", .text(maxLength: 60),
                            "A course name from the timetable: due when its class on that date ends. Not with time"),
        ],
        effect: .changes)

    public static let updateTodo = AITool(
        name: "update_todo",
        description: """
            Propose changes to one to-do: its title, date, timing (time, afterCourse, or anyTime) or done (tick it \
            off or reopen it). Get the ID from list_todos. The user confirms on a card.
            """,
        parameters: [
            AIToolParameter("id", .text(maxLength: 40), "The to-do's ID from list_todos", required: true),
            AIToolParameter("title", .text(maxLength: TodoDraft.maxTitleLength), "New title"),
            AIToolParameter("date", .date, "New day"),
            AIToolParameter("time", .time, "New clock time"),
            AIToolParameter("afterCourse", .text(maxLength: 60), "New class to follow (a course name)"),
            AIToolParameter("anyTime", .boolean, "true: no particular time any more"),
            AIToolParameter("done", .boolean, "true: tick it off; false: not done yet"),
        ],
        effect: .changes)

    public static let deleteTodo = AITool(
        name: "delete_todo",
        description: "Propose deleting one to-do. Get the ID from list_todos. The user confirms on a card.",
        parameters: [AIToolParameter("id", .text(maxLength: 40), "The to-do's ID from list_todos", required: true)],
        effect: .changes)

    public static let getSchedule = AITool(
        name: "get_schedule",
        description: """
            The user's classes between two dates with times and rooms, and the teaching week. Defaults: today and the \
            next 6 days.
            """,
        parameters: [
            AIToolParameter("from", .date, "First day"),
            AIToolParameter("to", .date, "Last day, at most 14 days after from"),
        ],
        effect: .reads)

    public static let listCourses = AITool(
        name: "list_courses",
        description: """
            Every course in the timetable as CSV in the import format (name,teacher,room,weekday,periods,weeks), the \
            class period times and the term.
            """,
        effect: .reads)

    public static let addCourses = AITool(
        name: "add_courses",
        description: """
            Propose adding courses to the timetable, written as CSV in the import format. The user checks them in the \
            import preview (clashes, duplicates, lines that can't be read) before anything is saved. Header row: \
            name,teacher,room,weekday,periods,weeks (Chinese headers work too). weekday: 1-7 (1 = Monday) or 周一…周日; \
            periods: like 1-2; weeks: like 1-16, 1-15 odd, 2-16 even, or "1-8,10-16" (quote a field that has commas). \
            One line per weekly meeting: a course that meets twice a week has two lines.
            """,
        parameters: [AIToolParameter("csv", .text(maxLength: 20000), "The CSV text with its header row", required: true)],
        effect: .changes)

    public static let getVocabOverview = AITool(
        name: "get_vocab_overview",
        description: """
            The learner's vocabulary progress in the Words tab: today's plan, streak, words learned and mastered of \
            the book, words due, exam countdown and the pace needed, recent accuracy and study time.
            """,
        effect: .reads)

    public static let getVocabHistory = AITool(
        name: "get_vocab_history",
        description: "Vocabulary study day by day for the last days: answers, new words, accuracy and minutes.",
        parameters: [AIToolParameter("days", .integer(1...90), "How many days, counting today (default 14)")],
        effect: .reads)

    public static let getHardWords = AITool(
        name: "get_hard_words",
        description: """
            The words the learner forgets most, with their meanings, times forgotten, last answer and today's chance \
            of recalling them.
            """,
        parameters: [AIToolParameter("limit", .integer(1...50), "How many words (default 20)")],
        effect: .reads)

    public static let getWordProgress = AITool(
        name: "get_word_progress",
        description: "One word's learning record: state, answers, times forgotten, next review, chance of recall, last answers.",
        parameters: [AIToolParameter("word", .text(maxLength: 60), "The English word", required: true)],
        effect: .reads)

    public static let startFocus = AITool(
        name: "start_focus",
        description: """
            Propose starting a focus session (the Focus tab's Pomodoro timer) now. The user confirms on a card.
            """,
        parameters: [
            AIToolParameter("minutes", .integer(FocusSettings.totalRange),
                            "Total length including breaks", required: true),
            AIToolParameter("goal", .text(maxLength: 60), "What the user will work on, in their language"),
            AIToolParameter("breaks", .boolean, "false: one straight countdown; leave out to keep the user's setting"),
        ],
        effect: .changes)

    public static let getFocusHistory = AITool(
        name: "get_focus_history",
        description: """
            Focus sessions of the last days: minutes per day, each session's goal, start, focused minutes and whether \
            it was finished, and the session running now, if any.
            """,
        parameters: [AIToolParameter("days", .integer(1...31), "How many days, counting today (default 7)")],
        effect: .reads)

    /// Every tool, in the order the model sees them.
    public static let all: [AITool] = [
        listTodos, addTodo, updateTodo, deleteTodo, getSchedule, listCourses, addCourses,
        getVocabOverview, getVocabHistory, getHardWords, getWordProgress, startFocus, getFocusHistory,
    ]

    /// The longest result a reading tool returns; longer lists end with "…and N more".
    public static let maxResultCharacters = 4000

    static let weekdayNames = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

    static func dayName(_ day: LocalDate) -> String { "\(weekdayNames[day.weekday.rawValue - 1]) \(day)" }

    /// `lines` under `header`, cut to `maxResultCharacters` with a count of what was left out.
    static func list(_ header: String, _ lines: [String], empty: String) -> String {
        guard !lines.isEmpty else { return empty }
        var text = header
        for (index, line) in lines.enumerated() {
            let more = "\n…and \(lines.count - index) more"
            if text.count + line.count + 1 + more.count > maxResultCharacters {
                return text + more
            }
            text += "\n" + line
        }
        return text
    }
}

// MARK: - The world the tools see

/// What the tools read, taken when a call runs.
public struct AgentWorld: Sendable {
    public var today: LocalDate
    public var now: TimeOfDay
    public var timetable: Timetable
    public var todos: [Todo]
    public var focus: FocusSnapshot
    /// `nil` when the user isn't signed in to Words.
    public var vocab: VocabSnapshot?

    public init(today: LocalDate, now: TimeOfDay, timetable: Timetable, todos: [Todo], focus: FocusSnapshot,
                vocab: VocabSnapshot?) {
        self.today = today
        self.now = now
        self.timetable = timetable
        self.todos = todos
        self.focus = focus
        self.vocab = vocab
    }
}

public struct FocusSnapshot: Sendable {
    public struct Session: Sendable {
        public var goal: String
        public var startedAt: Date
        public var focusedSeconds: Int
        public var finished: Bool

        public init(goal: String, startedAt: Date, focusedSeconds: Int, finished: Bool) {
            self.goal = goal
            self.startedAt = startedAt
            self.focusedSeconds = focusedSeconds
            self.finished = finished
        }
    }

    public struct Running: Sendable {
        public var goal: String
        public var startedAt: Date
        public var endsAt: Date
        public var isPaused: Bool

        public init(goal: String, startedAt: Date, endsAt: Date, isPaused: Bool) {
            self.goal = goal
            self.startedAt = startedAt
            self.endsAt = endsAt
            self.isPaused = isPaused
        }
    }

    /// The user's focus setup, which a new session starts from.
    public var settings: FocusSettings
    /// Ended sessions, any order.
    public var sessions: [Session]
    public var running: Running?

    public init(settings: FocusSettings, sessions: [Session] = [], running: Running? = nil) {
        self.settings = settings
        self.sessions = sessions
        self.running = running
    }
}

public struct VocabSnapshot: Sendable {
    public var bookName: String
    /// The book's word keys.
    public var book: [String]
    /// Dictionary entries by word key (the book's and any others on the device).
    public var entries: [String: WordEntry]
    public var cards: [String: Card]
    public var events: [ReviewEvent]
    public var plan: DailyPlan
    public var exam: ExamCountdown?
    public var newWordsPerDay: Int
    public var desiredRetention: Double
    /// Today's study day.
    public var today: Int
    public var scheduler: Scheduler

    public init(bookName: String, book: [String], entries: [String: WordEntry], cards: [String: Card],
                events: [ReviewEvent], plan: DailyPlan, exam: ExamCountdown?, newWordsPerDay: Int,
                desiredRetention: Double, today: Int, scheduler: Scheduler) {
        self.bookName = bookName
        self.book = book
        self.entries = entries
        self.cards = cards
        self.events = events
        self.plan = plan
        self.exam = exam
        self.newWordsPerDay = newWordsPerDay
        self.desiredRetention = desiredRetention
        self.today = today
        self.scheduler = scheduler
    }
}

/// A change a tool call proposes, for the user to confirm.
public enum AgentChange: Hashable, Sendable {
    case addTodo(Todo)
    case updateTodo(before: Todo, after: Todo)
    case deleteTodo(Todo)
    /// Courses to check in the import preview: the CSV and what the importer read from it now.
    case addCourses(csv: String, courses: Int, duplicates: Int, problems: Int)
    case startFocus(goal: String, settings: FocusSettings)
}

// MARK: - Running calls

extension AppTools {
    /// The answer of a reading tool, or an error the model can act on.
    public static func read(_ call: AIPendingCall, in world: AgentWorld) -> AIToolResult {
        let id = call.id
        let arguments = call.arguments
        func result(_ outcome: Result<String, AIToolError>) -> AIToolResult {
            switch outcome {
            case .success(let text): AIToolResult(callID: id, content: text)
            case .failure(let error): AIToolResult(callID: id, content: error.message, isError: true)
            }
        }
        switch call.tool.name {
        case listTodos.name:
            return result(todoList(arguments, in: world))
        case getSchedule.name:
            return result(schedule(arguments, in: world))
        case listCourses.name:
            return result(.success(courseList(world.timetable, today: world.today)))
        case getFocusHistory.name:
            return result(.success(focusHistory(world.focus, today: world.today, days: arguments.int("days") ?? 7)))
        case getVocabOverview.name, getVocabHistory.name, getHardWords.name, getWordProgress.name:
            guard let vocab = world.vocab else { return result(.failure(AIToolError(notSignedIn))) }
            switch call.tool.name {
            case getVocabOverview.name: return result(.success(vocabOverview(vocab)))
            case getVocabHistory.name: return result(.success(vocabHistory(vocab, days: arguments.int("days") ?? 14)))
            case getHardWords.name: return result(.success(hardWords(vocab, limit: arguments.int("limit") ?? 20)))
            default: return result(.success(wordProgress(vocab, word: arguments.text("word") ?? "")))
            }
        default:
            return result(.failure(AIToolError("\(call.tool.name) isn't a reading tool.")))
        }
    }

    public static let notSignedIn = """
        The user isn't signed in to Words (the vocabulary account), so there are no learning numbers. They can sign \
        in on the Words tab.
        """

    /// The change a changing tool proposes, or why it can't be proposed (for the model to fix or explain).
    public static func proposal(for call: AIPendingCall, in world: AgentWorld) -> Result<AgentChange, AIToolError> {
        let arguments = call.arguments
        switch call.tool.name {
        case addTodo.name:
            return newTodo(arguments, in: world).map(AgentChange.addTodo)
        case updateTodo.name:
            return changedTodo(arguments, in: world).map { AgentChange.updateTodo(before: $0.before, after: $0.after) }
        case deleteTodo.name:
            return todo(arguments.text("id") ?? "", in: world.todos).map(AgentChange.deleteTodo)
        case addCourses.name:
            return courses(arguments.text("csv") ?? "", in: world.timetable)
        case startFocus.name:
            return focusSession(arguments, in: world)
        default:
            return .failure(AIToolError("\(call.tool.name) doesn't change anything."))
        }
    }

    /// What the model hears after the user confirmed `change` (`detail` adds what the app knows only then, such
    /// as the number of courses imported).
    public static func confirmedResult(_ change: AgentChange, detail: String? = nil) -> String {
        let text = switch change {
        case .addTodo(let todo): "Added the to-do (id \(shortID(todo.id)))."
        case .updateTodo: "Saved the changes."
        case .deleteTodo: "Deleted the to-do."
        case .addCourses: "The user imported the courses."
        case .startFocus: "The focus session started."
        }
        return [text, detail].compactMap { $0 }.joined(separator: " ")
    }
}

// MARK: - To-dos

extension AppTools {
    /// A to-do's ID as the model sees it: the first 8 hex digits of its UUID.
    public static func shortID(_ id: UUID) -> String { String(id.uuidString.prefix(8)).lowercased() }

    /// The to-do with this ID (short or full, any case, with or without `#`).
    static func todo(_ text: String, in todos: [Todo]) -> Result<Todo, AIToolError> {
        let key = text.trimmingCharacters(in: .whitespaces).lowercased().replacingOccurrences(of: "#", with: "")
            .replacingOccurrences(of: "id ", with: "")
        let matches = todos.filter { $0.id.uuidString.lowercased().hasPrefix(key) }
        guard key.count >= 4, matches.count == 1 else {
            return .failure(AIToolError("No to-do has the id \"\(text)\". Call list_todos for the current ids."))
        }
        return .success(matches[0])
    }

    static func timingText(_ todo: Todo, in timetable: Timetable) -> String {
        switch todo.timing {
        case .anytime:
            return "any time"
        case .at(let time):
            return "at \(time)"
        case .afterClass(let courseID):
            let name = timetable.courses.first { $0.id == courseID }?.name ?? "a deleted course"
            if let session = timetable.resolve(todo).session {
                return "after \(name) (ends \(session.end))"
            }
            return "after \(name) (no class that day)"
        }
    }

    static func todoLine(_ todo: Todo, in timetable: Timetable) -> String {
        var line = "id \(shortID(todo.id)) · \(dayName(todo.day)) · \(timingText(todo, in: timetable)) · \(todo.title)"
        if todo.isDone { line += " · done" }
        return line
    }

    static func range(_ arguments: AIToolArguments, today: LocalDate, defaultDays: Int, maxDays: Int)
        -> Result<(LocalDate, LocalDate), AIToolError> {
        let from = arguments.date("from") ?? today
        let to = arguments.date("to") ?? from.adding(days: defaultDays - 1)
        guard from <= to else { return .failure(AIToolError("\"to\" is before \"from\".")) }
        guard from.days(to: to) < maxDays else {
            return .failure(AIToolError("Ask for at most \(maxDays) days at a time."))
        }
        return .success((from, to))
    }

    static func todoList(_ arguments: AIToolArguments, in world: AgentWorld) -> Result<String, AIToolError> {
        range(arguments, today: world.today, defaultDays: 7, maxDays: 31).map { from, to in
            // Earliest first; within a day, by time (anytime and unknown classes last), then as created.
            let inRange = world.todos.enumerated().filter { $0.element.day >= from && $0.element.day <= to }
            let sorted = inRange.sorted { a, b in
                let dueA = world.timetable.resolve(a.element).due, dueB = world.timetable.resolve(b.element).due
                if a.element.day != b.element.day { return a.element.day < b.element.day }
                switch (dueA, dueB) {
                case let (x?, y?) where x != y: return x < y
                case (_?, nil): return true
                case (nil, _?): return false
                default: return a.offset < b.offset
                }
            }.map(\.element)
            let earlierOpen = world.todos.filter { $0.day < from && !$0.isDone && $0.day >= from.adding(days: -60) }.count
            var text = list("To-dos from \(dayName(from)) to \(dayName(to)) (\(sorted.count)):",
                            sorted.map { todoLine($0, in: world.timetable) },
                            empty: "No to-dos from \(dayName(from)) to \(dayName(to)).")
            if earlierOpen > 0 {
                text += "\n\(earlierOpen) unfinished to-do\(earlierOpen == 1 ? "" : "s") on earlier days (ask with an earlier \"from\")."
            }
            return text
        }
    }

    /// Checks a course name and that its class meets on `day`. A course that meets twice a week is two courses
    /// with one name; the one meeting that day is picked.
    static func classTiming(_ name: String, on day: LocalDate, in timetable: Timetable) -> Result<TodoTiming, AIToolError> {
        guard let match = TodoDraft.match(name, in: timetable.courses) else {
            let names = Array(Set(timetable.courses.map(\.name))).sorted()
            return .failure(AIToolError(names.isEmpty
                ? "The timetable has no courses, so a to-do can't follow a class. Use a time instead."
                : "No course is called \"\(name)\". Courses: \(names.joined(separator: ", "))."))
        }
        let ids = Set(timetable.courses.filter { $0.name.lowercased() == match.name.lowercased() }.map(\.id))
        guard let session = timetable.sessions(on: day).first(where: { ids.contains($0.course.id) }) else {
            let near = (-7...7).map { day.adding(days: $0) }.filter { date in
                timetable.sessions(on: date).contains { ids.contains($0.course.id) }
            }.map(dayName)
            let days = near.isEmpty ? "it has no classes within a week of that day" : "its classes near then: \(near.joined(separator: ", "))"
            return .failure(AIToolError("\(match.name) has no class on \(dayName(day)); \(days). Pick one of those days or a time."))
        }
        return .success(.afterClass(courseID: session.course.id))
    }

    static func newTodo(_ arguments: AIToolArguments, in world: AgentWorld) -> Result<Todo, AIToolError> {
        guard let title = arguments.text("title"), let day = arguments.date("date") else {
            return .failure(AIToolError("title and date are needed."))
        }
        guard day >= world.today else {
            return .failure(AIToolError("\(dayName(day)) is before today (\(dayName(world.today))); to-dos are for today or later."))
        }
        if arguments.has("time") && arguments.has("afterCourse") {
            return .failure(AIToolError("Give either time or afterCourse, not both."))
        }
        var timing = TodoTiming.anytime
        if let time = arguments.time("time") {
            timing = .at(time)
        } else if let name = arguments.text("afterCourse") {
            switch classTiming(name, on: day, in: world.timetable) {
            case .success(let found): timing = found
            case .failure(let error): return .failure(error)
            }
        }
        return .success(Todo(title: title, day: day, timing: timing))
    }

    static func changedTodo(_ arguments: AIToolArguments, in world: AgentWorld) -> Result<(before: Todo, after: Todo), AIToolError> {
        let found: Todo
        switch todo(arguments.text("id") ?? "", in: world.todos) {
        case .success(let todo): found = todo
        case .failure(let error): return .failure(error)
        }
        let timings = ["time", "afterCourse", "anyTime"].filter(arguments.has)
        guard timings.count <= 1 else {
            return .failure(AIToolError("Give only one of time, afterCourse and anyTime."))
        }
        var after = found
        if let title = arguments.text("title") { after.title = title }
        if let day = arguments.date("date") { after.day = day }
        if let done = arguments.bool("done") { after.isDone = done }
        if let time = arguments.time("time") {
            after.timing = .at(time)
        } else if arguments.bool("anyTime") == true {
            after.timing = .anytime
        } else if let name = arguments.text("afterCourse") {
            switch classTiming(name, on: after.day, in: world.timetable) {
            case .success(let timing): after.timing = timing
            case .failure(let error): return .failure(error)
            }
        } else if case .afterClass(let courseID) = after.timing, after.day != found.day {
            // Moved to another day: follow the same course's class that day (maybe its other weekly meeting).
            let name = world.timetable.courses.first { $0.id == courseID }?.name ?? ""
            switch classTiming(name, on: after.day, in: world.timetable) {
            case .success(let timing):
                after.timing = timing
            case .failure:
                return .failure(AIToolError("""
                    The to-do follows \(name.isEmpty ? "a class" : name), which has no class on \(dayName(after.day)). \
                    Give a time, anyTime, or another afterCourse too.
                    """))
            }
        }
        guard after != found else { return .failure(AIToolError("That doesn't change anything.")) }
        return .success((found, after))
    }
}

// MARK: - Timetable

extension AppTools {
    static func termLine(_ timetable: Timetable, today: LocalDate) -> String {
        let semester = timetable.semester
        let term = "The term starts \(dayName(semester.startDate)) and has \(semester.totalWeeks) weeks"
        switch semester.status(on: today) {
        case .notStarted(let days): return "\(term); it begins in \(days) days."
        case .inProgress(let week): return "\(term); today is in week \(week)."
        case .finished: return "\(term); it has ended."
        }
    }

    static func schedule(_ arguments: AIToolArguments, in world: AgentWorld) -> Result<String, AIToolError> {
        range(arguments, today: world.today, defaultDays: 7, maxDays: 14).map { from, to in
            var lines: [String] = []
            var day = from
            while day <= to {
                let sessions = world.timetable.sessions(on: day)
                for session in sessions {
                    var line = "\(dayName(day)) \(session.start)–\(session.end) \(session.course.name)"
                    let details = [session.course.room, session.course.teacher].filter { !$0.isEmpty }
                    if !details.isEmpty { line += " (\(details.joined(separator: ", ")))" }
                    line += " · week \(session.week)"
                    lines.append(line)
                }
                day = day.adding(days: 1)
            }
            let header = "\(termLine(world.timetable, today: world.today))\nClasses from \(dayName(from)) to \(dayName(to)):"
            return list(header, lines, empty: "\(header)\nNone.")
        }
    }

    static func courseList(_ timetable: Timetable, today: LocalDate) -> String {
        let periods = timetable.periods.periods.enumerated().map { "\($0.offset + 1) \($0.element.start)–\($0.element.end)" }
        let header = """
            \(termLine(timetable, today: today))
            Class periods: \(periods.joined(separator: ", ")).
            """
        guard !timetable.courses.isEmpty else { return "\(header)\nThe timetable has no courses yet." }
        let csv = CourseCSV.export(timetable.courses).replacingOccurrences(of: "\r\n", with: "\n")
            .trimmingCharacters(in: .newlines)
        let rows = csv.components(separatedBy: "\n")
        return list("\(header)\nCourses (\(rows.count - 1)):\n\(rows[0])", Array(rows.dropFirst()), empty: header)
    }

    static func courses(_ csv: String, in timetable: Timetable) -> Result<AgentChange, AIToolError> {
        let parsed = CourseImporter(for: timetable).parse(csv)
        let plan = timetable.planningImport(of: parsed.courses)
        let problems = parsed.problems.prefix(8).map(problemText)
        guard !plan.added.isEmpty else {
            if parsed.courses.count == 1 {
                return .failure(AIToolError("That course is already in the timetable; nothing to add."))
            } else if !parsed.courses.isEmpty {
                return .failure(AIToolError("All \(parsed.courses.count) courses are already in the timetable; nothing to add."))
            }
            var message = "No course could be read from the CSV."
            if !problems.isEmpty { message += " Problems:\n" + problems.joined(separator: "\n") }
            message += "\nUse the header name,teacher,room,weekday,periods,weeks and one line per weekly meeting."
            return .failure(AIToolError(message))
        }
        return .success(.addCourses(csv: csv, courses: plan.added.count, duplicates: plan.duplicates.count,
                                    problems: parsed.problems.count))
    }

    /// What the importer couldn't read, for the model.
    public static func problemText(_ problem: ImportProblem) -> String {
        let detail = switch problem.kind {
        case .missingColumns(let columns): "the header row is missing \(columns.map(\.rawValue).joined(separator: ", "))"
        case .missingValue(let column): "missing \(column.rawValue)"
        case .invalidValue(let column, let text): "can't read the \(column.rawValue) \"\(text)\""
        case .periodsOutOfRange(let text, let periodsPerDay): "periods \"\(text)\" are outside 1–\(periodsPerDay)"
        }
        return "Line \(problem.line): \(detail)"
    }

    /// The summary the model hears after the user's import.
    public static func importedResult(added: Int, skipped: Int) -> String {
        var text = "The user imported \(added) course\(added == 1 ? "" : "s")"
        if skipped > 0 { text += "; \(skipped) already in the timetable were skipped" }
        return text + "."
    }
}

// MARK: - Focus

extension AppTools {
    static func focusSession(_ arguments: AIToolArguments, in world: AgentWorld) -> Result<AgentChange, AIToolError> {
        if let running = world.focus.running {
            let goal = running.goal.isEmpty ? "" : " (\(running.goal))"
            return .failure(AIToolError("""
                A focus session\(goal) is already running until \(TimeOfDay(running.endsAt)). The user can stop it on \
                the Focus tab first.
                """))
        }
        var settings = world.focus.settings
        settings.totalMinutes = arguments.int("minutes") ?? settings.totalMinutes
        if let breaks = arguments.bool("breaks") { settings.usesBreaks = breaks }
        return .success(.startFocus(goal: arguments.text("goal") ?? "", settings: settings))
    }

    static func minutes(_ seconds: Int) -> Int { (seconds + 30) / 60 }

    static func focusHistory(_ focus: FocusSnapshot, today: LocalDate, days: Int) -> String {
        let first = today.adding(days: -(days - 1))
        let sessions = focus.sessions.filter { LocalDate($0.startedAt) >= first }.sorted { $0.startedAt < $1.startedAt }
        var lines: [String] = []
        if let running = focus.running {
            let goal = running.goal.isEmpty ? "" : " on \"\(running.goal)\""
            lines.append("Running now\(goal): started \(TimeOfDay(running.startedAt)), ends \(TimeOfDay(running.endsAt))\(running.isPaused ? ", paused" : "").")
        }
        let s = focus.settings
        lines.append("""
            Setup: \(s.totalMinutes) minutes\(s.usesBreaks ? ", \(s.focusMinutes)-minute blocks with \(s.shortBreakMinutes)-minute breaks" : ", no breaks").
            """)
        var day = first
        var total = 0
        while day <= today {
            let daySessions = sessions.filter { LocalDate($0.startedAt) == day }
            let focused = daySessions.reduce(0) { $0 + $1.focusedSeconds }
            total += focused
            if daySessions.isEmpty {
                lines.append("\(dayName(day)): no focus sessions")
            } else {
                let items = daySessions.map { session in
                    let goal = session.goal.isEmpty ? "no goal" : "\"\(session.goal)\""
                    return "\(TimeOfDay(session.startedAt)) \(goal) \(minutes(session.focusedSeconds)) min\(session.finished ? "" : " (stopped early)")"
                }
                lines.append("\(dayName(day)): \(minutes(focused)) min focused — \(items.joined(separator: "; "))")
            }
            day = day.adding(days: 1)
        }
        return list("Focus, last \(days) day\(days == 1 ? "" : "s") (\(minutes(total)) min in all):", lines, empty: "")
    }
}

// MARK: - Vocabulary

extension AppTools {
    /// `1 day`, `2 days`.
    static func count(_ number: Int, _ word: String) -> String { "\(number) \(word)\(number == 1 ? "" : "s")" }

    static func percent(_ value: Double?) -> String {
        value.map { "\(Int(($0 * 100).rounded()))%" } ?? "—"
    }

    static func meaning(_ entry: WordEntry?) -> String {
        guard let entry else { return "" }
        let line = entry.translation.split(separator: "\n").first.map(String.init) ?? ""
        return line.count > 40 ? String(line.prefix(40)) + "…" : line
    }

    static func vocabOverview(_ vocab: VocabSnapshot) -> String {
        let plan = vocab.plan
        let progress = VocabStats.progress(book: vocab.book, cards: vocab.cards, today: vocab.today)
        let streak = VocabStats.streak(vocab.events, today: vocab.today)
        let week = VocabStats.series(vocab.events, endingOn: vocab.today, count: 7)
        let month = VocabStats.series(vocab.events, endingOn: vocab.today, count: 30)
        func summary(_ days: [DayStats]) -> String {
            let answers = days.reduce(0) { $0 + $1.answers }
            let correct = days.reduce(0) { $0 + $1.correct }
            let minutes = days.reduce(0) { $0 + $1.durationMs } / 60000
            let studied = days.filter(\.studied).count
            return "\(count(studied, "day")) studied, \(count(answers, "answer")), \(percent(answers == 0 ? nil : Double(correct) / Double(answers))) right, \(minutes) min"
        }
        var lines = [
            "Book: \(vocab.bookName), \(progress.total) words. Learned \(progress.learned), mastered \(progress.mastered) (stable for 30+ days), not seen yet \(plan.unseenInBook).",
            "Today: new words \(plan.newDone) of \(plan.newTarget), reviews done \(plan.reviewsDone), reviews left \(plan.reviewsRemaining)\(plan.estimatedMinutes.map { ", about \($0) min of work left" } ?? "").",
            "Streak: \(streak.days) day\(streak.days == 1 ? "" : "s")\(streak.includesToday ? " (including today)" : " (not studied yet today)").",
            "Last 7 days: \(summary(week)).",
            "Last 30 days: \(summary(month)).",
            "Settings: \(vocab.newWordsPerDay) new words a day, target recall \(percent(vocab.desiredRetention)).",
            "Hard words (forgotten \(VocabStats.hardWordLapses)+ times): \(VocabStats.hardWords(vocab.cards.values).count).",
        ]
        if let exam = vocab.exam {
            lines.append("Exam on \(dayName(exam.examDate)): \(count(exam.daysLeft, "day")) left, \(count(exam.wordsLeft, "word")) not seen yet, needs \(count(exam.newWordsPerDayNeeded, "new word")) a day to see them all (now \(vocab.newWordsPerDay)).")
        } else {
            lines.append("No exam date set (Words → Settings).")
        }
        return lines.joined(separator: "\n")
    }

    static func vocabHistory(_ vocab: VocabSnapshot, days: Int) -> String {
        let series = VocabStats.series(vocab.events, endingOn: vocab.today, count: days)
        let lines = series.reversed().map { stats in
            let date = dayName(LocalDate(daysSinceEpoch: stats.day))
            guard stats.studied else { return "\(date): no study" }
            return "\(date): \(count(stats.answers, "answer")), \(count(stats.newWords, "new word")), \(stats.reviewedWords) reviewed, \(percent(stats.accuracy)) right, \(stats.durationMs / 60000) min"
        }
        return list("Vocabulary study, last \(days) day\(days == 1 ? "" : "s"), newest first:", lines, empty: "")
    }

    static func lastAnswers(_ vocab: VocabSnapshot) -> [String: ReviewEvent] {
        var last: [String: ReviewEvent] = [:]
        for event in vocab.events where (last[event.headword]?.reviewedAt ?? .distantPast) <= event.reviewedAt {
            last[event.headword] = event
        }
        return last
    }

    static func ratingName(_ rating: Rating) -> String {
        switch rating {
        case .again: "Again (forgot)"
        case .hard: "Hard"
        case .good: "Good"
        case .easy: "Easy"
        }
    }

    static func hardWords(_ vocab: VocabSnapshot, limit: Int) -> String {
        let cards = VocabStats.hardWords(vocab.cards.values).prefix(limit)
        let last = lastAnswers(vocab)
        let lines = cards.map { card in
            var parts = [card.headword]
            let meaning = meaning(vocab.entries[card.headword])
            if !meaning.isEmpty { parts.append(meaning) }
            parts.append("forgotten \(count(card.lapses, "time"))")
            if let event = last[card.headword] {
                parts.append("last answer \(ratingName(event.rating)) on \(LocalDate(daysSinceEpoch: event.studyDay))")
            }
            parts.append("recall chance today \(percent(vocab.scheduler.retrievability(of: card, on: vocab.today)))")
            return parts.joined(separator: " · ")
        }
        return list("Hardest words (\(cards.count)):", lines,
                    empty: "No hard words yet: no word has been forgotten \(VocabStats.hardWordLapses) or more times after learning it.")
    }

    static func wordProgress(_ vocab: VocabSnapshot, word: String) -> String {
        let key = Headword.key(word)
        let entry = vocab.entries[key]
        let inBook = vocab.book.contains(key)
        guard let card = vocab.cards[key], card.state != .new else {
            let known = entry == nil ? "isn't in the dictionary on this device" : (inBook ? "is in the book" : "isn't in the current book")
            return "\"\(word)\" \(known) and hasn't been studied yet."
        }
        let events = vocab.events.filter { $0.headword == key }.sorted { $0.reviewedAt < $1.reviewedAt }
        let state = switch card.state {
        case .new: "new"
        case .learning: "being learned"
        case .review: "learned"
        case .relearning: "being relearned after a lapse"
        }
        var lines = ["\(card.headword)\(entry.map { " — \(meaning($0))" } ?? "")\(inBook ? "" : " (not in the current book)")",
                     "State: \(state). Answers: \(events.count), forgotten after learning: \(count(card.lapses, "time"))."]
        if let first = card.firstReviewDay { lines.append("First studied: \(LocalDate(daysSinceEpoch: first)).") }
        if let due = card.dueDay(today: vocab.today) {
            lines.append(due <= vocab.today ? "Due for review today." : "Next review: \(LocalDate(daysSinceEpoch: due)).")
        }
        lines.append("Recall chance today: \(percent(vocab.scheduler.retrievability(of: card, on: vocab.today))).")
        if let stability = card.memory?.stability {
            lines.append("Memory stability: \(count(Int(stability.rounded()), "day")).")
        }
        let recent = events.suffix(5).map { "\(LocalDate(daysSinceEpoch: $0.studyDay)) \(ratingName($0.rating))" }
        if !recent.isEmpty { lines.append("Last answers: \(recent.joined(separator: ", ")).") }
        return lines.joined(separator: "\n")
    }
}
