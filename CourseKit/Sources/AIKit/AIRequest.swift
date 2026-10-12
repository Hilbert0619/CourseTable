import Foundation

/// One turn of a conversation.
public struct AIMessage: Hashable, Codable, Sendable {
    public enum Role: String, Hashable, Codable, Sendable { case user, assistant }

    public var role: Role
    public var text: String

    public init(_ role: Role, _ text: String) {
        self.role = role
        self.text = text
    }
}

/// What to ask, independent of the provider.
public struct AIRequest: Hashable, Sendable {
    public var system: String
    public var messages: [AIMessage]
    /// The answer must be one JSON object (JSON mode where the provider has it; the prompt asks for it anyway).
    public var wantsJSON: Bool

    public init(system: String, messages: [AIMessage], wantsJSON: Bool = false) {
        self.system = system
        self.messages = messages
        self.wantsJSON = wantsJSON
    }

    public init(system: String, user: String, wantsJSON: Bool = false) {
        self.init(system: system, messages: [AIMessage(.user, user)], wantsJSON: wantsJSON)
    }

    /// Drops the oldest turns until the text fits `maxCharacters`, always keeping the latest message. The first kept
    /// message is a user turn, as the APIs expect.
    public func trimmed(maxCharacters: Int) -> AIRequest {
        var kept: [AIMessage] = []
        var used = system.count
        for message in messages.reversed() {
            if !kept.isEmpty && used + message.text.count > maxCharacters { break }
            kept.insert(message, at: 0)
            used += message.text.count
        }
        while kept.count > 1 && kept.first?.role != .user { kept.removeFirst() }
        var copy = self
        copy.messages = kept
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
            var messages = request.system.isEmpty ? [] : [OpenAIBody.Message(role: "system", content: request.system)]
            messages += request.messages.map { OpenAIBody.Message(role: $0.role.rawValue, content: $0.text) }
            let body = OpenAIBody(
                model: model, messages: messages, stream: true,
                streamOptions: reportsStreamUsage ? .init(includeUsage: true) : nil,
                responseFormat: request.wantsJSON && supportsJSONMode ? .init(type: "json_object") : nil)
            return AIHTTPRequest(url: base.appendingPathComponent("chat/completions"), method: "POST",
                                 headers: headers(key: key), body: try encoder.encode(body))
        case .anthropic:
            let body = AnthropicBody(
                model: model, maxTokens: Self.anthropicMaxTokens, system: request.system.isEmpty ? nil : request.system,
                messages: request.messages.map { .init(role: $0.role.rawValue, content: $0.text) }, stream: true)
            return AIHTTPRequest(url: anthropicURL(base, "messages"), method: "POST", headers: headers(key: key),
                                 body: try encoder.encode(body))
        }
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

private struct OpenAIBody: Encodable {
    struct Message: Encodable {
        var role: String
        var content: String
    }
    struct StreamOptions: Encodable {
        var includeUsage: Bool
        enum CodingKeys: String, CodingKey { case includeUsage = "include_usage" }
    }
    struct ResponseFormat: Encodable { var type: String }

    var model: String
    var messages: [Message]
    var stream: Bool
    var streamOptions: StreamOptions?
    var responseFormat: ResponseFormat?

    enum CodingKeys: String, CodingKey {
        case model, messages, stream
        case streamOptions = "stream_options"
        case responseFormat = "response_format"
    }
}

private struct AnthropicBody: Encodable {
    struct Message: Encodable {
        var role: String
        var content: String
    }

    var model: String
    var maxTokens: Int
    var system: String?
    var messages: [Message]
    var stream: Bool

    enum CodingKeys: String, CodingKey {
        case model, system, messages, stream
        case maxTokens = "max_tokens"
    }
}
