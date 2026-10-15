import Foundation
import Observation
import SwiftData
import AIKit
import CourseKit
import VocabKit

/// What the Assistant shows for one tool call: a line for a lookup, a card for a change.
struct AgentCard: Hashable, Codable, Identifiable {
    enum State: String, Hashable, Codable {
        /// A reading tool ran.
        case looked
        /// It couldn't be done; the model was told why.
        case failed
        /// A change waiting for the user's tap.
        case waiting
        case confirmed
        case cancelled
        case undone
    }

    var id: String
    var tool: String
    var title: String
    var lines: [String] = []
    var state: State
}

/// Undoing a confirmed change, while the chat is open.
enum AgentUndo {
    case deleteTodo(UUID)
    case restoreTodo(Todo)
    case deleteCourses([UUID])
}

/// The app's data the tools read and change, from the Assistant's environment.
@MainActor
struct AgentContext {
    let planner: PlannerModel
    let vocab: VocabApp
    let modelContext: ModelContext
    var focus: FocusEngine { FocusEngine.shared }

    func timetable() -> Timetable {
        let settings = (try? modelContext.fetch(FetchDescriptor<SettingsRecord>()))?.first
        let courses = (try? modelContext.fetch(FetchDescriptor<CourseRecord>(sortBy: [SortDescriptor(\.createdAt)]))) ?? []
        return Persistence.timetable(settings: settings, courses: courses)
    }

    func world(at now: Date = .now) -> AgentWorld {
        let running = focus.run.map { run in
            FocusSnapshot.Running(
                goal: run.goal, startedAt: run.startedAt,
                endsAt: run.startedAt.addingTimeInterval(TimeInterval(run.plan.totalSeconds) + run.pausedSeconds - run.skippedSeconds),
                isPaused: run.isPaused)
        }
        let sessions = planner.focusSessions.map {
            FocusSnapshot.Session(goal: $0.goal, startedAt: $0.startedAt, focusedSeconds: $0.focusedSeconds, finished: $0.finished)
        }
        return AgentWorld(today: LocalDate(now), now: TimeOfDay(now), timetable: timetable(), todos: planner.todos,
                          focus: FocusSnapshot(settings: focus.settings, sessions: sessions, running: running),
                          vocab: vocabSnapshot(at: now))
    }

    private func vocabSnapshot(at now: Date) -> VocabSnapshot? {
        guard vocab.account != nil else { return nil }
        let model = vocab.model
        var entries = model.words
        for (key, entry) in model.bookEntries { entries[key] = entry }
        return VocabSnapshot(
            bookName: model.currentBook?.name ?? "", book: model.bookWords, entries: entries, cards: model.cards,
            events: model.events, plan: model.plan(at: now), exam: model.examCountdown(at: now),
            newWordsPerDay: model.settings.dailyNewWords, desiredRetention: model.settings.desiredRetention,
            today: model.clock.day(of: now), scheduler: model.scheduler)
    }

    /// Makes a confirmed change (not courses: those go through the import preview).
    func apply(_ change: AgentChange) -> AgentUndo? {
        switch change {
        case .addTodo(let todo):
            planner.save(todo)
            askForNotifications(todo)
            return .deleteTodo(todo.id)
        case .updateTodo(let before, let after):
            planner.save(after)
            askForNotifications(after)
            return .restoreTodo(before)
        case .deleteTodo(let todo):
            planner.delete(todo.id)
            return .restoreTodo(todo)
        case .startFocus(let goal, let settings):
            focus.goal = goal
            focus.settings = settings
            focus.start()
            return nil
        case .addCourses:
            return nil
        }
    }

    func undo(_ undo: AgentUndo) {
        switch undo {
        case .deleteTodo(let id):
            planner.delete(id)
        case .restoreTodo(let todo):
            planner.save(todo)
        case .deleteCourses(let ids):
            let records = (try? modelContext.fetch(FetchDescriptor<CourseRecord>())) ?? []
            for record in records where ids.contains(record.id) { modelContext.delete(record) }
            try? modelContext.save()
        }
    }

    /// A to-do with a time notifies; the first one asks for permission, as the to-do editor does.
    private func askForNotifications(_ todo: Todo) {
        guard todo.timing != .anytime, !UITestLaunch.isActive else { return }
        Task { _ = await ClassReminders.requestAuthorization() }
    }
}

/// An Assistant chat with the app's tools (docs/ai-agent-spec.md): each round streams through `round`;
/// reading tools run at once; changes wait as cards until the user confirms or cancels them, and the next round
/// starts when every call has its result.
@MainActor @Observable
final class AgentRun {
    enum Notice: Equatable {
        /// The provider rejected the tools; the chat goes on without them.
        case toolsUnavailable
        case roundLimit
        case cutOff
        case refused
    }

    /// Courses to check in the import preview.
    struct ImportRequest: Identifiable {
        var id: String
        var csv: String
    }

