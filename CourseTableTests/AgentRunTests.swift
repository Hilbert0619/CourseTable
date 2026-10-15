import Foundation
import SwiftData
import Testing
import AIKit
import CourseKit
@testable import CourseTable

/// The Assistant's agent loop in the app, with in-memory stores and the demo transport's scripted tool calls.
@MainActor
@Suite struct AgentRunTests {
    let today = LocalDate(.now)

    struct Setup {
        let ai: AIStore
        let planner: PlannerModel
        let courses: ModelContainer
        let context: AgentContext
    }

    func setUp() throws -> Setup {
        let ai = AIStore(defaults: UserDefaults(suiteName: "agent-test-\(UUID().uuidString)")!, cacheURL: nil,
                         transport: DemoAITransport(), memoryKeys: [:])
        ai.save(AIConnection(provider: try #require(AIProvider.preset(id: "deepseek"))), key: "demo")
        let planner = PlannerModel(container: try PlannerStore.makeContainer(inMemory: true))
        let courses = try Persistence.makeContainer(inMemory: true)
        Persistence.insertDemoData(into: courses.mainContext, today: today)
        let context = AgentContext(planner: planner, vocab: VocabApp.preview(), modelContext: courses.mainContext)
        return Setup(ai: ai, planner: planner, courses: courses, context: context)
    }

    func system() -> String {
        AIPrompts.assistant(language: .english, today: today, now: TimeOfDay(hour: 9, minute: 0), schedule: nil,
                            weakWords: nil, usesTools: true)
    }

    /// Waits until the model has nothing more to say or a card waits for the user.
    func settle(_ run: AgentRun) async throws {
        for _ in 0..<300 {
            try await Task.sleep(for: .milliseconds(20))
            if !run.round.isRunning && !run.session.isWaitingForModel { return }
            if run.conversation.cards.values.contains(where: { $0.state == .waiting }) && !run.round.isRunning { return }
        }
        Issue.record("The run didn't settle")
    }

    func waitingCard(_ run: AgentRun) throws -> AgentCard {
        try #require(run.conversation.cards.values.first { $0.state == .waiting })
    }

    @Test func aReminderIsProposedConfirmedAndUndone() async throws {
        let setup = try setUp()
        let run = AgentRun(usesTools: true)
        run.send("Remind me to hand in the paper after class tomorrow", system: system(), store: setup.ai, context: setup.context)
        try await settle(run)

        // The schedule was looked up, then the to-do proposed; nothing is saved yet.
        #expect(run.conversation.cards.values.contains { $0.tool == "get_schedule" && $0.state == .looked })
        let card = try waitingCard(run)
        #expect(card.tool == "add_todo")
        #expect(card.lines.first == "Hand in the paper")
        #expect(setup.planner.todos.allSatisfy { $0.title != "Hand in the paper" })

        run.confirm(card.id, store: setup.ai, context: setup.context)
        try await settle(run)
        let added = try #require(setup.planner.todos.first { $0.title == "Hand in the paper" })
        #expect(added.day == today.adding(days: 1))
        #expect(run.conversation.cards[card.id]?.state == .confirmed)
        #expect(run.messages.last?.text.hasPrefix("Done") == true)
        // The model heard the result.
        let result = try #require(run.messages.flatMap(\.toolResults).first { $0.callID == card.id })
        #expect(result.content.hasPrefix("Added the to-do (id \(AppTools.shortID(added.id)))"))

        run.undo(card.id, store: setup.ai, context: setup.context)
        #expect(setup.planner.todos.allSatisfy { $0.title != "Hand in the paper" })
        #expect(run.conversation.cards[card.id]?.state == .undone)
        #expect(!run.canUndo(card.id))

        // The chat was saved with its cards, without thinking blocks or pictures.
        let saved = try #require(setup.ai.conversations.first)
        #expect(saved.cards[card.id]?.state == .undone)
        #expect(saved.messages.count == run.messages.count)
    }

    @Test func aCancelledProposalChangesNothing() async throws {
        let setup = try setUp()
        let run = AgentRun(usesTools: true)
        run.send("Remind me to hand in the paper after class tomorrow", system: system(), store: setup.ai, context: setup.context)
        try await settle(run)
        let card = try waitingCard(run)
        let before = setup.planner.todos
        run.cancel(card.id, store: setup.ai, context: setup.context)
        try await settle(run)
        #expect(setup.planner.todos == before)
        #expect(run.conversation.cards[card.id]?.state == .cancelled)
        #expect(run.messages.last?.text == "OK, I didn't add it.")
    }

    @Test func aNewMessageCancelsAWaitingProposal() async throws {
        let setup = try setUp()
        let run = AgentRun(usesTools: true)
        run.send("Remind me to hand in the paper after class tomorrow", system: system(), store: setup.ai, context: setup.context)
        try await settle(run)
        let card = try waitingCard(run)
        run.send("How should I plan this week?", system: system(), store: setup.ai, context: setup.context)
        #expect(run.conversation.cards[card.id]?.state == .cancelled)
        try await settle(run)
        #expect(run.messages.last?.role == .assistant)
    }

    @Test func coursesGoThroughTheImportPreview() async throws {
        let setup = try setUp()
        let run = AgentRun(usesTools: true)
        run.send("Add these courses\n\n" + AIPrompts.photoText("[x 0.20 y 0.10 w 0.08 h 0.02] 星期一"), system: system(),
                 store: setup.ai, context: setup.context)
        try await settle(run)
        let card = try waitingCard(run)
        #expect(card.tool == "add_courses")
        run.confirm(card.id, store: setup.ai, context: setup.context)
        let request = try #require(run.importRequest)
        #expect(request.csv.contains("线性代数"))

        // The preview imported one course (what ImportView reports).
        let course = Course(name: "线性代数", teacher: "", room: "C305", weekday: .monday, startPeriod: 1, periodCount: 2,
                            weeks: CourseWeeks(first: 1, last: 16))
        setup.courses.mainContext.insert(CourseRecord(course))
        try setup.courses.mainContext.save()
        run.imported(card.id, added: [course], skipped: 0, store: setup.ai, context: setup.context)
        try await settle(run)
        #expect(run.conversation.cards[card.id]?.state == .confirmed)
        #expect(run.messages.flatMap(\.toolResults).contains { $0.content == "The user imported 1 course." })

        run.undo(card.id, store: setup.ai, context: setup.context)
        let left = try setup.courses.mainContext.fetch(FetchDescriptor<CourseRecord>())
        #expect(!left.contains { $0.id == course.id })
    }

    @Test func withoutToolsTheChatIsPlain() async throws {
        let setup = try setUp()
        let run = AgentRun(usesTools: false)
        run.send("Remind me to hand in the paper after class tomorrow", system: system(), store: setup.ai, context: setup.context)
        try await settle(run)
        #expect(run.conversation.cards.isEmpty)
        #expect(run.messages.last?.role == .assistant)
    }

    @Test func chatsSavedBy04StillOpen() throws {
        let old = #"[{"id":"6B4E0B4A-2B7A-4E2F-9A57-2D4C2B9D8C11","messages":[{"role":"user","text":"Hi"}],"updated":0}]"#
        let chats = try JSONDecoder().decode([AIConversation].self, from: Data(old.utf8))
        #expect(chats[0].messages == [AIMessage(.user, "Hi")])
        #expect(chats[0].cards.isEmpty)
    }
}
