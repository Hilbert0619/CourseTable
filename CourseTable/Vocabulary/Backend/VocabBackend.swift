import Foundation
import VocabKit

/// The Supabase project the app talks to (docs/vocabulary-spec.md). The publishable key is public by
/// design: row-level security decides what each signed-in user may read and write.
enum BackendConfig {
    static let url = URL(string: "https://prkvpkxrugvbmrwxehhs.supabase.co")!
    static let publishableKey = "sb_publishable_Wo7kzWr72kGjftesgD4EUg_OD0qBuno"
}

struct Account: Hashable, Sendable {
    var id: UUID
    var email: String
}

enum SignUpOutcome: Hashable, Sendable {
    case signedIn(Account)
    /// The account exists but its email must be confirmed with the link Supabase sent.
    case confirmationSent
}

/// Everything the Words tab needs from the server. `SupabaseBackend` is the real one; `DemoBackend`
/// keeps everything in memory for tests, previews and screenshots.
protocol VocabBackend: AnyObject, Sendable {
    /// The account of the session stored on this device, even if its token has expired (it is
    /// refreshed on the next request), so the app also opens signed in when offline.
    func restoreAccount() async -> Account?
    func signUp(email: String, password: String) async throws -> SignUpOutcome
    func signIn(email: String, password: String) async throws -> Account
    /// Always signs out on this device, even offline.
    func signOut() async
    func sendPasswordReset(email: String) async throws
    /// Signs in with the session carried by an email link.
    func openLink(_ url: URL) async throws -> Account
    func updatePassword(_ password: String) async throws
    func deleteAccount() async throws

    func books() async throws -> [RemoteBook]
    /// Entries of a book in study order, with their dictionary words; rows `from...to` (0-based).
    func entries(book: UUID, from: Int, to: Int) async throws -> [RemoteEntry]
    func saveBooks(_ books: [RemoteBook]) async throws
    func saveEntries(_ entries: [RemoteEntry]) async throws
    /// Uploading an answer twice is harmless: the id decides.
    func uploadReviews(_ reviews: [RemoteReview]) async throws
    /// The learner's answers received after `receivedAfter`, oldest first; rows `from...to`.
    func reviews(receivedAfter: Date?, from: Int, to: Int) async throws -> [RemoteReview]
    func favorites() async throws -> [RemoteFavorite]
    func saveFavorites(_ favorites: [RemoteFavorite]) async throws
    func settings() async throws -> RemoteSettings?
    func saveSettings(_ settings: RemoteSettings) async throws
}

/// Why signing in or up failed, in words the learner can act on.
enum VocabAuthError: LocalizedError, Equatable {
    case invalidCredentials
    case emailNotConfirmed
    case weakPassword
    case invalidEmail
    case rateLimited
    case emailNotAllowed
    case linkExpired
    case offline
    case other(String)

    var errorDescription: String? {
        switch self {
        case .invalidCredentials: String(localized: "The email or password is wrong.")
        case .emailNotConfirmed: String(localized: "Confirm your email first: tap the link in the email we sent you.")
        case .weakPassword: String(localized: "Choose a longer password (at least 8 characters).")
        case .invalidEmail: String(localized: "That doesn't look like an email address.")
        case .rateLimited: String(localized: "Too many emails or attempts. Wait a while and try again.")
        case .emailNotAllowed: String(localized: "The server can't send email to this address yet. Use the email of the project's owner for now.")
        case .linkExpired: String(localized: "This link has expired or was already used. Ask for a new one.")
        case .offline: String(localized: "You're offline. Connect to the internet and try again.")
        case .other(let message): message
        }
    }
}

// MARK: - Rows exchanged with the server (table columns, docs/vocabulary-spec.md §5)

struct RemoteBook: Codable, Hashable, Sendable {
    var id: UUID
    var ownerID: UUID?
    var name: String
    var description: String
    var source: String
    var licence: String
    var wordCount: Int
    var updatedAt: Date
    var deletedAt: Date?

    enum CodingKeys: String, CodingKey {
        case id, name, description, source, licence
        case ownerID = "owner_id", wordCount = "word_count", updatedAt = "updated_at", deletedAt = "deleted_at"
    }

    func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(ownerID, forKey: .ownerID)
        try container.encode(name, forKey: .name)
        try container.encode(description, forKey: .description)
        try container.encode(source, forKey: .source)
        try container.encode(licence, forKey: .licence)
        try container.encode(wordCount, forKey: .wordCount)
        try container.encode(updatedAt, forKey: .updatedAt)
        try container.encode(deletedAt, forKey: .deletedAt)
    }
}

/// A row of `words`.
struct RemoteWord: Codable, Hashable, Sendable {
    var id: Int
    var headword: String
    var display: String
    var phonetic: String
    var translationZh: String
    var definitionEn: String
    var pos: String
    var collins: Int
    var oxford: Bool
    var tags: [String]
    var bnc: Int?
    var frq: Int?
    var exchange: String

