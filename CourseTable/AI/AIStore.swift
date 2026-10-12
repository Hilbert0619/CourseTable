import Foundation
import Observation
import AIKit

/// Requests and tokens sent through one connection in one month.
struct AIUsageTotal: Hashable, Codable {
    var requests = 0
    var inputTokens = 0
    var outputTokens = 0
}

/// A finished answer kept on the device, so showing it again costs nothing.
struct CachedAIAnswer: Hashable, Codable {
    var text: String
    var model: String
    var date: Date
}

/// An Assistant chat, kept on the device.
struct AIConversation: Identifiable, Hashable, Codable {
    var id = UUID()
    var messages: [AIMessage] = []
    var updated = Date.now

    /// The first question, shortened.
    var title: String {
        let first = messages.first { $0.role == .user }?.text ?? ""
        let line = first.split(separator: "\n").first.map(String.init) ?? ""
        return line.count > 40 ? String(line.prefix(40)) + "…" : line
    }
}

/// The user's AI connections (keys in the Keychain), the default one, monthly usage and saved answers
/// (docs/ai-spec.md). Nothing here talks to CourseTable's own server.
@MainActor @Observable
final class AIStore {
    private(set) var connections: [AIConnection] = []
    private(set) var defaultID: UUID?
    private(set) var usage: [String: AIUsageTotal] = [:]
    let transport: any AITransport

    /// Assistant chats, newest first.
    private(set) var conversations: [AIConversation] = []

    private let defaults: UserDefaults
    private let cacheURL: URL?
    private var cache: [String: CachedAIAnswer] = [:]
    /// UI tests: keys stay in memory instead of the Keychain.
    private var memoryKeys: [UUID: String]?

    static let maxCachedAnswers = 400

    private enum Keys {
        static let connections = "ai.connections"
        static let defaultID = "ai.defaultConnection"
        static let usage = "ai.usage"
    }

    init(defaults: UserDefaults, cacheURL: URL?, transport: any AITransport, memoryKeys: [UUID: String]? = nil) {
        self.defaults = defaults
        self.cacheURL = cacheURL
        self.transport = transport
        self.memoryKeys = memoryKeys
        let decoder = JSONDecoder()
        if let data = defaults.data(forKey: Keys.connections),
           let saved = try? decoder.decode([AIConnection].self, from: data) {
            connections = saved
        }
        defaultID = defaults.string(forKey: Keys.defaultID).flatMap(UUID.init(uuidString:))
        if let data = defaults.data(forKey: Keys.usage), let saved = try? decoder.decode([String: AIUsageTotal].self, from: data) {
            usage = saved
        }
        if let cacheURL, let data = try? Data(contentsOf: cacheURL),
           let saved = try? decoder.decode([String: CachedAIAnswer].self, from: data) {
            cache = saved
        }
        if let url = cacheURL?.deletingLastPathComponent().appendingPathComponent("AIConversations.json"),
           let data = try? Data(contentsOf: url),
           let saved = try? decoder.decode([AIConversation].self, from: data) {
            conversations = saved
        }
    }

    /// Next to the saved answers.
    private var conversationsURL: URL? {
        cacheURL?.deletingLastPathComponent().appendingPathComponent("AIConversations.json")
    }

    static func makeForLaunch() -> AIStore {
        guard UITestLaunch.isActive else {
            let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            return AIStore(defaults: .standard, cacheURL: support?.appendingPathComponent("AIAnswers.json"),
                           transport: URLSessionAITransport())
        }
        // UI tests: a fresh store each launch; with -aiDemo, one connection answered by canned text. The screens'
        // drafts and switches (kept with @AppStorage) start empty too.
        for key in ["ai.sentenceDraft", "ai.essayDraft", "ai.essayPrompt", "ai.essayTask", "ai.includeSchedule",
                    "ai.includeHardWords"] {
            UserDefaults.standard.removeObject(forKey: key)
        }
        let defaults = UserDefaults(suiteName: "ui-test-ai-\(UUID().uuidString)") ?? .standard
        let store = AIStore(defaults: defaults, cacheURL: nil, transport: DemoAITransport(), memoryKeys: [:])
        if UITestLaunch.wantsAIDemo, let deepSeek = AIProvider.preset(id: "deepseek") {
            store.save(AIConnection(provider: deepSeek), key: "demo-key")
        }
        return store
    }

    // MARK: Connections

    /// The connection AI help uses: the chosen default, or else the first.
    var defaultConnection: AIConnection? {
        connections.first { $0.id == defaultID } ?? connections.first
    }

