import Foundation

/// A picture sent with a message (JPEG). A saved chat keeps only that there was one (`data` empty).
public struct AIImage: Hashable, Codable, Sendable {
    public var data: Data
    public var mediaType: String

    public init(data: Data, mediaType: String = "image/jpeg") {
        self.data = data
        self.mediaType = mediaType
    }

    public var isAvailable: Bool { !data.isEmpty }

    /// Stands in for a picture that is no longer kept.
    static let missingNote = "(A photo was sent here earlier; it isn't available any more.)"
}

/// A tool the model asked to run.
public struct AIToolCall: Hashable, Codable, Sendable, Identifiable {
    /// The provider's ID, which the result must name.
    public var id: String
    public var name: String
    /// The arguments as the model wrote them (JSON text, possibly invalid).
    public var arguments: String

    public init(id: String, name: String, arguments: String) {
        self.id = id
        self.name = name
        self.arguments = arguments
    }
}

/// What came of a tool call, for the model.
public struct AIToolResult: Hashable, Codable, Sendable {
    public var callID: String
    public var content: String
    /// The call was wrong (unknown tool, bad arguments) or failed; the model may try again.
    public var isError: Bool

    public init(callID: String, content: String, isError: Bool = false) {
        self.callID = callID
        self.content = content
        self.isError = isError
    }
}

/// An Anthropic thinking block, sent back unchanged while the tools of the same turn run.
public struct AIThinkingBlock: Hashable, Codable, Sendable {
    public var text: String
    public var signature: String?
    /// A `redacted_thinking` block's data.
    public var redactedData: String?

    public init(text: String = "", signature: String? = nil, redactedData: String? = nil) {
        self.text = text
        self.signature = signature
        self.redactedData = redactedData
    }
}

/// One turn of a conversation.
public struct AIMessage: Hashable, Codable, Sendable {
    public enum Role: String, Hashable, Codable, Sendable { case user, assistant }

    public var role: Role
    public var text: String
    /// User: pictures sent with the text.
    public var images: [AIImage]
    /// Assistant: the tools it asked to run, in order.
    public var toolCalls: [AIToolCall]
    /// User: the results of the previous answer's tool calls (sent before `text`).
    public var toolResults: [AIToolResult]
    /// Assistant, OpenAI-compatible: the model's `reasoning_content`. DeepSeek needs every earlier one back once
    /// a request has tools.
    public var reasoning: String?
    /// Assistant, Anthropic: thinking blocks, needed back unchanged while that turn's tools run.
    public var thinking: [AIThinkingBlock]

    public init(_ role: Role, _ text: String, images: [AIImage] = [], toolCalls: [AIToolCall] = [],
                toolResults: [AIToolResult] = [], reasoning: String? = nil, thinking: [AIThinkingBlock] = []) {
        self.role = role
        self.text = text
        self.images = images
        self.toolCalls = toolCalls
        self.toolResults = toolResults
        self.reasoning = reasoning
        self.thinking = thinking
    }

    /// A message the user typed (not only tool results): where a turn starts.
    public var startsTurn: Bool { role == .user && toolResults.isEmpty }

    /// What the message costs in the conversation's length budget.
    var size: Int {
        text.count + images.count * 1500 + (reasoning?.count ?? 0)
            + toolCalls.reduce(0) { $0 + $1.name.count + $1.arguments.count }
            + toolResults.reduce(0) { $0 + $1.content.count }
    }

    /// The message as a saved chat keeps it: without the pictures' bytes and without thinking blocks (which only
    /// matter while a turn's tools run).
    public var forStorage: AIMessage {
        var copy = self
        copy.images = images.map { AIImage(data: Data(), mediaType: $0.mediaType) }
        copy.thinking = []
        return copy
    }

    private enum CodingKeys: String, CodingKey { case role, text, images, toolCalls, toolResults, reasoning, thinking }

