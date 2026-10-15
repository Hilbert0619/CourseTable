import CourseKit
import Foundation
import Testing
import VocabKit
@testable import AIKit

/// The agent cases in `spec/fixtures/ai-tool-*.json`, `ai-agent-tools.json` and `ai-timetable-photo.json`, which
/// the Android port reads too.

/// A tool as the fixtures write it.
struct FixtureTool: Decodable {
    struct Parameter: Decodable {
        var name: String
        var type: String
        var max: Int?
        var min: Int?
        var choices: [String]?
        var description: String
        var required: Bool?
    }
    var name: String
    var description: String
    var effect: String
    var parameters: [Parameter]

    var tool: AITool {
        AITool(name: name, description: description, parameters: parameters.map { parameter in
            let kind: AIToolParameter.Kind = switch parameter.type {
            case "text": .text(maxLength: parameter.max ?? 100)
            case "date": .date
            case "time": .time
            case "integer": .integer((parameter.min ?? 0)...(parameter.max ?? 100))
            case "boolean": .boolean
            default: .choice(parameter.choices ?? [])
            }
            return AIToolParameter(parameter.name, kind, parameter.description, required: parameter.required ?? false)
        }, effect: effect == "reads" ? .reads : .changes)
    }
}

struct ToolStreamCase: Decodable, CustomTestStringConvertible {
    struct Thinking: Decodable {
        var text: String
        var signature: String?
        var redactedData: String?
    }
    var name: String
    var format: AIWireFormat
    var lines: [String]
    var text: String
    var reasoning: String
    var stop: String?
    var toolCalls: [AIToolCall]
    var thinking: [Thinking]
    var inputTokens: Int?
    var outputTokens: Int?
    var ended: Bool
    var sawThinking: Bool

    var testDescription: String { name }

    var stream: StreamCase {
        StreamCase(name: name, format: format, lines: lines, lineEnding: nil, text: text, stop: stop,
                   inputTokens: inputTokens, outputTokens: outputTokens, ended: ended, sawThinking: sawThinking, error: nil)
    }
}

struct RequestCase: Decodable, CustomTestStringConvertible {
    struct Connection: Decodable {
        var providerID: String
        var format: AIWireFormat
        var baseURL: String
        var model: String
    }
    var name: String
    var connection: Connection
    var system: String
    var tools: [FixtureTool]
    var messages: [AIMessage]
    var body: JSONValue

    var testDescription: String { name }
}

struct ArgumentCases: Decodable {
    struct Case: Decodable, CustomTestStringConvertible {
        var name: String
        var arguments: String
        var values: [String: JSONValue]?
        var error: String?
        var testDescription: String { name }
    }
    var tool: FixtureTool
    var cases: [Case]
}

@Suite struct AIToolFixtureTests {
    static let streams = try! AISharedFixtures.decode(StreamsFile.self, from: "ai-tool-streams.json").streams
    static let requests = try! AISharedFixtures.decode(RequestsFile.self, from: "ai-tool-requests.json").requests
    static let arguments = try! AISharedFixtures.decode(ArgumentCases.self, from: "ai-tool-arguments.json")

    struct StreamsFile: Decodable { var streams: [ToolStreamCase] }
    struct RequestsFile: Decodable { var requests: [RequestCase] }

    @Test(arguments: streams, [1, 5, 64, 4096])
    func toolCallStreamsDecodeTheSameInAnyChunkSize(_ item: ToolStreamCase, chunkSize: Int) {
        let (answer, error, sawThinking) = AISharedFixtureTests.run(item.stream, chunkSize: chunkSize)
        #expect(error == nil)
        #expect(answer.text == item.text)
        #expect(answer.reasoning == item.reasoning)
        #expect(AISharedFixtures.stop(answer.stop) == item.stop)
        #expect(answer.toolCalls == item.toolCalls)
        #expect(answer.thinkingBlocks.map(\.text) == item.thinking.map(\.text))
        #expect(answer.thinkingBlocks.map(\.signature) == item.thinking.map(\.signature))
        #expect(answer.thinkingBlocks.map(\.redactedData) == item.thinking.map(\.redactedData))
        #expect(answer.inputTokens == item.inputTokens)
        #expect(answer.outputTokens == item.outputTokens)
        #expect(answer.hasEnded == item.ended)
        #expect(sawThinking == item.sawThinking)
    }

