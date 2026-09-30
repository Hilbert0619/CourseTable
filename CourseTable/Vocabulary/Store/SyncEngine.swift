import Foundation
import SwiftData
import VocabKit

/// Keeps the vocabulary store and the server in step (docs/vocabulary-spec.md §8). Everything works
/// offline; a sync uploads what changed here and downloads what changed elsewhere:
/// own books → book list → the current book's words → answers up → answers down → favourites → settings.
@MainActor
@Observable
final class SyncEngine {
    enum Phase: Equatable {
        case idle
        case syncing
        case failed(String)
    }

    private(set) var phase: Phase = .idle
    private(set) var lastSync: Date?
    /// Words downloaded so far while fetching a book, and the book's size.
    private(set) var download: (done: Int, total: Int)?

    let backend: any VocabBackend
    let model: VocabModel

    static let pageSize = 1000

    init(backend: any VocabBackend, model: VocabModel) {
        self.backend = backend
        self.model = model
        lastSync = VocabStore.syncState(in: model.context).lastSyncAt
    }

    private var context: ModelContext { model.context }

    /// Syncs for `account`, first clearing the store if it holds another account's data.
    func sync(for account: Account) async {
        guard phase != .syncing else { return }
        phase = .syncing
        do {
            let state = VocabStore.syncState(in: context)
            if let owner = state.userID, owner != account.id {
                model.wipe()
            }
            VocabStore.syncState(in: context).userID = account.id
            try context.save()

            try await pushBooks()
            try await pullBooks()
            try await downloadCurrentBook()
            try await pushReviews()
            try await pullReviews()
            try await syncFavorites()
            try await syncSettings()

            let now = Date.now
            VocabStore.syncState(in: context).lastSyncAt = now
            try context.save()
            lastSync = now
            phase = .idle
        } catch {
            download = nil
            phase = .failed(Self.message(for: error))
        }
        model.load()
    }

    static func message(for error: any Error) -> String {
        if let urlError = error as? URLError,
           [.notConnectedToInternet, .networkConnectionLost, .timedOut, .cannotFindHost, .cannotConnectToHost].contains(urlError.code) {
            return String(localized: "Offline. Your answers are saved on this iPhone and will upload later.")
        }
        return error.localizedDescription
    }

    // MARK: - Books

