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
    case other(String)
}

/// What a streamed answer reports, in either wire format.
public enum AIStreamEvent: Hashable, Sendable {
    case text(String)
    /// A reasoning model is thinking (its reasoning isn't shown).
    case thinking
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
            if let reasoning = choice.delta?.reasoningContent ?? choice.delta?.reasoning, !reasoning.isEmpty {
                events.append(.thinking)
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
        switch payload.type {
        case "message_start":
            let usage = payload.message?.usage
            return usage?.inputTokens == nil ? [] : [.usage(input: usage?.inputTokens, output: nil)]
        case "content_block_delta":
            switch payload.delta?.type {
            case "text_delta": return (payload.delta?.text).map { $0.isEmpty ? [] : [.text($0)] } ?? []
            case "thinking_delta": return [.thinking]
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
            return []  // ping, content_block_start, content_block_stop
        }
    }

    private static func openAIStop(_ reason: String) -> AIStopReason {
        switch reason {
        case "stop": .complete
        case "length": .cutOff
        case "content_filter": .refused
        default: .other(reason)
        }
    }

    private static func anthropicStop(_ reason: String) -> AIStopReason {
        switch reason {
        case "end_turn", "stop_sequence": .complete
        case "max_tokens": .cutOff
        case "refusal": .refused
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

    public init() {}

    public mutating func apply(_ event: AIStreamEvent) {
        switch event {
        case .text(let piece):
            text += piece
            isThinking = false
        case .thinking:
            if text.isEmpty { isThinking = true }
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
}

private struct OpenAIChunk: Decodable {
    struct Choice: Decodable {
        struct Delta: Decodable {
            var content: String?
            var reasoningContent: String?
            var reasoning: String?
            enum CodingKeys: String, CodingKey {
                case content, reasoning
                case reasoningContent = "reasoning_content"
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
    struct Delta: Decodable {
        var type: String?
        var text: String?
        var stopReason: String?
        enum CodingKeys: String, CodingKey {
            case type, text
            case stopReason = "stop_reason"
        }
    }
    var type: String
    var message: Message?
    var delta: Delta?
    var usage: Usage?
    var error: ErrorBody?
}
