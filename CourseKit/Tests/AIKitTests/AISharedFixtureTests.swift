import CourseKit
import Foundation
import Testing
@testable import AIKit

/// The AI cases in `spec/fixtures/ai-*.json`, which the Android port's tests read too: both platforms must parse
/// streams, map errors and read to-do drafts the same way.
enum AISharedFixtures {
    static let directory = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()  // AIKitTests
        .deletingLastPathComponent()  // Tests
        .deletingLastPathComponent()  // CourseKit
        .deletingLastPathComponent()  // the repository
        .appendingPathComponent("spec")
        .appendingPathComponent("fixtures")

    static func decode<T: Decodable>(_ type: T.Type, from name: String) throws -> T {
        try JSONDecoder().decode(T.self, from: Data(contentsOf: directory.appendingPathComponent(name)))
    }

    static func kind(_ error: AIError) -> String {
        switch error {
        case .wrongKey: "wrongKey"
        case .noCredit: "noCredit"
        case .notFound: "notFound"
        case .rateLimited: "rateLimited"
        case .busy: "busy"
        case .rejected: "rejected"
        case .network: "network"
        case .badAddress: "badAddress"
        case .noModel: "noModel"
        case .unreadable: "unreadable"
        case .empty: "empty"
        }
    }

    static func stop(_ reason: AIStopReason?) -> String? {
        switch reason {
        case .complete: "complete"
        case .cutOff: "cutOff"
        case .refused: "refused"
        case .toolUse: "toolUse"
        case .other(let text): text
        case nil: nil
        }
    }
}

struct StreamCase: Decodable, CustomTestStringConvertible {
    var name: String
    var format: AIWireFormat
    var lines: [String]
    var lineEnding: String?
    var text: String
    var stop: String?
    var inputTokens: Int?
    var outputTokens: Int?
    var ended: Bool?
    var sawThinking: Bool?
    var error: String?

    var testDescription: String { name }
    var bytes: [UInt8] { Array(lines.joined(separator: lineEnding ?? "\n").utf8) }
}

struct ErrorCase: Decodable, CustomTestStringConvertible {
    var name: String
    var status: Int
    var body: String
    var kind: String
    var message: String?

    var testDescription: String { name }
}

struct TodoDraftCases: Decodable {
    struct NamedCourse: Decodable {
        var id: UUID
        var name: String
    }
    struct Case: Decodable, CustomTestStringConvertible {
        var name: String
        var answer: String
        var title: String?
        var date: String?
        var timing: String?
        var unknownCourse: String?
        var error: Bool?

        var testDescription: String { name }
    }
    var today: String
    var courses: [NamedCourse]
    var cases: [Case]
}

@Suite struct AISharedFixtureTests {
    static let streams = try! AISharedFixtures.decode(StreamsFile.self, from: "ai-streams.json").streams
    static let errors = try! AISharedFixtures.decode(ErrorsFile.self, from: "ai-errors.json").errors
    static let drafts = try! AISharedFixtures.decode(TodoDraftCases.self, from: "ai-todo-drafts.json")

    struct StreamsFile: Decodable { var streams: [StreamCase] }
    struct ErrorsFile: Decodable { var errors: [ErrorCase] }

    /// Runs a stream through the parser in chunks of `size` bytes.
    static func run(_ stream: StreamCase, chunkSize size: Int) -> (AIAnswer, AIError?, Bool) {
        var parser = SSEParser()
        let decoder = AIStreamDecoder(format: stream.format)
        var answer = AIAnswer()
        var sawThinking = false
        let bytes = stream.bytes
        var events: [SSEEvent] = []
        var offset = 0
        while offset < bytes.count {
            events += parser.feed(bytes[offset..<min(offset + size, bytes.count)])
            offset += size
        }
        events += parser.finish()
        do {
            for event in events {
                for item in try decoder.decode(event) {
                    if item == .thinking { sawThinking = true }
                    answer.apply(item)
                }
            }
        } catch let error as AIError {
            return (answer, error, sawThinking)
        } catch {
            Issue.record("Unexpected error \(error)")
        }
        return (answer, nil, sawThinking)
    }

    @Test(arguments: streams, [1, 3, 7, 4096])
    func streamsDecodeTheSameInAnyChunkSize(_ stream: StreamCase, chunkSize: Int) {
        let (answer, error, sawThinking) = Self.run(stream, chunkSize: chunkSize)
        #expect(answer.text == stream.text)
        if let expected = stream.error {
            #expect(error.map(AISharedFixtures.kind) == expected)
            return
        }
        #expect(error == nil)
        #expect(AISharedFixtures.stop(answer.stop) == stream.stop)
        #expect(answer.inputTokens == stream.inputTokens)
        #expect(answer.outputTokens == stream.outputTokens)
        #expect(answer.hasEnded == stream.ended)
        #expect(sawThinking == stream.sawThinking)
        #expect(!answer.isThinking)
    }

    @Test(arguments: errors)
    func errorResponsesMapToKinds(_ item: ErrorCase) {
        let error = AIError.from(status: item.status, body: Data(item.body.utf8))
        #expect(AISharedFixtures.kind(error) == item.kind)
        if let message = item.message { #expect(error.providerMessage == message) }
    }

    @Test(arguments: drafts.cases)
    func todoDraftsAreReadTheSameWay(_ item: TodoDraftCases.Case) throws {
        let today = try #require(LocalDate(Self.drafts.today))
        let courses = Self.drafts.courses.map {
            Course(id: $0.id, name: $0.name, weekday: .monday, startPeriod: 1, weeks: CourseWeeks(first: 1, last: 16))
        }
        if item.error == true {
            #expect(throws: AIError.unreadable) { try TodoDraft.parse(item.answer, today: today, courses: courses) }
            return
        }
        let draft = try TodoDraft.parse(item.answer, today: today, courses: courses)
        #expect(draft.title == item.title)
        #expect(draft.day.description == item.date)
        let timing = switch draft.timing {
        case .anytime: "anytime"
        case .at(let time): "at \(time)"
        case .afterClass(let id): "after \(courses.first { $0.id == id }?.name ?? "?")"
        }
        #expect(timing == item.timing)
        #expect(draft.unknownCourse == item.unknownCourse)
    }
}
