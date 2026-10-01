import Foundation
import SwiftData
import Testing
import CourseKit
import VocabKit
@testable import CourseTable

/// The Words tab's store, model and sync against the in-memory demo backend.
@MainActor
@Suite struct VocabSyncTests {
    func makeApp(signedIn: Bool = true, history: Bool = false) throws -> (VocabApp, DemoBackend) {
        let backend = DemoBackend(signedIn: signedIn, history: history)
        return (VocabApp(backend: backend, container: try VocabStore.makeContainer(inMemory: true)), backend)
    }

    @Test func firstSyncDownloadsTheBookAndTheLearnersData() async throws {
        let (app, backend) = try makeApp(history: true)
        await app.start()
        #expect(app.account?.email == DemoBackend.demoEmail)
        #expect(app.sync.phase == .idle)
        #expect(app.sync.lastSync != nil)
        #expect(app.model.bookWords.count == VocabDemo.words.count)
        #expect(app.model.bookWords.first == "abandon")
        #expect(app.model.words.count == VocabDemo.words.count)
        #expect(app.model.events.count == (await backend.storedReviews()).count)
        #expect(!app.model.cards.isEmpty)
        #expect(app.model.favorites == ["ambiguous", "anticipate", "arbitrary"])
        #expect(app.model.settings.dailyNewWords == 5)
        #expect(app.model.settings.reviewMode == .mixed)
        #expect(app.model.settings.examDate != nil)
        #expect(app.model.unsentCount == 0)
    }