    enum CodingKeys: String, CodingKey {
        case id, headword, display, phonetic, pos, collins, oxford, tags, bnc, frq, exchange
        case translationZh = "translation_zh", definitionEn = "definition_en"
    }

    var entry: WordEntry {
        WordEntry(headword: headword, display: display, phonetic: phonetic, translation: translationZh,
                  definition: definitionEn, posMix: pos, collins: collins, oxford: oxford, tags: tags,
                  bnc: bnc, frq: frq, exchange: exchange)
    }

    init(id: Int, entry: WordEntry) {
        self.id = id
        headword = entry.headword
        display = entry.display
        phonetic = entry.phonetic
        translationZh = entry.translation
        definitionEn = entry.definition
        pos = entry.posMix
        collins = entry.collins
        oxford = entry.oxford
        tags = entry.tags
        bnc = entry.bnc
        frq = entry.frq
        exchange = entry.exchange
    }
}

/// A row of `book_entries`; `word` is the embedded dictionary row when downloading (never uploaded).
struct RemoteEntry: Codable, Hashable, Sendable {
    var id: UUID
    var bookID: UUID
    var position: Int
    var headword: String
    var wordID: Int?
    var display: String?
    var meaning: String?
    var phonetic: String?
    var pos: String?
    var example: String?
    var exampleTranslation: String?
    var updatedAt: Date
    var deletedAt: Date?
    var word: RemoteWord?

    enum CodingKeys: String, CodingKey {
        case id, position, headword, display, meaning, phonetic, pos, example
        case bookID = "book_id", wordID = "word_id", exampleTranslation = "example_translation"
        case updatedAt = "updated_at", deletedAt = "deleted_at", word = "words"
    }

    func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(bookID, forKey: .bookID)
        try container.encode(position, forKey: .position)
        try container.encode(headword, forKey: .headword)
        try container.encode(wordID, forKey: .wordID)
        try container.encode(display, forKey: .display)
        try container.encode(meaning, forKey: .meaning)
        try container.encode(phonetic, forKey: .phonetic)
        try container.encode(pos, forKey: .pos)
        try container.encode(example, forKey: .example)
        try container.encode(exampleTranslation, forKey: .exampleTranslation)
        try container.encode(updatedAt, forKey: .updatedAt)
        try container.encode(deletedAt, forKey: .deletedAt)
    }
}

/// A row of `review_logs`. `receivedAt` is set by the server and never uploaded.
struct RemoteReview: Codable, Hashable, Sendable {
    var id: UUID
    var headword: String
    var rating: Int
    var mode: String
    var reviewedAt: Date
    var studyDay: Int
    var durationMs: Int
    var receivedAt: Date?

    enum CodingKeys: String, CodingKey {
        case id, headword, rating, mode
        case reviewedAt = "reviewed_at", studyDay = "study_day", durationMs = "duration_ms", receivedAt = "received_at"
    }

    init(_ event: ReviewEvent) {
        id = event.id
        headword = event.headword
        rating = event.rating.rawValue
        mode = event.mode.rawValue
        reviewedAt = event.reviewedAt
        studyDay = event.studyDay
        durationMs = event.durationMs
    }

    var event: ReviewEvent? {
        guard let rating = Rating(rawValue: rating), let mode = ReviewMode(rawValue: mode) else { return nil }
        return ReviewEvent(id: id, headword: headword, rating: rating, mode: mode, reviewedAt: reviewedAt,
                           studyDay: studyDay, durationMs: durationMs)
    }

    func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(headword, forKey: .headword)
        try container.encode(rating, forKey: .rating)
        try container.encode(mode, forKey: .mode)
        try container.encode(reviewedAt, forKey: .reviewedAt)
        try container.encode(studyDay, forKey: .studyDay)
        try container.encode(durationMs, forKey: .durationMs)
    }
}

struct RemoteFavorite: Codable, Hashable, Sendable {
    var headword: String
    var isFavorite: Bool
    var updatedAt: Date

    enum CodingKeys: String, CodingKey {
        case headword
        case isFavorite = "is_favorite", updatedAt = "updated_at"
    }
}

struct RemoteSettings: Codable, Hashable, Sendable {
    var bookID: UUID?
    var dailyNewWords: Int
    var reviewMode: String
    /// `yyyy-MM-dd`.
    var examDate: String?
    var dayStartMinutes: Int
    var desiredRetention: Double
    var updatedAt: Date

    enum CodingKeys: String, CodingKey {
        case bookID = "book_id", dailyNewWords = "daily_new_words", reviewMode = "review_mode", examDate = "exam_date"
        case dayStartMinutes = "day_start_minutes", desiredRetention = "desired_retention", updatedAt = "updated_at"
    }

    func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(bookID, forKey: .bookID)
        try container.encode(dailyNewWords, forKey: .dailyNewWords)
        try container.encode(reviewMode, forKey: .reviewMode)
        try container.encode(examDate, forKey: .examDate)
        try container.encode(dayStartMinutes, forKey: .dayStartMinutes)
        try container.encode(desiredRetention, forKey: .desiredRetention)
        try container.encode(updatedAt, forKey: .updatedAt)
    }
}