    // Chats saved by 0.4 have only role and text.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        role = try container.decode(Role.self, forKey: .role)
        text = try container.decodeIfPresent(String.self, forKey: .text) ?? ""
        images = try container.decodeIfPresent([AIImage].self, forKey: .images) ?? []
        toolCalls = try container.decodeIfPresent([AIToolCall].self, forKey: .toolCalls) ?? []
        toolResults = try container.decodeIfPresent([AIToolResult].self, forKey: .toolResults) ?? []
        reasoning = try container.decodeIfPresent(String.self, forKey: .reasoning)
        thinking = try container.decodeIfPresent([AIThinkingBlock].self, forKey: .thinking) ?? []
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(role, forKey: .role)
        try container.encode(text, forKey: .text)
        if !images.isEmpty { try container.encode(images, forKey: .images) }
        if !toolCalls.isEmpty { try container.encode(toolCalls, forKey: .toolCalls) }
        if !toolResults.isEmpty { try container.encode(toolResults, forKey: .toolResults) }
        try container.encodeIfPresent(reasoning, forKey: .reasoning)
        if !thinking.isEmpty { try container.encode(thinking, forKey: .thinking) }
    }
}

/// What to ask, independent of the provider.
public struct AIRequest: Hashable, Sendable {
    public var system: String
    public var messages: [AIMessage]
    /// The answer must be one JSON object (JSON mode where the provider has it; the prompt asks for it anyway).
    public var wantsJSON: Bool
    /// The tools the model may call (`tool_choice` is left at the providers' default, "auto").
    public var tools: [AITool]

    public init(system: String, messages: [AIMessage], wantsJSON: Bool = false, tools: [AITool] = []) {
        self.system = system
        self.messages = messages
        self.wantsJSON = wantsJSON
        self.tools = tools
    }

    public init(system: String, user: String, wantsJSON: Bool = false) {
        self.init(system: system, messages: [AIMessage(.user, user)], wantsJSON: wantsJSON)
    }

    /// Drops the oldest turns until the text fits `maxCharacters`. The latest turn (from the last message the
    /// user typed, with its tool calls and results) is always kept whole, so the first kept message is a user
    /// turn and no tool call loses its result. Once anything is dropped, the thinking blocks are dropped too:
    /// Anthropic ties them to everything before them.
    public func trimmed(maxCharacters: Int) -> AIRequest {
        let starts = messages.indices.filter { messages[$0].startsTurn }
        guard let last = starts.last else { return self }
        var first = last
        var used = system.count + messages[last...].reduce(0) { $0 + $1.size }
        for start in starts.dropLast().reversed() {
            let turn = messages[start..<first].reduce(0) { $0 + $1.size }
            if used + turn > maxCharacters { break }
            used += turn
            first = start
        }
        guard first > 0 else { return self }
        var copy = self
        copy.messages = messages[first...].map { message in
            var message = message
            message.thinking = []
            return message
        }
        return copy
    }

    /// The same request without Anthropic thinking blocks: the retry when a provider rejects them.
    public var withoutThinking: AIRequest {
        var copy = self
        copy.messages = messages.map { message in
            var message = message
            message.thinking = []
            return message
        }
        return copy
    }
}

/// An HTTP request ready for URLSession or Ktor.
public struct AIHTTPRequest: Hashable, Sendable {
    public var url: URL
    public var method: String
    public var headers: [String: String]
    public var body: Data?
}

extension AIConnection {
    /// Anthropic needs an output ceiling; this is the most an answer may use, not a target.
    public static let anthropicMaxTokens = 16000
    public static let anthropicVersion = "2023-06-01"

