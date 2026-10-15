import Foundation

/// One Server-Sent Event: an optional `event:` name and its `data:` lines joined by newlines.
public struct SSEEvent: Hashable, Sendable {
    public var name: String?
    public var data: String

    public init(name: String? = nil, data: String) {
        self.name = name
        self.data = data
    }
}

/// Splits a byte stream into Server-Sent Events. Works on bytes, so a UTF-8 character split across network chunks
/// stays whole, and on blank lines, which `URLSession.AsyncBytes.lines` would skip.
public struct SSEParser: Sendable {
    private var line: [UInt8] = []
    private var name: String?
    private var data: [String] = []

    public init() {}

    public mutating func feed<S: Sequence>(_ bytes: S) -> [SSEEvent] where S.Element == UInt8 {
        var events: [SSEEvent] = []
        for byte in bytes {
            if byte == UInt8(ascii: "\n") {
                if line.last == UInt8(ascii: "\r") { line.removeLast() }
                if let event = take(String(decoding: line, as: UTF8.self)) { events.append(event) }
                line.removeAll(keepingCapacity: true)
            } else {
                line.append(byte)
            }
        }
        return events
    }

    /// The last event when the stream ends without a blank line.
    public mutating func finish() -> [SSEEvent] {
        var events: [SSEEvent] = []
        if !line.isEmpty {
            if let event = take(String(decoding: line, as: UTF8.self)) { events.append(event) }
            line.removeAll()
        }
        if let event = take("") { events.append(event) }
        return events
    }

    private mutating func take(_ text: String) -> SSEEvent? {
        if text.isEmpty {
            defer { name = nil; data.removeAll() }
            return data.isEmpty ? nil : SSEEvent(name: name, data: data.joined(separator: "\n"))
        }
        if text.hasPrefix(":") { return nil }  // a comment, e.g. ": keep-alive"
        let field: Substring
        var value: Substring
        if let colon = text.firstIndex(of: ":") {
            field = text[..<colon]
            value = text[text.index(after: colon)...]
            if value.hasPrefix(" ") { value = value.dropFirst() }
        } else {
            field = Substring(text)
            value = ""
        }
        switch field {
        case "data": data.append(String(value))
        case "event": name = String(value)
        default: break
        }
        return nil
    }
}

public enum AIStopReason: Hashable, Sendable {
    case complete
    /// The provider's length limit cut the answer off.
    case cutOff
    /// The provider's safety filter declined.
    case refused
    /// The model wants tools run before it goes on.
    case toolUse
    case other(String)
}

/// What a streamed answer reports, in either wire format.
public enum AIStreamEvent: Hashable, Sendable {
    case text(String)
    /// A reasoning model is thinking (its reasoning isn't shown).
    case thinking
    /// OpenAI-compatible `reasoning_content`, kept to be sent back (DeepSeek needs it once tools are on).
    case reasoning(String)
    /// Anthropic thinking block `index` started; a redacted block brings its data.
    case thinkingBlock(index: Int, redactedData: String?)
    case thinkingText(index: Int, String)
    case thinkingSignature(index: Int, String)
    /// A tool call starts, or (OpenAI-compatible) its ID or name arrives. `index` is `nil` when the provider
    /// leaves it out.
    case toolCall(index: Int?, id: String?, name: String?)
    /// A piece of a tool call's JSON arguments.
    case toolArguments(index: Int?, String)
    case usage(input: Int?, output: Int?)
    case stop(AIStopReason)
    /// The stream is over (`[DONE]` or `message_stop`).
    case end
}

/// Turns Server-Sent Events into `AIStreamEvent`s.
public struct AIStreamDecoder: Sendable {
    public var format: AIWireFormat

    public init(format: AIWireFormat) {
        self.format = format
    }

    public func decode(_ event: SSEEvent) throws -> [AIStreamEvent] {
        switch format {
        case .openAI: try decodeOpenAI(event)
        case .anthropic: try decodeAnthropic(event)
        }
    }

