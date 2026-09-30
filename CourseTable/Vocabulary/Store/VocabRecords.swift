import Foundation
import SwiftData
import CourseKit
import VocabKit

/// The vocabulary store (docs/vocabulary-spec.md §5.3): its own SwiftData file, `Vocabulary.store`, so
/// the timetable's store is never migrated for it. Versioned from the start; a later schema adds a
/// `VocabSchemaV2` and a migration stage.
enum VocabSchemaV1: VersionedSchema {
    static var versionIdentifier: Schema.Version { Schema.Version(1, 0, 0) }

    static var models: [any PersistentModel.Type] {
        [WordRecord.self, BookRecord.self, BookEntryRecord.self, ReviewLogRecord.self,
         FavoriteRecord.self, VocabSettingsRecord.self, SyncStateRecord.self]
    }

    /// A dictionary entry downloaded with a book.
    @Model
    final class WordRecord {
        @Attribute(.unique) var headword: String
        var serverID: Int
        var display: String
        var phonetic: String
        var translation: String
        var definition: String
        var posMix: String
        var collins: Int
        var oxford: Bool
        /// Space-separated, e.g. `cet4 cet6 ky`.
        var tagsText: String
        var bnc: Int?
        var frq: Int?
        var exchange: String

        init(_ entry: WordEntry, serverID: Int) {
            headword = entry.headword
            self.serverID = serverID
            display = entry.display
            phonetic = entry.phonetic
            translation = entry.translation
            definition = entry.definition
            posMix = entry.posMix
            collins = entry.collins
            oxford = entry.oxford
            tagsText = entry.tags.joined(separator: " ")
            bnc = entry.bnc
            frq = entry.frq
            exchange = entry.exchange
        }

        func update(from entry: WordEntry, serverID: Int) {
            self.serverID = serverID
            display = entry.display
            phonetic = entry.phonetic
            translation = entry.translation
            definition = entry.definition
            posMix = entry.posMix
            collins = entry.collins
            oxford = entry.oxford
            tagsText = entry.tags.joined(separator: " ")
            bnc = entry.bnc
            frq = entry.frq
            exchange = entry.exchange
        }

        var entry: WordEntry {
            WordEntry(headword: headword, display: display, phonetic: phonetic, translation: translation,
                      definition: definition, posMix: posMix, collins: collins, oxford: oxford,
                      tags: tagsText.split(separator: " ").map(String.init), bnc: bnc, frq: frq, exchange: exchange)
        }
    }

    /// A system book or one of the learner's own.
    @Model
    final class BookRecord {
        @Attribute(.unique) var id: UUID
        /// `nil` for system books.
        var ownerID: UUID?
        var name: String
        var detail: String
        var source: String
        var licence: String
        var wordCount: Int
        var updatedAt: Date
        var deletedAt: Date?
        /// When the entries were last downloaded; `nil` until they are on the device.
        var downloadedAt: Date?
        /// Changed on this device and not yet uploaded.
        var needsUpload: Bool

        init(_ remote: RemoteBook) {
            id = remote.id
            ownerID = remote.ownerID
            name = remote.name
            detail = remote.description
            source = remote.source
            licence = remote.licence
            wordCount = remote.wordCount
            updatedAt = remote.updatedAt
            deletedAt = remote.deletedAt
            downloadedAt = nil
            needsUpload = false
        }

        init(ownBookNamed name: String, owner: UUID, wordCount: Int, now: Date = .now) {
            id = UUID()
            ownerID = owner
            self.name = name
            detail = ""
            source = ""
            licence = ""
            self.wordCount = wordCount
            updatedAt = now
            deletedAt = nil
            downloadedAt = now
            needsUpload = true
        }

        func update(from remote: RemoteBook) {
            name = remote.name
            detail = remote.description
            source = remote.source
            licence = remote.licence
            wordCount = remote.wordCount
            deletedAt = remote.deletedAt
            if remote.updatedAt > updatedAt { downloadedAt = nil }
            updatedAt = remote.updatedAt
        }

        var isSystem: Bool { ownerID == nil }

        var remote: RemoteBook {
            RemoteBook(id: id, ownerID: ownerID, name: name, description: detail, source: source, licence: licence,
                       wordCount: wordCount, updatedAt: updatedAt, deletedAt: deletedAt)
        }
    }

    /// A word in a book, in study order, with the meaning the learner gave it if any.
    @Model
    final class BookEntryRecord {
        @Attribute(.unique) var id: UUID
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
        var needsUpload: Bool