    @Test(arguments: requests)
    func requestBodiesWithToolsAndPictures(_ item: RequestCase) throws {
        let connection = AIConnection(providerID: item.connection.providerID, name: "Test", format: item.connection.format,
                                      baseURL: item.connection.baseURL, model: item.connection.model)
        let request = AIRequest(system: item.system, messages: item.messages, tools: item.tools.map(\.tool))
        let http = try connection.chatRequest(request, key: "k")
        let body = try JSONDecoder().decode(JSONValue.self, from: try #require(http.body))
        #expect(body == item.body, "\(body.jsonText)")
    }

    @Test(arguments: arguments.cases)
    func argumentsAreCheckedAndNormalised(_ item: ArgumentCases.Case) {
        switch Self.arguments.tool.tool.validate(item.arguments) {
        case .success(let arguments):
            #expect(item.error == nil, "Expected the error \(item.error ?? "")")
            #expect(arguments.values == item.values)
        case .failure(let problem):
            #expect(problem.message == item.error)
        }
    }
}

// MARK: - App tools

struct AgentToolCases: Decodable {
    struct World: Decodable {
        struct Focus: Decodable {
            struct Session: Decodable {
                var goal: String
                var at: String
                var focusedSeconds: Int
                var finished: Bool
            }
            var settings: FocusSettings
            var sessions: [Session]
        }
        struct Vocab: Decodable {
            struct Event: Decodable {
                var word: String
                var rating: Int
                var at: String
                var durationMs: Int
            }
            var bookName: String
            var book: [String]
            var entries: [String: String]
            var newWordsPerDay: Int
            var examDate: LocalDate
            var events: [Event]
        }
        var today: LocalDate
        var now: TimeOfDay
        var semester: Semester
        var courses: [Course]
        var todos: [Todo]
        var focus: Focus
        var vocab: Vocab
    }
    struct Case: Decodable, CustomTestStringConvertible {
        var name: String
        var tool: String
        var arguments: String
        var result: String?
        var error: String?
        var proposal: JSONValue?
        var running: Bool?
        var signedOut: Bool?
        var testDescription: String { name }
    }
    var world: World
    var cases: [Case]

    /// `YYYY-MM-DD HH:mm` in the current time zone.
    static func date(_ text: String) -> Date {
        let parts = text.split(separator: " ")
        return LocalDate(String(parts[0]))!.date(at: TimeOfDay(String(parts[1]))!)
    }

    func agentWorld(running: Bool, signedOut: Bool) -> AgentWorld {
        let timetable = Timetable(semester: world.semester, courses: world.courses)
        let sessions = world.focus.sessions.map {
            FocusSnapshot.Session(goal: $0.goal, startedAt: Self.date($0.at), focusedSeconds: $0.focusedSeconds, finished: $0.finished)
        }
        let runningSession = running
            ? FocusSnapshot.Running(goal: "英语阅读", startedAt: world.today.date(at: TimeOfDay(hour: 10, minute: 0)),
                                    endsAt: world.today.date(at: TimeOfDay(hour: 11, minute: 0)), isPaused: false)
            : nil
        var vocab: VocabSnapshot?
        if !signedOut {
            let clock = StudyClock()
            let scheduler = Scheduler()
            let events = world.vocab.events.map { event in
                let at = Self.date(event.at)
                return ReviewEvent(headword: event.word, rating: Rating(rawValue: event.rating)!, mode: .recognition,
                                   reviewedAt: at, studyDay: clock.day(of: at), durationMs: event.durationMs)
            }
            let cards = scheduler.replay(events)
            let now = world.today.date(at: world.now)
            let book = world.vocab.book
            let unseen = book.filter { (cards[$0]?.state ?? .new) == .new }.count
            vocab = VocabSnapshot(
                bookName: world.vocab.bookName, book: book,
                entries: world.vocab.entries.mapValues { WordEntry(headword: "", translation: $0) }
                    .reduce(into: [:]) { result, item in
                        var entry = item.value
                        entry.headword = item.key
                        entry.display = item.key
                        result[item.key] = entry
                    },
                cards: cards, events: events,
                plan: DailyPlan(cards: cards, history: events, book: book, newWordsPerDay: world.vocab.newWordsPerDay,
                                now: now, clock: clock),
                exam: ExamCountdown(examDate: world.vocab.examDate, today: clock.day(of: now), wordsLeft: unseen),
                newWordsPerDay: world.vocab.newWordsPerDay, desiredRetention: 0.9, today: clock.day(of: now),
                scheduler: scheduler)
        }
        return AgentWorld(today: world.today, now: world.now, timetable: timetable, todos: world.todos,
                          focus: FocusSnapshot(settings: world.focus.settings, sessions: sessions, running: runningSession),
                          vocab: vocab)
    }
}

@Suite struct AppToolFixtureTests {
    static let file = try! AISharedFixtures.decode(AgentToolCases.self, from: "ai-agent-tools.json")

    static func timing(_ timing: TodoTiming) -> String {
        switch timing {
        case .anytime: "anytime"
        case .at(let time): "at \(time)"
        case .afterClass(let id): "after \(id.uuidString)"
        }
    }

    static func describe(_ todo: Todo) -> JSONValue {
        ["title": .string(todo.title), "date": .string(todo.day.description), "timing": .string(timing(todo.timing)),
         "done": .bool(todo.isDone)]
    }