    private func decodeOpenAI(_ event: SSEEvent) throws -> [AIStreamEvent] {
        let data = event.data.trimmingCharacters(in: .whitespaces)
        if data == "[DONE]" { return [.end] }
        guard let chunk = try? JSONDecoder().decode(OpenAIChunk.self, from: Data(data.utf8)) else {
            throw AIError.unreadable
        }
        if let error = chunk.error { throw AIError.from(status: nil, details: error.details) }
        var events: [AIStreamEvent] = []
        for choice in chunk.choices ?? [] {
            if let text = choice.delta?.content, !text.isEmpty { events.append(.text(text)) }
            if let reasoning = choice.delta?.reasoningContent, !reasoning.isEmpty {
                events += [.thinking, .reasoning(reasoning)]
            } else if let reasoning = choice.delta?.reasoning, !reasoning.isEmpty {
                events.append(.thinking)
            }
            for call in choice.delta?.toolCalls ?? [] {
                let id = call.id.flatMap { $0.isEmpty ? nil : $0 }
                let name = call.function?.name.flatMap { $0.isEmpty ? nil : $0 }
                if id != nil || name != nil { events.append(.toolCall(index: call.index, id: id, name: name)) }
                if let arguments = call.function?.arguments, !arguments.isEmpty {
                    events.append(.toolArguments(index: call.index, arguments))
                }
            }
            if let reason = choice.finishReason { events.append(.stop(Self.openAIStop(reason))) }
        }
        if let usage = chunk.usage, usage.promptTokens != nil || usage.completionTokens != nil {
            events.append(.usage(input: usage.promptTokens, output: usage.completionTokens))
        }
        return events
    }

    private func decodeAnthropic(_ event: SSEEvent) throws -> [AIStreamEvent] {
        guard let payload = try? JSONDecoder().decode(AnthropicEvent.self, from: Data(event.data.utf8)) else {
            throw AIError.unreadable
        }
        let index = payload.index ?? 0
        switch payload.type {
        case "message_start":
            let usage = payload.message?.usage
            return usage?.inputTokens == nil ? [] : [.usage(input: usage?.inputTokens, output: nil)]
        case "content_block_start":
            guard let block = payload.contentBlock else { return [] }
            switch block.type {
            case "tool_use":
                return [.toolCall(index: index, id: block.id, name: block.name)]
            case "thinking":
                var events: [AIStreamEvent] = [.thinkingBlock(index: index, redactedData: nil)]
                if let text = block.thinking, !text.isEmpty { events.append(.thinkingText(index: index, text)) }
                if let signature = block.signature, !signature.isEmpty { events.append(.thinkingSignature(index: index, signature)) }
                return events
            case "redacted_thinking":
                return [.thinkingBlock(index: index, redactedData: block.data ?? "")]
            case "text":
                return (block.text).map { $0.isEmpty ? [] : [.text($0)] } ?? []
            default:
                return []
            }
        case "content_block_delta":
            switch payload.delta?.type {
            case "text_delta": return (payload.delta?.text).map { $0.isEmpty ? [] : [.text($0)] } ?? []
            case "thinking_delta":
                let text = payload.delta?.thinking ?? ""
                return text.isEmpty ? [.thinking] : [.thinking, .thinkingText(index: index, text)]
            case "signature_delta":
                return (payload.delta?.signature).map { [.thinkingSignature(index: index, $0)] } ?? []
            case "input_json_delta":
                let json = payload.delta?.partialJSON ?? ""
                return json.isEmpty ? [] : [.toolArguments(index: index, json)]
            default: return []
            }
        case "message_delta":
            var events: [AIStreamEvent] = []
            if let reason = payload.delta?.stopReason { events.append(.stop(Self.anthropicStop(reason))) }
            if let output = payload.usage?.outputTokens { events.append(.usage(input: nil, output: output)) }
            return events
        case "message_stop":
            return [.end]
        case "error":
            throw AIError.from(status: nil, details: payload.error?.details ?? AIErrorDetails())
        default:
            return []  // ping, content_block_stop
        }
    }

    static func openAIStop(_ reason: String) -> AIStopReason {
        switch reason {
        case "stop": .complete
        case "length": .cutOff
        case "content_filter": .refused
        case "tool_calls", "function_call": .toolUse
        default: .other(reason)
        }
    }

    static func anthropicStop(_ reason: String) -> AIStopReason {
        switch reason {
        case "end_turn", "stop_sequence": .complete
        case "max_tokens": .cutOff
        case "refusal": .refused
        case "tool_use": .toolUse
        default: .other(reason)
        }
    }
}

/// A streamed answer so far.
public struct AIAnswer: Hashable, Sendable {
    public var text = ""
    public var isThinking = false
    public var stop: AIStopReason?
    public var inputTokens: Int?
    public var outputTokens: Int?
    public var hasEnded = false
    /// OpenAI-compatible reasoning text.
    public var reasoning = ""
    /// Anthropic thinking blocks, in order.
    public private(set) var thinkingBlocks: [AIThinkingBlock] = []
    /// The tools the model asked for, in order; arguments are complete once the stream has ended.
    public private(set) var toolCalls: [AIToolCall] = []
    private var thinkingPositions: [Int: Int] = [:]
    private var callPositions: [Int: Int] = [:]