    private(set) var conversation: AIConversation
    private(set) var session: AIAgentSession
    /// The answer streaming in now.
    let round = AIRun()
    private(set) var notice: Notice?
    var importRequest: ImportRequest?
    @ObservationIgnored private var changes: [String: AgentChange] = [:]
    private var undos: [String: AgentUndo] = [:]
    @ObservationIgnored private var withoutThinking = false

    init(conversation: AIConversation = AIConversation(), usesTools: Bool) {
        var conversation = conversation
        // Changes still waiting when a saved chat was closed weren't made.
        for (id, card) in conversation.cards where card.state == .waiting { conversation.cards[id]?.state = .cancelled }
        self.conversation = conversation
        session = AIAgentSession(messages: conversation.messages, tools: usesTools ? AppTools.all : [])
    }

    var usesTools: Bool { !session.tools.isEmpty }
    var messages: [AIMessage] { session.messages }
    var isEmpty: Bool { session.messages.isEmpty && !round.hasContent }
    func canUndo(_ id: String) -> Bool { undos[id] != nil }

    // MARK: Turns

    /// Sends the user's message; changes still waiting are cancelled by it.
    func send(_ text: String, images: [AIImage] = [], system: String, store: AIStore, context: AgentContext) {
        round.reset()
        notice = nil
        for (id, card) in conversation.cards where card.state == .waiting {
            conversation.cards[id]?.state = .cancelled
            changes[id] = nil
        }
        session.send(text, images: images, system: system)
        save(store)
        nextRound(store: store, context: context)
    }

    /// After an error: asks again.
    func retry(store: AIStore, context: AgentContext) {
        round.reset()
        nextRound(store: store, context: context)
    }

    /// Keeps what has arrived so far.
    func stop(store: AIStore) {
        let answer = round.answer
        round.cancel()
        session.receiveStopped(answer)
        round.reset()
        save(store)
    }

    private func nextRound(store: AIStore, context: AgentContext) {
        guard session.isWaitingForModel else { return }
        let request = withoutThinking ? session.request().withoutThinking : session.request()
        round.start(request, store: store, onEnd: { [weak self] answer in
            self?.finishRound(answer, store: store, context: context)
        })
    }

    private func finishRound(_ answer: AIAnswer, store: AIStore, context: AgentContext) {
        if let error = round.error {
            if usesTools, error.rejectsTools, !session.messages.contains(where: { !$0.toolCalls.isEmpty }) {
                // The model can't use tools: carry on without them.
                session = AIAgentSession(messages: session.messages, tools: [], system: session.system)
                notice = .toolsUnavailable
                retry(store: store, context: context)
            } else if !withoutThinking, Self.rejectsThinking(error) {
                // A thinking block the provider won't take back (e.g. after the model changed): drop them once.
                withoutThinking = true
                retry(store: store, context: context)
            }
            return  // The error stays on screen with "Try Again".
        }
        let pending = session.receive(answer)
        round.reset()
        if answer.stop == .cutOff { notice = .cutOff }
        if answer.stop == .refused { notice = .refused }
        if session.stoppedAtRoundLimit { notice = .roundLimit }
        if !pending.isEmpty {
            let world = context.world()
            for call in pending { handle(call, in: world) }
        }
        save(store)
        nextRound(store: store, context: context)
    }

    private func handle(_ call: AIPendingCall, in world: AgentWorld) {
        if call.needsConfirmation {
            switch AppTools.proposal(for: call, in: world) {
            case .success(let change):
                changes[call.id] = change
                conversation.cards[call.id] = AgentCard.proposal(call.id, tool: call.tool.name, change: change,
                                                                 timetable: world.timetable)
            case .failure(let problem):
                session.resolve(call.id, content: problem.message, isError: true)
                conversation.cards[call.id] = AgentCard(id: call.id, tool: call.tool.name,
                                                        title: AgentCard.title(of: call.tool.name), state: .failed)
            }
        } else {
            let result = AppTools.read(call, in: world)
            session.resolve(call.id, content: result.content, isError: result.isError)
            conversation.cards[call.id] = AgentCard.lookup(call)
        }
    }

    private static func rejectsThinking(_ error: AIError) -> Bool {
        guard case .rejected(_, let message) = error, let message = message?.lowercased() else { return false }
        return message.contains("signature") || message.contains("thinking")
    }

    // MARK: Cards

    func confirm(_ id: String, store: AIStore, context: AgentContext) {
        guard let change = changes[id], conversation.cards[id]?.state == .waiting else { return }
        if case .addCourses(let csv, _, _, _) = change {
            importRequest = ImportRequest(id: id, csv: csv)
            return
        }
        changes[id] = nil
        undos[id] = context.apply(change)
        conversation.cards[id]?.state = .confirmed
        session.resolve(id, content: AppTools.confirmedResult(change))
        save(store)
        nextRound(store: store, context: context)
    }