    var isSetUp: Bool { defaultConnection != nil }

    func key(for connection: AIConnection) -> String? {
        if let memoryKeys { return memoryKeys[connection.id] }
        return AIKeychain.key(for: connection.id)
    }

    func hasKey(_ connection: AIConnection) -> Bool { !(key(for: connection) ?? "").isEmpty }

    /// Adds or updates a connection. A `nil` key keeps the stored one; an empty key removes it.
    func save(_ connection: AIConnection, key: String?) {
        if let index = connections.firstIndex(where: { $0.id == connection.id }) {
            connections[index] = connection
        } else {
            connections.append(connection)
        }
        if let key {
            let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
            if memoryKeys != nil {
                memoryKeys?[connection.id] = trimmed.isEmpty ? nil : trimmed
            } else {
                AIKeychain.setKey(trimmed, for: connection.id)
            }
        }
        if defaultID == nil { defaultID = connection.id }
        persistConnections()
    }

    func delete(_ connection: AIConnection) {
        connections.removeAll { $0.id == connection.id }
        if memoryKeys != nil {
            memoryKeys?[connection.id] = nil
        } else {
            AIKeychain.setKey(nil, for: connection.id)
        }
        if defaultID == connection.id { defaultID = connections.first?.id }
        persistConnections()
    }

    func makeDefault(_ connection: AIConnection) {
        defaultID = connection.id
        persistConnections()
    }

    private func persistConnections() {
        defaults.set(try? JSONEncoder().encode(connections), forKey: Keys.connections)
        defaults.set(defaultID?.uuidString, forKey: Keys.defaultID)
    }

    // MARK: Usage

    func record(_ answer: AIAnswer, for connection: AIConnection, at date: Date = .now) {
        let key = Self.usageKey(connection.id, date)
        var total = usage[key] ?? AIUsageTotal()
        total.requests += 1
        total.inputTokens += answer.inputTokens ?? 0
        total.outputTokens += answer.outputTokens ?? 0
        usage[key] = total
        defaults.set(try? JSONEncoder().encode(usage), forKey: Keys.usage)
    }

    func usage(of connection: AIConnection, in month: Date = .now) -> AIUsageTotal {
        usage[Self.usageKey(connection.id, month)] ?? AIUsageTotal()
    }

    private static func usageKey(_ id: UUID, _ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.year, .month], from: date)
        return "\(id.uuidString)|\(parts.year ?? 0)-\(parts.month ?? 0)"
    }

    // MARK: Saved answers

    func cachedAnswer(_ key: String) -> CachedAIAnswer? { cache[key] }

    func saveAnswer(_ text: String, model: String, for key: String) {
        cache[key] = CachedAIAnswer(text: text, model: model, date: .now)
        if cache.count > Self.maxCachedAnswers {
            let oldest = cache.sorted { $0.value.date < $1.value.date }.prefix(cache.count - Self.maxCachedAnswers)
            for (key, _) in oldest { cache[key] = nil }
        }
        guard let cacheURL else { return }
        try? FileManager.default.createDirectory(at: cacheURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? JSONEncoder().encode(cache).write(to: cacheURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    /// Deletes the saved answers and the Assistant chats.
    func clearSavedAnswers() {
        cache = [:]
        conversations = []
        if let cacheURL { try? FileManager.default.removeItem(at: cacheURL) }
        if let url = conversationsURL { try? FileManager.default.removeItem(at: url) }
    }

    var savedAnswerCount: Int { cache.count }

    // MARK: Assistant chats

    static let maxConversations = 30

    func saveConversation(_ conversation: AIConversation) {
        guard !conversation.messages.isEmpty else { return }
        var updated = conversation
        updated.updated = .now
        conversations.removeAll { $0.id == conversation.id }
        conversations.insert(updated, at: 0)
        if conversations.count > Self.maxConversations { conversations.removeLast(conversations.count - Self.maxConversations) }
        persistConversations()
    }

    func deleteConversation(_ id: UUID) {
        conversations.removeAll { $0.id == id }
        persistConversations()
    }

    private func persistConversations() {
        guard let url = conversationsURL else { return }
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? JSONEncoder().encode(conversations).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    // MARK: Language

    /// Answers are written in the app's language.
    static var answerLanguage: AIAnswerLanguage {
        (Bundle.main.preferredLocalizations.first ?? "en").hasPrefix("zh") ? .simplifiedChinese : .english
    }
}
