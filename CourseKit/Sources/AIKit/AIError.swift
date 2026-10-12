import Foundation

/// What went wrong, in terms the app can explain. `message` is the provider's own text, when it sent one.
public enum AIError: Error, Hashable, Sendable {
    /// 401/403: the key is wrong, expired or not allowed to use this model.
    case wrongKey(message: String?)
    /// No credit or quota left (402, or any error that says so).
    case noCredit(message: String?)
    /// 404: no such model, or the address is wrong.
    case notFound(message: String?)
    /// 429: too many requests for now.
    case rateLimited(message: String?)
    /// 5xx / 529: the provider is overloaded or down.
    case busy(message: String?)
    /// Any other rejected request.
    case rejected(status: Int?, message: String?)
    /// The connection failed (no network, timeout, TLS…).
    case network(message: String)
    /// The base URL isn't a valid http(s) address.
    case badAddress
    /// No model name.
    case noModel
    /// The response wasn't in the expected format.
    case unreadable
    /// The answer ended without any text.
    case empty

    /// An error response, from its HTTP status and body (both formats wrap the details in `"error"`).
    public static func from(status: Int, body: Data) -> AIError {
        let details = (try? JSONDecoder().decode(ErrorEnvelope.self, from: body))?.error?.details
            ?? AIErrorDetails(message: String(data: body.prefix(300), encoding: .utf8)?
                .trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty)
        return from(status: status, details: details)
    }

    static func from(status: Int?, details: AIErrorDetails) -> AIError {
        let message = details.message
        let words = [details.type, details.code, message].compactMap { $0?.lowercased() }.joined(separator: " ")
        // "Quota" alone also shows up in per-minute rate limits (429), so it only counts for other statuses.
        let creditWords = ["insufficient", "balance", "credit", "arrearage", "billing"]
        if status == 402 || creditWords.contains(where: words.contains) || (status != 429 && words.contains("quota")) {
            return .noCredit(message: message)
        }
        switch status {
        case 401, 403: return .wrongKey(message: message)
        case 404: return .notFound(message: message)
        case 429: return .rateLimited(message: message)
        case let status? where status >= 500: return .busy(message: message)
        default: break
        }
        // Errors inside a stream have no status; go by their type.
        if words.contains("overloaded") || words.contains("api_error") { return .busy(message: message) }
        if words.contains("rate_limit") { return .rateLimited(message: message) }
        if words.contains("authentication") || words.contains("permission") { return .wrongKey(message: message) }
        if words.contains("not_found") { return .notFound(message: message) }
        return .rejected(status: status, message: message)
    }

    public var providerMessage: String? {
        switch self {
        case .wrongKey(let message), .noCredit(let message), .notFound(let message), .rateLimited(let message),
             .busy(let message), .rejected(_, let message):
            message
        case .network(let message):
            message
        case .badAddress, .noModel, .unreadable, .empty:
            nil
        }
    }

    /// Worth trying again later without changing anything.
    public var isTransient: Bool {
        switch self {
        case .rateLimited, .busy, .network: true
        default: false
        }
    }
}

public struct AIErrorDetails: Hashable, Sendable {
    public var message: String?
    public var type: String?
    public var code: String?

    public init(message: String? = nil, type: String? = nil, code: String? = nil) {
        self.message = message
        self.type = type
        self.code = code
    }
}

/// `{"message": …, "type": …, "code": …}`; `code` is a string or a number depending on the provider.
struct ErrorBody: Decodable {
    var message: String?
    var type: String?
    var code: String?

    enum CodingKeys: String, CodingKey { case message, type, code }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        message = try? container.decodeIfPresent(String.self, forKey: .message)
        type = try? container.decodeIfPresent(String.self, forKey: .type)
        if let text = try? container.decodeIfPresent(String.self, forKey: .code) {
            code = text
        } else if let number = try? container.decodeIfPresent(Int.self, forKey: .code) {
            code = String(number)
        }
    }

    var details: AIErrorDetails { AIErrorDetails(message: message, type: type, code: code) }
}

private struct ErrorEnvelope: Decodable {
    var error: ErrorBody?

    enum CodingKeys: String, CodingKey { case error }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let body = try? container.decodeIfPresent(ErrorBody.self, forKey: .error) {
            error = body
        } else if let text = try? container.decodeIfPresent(String.self, forKey: .error) {
            error = ErrorBody(message: text)  // e.g. Ollama: {"error": "model not found"}
        }
    }
}

extension ErrorBody {
    init(message: String) {
        self.message = message
    }
}

extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
