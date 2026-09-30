import Foundation
import SwiftData
import CourseKit
import VocabKit

/// The Words tab's state, loaded from the vocabulary store: dictionary words, books, answers and the
/// cards replayed from them, favourites and settings. Views read it; answers and edits go through it.
/// Scheduling, plans and statistics come from VocabKit.
@MainActor
@Observable
final class VocabModel {
    let context: ModelContext

    /// Dictionary entries by word key.
    private(set) var words: [String: WordEntry] = [:]
    /// Books that aren't deleted: system books first, then the learner's own, by name.
    private(set) var books: [BookRecord] = []
    private(set) var settings = VocabSettings.defaults
    private(set) var events: [ReviewEvent] = []
    private(set) var cards: [String: Card] = [:]
    private(set) var favorites: Set<String> = []
    /// The current book's words in study order.
    private(set) var bookWords: [String] = []
    /// The current book's entries: dictionary entries with the learner's own meanings on top.
    private(set) var bookEntries: [String: WordEntry] = [:]
    /// Answers recorded on this device and not uploaded yet.
    private(set) var unsentCount = 0

    @ObservationIgnored private var cachedSearch: WordSearch?
    @ObservationIgnored private var cachedPool: ChoicePool?

    init(context: ModelContext) {
        self.context = context
    }

    // MARK: - Loading

    func load() {
        words = Dictionary((try? context.fetch(FetchDescriptor<WordRecord>()))?.map { ($0.headword, $0.entry) } ?? [],
                           uniquingKeysWith: { first, _ in first })
        let allBooks = (try? context.fetch(FetchDescriptor<BookRecord>())) ?? []
        books = allBooks.filter { $0.deletedAt == nil }.sorted { a, b in
            a.isSystem != b.isSystem ? a.isSystem : a.name.localizedStandardCompare(b.name) == .orderedAscending
        }
        settings = VocabStore.settingsRecord(in: context).settings
        let records = (try? context.fetch(FetchDescriptor<ReviewLogRecord>())) ?? []
        events = records.compactMap(\.event)
        unsentCount = records.filter { !$0.pushed }.count
        cards = scheduler.replay(events)
        favorites = Set(((try? context.fetch(FetchDescriptor<FavoriteRecord>())) ?? []).filter(\.isFavorite).map(\.headword))
        loadCurrentBook()
    }

    private func loadCurrentBook() {
        cachedSearch = nil
        cachedPool = nil
        guard let book = currentBook else {
            bookWords = []
            bookEntries = [:]
            return
        }
        let bookID = book.id
        let descriptor = FetchDescriptor<BookEntryRecord>(predicate: #Predicate { $0.bookID == bookID },
                                                          sortBy: [SortDescriptor(\.position)])
        let entries = ((try? context.fetch(descriptor)) ?? []).filter { $0.deletedAt == nil }
        bookWords = entries.map(\.headword)
        bookEntries = Dictionary(entries.map { ($0.headword, $0.entry(dictionary: words[$0.headword])) },
                                 uniquingKeysWith: { first, _ in first })
    }

    // MARK: - Derived

    /// The chosen book, or the first system book.
    var currentBook: BookRecord? {
        books.first { $0.id == settings.bookID } ?? books.first(where: \.isSystem) ?? books.first
    }

    var clock: StudyClock { StudyClock(dayStart: settings.dayStart) }

    var scheduler: Scheduler { Scheduler(parameters: FSRSParameters(desiredRetention: settings.desiredRetention)) }

    var today: Int { clock.day(of: .now) }

    func entry(_ headword: String) -> WordEntry? {
        let key = Headword.key(headword)
        return bookEntries[key] ?? words[key]
    }

    func plan(at now: Date = .now) -> DailyPlan {
        DailyPlan(cards: cards, history: events, book: bookWords, newWordsPerDay: settings.dailyNewWords, now: now, clock: clock)
    }

    func examCountdown(at now: Date = .now) -> ExamCountdown? {
        guard let examDate = settings.examDate else { return nil }
        let unseen = bookWords.filter { (cards[$0]?.state ?? .new) == .new }.count
        return ExamCountdown(examDate: examDate, today: clock.day(of: now), wordsLeft: unseen)
    }

    /// Searches every word on the device: the current book and all downloaded dictionary words.
    var search: WordSearch {
        if let cachedSearch { return cachedSearch }
        var all = words
        for (key, entry) in bookEntries { all[key] = entry }
        let built = WordSearch(entries: Array(all.values))
        cachedSearch = built
        return built
    }