        init(_ remote: RemoteEntry) {
            id = remote.id
            bookID = remote.bookID
            position = remote.position
            headword = Headword.key(remote.headword)
            wordID = remote.wordID
            display = remote.display
            meaning = remote.meaning
            phonetic = remote.phonetic
            pos = remote.pos
            example = remote.example
            exampleTranslation = remote.exampleTranslation
            updatedAt = remote.updatedAt
            deletedAt = remote.deletedAt
            needsUpload = false
        }

        init(_ word: ImportedWord, book: UUID, position: Int, wordID: Int?, now: Date = .now) {
            id = UUID()
            bookID = book
            self.position = position
            headword = word.headword
            self.wordID = wordID
            display = word.display == word.headword ? nil : word.display
            meaning = word.meaning
            phonetic = word.phonetic
            pos = word.partOfSpeech
            example = word.example
            exampleTranslation = word.exampleTranslation
            updatedAt = now
            deletedAt = nil
            needsUpload = true
        }

        func update(from remote: RemoteEntry) {
            position = remote.position
            wordID = remote.wordID
            display = remote.display
            meaning = remote.meaning
            phonetic = remote.phonetic
            pos = remote.pos
            example = remote.example
            exampleTranslation = remote.exampleTranslation
            updatedAt = remote.updatedAt
            deletedAt = remote.deletedAt
        }

        /// The dictionary's entry with anything the learner gave taking its place.
        func entry(dictionary: WordEntry?) -> WordEntry {
            var entry = dictionary ?? WordEntry(headword: headword, display: display ?? headword, translation: "")
            if let display { entry.display = display }
            if let meaning, !meaning.isEmpty { entry.translation = meaning }
            if let phonetic, !phonetic.isEmpty { entry.phonetic = phonetic.trimmingCharacters(in: CharacterSet(charactersIn: "/[] ")) }
            return entry
        }

        var remote: RemoteEntry {
            RemoteEntry(id: id, bookID: bookID, position: position, headword: headword, wordID: wordID, display: display,
                        meaning: meaning, phonetic: phonetic, pos: pos, example: example,
                        exampleTranslation: exampleTranslation, updatedAt: updatedAt, deletedAt: deletedAt)
        }
    }

    /// One answer, as recorded on this device or downloaded from another.
    @Model
    final class ReviewLogRecord {
        @Attribute(.unique) var id: UUID
        var headword: String
        var rating: Int
        var mode: String
        var reviewedAt: Date
        var studyDay: Int
        var durationMs: Int
        /// Uploaded (or downloaded, so already on the server).
        var pushed: Bool
        var receivedAt: Date?

        init(_ event: ReviewEvent, pushed: Bool = false, receivedAt: Date? = nil) {
            id = event.id
            headword = event.headword
            rating = event.rating.rawValue
            mode = event.mode.rawValue
            reviewedAt = event.reviewedAt
            studyDay = event.studyDay
            durationMs = event.durationMs
            self.pushed = pushed
            self.receivedAt = receivedAt
        }

        var event: ReviewEvent? {
            guard let rating = Rating(rawValue: rating), let mode = ReviewMode(rawValue: mode) else { return nil }
            return ReviewEvent(id: id, headword: headword, rating: rating, mode: mode, reviewedAt: reviewedAt,
                               studyDay: studyDay, durationMs: durationMs)
        }
    }

    @Model
    final class FavoriteRecord {
        @Attribute(.unique) var headword: String
        var isFavorite: Bool
        var updatedAt: Date
        var needsUpload: Bool

        init(headword: String, isFavorite: Bool, updatedAt: Date, needsUpload: Bool) {
            self.headword = headword
            self.isFavorite = isFavorite
            self.updatedAt = updatedAt
            self.needsUpload = needsUpload
        }

        var remote: RemoteFavorite { RemoteFavorite(headword: headword, isFavorite: isFavorite, updatedAt: updatedAt) }
    }

    /// The single settings row.
    @Model
    final class VocabSettingsRecord {
        var bookID: UUID?
        var dailyNewWords: Int
        var reviewMode: String
        /// `yyyy-MM-dd`.
        var examDate: String?
        var dayStartMinutes: Int
        var desiredRetention: Double
        var updatedAt: Date
        var needsUpload: Bool

        init() {
            bookID = nil
            examDate = nil
            dailyNewWords = VocabSettings.defaults.dailyNewWords
            reviewMode = VocabSettings.defaults.reviewMode.rawValue
            dayStartMinutes = VocabSettings.defaults.dayStart.minutes
            desiredRetention = VocabSettings.defaults.desiredRetention
            updatedAt = .distantPast
            needsUpload = false
        }