    public init() {}

    public mutating func apply(_ event: AIStreamEvent) {
        switch event {
        case .text(let piece):
            text += piece
            isThinking = false
        case .thinking:
            if text.isEmpty && toolCalls.isEmpty { isThinking = true }
        case .reasoning(let piece):
            reasoning += piece
        case .thinkingBlock(let index, let data):
            thinkingPositions[index] = thinkingBlocks.count
            thinkingBlocks.append(AIThinkingBlock(redactedData: data))
        case .thinkingText(let index, let piece):
            if let position = thinkingPosition(index) { thinkingBlocks[position].text += piece }
        case .thinkingSignature(let index, let piece):
            if let position = thinkingPosition(index) {
                thinkingBlocks[position].signature = (thinkingBlocks[position].signature ?? "") + piece
            }
        case .toolCall(let index, let id, let name):
            let position: Int
            if let index, let known = callPositions[index] {
                position = known
            } else if index == nil, let last = toolCalls.indices.last, id == nil || id == toolCalls[last].id || toolCalls[last].id.isEmpty {
                position = last
            } else {
                position = toolCalls.count
                toolCalls.append(AIToolCall(id: "", name: "", arguments: ""))
                if let index { callPositions[index] = position }
            }
            if let id, toolCalls[position].id.isEmpty { toolCalls[position].id = id }
            if let name, toolCalls[position].name.isEmpty { toolCalls[position].name = name }
            isThinking = false
        case .toolArguments(let index, let piece):
            if let index, let position = callPositions[index] {
                toolCalls[position].arguments += piece
            } else if index == nil, let last = toolCalls.indices.last {
                toolCalls[last].arguments += piece
            }
        case .usage(let input, let output):
            if let input { inputTokens = input }
            if let output { outputTokens = output }
        case .stop(let reason):
            stop = reason
            isThinking = false
        case .end:
            hasEnded = true
            isThinking = false
        }
    }

    /// Thinking deltas without a block start (some proxies) open one.
    private mutating func thinkingPosition(_ index: Int) -> Int? {
        if let position = thinkingPositions[index] { return position }
        thinkingPositions[index] = thinkingBlocks.count
        thinkingBlocks.append(AIThinkingBlock())
        return thinkingBlocks.count - 1
    }

    /// Something came back: text, tool calls, or a refusal.
    public var hasResult: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !toolCalls.isEmpty || stop == .refused
    }
}

private struct OpenAIChunk: Decodable {
    struct Choice: Decodable {
        struct Delta: Decodable {
            struct ToolCall: Decodable {
                struct Function: Decodable {
                    var name: String?
                    var arguments: String?
                }
                var index: Int?
                var id: String?
                var function: Function?
            }
            var content: String?
            var reasoningContent: String?
            var reasoning: String?
            var toolCalls: [ToolCall]?
            enum CodingKeys: String, CodingKey {
                case content, reasoning
                case reasoningContent = "reasoning_content"
                case toolCalls = "tool_calls"
            }
        }
        var delta: Delta?
        var finishReason: String?
        enum CodingKeys: String, CodingKey {
            case delta
            case finishReason = "finish_reason"
        }
    }
    struct Usage: Decodable {
        var promptTokens: Int?
        var completionTokens: Int?
        enum CodingKeys: String, CodingKey {
            case promptTokens = "prompt_tokens"
            case completionTokens = "completion_tokens"
        }
    }
    var choices: [Choice]?
    var usage: Usage?
    var error: ErrorBody?
}

private struct AnthropicEvent: Decodable {
    struct Usage: Decodable {
        var inputTokens: Int?
        var outputTokens: Int?
        enum CodingKeys: String, CodingKey {
            case inputTokens = "input_tokens"
            case outputTokens = "output_tokens"
        }
    }
    struct Message: Decodable { var usage: Usage? }
    struct Block: Decodable {
        var type: String
        var id: String?
        var name: String?
        var text: String?
        var thinking: String?
        var signature: String?
        var data: String?
    }
    struct Delta: Decodable {
        var type: String?
        var text: String?
        var thinking: String?
        var signature: String?
        var partialJSON: String?
        var stopReason: String?
        enum CodingKeys: String, CodingKey {
            case type, text, thinking, signature
            case partialJSON = "partial_json"
            case stopReason = "stop_reason"
        }
    }
    var type: String
    var index: Int?
    var message: Message?
    var contentBlock: Block?
    var delta: Delta?
    var usage: Usage?
    var error: ErrorBody?
    enum CodingKeys: String, CodingKey {
        case type, index, message, delta, usage, error
        case contentBlock = "content_block"
    }
}