    /// The streaming chat request for `request`.
    public func chatRequest(_ request: AIRequest, key: String?) throws -> AIHTTPRequest {
        guard let base = normalizedBaseURL else { throw AIError.badAddress }
        guard !model.trimmingCharacters(in: .whitespaces).isEmpty else { throw AIError.noModel }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        switch format {
        case .openAI:
            var body: [String: JSONValue] = [
                "model": .string(model),
                "messages": .array(Self.openAIMessages(request)),
                "stream": true,
            ]
            if reportsStreamUsage { body["stream_options"] = ["include_usage": true] }
            if request.wantsJSON && supportsJSONMode { body["response_format"] = ["type": "json_object"] }
            if !request.tools.isEmpty {
                body["tools"] = .array(request.tools.map { tool in
                    ["type": "function",
                     "function": ["name": .string(tool.name), "description": .string(tool.description),
                                  "parameters": tool.schema]]
                })
            }
            return AIHTTPRequest(url: base.appendingPathComponent("chat/completions"), method: "POST",
                                 headers: headers(key: key), body: try encoder.encode(JSONValue.object(body)))
        case .anthropic:
            var body: [String: JSONValue] = [
                "model": .string(model),
                "max_tokens": .int(Self.anthropicMaxTokens),
                "messages": .array(Self.anthropicMessages(request.messages)),
                "stream": true,
            ]
            if !request.system.isEmpty { body["system"] = .string(request.system) }
            if !request.tools.isEmpty {
                body["tools"] = .array(request.tools.map { tool in
                    ["name": .string(tool.name), "description": .string(tool.description), "input_schema": tool.schema]
                })
            }
            return AIHTTPRequest(url: anthropicURL(base, "messages"), method: "POST", headers: headers(key: key),
                                 body: try encoder.encode(JSONValue.object(body)))
        }
    }

    /// Chat Completions messages: the system prompt, then per message the tool results as `tool` messages, the
    /// text (with pictures as content parts) and the assistant's tool calls.
    static func openAIMessages(_ request: AIRequest) -> [JSONValue] {
        var messages: [JSONValue] = request.system.isEmpty ? [] : [["role": "system", "content": .string(request.system)]]
        for message in request.messages {
            switch message.role {
            case .user:
                for result in message.toolResults {
                    messages.append(["role": "tool", "tool_call_id": .string(result.callID),
                                     "content": .string(result.isError ? "Error: \(result.content)" : result.content)])
                }
                if !message.text.isEmpty || !message.images.isEmpty {
                    messages.append(["role": "user", "content": openAIContent(message)])
                }
            case .assistant:
                guard !message.text.isEmpty || !message.toolCalls.isEmpty else { continue }
                var fields: [String: JSONValue] = ["role": "assistant", "content": .string(message.text)]
                if !message.toolCalls.isEmpty {
                    fields["tool_calls"] = .array(message.toolCalls.map { call in
                        ["id": .string(call.id), "type": "function",
                         "function": ["name": .string(call.name), "arguments": .string(normalizedArguments(call.arguments))]]
                    })
                }
                if let reasoning = message.reasoning, !reasoning.isEmpty { fields["reasoning_content"] = .string(reasoning) }
                messages.append(.object(fields))
            }
        }
        return messages
    }

    private static func openAIContent(_ message: AIMessage) -> JSONValue {
        let available = message.images.filter(\.isAvailable)
        var text = message.text
        if available.count < message.images.count { text = [AIImage.missingNote, text].filter { !$0.isEmpty }.joined(separator: "\n") }
        guard !available.isEmpty else { return .string(text) }
        var parts: [JSONValue] = available.map { image in
            ["type": "image_url", "image_url": ["url": .string("data:\(image.mediaType);base64,\(image.data.base64EncodedString())")]]
        }
        if !text.isEmpty { parts.append(["type": "text", "text": .string(text)]) }
        return .array(parts)
    }