    static func describe(_ change: AgentChange) -> JSONValue {
        switch change {
        case .addTodo(let todo):
            return ["add": describe(todo)]
        case .updateTodo(_, let after):
            var fields = describe(after).objectValue!
            fields["id"] = .string(after.id.uuidString)
            return ["update": .object(fields)]
        case .deleteTodo(let todo):
            return ["delete": .string(todo.id.uuidString)]
        case .addCourses(_, let courses, let duplicates, let problems):
            return ["courses": ["courses": .int(courses), "duplicates": .int(duplicates), "problems": .int(problems)]]
        case .startFocus(let goal, let settings):
            return ["focus": ["goal": .string(goal), "totalMinutes": .int(settings.totalMinutes), "usesBreaks": .bool(settings.usesBreaks)]]
        }
    }

    @Test(arguments: file.cases)
    func toolsAnswerAndProposeTheSameWay(_ item: AgentToolCases.Case) throws {
        let world = Self.file.agentWorld(running: item.running ?? false, signedOut: item.signedOut ?? false)
        var session = AIAgentSession(tools: AppTools.all)
        session.send("test", system: "")
        var answer = AIAnswer()
        answer.apply(.toolCall(index: 0, id: "c1", name: item.tool))
        answer.apply(.toolArguments(index: 0, item.arguments))
        let pending = session.receive(answer)
        var outcome: JSONValue
        if let call = pending.first {
            if call.needsConfirmation {
                switch AppTools.proposal(for: call, in: world) {
                case .success(let change): outcome = ["proposal": Self.describe(change)]
                case .failure(let error): outcome = ["error": .string(error.message)]
                }
            } else {
                let result = AppTools.read(call, in: world)
                outcome = result.isError ? ["error": .string(result.content)] : ["result": .string(result.content)]
            }
        } else {
            let result = try #require(session.messages.last?.toolResults.first)
            #expect(result.isError)
            outcome = ["error": .string(result.content)]
        }
        var expected: [String: JSONValue] = [:]
        if let result = item.result { expected["result"] = .string(result) }
        if let error = item.error { expected["error"] = .string(error) }
        if let proposal = item.proposal { expected["proposal"] = proposal }
        #expect(outcome == .object(expected), "ACTUAL \(item.name) \(outcome.jsonText)")
    }

    @Test func confirmedChangesAreToldToTheModel() {
        let todo = Todo(id: UUID(uuidString: "ABCDEF12-0000-0000-0000-000000000000")!, title: "x", day: LocalDate(year: 2026, month: 10, day: 16))
        #expect(AppTools.confirmedResult(.addTodo(todo)) == "Added the to-do (id abcdef12).")
        #expect(AppTools.importedResult(added: 3, skipped: 1) == "The user imported 3 courses; 1 already in the timetable were skipped.")
        #expect(AppTools.importedResult(added: 1, skipped: 0) == "The user imported 1 course.")
    }

    @Test func longListsAreCut() {
        let lines = (1...500).map { "line \($0) with some words in it" }
        let text = AppTools.list("Header:", lines, empty: "none")
        #expect(text.count <= AppTools.maxResultCharacters)
        #expect(text.hasPrefix("Header:\nline 1 "))
        #expect(text.range(of: #"\n…and \d+ more$"#, options: .regularExpression) != nil)
        #expect(AppTools.list("Header:", [], empty: "none") == "none")
    }

    @Test func everyToolHasAUsableSchema() {
        let names = AppTools.all.map(\.name)
        #expect(Set(names).count == names.count)
        for tool in AppTools.all {
            #expect(tool.schema["type"] == "object", "\(tool.name)")
            #expect(tool.name.range(of: "^[a-z_]{1,64}$", options: .regularExpression) != nil)
            #expect(tool.description.count < 1024)
        }
    }
}

// MARK: - Photos

struct PhotoCases: Decodable {
    struct OCRCase: Decodable, CustomTestStringConvertible {
        struct Box: Decodable {
            var text: String
            var x: Double
            var y: Double
            var width: Double
            var height: Double
        }
        var name: String
        var boxes: [Box]
        var text: String
        var testDescription: String { name }
    }
    struct AnswerCase: Decodable, CustomTestStringConvertible {
        var name: String
        var answer: String
        var csv: String?
        var periods: [[String]]?
        var none: Bool?
        var testDescription: String { name }
    }
    var ocr: [OCRCase]
    var answers: [AnswerCase]
}

@Suite struct TimetablePhotoFixtureTests {
    static let file = try! AISharedFixtures.decode(PhotoCases.self, from: "ai-timetable-photo.json")

    @Test(arguments: file.ocr)
    func textBoxesBecomePositionLines(_ item: PhotoCases.OCRCase) {
        let boxes = item.boxes.map { TimetablePhoto.TextBox(text: $0.text, x: $0.x, y: $0.y, width: $0.width, height: $0.height) }
        #expect(TimetablePhoto.ocrText(boxes) == item.text)
    }

    @Test(arguments: file.answers)
    func answersGiveCSVAndPeriods(_ item: PhotoCases.AnswerCase) {
        let reading = TimetablePhoto.reading(from: item.answer)
        if item.none == true {
            #expect(reading == nil)
            return
        }
        #expect(reading?.csv == item.csv)
        let periods = reading?.periods?.periods.map { [$0.start.description, $0.end.description] }
        #expect(periods == item.periods)
    }
}
