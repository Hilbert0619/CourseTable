import Foundation

/// A tool call from the latest answer that passed the checks, waiting for the app: a reading tool to run, or a
/// change for the user to confirm.
public struct AIPendingCall: Hashable, Sendable, Identifiable {
    public var call: AIToolCall
    public var tool: AITool
    public var arguments: AIToolArguments

    public var id: String { call.id }
    public var needsConfirmation: Bool { tool.effect == .changes }
}

/// An Assistant conversation with tools (docs/ai-agent-spec.md §4), as plain state the app drives:
///
/// 1. `send` the user's message, then ask the provider with `request()`.
/// 2. Hand the streamed answer to `receive`. Calls of unknown tools or with bad arguments get an error result
///    right away (so the model can fix them); the rest come back as pending calls.
/// 3. Run the reading calls, show the changes as proposals, and `resolve` each call with its result, e.g.
///    "added" or "the user cancelled".
/// 4. Once every call has a result (`isWaitingForModel`), ask again; repeat until the answer has no calls.
///
/// After `maxRounds` rounds of tool calls in one turn, further calls are answered with an error and the turn
/// stops (`stoppedAtRoundLimit`).
public struct AIAgentSession: Hashable, Sendable {
    public private(set) var messages: [AIMessage]
    public let tools: [AITool]
    /// The system prompt of the current turn; it stays the same for every request of a turn.
    public private(set) var system: String
    /// Rounds of tool calls since the user's message.
    public private(set) var rounds = 0
    public private(set) var stoppedAtRoundLimit = false
    /// The latest answer's calls still waiting for a result, in order.
    public private(set) var pending: [AIPendingCall] = []
    /// Results of the latest answer's calls, by call ID, until all are in.
    private var results: [String: AIToolResult] = [:]
    /// The latest answer's call IDs, in order.
    private var callOrder: [String] = []

    public static let maxRounds = 8

    public static let cancelledResult = "The user cancelled; nothing changed."
    public static let interruptedResult = "The user didn't answer this; they wrote a new message instead. Nothing changed."
    public static let closedResult = "The chat was closed before the user answered. Nothing changed."
    public static let roundLimitResult = "Not run: too many steps in one answer. Stop calling tools and answer the user."

    /// Continues `messages` (e.g. a saved chat). Calls at the end that never got results are closed as unanswered,
    /// and thinking blocks are dropped, so the history can be sent as it is.
    public init(messages: [AIMessage] = [], tools: [AITool], system: String = "") {
        var messages = messages.map { message in
            var copy = message
            copy.thinking = []
            return copy
        }
        if let last = messages.last, last.role == .assistant, !last.toolCalls.isEmpty {
            messages.append(AIMessage(.user, "", toolResults: last.toolCalls.map {
                AIToolResult(callID: $0.id, content: Self.closedResult)
            }))
        }
        self.messages = messages
        self.tools = tools
        self.system = system
    }

    /// Adds the user's message with this turn's system prompt. Calls still waiting are closed as unanswered
    /// first, in the same message. Thinking blocks of earlier turns are dropped: they belong to the turn that
    /// made them, and a new turn's system prompt differs.
    public mutating func send(_ text: String, images: [AIImage] = [], system: String) {
        var toolResults: [AIToolResult] = []
        if !callOrder.isEmpty {
            for id in callOrder where results[id] == nil {
                results[id] = AIToolResult(callID: id, content: Self.interruptedResult)
            }
            toolResults = callOrder.compactMap { results[$0] }
            clearCalls()
        }
        for index in messages.indices { messages[index].thinking = [] }
        messages.append(AIMessage(.user, text, images: images, toolResults: toolResults))
        self.system = system
        rounds = 0
        stoppedAtRoundLimit = false
    }

    /// The request for the next answer.
    public func request(maxCharacters: Int = .max) -> AIRequest {
        AIRequest(system: system, messages: messages, tools: tools).trimmed(maxCharacters: maxCharacters)
    }

    /// The last message is the user's or the tools' results: the model's turn.
    public var isWaitingForModel: Bool {
        pending.isEmpty && callOrder.isEmpty && messages.last?.role == .user && !stoppedAtRoundLimit
    }

    /// Takes a finished answer. Returns the calls the app must handle; empty when the answer is final (or every
    /// call was answered with an error, and `isWaitingForModel` says to ask again).
    @discardableResult
    public mutating func receive(_ answer: AIAnswer) -> [AIPendingCall] {
        var calls = answer.toolCalls
        let used = Set(messages.flatMap(\.toolCalls).map(\.id))
        for index in calls.indices where calls[index].id.isEmpty || used.contains(calls[index].id)
            || calls[..<index].contains(where: { $0.id == calls[index].id }) {
            calls[index].id = "call_\(rounds + 1)_\(index + 1)"
        }
        // A cut-off or refused answer's calls are incomplete: keep the text only.
        if answer.stop == .cutOff || answer.stop == .refused { calls = calls.filter { JSONValue.parse($0.arguments) != nil } }
        messages.append(AIMessage(.assistant, answer.text.trimmingCharacters(in: .whitespacesAndNewlines),
                                  toolCalls: calls, reasoning: answer.reasoning.isEmpty ? nil : answer.reasoning,
                                  thinking: answer.thinkingBlocks))
        guard !calls.isEmpty else { return [] }
        rounds += 1
        callOrder = calls.map(\.id)
        if rounds > Self.maxRounds {
            stoppedAtRoundLimit = true
            for call in calls { results[call.id] = AIToolResult(callID: call.id, content: Self.roundLimitResult, isError: true) }
            finishRoundIfComplete()
            return []
        }
        for call in calls {
            guard let tool = tools.first(where: { $0.name == call.name }) else {
                let names = tools.map(\.name).joined(separator: ", ")
                results[call.id] = AIToolResult(callID: call.id, content: "There is no tool named \"\(call.name)\". Tools: \(names).",
                                                isError: true)
                continue
            }
            switch tool.validate(call.arguments) {
            case .success(let arguments):
                pending.append(AIPendingCall(call: call, tool: tool, arguments: arguments))
            case .failure(let problem):
                results[call.id] = AIToolResult(callID: call.id, content: "Invalid arguments for \(tool.name): \(problem.message)",
                                                isError: true)
            }
        }
        let waiting = pending
        finishRoundIfComplete()
        return waiting
    }

    /// Keeps only the text of an answer the user stopped while it streamed (its calls may be incomplete).
    public mutating func receiveStopped(_ answer: AIAnswer) {
        let text = answer.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        messages.append(AIMessage(.assistant, text, reasoning: answer.reasoning.isEmpty ? nil : answer.reasoning))
    }

    /// Records the result of a pending call.
    public mutating func resolve(_ callID: String, content: String, isError: Bool = false) {
        guard pending.contains(where: { $0.id == callID }) else { return }
        pending.removeAll { $0.id == callID }
        results[callID] = AIToolResult(callID: callID, content: content, isError: isError)
        finishRoundIfComplete()
    }

    /// Once every call of the latest answer has a result, they go into one message for the model.
    private mutating func finishRoundIfComplete() {
        guard !callOrder.isEmpty, pending.isEmpty, callOrder.allSatisfy({ results[$0] != nil }) else { return }
        messages.append(AIMessage(.user, "", toolResults: callOrder.compactMap { results[$0] }))
        clearCalls()
    }

    private mutating func clearCalls() {
        pending = []
        results = [:]
        callOrder = []
    }
}
