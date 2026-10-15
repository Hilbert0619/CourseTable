import CourseKit
import Foundation

/// One argument of a tool.
public struct AIToolParameter: Hashable, Sendable {
    public enum Kind: Hashable, Sendable {
        /// Text up to `maxLength` characters (trimmed; an empty text counts as left out).
        case text(maxLength: Int)
        /// `YYYY-MM-DD`.
        case date
        /// 24-hour `HH:mm`.
        case time
        case integer(ClosedRange<Int>)
        case boolean
        /// One of these words.
        case choice([String])
    }

    public var name: String
    public var kind: Kind
    public var description: String
    public var isRequired: Bool

    public init(_ name: String, _ kind: Kind, _ description: String, required: Bool = false) {
        self.name = name
        self.kind = kind
        self.description = description
        self.isRequired = required
    }
}

/// A function of the app the model may call ("tool"). Reading tools run on their own; changing tools become a
/// proposal the user confirms (docs/ai-agent-spec.md §2).
public struct AITool: Hashable, Sendable, Identifiable {
    public enum Effect: Hashable, Sendable {
        case reads
        case changes
    }

    public var name: String
    public var description: String
    public var parameters: [AIToolParameter]
    public var effect: Effect

    public var id: String { name }

    public init(name: String, description: String, parameters: [AIToolParameter] = [], effect: Effect) {
        self.name = name
        self.description = description
        self.parameters = parameters
        self.effect = effect
    }

    /// The parameters as JSON Schema. Only the keywords every provider accepts (type, description, enum,
    /// minimum, maximum, properties, required); length limits and formats are in the descriptions, and
    /// `validate` checks them.
    public var schema: JSONValue {
        var properties: [String: JSONValue] = [:]
        for parameter in parameters {
            var property: [String: JSONValue] = ["description": .string(parameter.description)]
            switch parameter.kind {
            case .text(let maxLength):
                property["type"] = "string"
                property["description"] = .string("\(parameter.description) (at most \(maxLength) characters)")
            case .date:
                property["type"] = "string"
                property["description"] = .string("\(parameter.description) (YYYY-MM-DD)")
            case .time:
                property["type"] = "string"
                property["description"] = .string("\(parameter.description) (24-hour HH:mm)")
            case .integer(let range):
                property["type"] = "integer"
                property["minimum"] = .int(range.lowerBound)
                property["maximum"] = .int(range.upperBound)
            case .boolean:
                property["type"] = "boolean"
            case .choice(let words):
                property["type"] = "string"
                property["enum"] = .array(words.map(JSONValue.string))
            }
            properties[parameter.name] = .object(property)
        }
        var schema: [String: JSONValue] = ["type": "object", "properties": .object(properties)]
        let required = parameters.filter(\.isRequired).map(\.name)
        if !required.isEmpty { schema["required"] = .array(required.map(JSONValue.string)) }
        return .object(schema)
    }

    /// Checks the arguments the model wrote (JSON text) against the parameters: required ones are there, each
    /// value has the right type, length, format or range. Values are normalised (dates and times in their
    /// canonical form, numbers given as text read as numbers); unknown names are ignored.
    public func validate(_ text: String) -> Result<AIToolArguments, AIToolError> {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let fields: [String: JSONValue]
        if trimmed.isEmpty || trimmed == "null" {
            fields = [:]
        } else if let object = JSONValue.parse(trimmed)?.objectValue {
            fields = object
        } else {
            return .failure(AIToolError("The arguments aren't a JSON object. Call \(name) again with valid JSON."))
        }
        var values: [String: JSONValue] = [:]
        for parameter in parameters {
            let key = parameter.name
            switch Self.normalize(fields[key], as: parameter.kind) {
            case .missing:
                if parameter.isRequired { return .failure(AIToolError("\"\(key)\" is missing.")) }
            case .value(let value):
                values[key] = value
            case .wrong(let expectation):
                return .failure(AIToolError("\"\(key)\" \(expectation)."))
            }
        }
        return .success(AIToolArguments(values: values))
    }

    private enum Normalized {
        case missing
        case value(JSONValue)
        case wrong(String)
    }

    private static func normalize(_ value: JSONValue?, as kind: AIToolParameter.Kind) -> Normalized {
        guard let value, value != .null else { return .missing }
        let text: String? = switch value {
        case .string(let text): text.trimmingCharacters(in: .whitespacesAndNewlines)
        case .int(let number): String(number)
        case .double(let number): String(number)
        case .bool(let flag): String(flag)
        default: nil
        }
        switch kind {
        case .text(let maxLength):
            guard let text else { return .wrong("must be text") }
            if text.isEmpty { return .missing }
            return text.count <= maxLength ? .value(.string(text)) : .wrong("is too long (at most \(maxLength) characters)")
        case .date:
            guard let text else { return .wrong("must be a date like 2026-10-15") }
            if text.isEmpty { return .missing }
            guard let date = LocalDate(String(text.prefix(10))), text.count == 10 || text.dropFirst(10).first == "T"
            else { return .wrong("must be a date like 2026-10-15") }
            return .value(.string(date.description))
        case .time:
            guard let text else { return .wrong("must be a 24-hour time like 08:30") }
            if text.isEmpty { return .missing }
            guard let time = TimeOfDay(text) else { return .wrong("must be a 24-hour time like 08:30") }
            return .value(.string(time.description))
        case .integer(let range):
            let number = value.intValue ?? text.flatMap { Int($0) }
            guard let number else {
                if text?.isEmpty == true { return .missing }
                return .wrong("must be a whole number")
            }
            return range.contains(number) ? .value(.int(number))
                : .wrong("must be between \(range.lowerBound) and \(range.upperBound)")
        case .boolean:
            switch value {
            case .bool(let flag): return .value(.bool(flag))
            case .string(let word) where ["true", "false"].contains(word.lowercased()):
                return .value(.bool(word.lowercased() == "true"))
            default: return .wrong("must be true or false")
            }
        case .choice(let words):
            guard let text, let word = words.first(where: { $0.lowercased() == text.lowercased() }) else {
                return .wrong("must be one of: \(words.joined(separator: ", "))")
            }
            return .value(.string(word))
        }
    }
}

/// Arguments that passed `AITool.validate`.
public struct AIToolArguments: Hashable, Sendable {
    public var values: [String: JSONValue]

    public init(values: [String: JSONValue] = [:]) {
        self.values = values
    }

    public func text(_ name: String) -> String? { values[name]?.stringValue }
    public func int(_ name: String) -> Int? { values[name]?.intValue }
    public func date(_ name: String) -> LocalDate? { text(name).flatMap(LocalDate.init) }
    public func time(_ name: String) -> TimeOfDay? { text(name).flatMap(TimeOfDay.init) }

    public func bool(_ name: String) -> Bool? {
        if case .bool(let flag) = values[name] { return flag }
        return nil
    }

    public func has(_ name: String) -> Bool { values[name] != nil }
}

/// Why a tool call can't run, in words for the model (sent back as an error result so it can fix the call).
public struct AIToolError: Error, Hashable, Sendable {
    public var message: String

    public init(_ message: String) {
        self.message = message
    }
}