        var settings: VocabSettings {
            get {
                VocabSettings(bookID: bookID, dailyNewWords: dailyNewWords,
                              reviewMode: SessionMode(rawValue: reviewMode) ?? .recognition,
                              examDate: examDate.flatMap { LocalDate($0) }, dayStart: TimeOfDay(minutes: dayStartMinutes),
                              desiredRetention: desiredRetention)
            }
            set {
                bookID = newValue.bookID
                dailyNewWords = newValue.dailyNewWords
                reviewMode = newValue.reviewMode.rawValue
                examDate = newValue.examDate?.description
                dayStartMinutes = newValue.dayStart.minutes
                desiredRetention = newValue.desiredRetention
            }
        }

        var remote: RemoteSettings {
            RemoteSettings(bookID: bookID, dailyNewWords: dailyNewWords, reviewMode: reviewMode, examDate: examDate,
                           dayStartMinutes: dayStartMinutes, desiredRetention: desiredRetention, updatedAt: updatedAt)
        }

        func update(from remote: RemoteSettings) {
            bookID = remote.bookID
            dailyNewWords = remote.dailyNewWords
            reviewMode = remote.reviewMode
            examDate = remote.examDate
            dayStartMinutes = remote.dayStartMinutes
            desiredRetention = remote.desiredRetention
            updatedAt = remote.updatedAt
            needsUpload = false
        }
    }

    /// The single sync bookkeeping row.
    @Model
    final class SyncStateRecord {
        /// The account whose data this store holds.
        var userID: UUID?
        /// Newest `received_at` pulled so far.
        var pullCursor: Date?
        var lastSyncAt: Date?

        init() {
            userID = nil
            pullCursor = nil
            lastSyncAt = nil
        }
    }
}

typealias WordRecord = VocabSchemaV1.WordRecord
typealias BookRecord = VocabSchemaV1.BookRecord
typealias BookEntryRecord = VocabSchemaV1.BookEntryRecord
typealias ReviewLogRecord = VocabSchemaV1.ReviewLogRecord
typealias FavoriteRecord = VocabSchemaV1.FavoriteRecord
typealias VocabSettingsRecord = VocabSchemaV1.VocabSettingsRecord
typealias SyncStateRecord = VocabSchemaV1.SyncStateRecord

enum VocabMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [VocabSchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}

/// The learner's study settings as a value.
struct VocabSettings: Hashable, Sendable {
    var bookID: UUID?
    var dailyNewWords: Int
    var reviewMode: SessionMode
    var examDate: LocalDate?
    var dayStart: TimeOfDay
    /// FSRS target retention, `0.80...0.95` in the settings screen.
    var desiredRetention: Double

    static let defaults = VocabSettings(bookID: nil, dailyNewWords: 20, reviewMode: .recognition, examDate: nil,
                                        dayStart: StudyClock.defaultDayStart, desiredRetention: 0.9)
}

enum VocabStore {
    static var schema: Schema { Schema(versionedSchema: VocabSchemaV1.self) }

    /// The store in Application Support (`Vocabulary.store`), or in memory for tests and UI tests.
    static func makeContainer(inMemory: Bool = false) throws -> ModelContainer {
        let configuration: ModelConfiguration
        if inMemory {
            configuration = ModelConfiguration("Vocabulary", schema: schema, isStoredInMemoryOnly: true)
        } else {
            let folder = URL.applicationSupportDirectory
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            configuration = ModelConfiguration("Vocabulary", schema: schema, url: folder.appending(path: "Vocabulary.store"),
                                               cloudKitDatabase: .none)
        }
        return try ModelContainer(for: schema, migrationPlan: VocabMigrationPlan.self, configurations: [configuration])
    }

    /// Deletes everything (signing out, or another account signing in).
    @MainActor
    static func wipe(_ context: ModelContext) throws {
        try context.delete(model: WordRecord.self)
        try context.delete(model: BookRecord.self)
        try context.delete(model: BookEntryRecord.self)
        try context.delete(model: ReviewLogRecord.self)
        try context.delete(model: FavoriteRecord.self)
        try context.delete(model: VocabSettingsRecord.self)
        try context.delete(model: SyncStateRecord.self)
        try context.save()
    }

    @MainActor
    static func settingsRecord(in context: ModelContext) -> VocabSettingsRecord {
        if let existing = try? context.fetch(FetchDescriptor<VocabSettingsRecord>()).first { return existing }
        let created = VocabSettingsRecord()
        context.insert(created)
        return created
    }

    @MainActor
    static func syncState(in context: ModelContext) -> SyncStateRecord {
        if let existing = try? context.fetch(FetchDescriptor<SyncStateRecord>()).first { return existing }
        let created = SyncStateRecord()
        context.insert(created)
        return created
    }
}