    /// Wrong options for multiple choice come from the current book (or every word if it's tiny).
    var choicePool: ChoicePool {
        if let cachedPool { return cachedPool }
        let pool = bookEntries.count >= 8 ? Array(bookEntries.values) : Array(words.values) + Array(bookEntries.values)
        let built = ChoicePool(pool)
        cachedPool = built
        return built
    }

    var hardWords: [Card] { VocabStats.hardWords(cards.values) }

    // MARK: - Changes

    /// Stores an answer given in a study session and the card it produced.
    func record(_ event: ReviewEvent, card: Card) {
        context.insert(ReviewLogRecord(event))
        try? context.save()
        events.append(event)
        cards[event.headword] = card
        unsentCount += 1
    }

    /// Adds answers downloaded from the server and rebuilds the cards of the words they touch.
    func merge(downloaded: [RemoteReview]) {
        let known = Set(events.map(\.id))
        var added: [ReviewEvent] = []
        for row in downloaded {
            guard !known.contains(row.id), let event = row.event else { continue }
            context.insert(ReviewLogRecord(event, pushed: true, receivedAt: row.receivedAt))
            added.append(event)
        }
        guard !added.isEmpty else { return }
        try? context.save()
        events += added
        let rebuilt = SyncMerge.rebuiltCards(allEvents: events, newEvents: added, scheduler: scheduler)
        for (word, card) in rebuilt { cards[word] = card }
    }

    func markSent(_ ids: Set<UUID>) {
        let records = (try? context.fetch(FetchDescriptor<ReviewLogRecord>(predicate: #Predicate { !$0.pushed }))) ?? []
        for record in records where ids.contains(record.id) { record.pushed = true }
        try? context.save()
        unsentCount = records.filter { !$0.pushed }.count
    }

    func isFavorite(_ headword: String) -> Bool { favorites.contains(Headword.key(headword)) }

    func setFavorite(_ headword: String, _ on: Bool) {
        let key = Headword.key(headword)
        let descriptor = FetchDescriptor<FavoriteRecord>(predicate: #Predicate { $0.headword == key })
        if let record = try? context.fetch(descriptor).first {
            record.isFavorite = on
            record.updatedAt = .now
            record.needsUpload = true
        } else {
            context.insert(FavoriteRecord(headword: key, isFavorite: on, updatedAt: .now, needsUpload: true))
        }
        try? context.save()
        if on { favorites.insert(key) } else { favorites.remove(key) }
    }

    func updateSettings(_ change: (inout VocabSettings) -> Void) {
        let record = VocabStore.settingsRecord(in: context)
        var value = record.settings
        change(&value)
        guard value != record.settings else { return }
        let bookChanged = value.bookID != record.settings.bookID
        let schedulingChanged = value.desiredRetention != record.settings.desiredRetention
        record.settings = value
        record.updatedAt = .now
        record.needsUpload = true
        try? context.save()
        settings = value
        if schedulingChanged { cards = scheduler.replay(events) }
        if bookChanged { loadCurrentBook() }
    }

    // MARK: - Own books

    /// Creates a book from an imported list; words the dictionary knows link to it.
    @discardableResult
    func importBook(named name: String, words imported: [ImportedWord], owner: UUID) -> BookRecord {
        let book = BookRecord(ownBookNamed: name, owner: owner, wordCount: imported.count)
        context.insert(book)
        let serverIDs = Dictionary(((try? context.fetch(FetchDescriptor<WordRecord>())) ?? []).map { ($0.headword, $0.serverID) },
                                   uniquingKeysWith: { first, _ in first })
        for (index, word) in imported.enumerated() {
            context.insert(BookEntryRecord(word, book: book.id, position: index + 1, wordID: serverIDs[word.headword]))
        }
        try? context.save()
        load()
        return book
    }

    func renameBook(_ book: BookRecord, to name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !book.isSystem, !trimmed.isEmpty, trimmed != book.name else { return }
        book.name = String(trimmed.prefix(100))
        book.updatedAt = .now
        book.needsUpload = true
        try? context.save()
        load()
    }

    func deleteBook(_ book: BookRecord) {
        guard !book.isSystem else { return }
        book.deletedAt = .now
        book.updatedAt = .now
        book.needsUpload = true
        if settings.bookID == book.id {
            let record = VocabStore.settingsRecord(in: context)
            record.bookID = nil
            record.updatedAt = .now
            record.needsUpload = true
        }
        try? context.save()
        load()
    }

    /// Everything on this device, for signing out.
    func wipe() {
        try? VocabStore.wipe(context)
        load()
    }
}