extension AIStreamDecoder {
    /// Reads a response that came back whole instead of streamed (some proxies ignore `stream: true`): the answer's
    /// text and events, or the error it carries. `nil` when the body is neither.
    public func decodeComplete(_ data: Data) throws -> [AIStreamEvent]? {
        guard let response = try? JSONDecoder().decode(CompleteResponse.self, from: data) else { return nil }
        if let error = response.error { throw AIError.from(status: nil, details: error.details) }
        var events: [AIStreamEvent] = []
        switch format {
        case .openAI:
            guard let choice = response.choices?.first else { return nil }
            if let reasoning = choice.message?.reasoningContent, !reasoning.isEmpty { events.append(.reasoning(reasoning)) }
            if let text = choice.message?.content, !text.isEmpty { events.append(.text(text)) }
            for (index, call) in (choice.message?.toolCalls ?? []).enumerated() {
                events.append(.toolCall(index: index, id: call.id, name: call.function?.name))
                if let arguments = call.function?.arguments { events.append(.toolArguments(index: index, arguments)) }
            }
            if let reason = choice.finishReason { events.append(.stop(Self.openAIStop(reason))) }
            if let usage = response.usage {
                events.append(.usage(input: usage.promptTokens ?? usage.inputTokens,
                                     output: usage.completionTokens ?? usage.outputTokens))
            }
        case .anthropic:
            guard let content = response.content else { return nil }
            for (index, block) in content.enumerated() {
                switch block.type {
                case "text":
                    if let text = block.text, !text.isEmpty { events.append(.text(text)) }
                case "thinking":
                    events.append(.thinkingBlock(index: index, redactedData: nil))
                    if let text = block.thinking, !text.isEmpty { events.append(.thinkingText(index: index, text)) }
                    if let signature = block.signature { events.append(.thinkingSignature(index: index, signature)) }
                case "redacted_thinking":
                    events.append(.thinkingBlock(index: index, redactedData: block.data ?? ""))
                case "tool_use":
                    events.append(.toolCall(index: index, id: block.id, name: block.name))
                    events.append(.toolArguments(index: index, (block.input ?? .object([:])).jsonText))
                default:
                    break
                }
            }
            if let reason = response.stopReason { events.append(.stop(Self.anthropicStop(reason))) }
            if let usage = response.usage { events.append(.usage(input: usage.inputTokens, output: usage.outputTokens)) }
        }
        events.append(.end)
        return events
    }
}

private struct CompleteResponse: Decodable {
    struct Choice: Decodable {
        struct Message: Decodable {
            struct ToolCall: Decodable {
                struct Function: Decodable {
                    var name: String?
                    var arguments: String?
                }
                var id: String?
                var function: Function?
            }
            var content: String?
            var reasoningContent: String?
            var toolCalls: [ToolCall]?
            enum CodingKeys: String, CodingKey {
                case content
                case reasoningContent = "reasoning_content"
                case toolCalls = "tool_calls"
            }
        }
        var message: Message?
        var finishReason: String?
        enum CodingKeys: String, CodingKey {
            case message
            case finishReason = "finish_reason"
        }
    }
    struct Block: Decodable {
        var type: String
        var text: String?
        var thinking: String?
        var signature: String?
        var data: String?
        var id: String?
        var name: String?
        var input: JSONValue?
    }
    struct Usage: Decodable {
        var promptTokens: Int?
        var completionTokens: Int?
        var inputTokens: Int?
        var outputTokens: Int?
        enum CodingKeys: String, CodingKey {
            case promptTokens = "prompt_tokens"
            case completionTokens = "completion_tokens"
            case inputTokens = "input_tokens"
            case outputTokens = "output_tokens"
        }
    }
    var choices: [Choice]?
    var content: [Block]?
    var stopReason: String?
    var usage: Usage?
    var error: ErrorBody?
    enum CodingKeys: String, CodingKey {
        case choices, content, usage, error
        case stopReason = "stop_reason"
    }
}