    /// The learner's own books and their words; books first, so every entry's book exists on the server.
    private func pushBooks() async throws {
        let books = try context.fetch(FetchDescriptor<BookRecord>(predicate: #Predicate { $0.needsUpload }))
        try await backend.saveBooks(books.map(\.remote))
        for book in books { book.needsUpload = false }
        try context.save()
        let entries = try context.fetch(FetchDescriptor<BookEntryRecord>(predicate: #Predicate { $0.needsUpload }))
        for batch in SyncMerge.batches(entries) {
            try await backend.saveEntries(batch.map(\.remote))
            for entry in batch { entry.needsUpload = false }
            try context.save()
        }
    }

    private func pullBooks() async throws {
        let remote = try await backend.books()
        let local = Dictionary(try context.fetch(FetchDescriptor<BookRecord>()).map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        for book in remote {
            if let existing = local[book.id] {
                if !existing.needsUpload && book.updatedAt >= existing.updatedAt { existing.update(from: book) }
            } else {
                context.insert(BookRecord(book))
            }
        }
        try context.save()
        model.load()
    }

    private func downloadCurrentBook() async throws {
        guard let book = model.currentBook, book.downloadedAt == nil, book.deletedAt == nil else { return }
        let bookID = book.id
        download = (0, book.wordCount)
        var fetched: [RemoteEntry] = []
        while true {
            let page = try await backend.entries(book: bookID, from: fetched.count, to: fetched.count + Self.pageSize - 1)
            fetched += page
            download = (fetched.count, max(book.wordCount, fetched.count))
            if page.count < Self.pageSize { break }
        }

        let words = Dictionary(try context.fetch(FetchDescriptor<WordRecord>()).map { ($0.headword, $0) }, uniquingKeysWith: { a, _ in a })
        let entries = Dictionary(try context.fetch(FetchDescriptor<BookEntryRecord>(predicate: #Predicate { $0.bookID == bookID }))
            .map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        for remote in fetched {
            if let word = remote.word {
                let entry = word.entry
                if let existing = words[entry.headword] {
                    existing.update(from: entry, serverID: word.id)
                } else {
                    context.insert(WordRecord(entry, serverID: word.id))
                }
            }
            if let existing = entries[remote.id] {
                if !existing.needsUpload { existing.update(from: remote) }
            } else {
                context.insert(BookEntryRecord(remote))
            }
        }
        book.downloadedAt = .now
        try context.save()
        download = nil
        model.load()
    }

    // MARK: - Answers

    private func pushReviews() async throws {
        let unsent = try context.fetch(FetchDescriptor<ReviewLogRecord>(predicate: #Predicate { !$0.pushed }))
        for batch in SyncMerge.batches(unsent.compactMap(\.event)) {
            try await backend.uploadReviews(batch.map(RemoteReview.init))
            model.markSent(Set(batch.map(\.id)))
        }
    }

    private func pullReviews() async throws {
        let state = VocabStore.syncState(in: context)
        let start = SyncMerge.pullStart(cursor: state.pullCursor)
        var offset = 0
        var received: [Date] = []
        while true {
            let page = try await backend.reviews(receivedAfter: start, from: offset, to: offset + Self.pageSize - 1)
            model.merge(downloaded: page)
            received += page.compactMap(\.receivedAt)
            offset += page.count
            if page.count < Self.pageSize { break }
        }
        state.pullCursor = SyncMerge.advance(state.pullCursor, receivedAt: received)
        try context.save()
    }

    // MARK: - Favourites and settings (newest edit wins)

    private func syncFavorites() async throws {
        let remote = Dictionary(try await backend.favorites().map { ($0.headword, $0) }, uniquingKeysWith: { a, _ in a })
        let local = Dictionary(try context.fetch(FetchDescriptor<FavoriteRecord>()).map { ($0.headword, $0) }, uniquingKeysWith: { a, _ in a })
        var upload: [RemoteFavorite] = []
        for word in Set(remote.keys).union(local.keys) {
            let mine = local[word].map { Versioned($0.isFavorite, updatedAt: $0.updatedAt) }
            let theirs = remote[word].map { Versioned($0.isFavorite, updatedAt: $0.updatedAt) }
            switch SyncMerge.resolve(local: mine, remote: theirs) {
            case .pushLocal:
                if let record = local[word] { upload.append(record.remote) }
            case .takeRemote:
                if let row = remote[word] {
                    if let record = local[word] {
                        record.isFavorite = row.isFavorite
                        record.updatedAt = row.updatedAt
                    } else {
                        context.insert(FavoriteRecord(headword: word, isFavorite: row.isFavorite, updatedAt: row.updatedAt, needsUpload: false))
                    }
                }
            case .unchanged:
                break
            }
        }
        try await backend.saveFavorites(upload)
        for record in local.values { record.needsUpload = false }
        try context.save()
    }

    private func syncSettings() async throws {
        let record = VocabStore.settingsRecord(in: context)
        let remote = try await backend.settings()
        let mine = record.updatedAt == .distantPast ? nil : Versioned(record.remote, updatedAt: record.updatedAt)
        switch SyncMerge.resolve(local: mine, remote: remote.map { Versioned($0, updatedAt: $0.updatedAt) }) {
        case .pushLocal:
            try await backend.saveSettings(record.remote)
            record.needsUpload = false
        case .takeRemote:
            if let remote { record.update(from: remote) }
        case .unchanged:
            record.needsUpload = false
        }
        try context.save()
    }
}