    func cancel(_ id: String, store: AIStore, context: AgentContext) {
        guard conversation.cards[id]?.state == .waiting else { return }
        changes[id] = nil
        conversation.cards[id]?.state = .cancelled
        session.resolve(id, content: AIAgentSession.cancelledResult)
        save(store)
        nextRound(store: store, context: context)
    }

    /// The import preview added courses.
    func imported(_ id: String, added: [Course], skipped: Int, store: AIStore, context: AgentContext) {
        guard conversation.cards[id]?.state == .waiting else { return }
        changes[id] = nil
        undos[id] = .deleteCourses(added.map(\.id))
        conversation.cards[id]?.state = .confirmed
        session.resolve(id, content: AppTools.importedResult(added: added.count, skipped: skipped))
        save(store)
        nextRound(store: store, context: context)
    }

    /// The import preview closed; without an import that's a no.
    func importClosed(_ id: String, store: AIStore, context: AgentContext) {
        cancel(id, store: store, context: context)
    }

    func undo(_ id: String, store: AIStore, context: AgentContext) {
        guard let undo = undos.removeValue(forKey: id) else { return }
        context.undo(undo)
        conversation.cards[id]?.state = .undone
        save(store)
    }

    private func save(_ store: AIStore) {
        conversation.messages = session.messages
        store.saveConversation(conversation)
    }
}

// MARK: - What the cards say

extension AgentCard {
    /// The name of a tool's action, as the chat shows it.
    static func title(of tool: String) -> String {
        switch tool {
        case AppTools.listTodos.name: String(localized: "Looked at your to-dos")
        case AppTools.getSchedule.name: String(localized: "Looked at your classes")
        case AppTools.listCourses.name: String(localized: "Looked at your courses")
        case AppTools.getVocabOverview.name: String(localized: "Looked at your vocabulary progress")
        case AppTools.getVocabHistory.name: String(localized: "Looked at your study history")
        case AppTools.getHardWords.name: String(localized: "Looked at your hard words")
        case AppTools.getWordProgress.name: String(localized: "Looked up a word")
        case AppTools.getFocusHistory.name: String(localized: "Looked at your focus sessions")
        case AppTools.addTodo.name: String(localized: "New To-Do")
        case AppTools.updateTodo.name: String(localized: "Change To-Do")
        case AppTools.deleteTodo.name: String(localized: "Delete To-Do")
        case AppTools.addCourses.name: String(localized: "Add Courses")
        case AppTools.startFocus.name: String(localized: "Start Focus")
        default: tool
        }
    }

    static func lookup(_ call: AIPendingCall) -> AgentCard {
        var title = title(of: call.tool.name)
        if call.tool.name == AppTools.getWordProgress.name, let word = call.arguments.text("word") {
            title = String(localized: "Looked up “\(word)”")
        }
        return AgentCard(id: call.id, tool: call.tool.name, title: title, state: .looked)
    }

    static func proposal(_ id: String, tool: String, change: AgentChange, timetable: Timetable) -> AgentCard {
        var lines: [String]
        switch change {
        case .addTodo(let todo), .deleteTodo(let todo):
            lines = [todo.title, when(todo, timetable)]
        case .updateTodo(let before, let after):
            lines = [after.title, when(after, timetable)]
            if before.day != after.day || before.timing != after.timing {
                lines.append(String(localized: "Was: \(when(before, timetable))"))
            }
            if before.isDone != after.isDone {
                lines.append(after.isDone ? String(localized: "Mark as Done") : String(localized: "Mark as Not Done"))
            }
        case .addCourses(_, let courses, let duplicates, let problems):
            lines = [String(localized: "\(courses) courses to add")]
            if duplicates > 0 { lines.append(String(localized: "\(duplicates) already in your timetable")) }
            if problems > 0 { lines.append(String(localized: "\(problems) lines can't be read")) }
        case .startFocus(let goal, let settings):
            let length = Duration.seconds(settings.totalMinutes * 60).formatted(.units(allowed: [.hours, .minutes], width: .wide))
            let rhythm = settings.usesBreaks
                ? String(localized: "\(settings.focusMinutes)-minute blocks with breaks")
                : String(localized: "No breaks")
            lines = [goal, "\(length) · \(rhythm)"].filter { !$0.isEmpty }
        }
        return AgentCard(id: id, tool: tool, title: title(of: tool), lines: lines, state: .waiting)
    }

    /// "Fri, Oct 16 · 17:30" or "· after Calculus".
    static func when(_ todo: Todo, _ timetable: Timetable) -> String {
        let day = todo.day.date().formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
        let timing: String
        switch todo.timing {
        case .anytime:
            timing = String(localized: "Any time")
        case .at(let time):
            timing = todo.day.date(at: time).formatted(date: .omitted, time: .shortened)
        case .afterClass(let id):
            let name = timetable.courses.first { $0.id == id }?.name ?? ""
            timing = String(localized: "After \(name)")
        }
        return "\(day) · \(timing)"
    }
}