    @Test func answersUploadAndAnotherDevicesAnswersArrive() async throws {
        let (app, backend) = try makeApp()
        await app.start()
        let model = app.model
        var session = StudySession(cards: model.cards, newWords: model.plan().newWords, now: .now, scheduler: model.scheduler,
                                   clock: model.clock, mode: .recognition, seed: 1)
        let item = try #require(session.next(at: .now))
        #expect(item.kind == .new)
        let event = session.answer(item, rating: .good, at: .now, durationMs: 3000)
        model.record(event, card: try #require(session.cards[item.headword]))
        #expect(model.unsentCount == 1)

        await app.syncNow()
        #expect(model.unsentCount == 0)
        #expect((await backend.storedReviews()).map(\.id) == [event.id])

        let other = ReviewEvent(headword: item.headword, rating: .good, mode: .choice, reviewedAt: .now.addingTimeInterval(600),
                                studyDay: model.today, durationMs: 2000)
        await backend.addReviewFromAnotherDevice(other)
        await app.syncNow()
        #expect(model.events.map(\.id) == [event.id, other.id])
        #expect(model.cards[item.headword]?.reps == 2)
        #expect(model.cards[item.headword]?.state == .review)

        await app.syncNow()
        #expect(model.events.count == 2)
    }

    @Test func newerEditsWinForFavouritesAndSettings() async throws {
        let (app, backend) = try makeApp(history: true)
        await app.start()
        app.model.setFavorite("abandon", true)
        app.model.setFavorite("ambiguous", false)
        app.model.updateSettings { $0.dailyNewWords = 30 }
        await app.syncNow()
        let favorites = await backend.storedFavorites()
        #expect(favorites.contains { $0.headword == "abandon" && $0.isFavorite })
        #expect(favorites.contains { $0.headword == "ambiguous" && !$0.isFavorite })
        #expect((await backend.storedSettings())?.dailyNewWords == 30)
        #expect(app.model.favorites == ["abandon", "anticipate", "arbitrary"])
        #expect(app.model.settings.dailyNewWords == 30)
    }

    @Test func importedListsUploadWithTheirWords() async throws {
        let (app, backend) = try makeApp()
        await app.start()
        let model = app.model
        let preview = WordListImport.preview("abandon\ntenacious 顽强的\nqwertyuiop\n") { model.words[$0] != nil }
        #expect(preview.words.map(\.headword) == ["abandon", "tenacious"])
        let book = model.importBook(named: "Mine", words: preview.words, owner: try #require(app.account?.id))
        model.updateSettings { $0.bookID = book.id }
        #expect(model.currentBook?.id == book.id)
        #expect(model.bookWords == ["abandon", "tenacious"])
        #expect(model.entry("tenacious")?.shortMeaning == "顽强的")
        #expect(model.entry("abandon")?.phonetic == "əˈbændən")

        await app.syncNow()
        #expect(app.sync.phase == .idle)
        #expect((await backend.storedBooks()).contains { $0.id == book.id && $0.name == "Mine" })
        #expect((await backend.storedEntries()).filter { $0.bookID == book.id }.count == 2)
        #expect((await backend.storedSettings())?.bookID == book.id)

        model.deleteBook(book)
        #expect(model.currentBook?.isSystem == true)
        await app.syncNow()
        #expect((await backend.storedBooks()).first { $0.id == book.id }?.deletedAt != nil)
    }

    @Test func signingOutClearsThisDevice() async throws {
        let (app, _) = try makeApp(history: true)
        await app.start()
        #expect(!app.model.events.isEmpty)
        await app.signOut()
        #expect(app.auth == .signedOut)
        #expect(app.model.words.isEmpty)
        #expect(app.model.events.isEmpty)
        #expect(app.model.favorites.isEmpty)
    }

    @Test func anotherAccountStartsFromAnEmptyStore() async throws {
        let (app, _) = try makeApp()
        await app.start()
        let model = app.model
        let event = ReviewEvent(headword: "abandon", rating: .good, mode: .choice, reviewedAt: .now, studyDay: model.today)
        model.record(event, card: model.scheduler.review(Card(headword: "abandon"), rating: .good, at: .now, day: model.today))
        #expect(model.events.count == 1)
        // The store belongs to the demo learner; syncing for someone else clears it first.
        await app.sync.sync(for: Account(id: UUID(), email: "someone@example.com"))
        #expect(model.events.isEmpty)
        #expect(!model.bookWords.isEmpty)
    }

    @Test func emailLinks() async throws {
        let (app, _) = try makeApp(signedIn: false)
        await app.start()
        #expect(app.auth == .signedOut)
        await app.open(URL(string: "https://example.com")!)
        #expect(app.auth == .signedOut)
        #expect(app.linkMessage == nil)

        await app.open(URL(string: "io.github.hilbert0619.coursetable://auth-callback#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired")!)
        #expect(app.auth == .signedOut)
        #expect(app.linkMessage == VocabAuthError.linkExpired.errorDescription)

        await app.open(URL(string: "io.github.hilbert0619.coursetable://auth-callback#access_token=a&refresh_token=b&type=recovery")!)
        #expect(app.account != nil)
        #expect(app.needsNewPassword)
        try await app.setNewPassword("correct-horse-7")
        #expect(!app.needsNewPassword)
    }

    @Test func signUpNeedsTheEmailedLink() async throws {
        let (app, _) = try makeApp(signedIn: false)
        await app.start()
        #expect(try await app.signUp(email: " New@Example.com ", password: "long-enough"))
        #expect(app.auth == .signedOut)
        await #expect(throws: VocabAuthError.weakPassword) {
            _ = try await app.signUp(email: "new@example.com", password: "short")
        }
    }

    @Test func authErrorCodes() {
        #expect(SupabaseBackend.vocabError(code: "invalid_credentials", message: "x") == .invalidCredentials)
        #expect(SupabaseBackend.vocabError(code: "email_address_not_authorized", message: "x") == .emailNotAllowed)
        #expect(SupabaseBackend.vocabError(code: "over_email_send_rate_limit", message: "x") == .rateLimited)
        #expect(SupabaseBackend.vocabError(code: "something_new", message: "Server said no") == .other("Server said no"))
    }
}

@MainActor
@Suite struct VocabRecordTests {
    @Test func recordsRoundTrip() throws {
        let entry = VocabDemo.words[0]
        #expect(WordRecord(entry, serverID: 7).entry == entry)

        let event = ReviewEvent(headword: "abandon", rating: .hard, mode: .spelling, reviewedAt: .now, studyDay: 20_000, durationMs: 1234)
        #expect(ReviewLogRecord(event).event == event)
        #expect(RemoteReview(event).event == event)

        let settings = VocabSettingsRecord()
        #expect(settings.settings == VocabSettings.defaults)
        var changed = settings.settings
        changed.examDate = LocalDate(year: 2026, month: 12, day: 19)
        changed.reviewMode = .spelling
        settings.settings = changed
        #expect(settings.settings == changed)
        #expect(settings.remote.examDate == "2026-12-19")
    }

    @Test func ownEntriesOverrideTheDictionary() {
        let dictionary = VocabDemo.words[0]
        let word = WordListImport.preview("Abandon\tv. 丢下\n") { _ in true }.words[0]
        let record = BookEntryRecord(word, book: UUID(), position: 1, wordID: 1)
        let entry = record.entry(dictionary: dictionary)
        #expect(entry.display == "Abandon")
        #expect(entry.translation == "v. 丢下")
        #expect(entry.phonetic == dictionary.phonetic)
        #expect(entry.definition == dictionary.definition)
    }

    @Test func demoHistoryLooksLikeTwoWeeksOfStudy() {
        let now = Date.now
        let events = VocabDemo.history(endingBefore: now)
        let today = StudyClock().day(of: now)
        #expect(events.count > 40)
        #expect(events.allSatisfy { $0.studyDay < today })
        #expect(Set(events.map(\.studyDay)).count == 13)
        #expect(Set(events.map(\.id)).count == events.count)
        #expect(Set(VocabStats.hardWords(Scheduler().replay(events).values).map(\.headword)).isSuperset(of: ["abandon", "ability"]))
        #expect(VocabDemo.examDate(from: now).hasSuffix("-12-19") || VocabDemo.examDate(from: now).contains("-12-"))
    }
}
