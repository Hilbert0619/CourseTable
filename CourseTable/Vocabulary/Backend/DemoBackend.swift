import Foundation
import VocabKit

/// An in-memory backend for UI tests, app tests and previews: a small 考研 book, optionally a signed-in
/// learner with two weeks of answers. Nothing leaves the device.
actor DemoBackend: VocabBackend {
    static let demoEmail = "learner@example.com"

    private(set) var account: Account?
    private var confirmedEmails: Set<String> = [demoEmail]
    private var bookRows: [RemoteBook]
    private var entryRows: [RemoteEntry]
    private var reviewRows: [RemoteReview] = []
    private var favoriteRows: [RemoteFavorite] = []
    private var settingsRow: RemoteSettings?
    private var clock = Date.now

    /// - Parameters:
    ///   - signedIn: start with the demo learner signed in.
    ///   - history: give the learner two weeks of answers ending yesterday, a few favourites and settings.
    init(signedIn: Bool, history: Bool, reviewMode: SessionMode = .mixed, now: Date = .now) {
        let book = RemoteBook(id: SystemBook.kaoyan.id, ownerID: nil, name: SystemBook.kaoyan.name,
                              description: SystemBook.kaoyan.description, source: SystemBook.kaoyan.source,
                              licence: SystemBook.kaoyan.licence, wordCount: VocabDemo.words.count,
                              updatedAt: now.addingTimeInterval(-30 * 86_400), deletedAt: nil)
        bookRows = [book]
        entryRows = VocabDemo.words.enumerated().map { index, entry in
            RemoteEntry(id: UUID(), bookID: book.id, position: index + 1, headword: entry.headword, wordID: index + 1,
                        updatedAt: book.updatedAt, word: RemoteWord(id: index + 1, entry: entry))
        }
        if signedIn {
            account = Account(id: UUID(uuidString: "0D3E0000-0000-4000-8000-000000000001")!, email: Self.demoEmail)
        }
        if history {
            reviewRows = VocabDemo.history(endingBefore: now).map { event in
                var row = RemoteReview(event)
                row.receivedAt = event.reviewedAt
                return row
            }
            favoriteRows = ["ambiguous", "anticipate", "arbitrary"].map {
                RemoteFavorite(headword: $0, isFavorite: true, updatedAt: now.addingTimeInterval(-86_400))
            }
            settingsRow = RemoteSettings(bookID: book.id, dailyNewWords: 5, reviewMode: reviewMode.rawValue,
                                         examDate: VocabDemo.examDate(from: now), dayStartMinutes: 240,
                                         desiredRetention: 0.9, updatedAt: now.addingTimeInterval(-86_400))
        }
        clock = now
    }

    // MARK: - Auth

    func restoreAccount() async -> Account? { account }

    func signUp(email: String, password: String) async throws -> SignUpOutcome {
        guard email.contains("@") else { throw VocabAuthError.invalidEmail }
        guard password.count >= 8 else { throw VocabAuthError.weakPassword }
        return .confirmationSent
    }

    func signIn(email: String, password: String) async throws -> Account {
        guard confirmedEmails.contains(email.lowercased()) else { throw VocabAuthError.emailNotConfirmed }
        guard password.count >= 8 else { throw VocabAuthError.invalidCredentials }
        let signedIn = Account(id: UUID(uuidString: "0D3E0000-0000-4000-8000-000000000001")!, email: email.lowercased())
        account = signedIn
        return signedIn
    }

    func signOut() async { account = nil }

    func sendPasswordReset(email: String) async throws {
        guard email.contains("@") else { throw VocabAuthError.invalidEmail }
    }

    func openLink(_ url: URL) async throws -> Account {
        let link = AuthLink(url)
        if case .failed = link { throw VocabAuthError.linkExpired }
        guard link.signsIn else { throw VocabAuthError.other("Not a sign-in link") }
        let signedIn = Account(id: UUID(uuidString: "0D3E0000-0000-4000-8000-000000000001")!, email: Self.demoEmail)
        account = signedIn
        return signedIn
    }

    func updatePassword(_ password: String) async throws {
        guard account != nil else { throw VocabAuthError.other("Not signed in") }
        guard password.count >= 8 else { throw VocabAuthError.weakPassword }
    }

    func deleteAccount() async throws {
        try requireAccount()
        account = nil
        reviewRows = []
        favoriteRows = []
        settingsRow = nil
        bookRows.removeAll { $0.ownerID != nil }
        entryRows.removeAll { entry in !bookRows.contains { $0.id == entry.bookID } }
    }

    // MARK: - Data

    func books() async throws -> [RemoteBook] {
        let user = try requireAccount()
        return bookRows.filter { $0.ownerID == nil || $0.ownerID == user.id }
    }

    func entries(book: UUID, from: Int, to: Int) async throws -> [RemoteEntry] {
        try requireAccount()
        let rows = entryRows.filter { $0.bookID == book }.sorted { $0.position < $1.position }
        guard from < rows.count else { return [] }
        return Array(rows[from...min(to, rows.count - 1)])
    }

    func saveBooks(_ books: [RemoteBook]) async throws {
        let user = try requireAccount()
        for book in books {
            guard book.ownerID == user.id else { throw VocabAuthError.other("Not your book") }
            if let index = bookRows.firstIndex(where: { $0.id == book.id }) {
                if book.updatedAt >= bookRows[index].updatedAt { bookRows[index] = book }
            } else {
                bookRows.append(book)
            }
        }
    }

    func saveEntries(_ entries: [RemoteEntry]) async throws {
        let user = try requireAccount()
        for entry in entries {
            guard bookRows.contains(where: { $0.id == entry.bookID && $0.ownerID == user.id }) else {
                throw VocabAuthError.other("Not your book")
            }
            var stored = entry
            stored.word = nil
            if let index = entryRows.firstIndex(where: { $0.id == entry.id }) {
                if entry.updatedAt >= entryRows[index].updatedAt { entryRows[index] = stored }
            } else {
                entryRows.append(stored)
            }
        }
    }

    func uploadReviews(_ reviews: [RemoteReview]) async throws {
        try requireAccount()
        let known = Set(reviewRows.map(\.id))
        for review in reviews where !known.contains(review.id) {
            var row = review
            clock = max(clock.addingTimeInterval(0.001), Date.now)
            row.receivedAt = clock
            reviewRows.append(row)
        }
    }

    func reviews(receivedAfter: Date?, from: Int, to: Int) async throws -> [RemoteReview] {
        try requireAccount()
        let rows = reviewRows
            .filter { row in receivedAfter.map { (row.receivedAt ?? .distantPast) > $0 } ?? true }
            .sorted { ($0.receivedAt ?? .distantPast) < ($1.receivedAt ?? .distantPast) }
        guard from < rows.count else { return [] }
        return Array(rows[from...min(to, rows.count - 1)])
    }

    func favorites() async throws -> [RemoteFavorite] {
        try requireAccount()
        return favoriteRows
    }

    func saveFavorites(_ favorites: [RemoteFavorite]) async throws {
        try requireAccount()
        for favorite in favorites {
            if let index = favoriteRows.firstIndex(where: { $0.headword == favorite.headword }) {
                if favorite.updatedAt >= favoriteRows[index].updatedAt { favoriteRows[index] = favorite }
            } else {
                favoriteRows.append(favorite)
            }
        }
    }

    func settings() async throws -> RemoteSettings? {
        try requireAccount()
        return settingsRow
    }

    func saveSettings(_ settings: RemoteSettings) async throws {
        try requireAccount()
        if let settingsRow, settings.updatedAt < settingsRow.updatedAt { return }
        settingsRow = settings
    }

    // MARK: - For tests

    /// Answers stored on the "server".
    func storedReviews() -> [RemoteReview] { reviewRows }
    func storedFavorites() -> [RemoteFavorite] { favoriteRows }
    func storedSettings() -> RemoteSettings? { settingsRow }
    func storedBooks() -> [RemoteBook] { bookRows }
    func storedEntries() -> [RemoteEntry] { entryRows }

    /// An answer uploaded by another device.
    func addReviewFromAnotherDevice(_ event: ReviewEvent) {
        var row = RemoteReview(event)
        clock = max(clock.addingTimeInterval(0.001), Date.now)
        row.receivedAt = clock
        reviewRows.append(row)
    }

    @discardableResult
    private func requireAccount() throws -> Account {
        guard let account else { throw VocabAuthError.other("Not signed in") }
        return account
    }
}