    /// Messages API messages: content blocks (thinking, text, tool use; tool results, pictures, text), with
    /// neighbouring messages of the same role joined, as the API wants roles to alternate. A message with
    /// only text stays a plain string.
    static func anthropicMessages(_ messages: [AIMessage]) -> [JSONValue] {
        var result: [(role: String, blocks: [JSONValue])] = []
        for message in messages {
            var blocks: [JSONValue] = []
            switch message.role {
            case .user:
                for item in message.toolResults {
                    var block: [String: JSONValue] = ["type": "tool_result", "tool_use_id": .string(item.callID),
                                                      "content": .string(item.content)]
                    if item.isError { block["is_error"] = true }
                    blocks.append(.object(block))
                }
                var text = message.text
                for image in message.images {
                    if image.isAvailable {
                        blocks.append(["type": "image", "source": ["type": "base64", "media_type": .string(image.mediaType),
                                                                   "data": .string(image.data.base64EncodedString())]])
                    } else {
                        text = [AIImage.missingNote, text].filter { !$0.isEmpty }.joined(separator: "\n")
                    }
                }
                if !text.isEmpty { blocks.append(["type": "text", "text": .string(text)]) }
            case .assistant:
                for block in message.thinking {
                    if let data = block.redactedData {
                        blocks.append(["type": "redacted_thinking", "data": .string(data)])
                    } else if let signature = block.signature {
                        blocks.append(["type": "thinking", "thinking": .string(block.text), "signature": .string(signature)])
                    }
                }
                if !message.text.isEmpty { blocks.append(["type": "text", "text": .string(message.text)]) }
                for call in message.toolCalls {
                    let input = JSONValue.parse(normalizedArguments(call.arguments)).flatMap { $0.objectValue != nil ? $0 : nil }
                    blocks.append(["type": "tool_use", "id": .string(call.id), "name": .string(call.name),
                                   "input": input ?? .object([:])])
                }
            }
            // An answer stopped before any text has nothing to send.
            guard !blocks.isEmpty else { continue }
            let role = message.role.rawValue
            if let last = result.last, last.role == role {
                result[result.count - 1].blocks += blocks
            } else {
                result.append((role, blocks))
            }
        }
        return result.map { item in
            if item.blocks.count == 1, case .object(let block) = item.blocks[0], block["type"] == "text", let text = block["text"] {
                return ["role": .string(item.role), "content": text]
            }
            return ["role": .string(item.role), "content": .array(item.blocks)]
        }
    }

    /// Empty arguments (a tool without parameters) as `{}`.
    private static func normalizedArguments(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "{}" : text
    }

    /// The request that lists the provider's models.
    public func modelsRequest(key: String?) throws -> AIHTTPRequest {
        guard let base = normalizedBaseURL else { throw AIError.badAddress }
        var headers = headers(key: key)
        headers["Content-Type"] = nil
        headers["Accept"] = "application/json"
        switch format {
        case .openAI:
            return AIHTTPRequest(url: base.appendingPathComponent("models"), method: "GET", headers: headers, body: nil)
        case .anthropic:
            var components = URLComponents(url: anthropicURL(base, "models"), resolvingAgainstBaseURL: false)!
            components.queryItems = [URLQueryItem(name: "limit", value: "100")]
            return AIHTTPRequest(url: components.url!, method: "GET", headers: headers, body: nil)
        }
    }

    /// The model IDs in a models-list response (`{"data": [{"id": …}]}` in both formats), sorted.
    public static func modelIDs(from data: Data) throws -> [String] {
        struct List: Decodable {
            struct Model: Decodable { var id: String }
            var data: [Model]
        }
        guard let list = try? JSONDecoder().decode(List.self, from: data) else { throw AIError.unreadable }
        return Array(Set(list.data.map(\.id))).sorted()
    }

    private func headers(key: String?) -> [String: String] {
        var headers = ["Content-Type": "application/json", "Accept": "text/event-stream"]
        let key = key?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        switch format {
        case .openAI:
            if !key.isEmpty { headers["Authorization"] = "Bearer \(key)" }
        case .anthropic:
            headers["x-api-key"] = key
            headers["anthropic-version"] = Self.anthropicVersion
        }
        return headers
    }

    /// `https://api.anthropic.com` and `https://api.anthropic.com/v1` both work as the base.
    private func anthropicURL(_ base: URL, _ endpoint: String) -> URL {
        base.lastPathComponent == "v1" ? base.appendingPathComponent(endpoint)
            : base.appendingPathComponent("v1").appendingPathComponent(endpoint)
    }
}
