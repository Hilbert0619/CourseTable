package io.github.hilbert0619.vocabkit.store

import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.vocabkit.Card
import io.github.hilbert0619.vocabkit.CardState
import io.github.hilbert0619.vocabkit.Rating
import io.github.hilbert0619.vocabkit.ReviewEvent
import io.github.hilbert0619.vocabkit.ReviewMode
import io.github.hilbert0619.vocabkit.Scheduler
import io.github.hilbert0619.vocabkit.SessionMode
import io.github.hilbert0619.vocabkit.StudyClock
import io.github.hilbert0619.vocabkit.StudyItem
import io.github.hilbert0619.vocabkit.StudySession
import io.github.hilbert0619.vocabkit.VocabStats
import io.github.hilbert0619.vocabkit.WordListImport
import io.github.hilbert0619.vocabkit.backend.Account
import io.github.hilbert0619.vocabkit.backend.DemoBackend
import io.github.hilbert0619.vocabkit.backend.RemoteEntry
import io.github.hilbert0619.vocabkit.backend.RemoteReview
import io.github.hilbert0619.vocabkit.backend.RemoteSettings
import io.github.hilbert0619.vocabkit.backend.VocabAuthError
import io.github.hilbert0619.vocabkit.backend.VocabDemo
import io.github.hilbert0619.vocabkit.backend.VocabJson
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject

/** The Words tab's store, model and sync against the in-memory demo backend (iOS `VocabSyncTests`). */
class VocabSyncTests {
    private fun makeApp(signedIn: Boolean = true, history: Boolean = false): Pair<VocabApp, DemoBackend> {
        val backend = DemoBackend(signedIn, history)
        return VocabApp(backend, VocabModel(InMemoryVocabStore())) to backend
    }

    @Test
    fun firstSyncDownloadsTheBookAndTheLearnersData() = runTest {
        val (app, backend) = makeApp(history = true)
        app.start()
        val state = app.model.state.value
        assertEquals(DemoBackend.DEMO_EMAIL, app.account?.email)
        assertEquals(SyncEngine.Phase.Idle, app.sync.phase.value)
        assertNotNull(app.sync.lastSync.value)
        assertEquals(VocabDemo.words.size, state.bookWords.size)
        assertEquals("abandon", state.bookWords.first())
        assertEquals(VocabDemo.words.size, state.words.size)
        assertEquals(backend.storedReviews().size, state.events.size)
        assertTrue(state.cards.isNotEmpty())
        assertEquals(setOf("ambiguous", "anticipate", "arbitrary"), state.favorites)
        assertEquals(5, state.settings.dailyNewWords)
        assertEquals(SessionMode.MIXED, state.settings.reviewMode)
        assertNotNull(state.settings.examDate)
        assertEquals(0, state.unsentCount)
    }

    @Test
    fun answersUploadAndAnotherDevicesAnswersArrive() = runTest {
        val (app, backend) = makeApp()
        app.start()
        val model = app.model
        val now = Instant.now()
        var state = model.state.value
        val session = StudySession(state.cards, state.plan(now).newWords, now, state.scheduler, state.clock, SessionMode.RECOGNITION, seed = 1u)
        val item = assertNotNull(session.next(now))
        assertEquals(StudyItem.Kind.NEW, item.kind)
        val event = session.answer(item, Rating.GOOD, now, durationMs = 3000)
        model.record(event, session.cards.getValue(item.headword))
        assertEquals(1, model.state.value.unsentCount)

        app.syncNow()
        assertEquals(0, model.state.value.unsentCount)
        assertEquals(listOf(event.id), backend.storedReviews().map { it.id })

        state = model.state.value
        val other = ReviewEvent(
            headword = item.headword, rating = Rating.GOOD, mode = ReviewMode.CHOICE, reviewedAt = now.plusSeconds(600),
            studyDay = state.today(now), durationMs = 2000,
        )
        backend.addReviewFromAnotherDevice(other)
        app.syncNow()
        state = model.state.value
        assertEquals(listOf(event.id, other.id), state.events.map { it.id })
        assertEquals(2, state.cards[item.headword]?.reps)
        assertEquals(CardState.REVIEW, state.cards[item.headword]?.state)

        app.syncNow()
        assertEquals(2, model.state.value.events.size)
    }

    @Test
    fun newerEditsWinForFavouritesAndSettings() = runTest {
        val (app, backend) = makeApp(history = true)
        app.start()
        app.model.setFavorite("abandon", true)
        app.model.setFavorite("ambiguous", false)
        app.model.updateSettings { it.copy(dailyNewWords = 30) }
        app.syncNow()
        val favorites = backend.storedFavorites()
        assertTrue(favorites.any { it.headword == "abandon" && it.isFavorite })
        assertTrue(favorites.any { it.headword == "ambiguous" && !it.isFavorite })
        assertEquals(30, backend.storedSettings()?.dailyNewWords)
        assertEquals(setOf("abandon", "anticipate", "arbitrary"), app.model.state.value.favorites)
        assertEquals(30, app.model.state.value.settings.dailyNewWords)
    }

    @Test
    fun importedListsUploadWithTheirWords() = runTest {
        val (app, backend) = makeApp()
        app.start()
        val model = app.model
        val preview = WordListImport.preview("abandon\ntenacious 顽强的\nqwertyuiop\n") { it in model.state.value.words }
        assertEquals(listOf("abandon", "tenacious"), preview.words.map { it.headword })
        val book = model.importBook("Mine", preview.words, assertNotNull(app.account).id)
        model.updateSettings { it.copy(bookId = book.id) }
        var state = model.state.value
        assertEquals(book.id, state.currentBook?.id)
        assertEquals(listOf("abandon", "tenacious"), state.bookWords)
        assertEquals("顽强的", state.entry("tenacious")?.shortMeaning)
        assertEquals("əˈbændən", state.entry("abandon")?.phonetic)

        app.syncNow()
        assertEquals(SyncEngine.Phase.Idle, app.sync.phase.value)
        assertTrue(backend.storedBooks().any { it.id == book.id && it.name == "Mine" })
        assertEquals(2, backend.storedEntries().count { it.bookId == book.id })
        assertEquals(book.id, backend.storedSettings()?.bookId)

        model.deleteBook(book)
        state = model.state.value
        assertEquals(true, state.currentBook?.isSystem)
        app.syncNow()
        assertNotNull(backend.storedBooks().first { it.id == book.id }.deletedAt)
    }

    @Test
    fun renamedListsUpload() = runTest {
        val (app, backend) = makeApp()
        app.start()
        val model = app.model
        val book = model.importBook("Mine", WordListImport.preview("abandon\n") { true }.words, assertNotNull(app.account).id)
        model.renameBook(book, "  Unit 1  ")
        assertEquals("Unit 1", model.state.value.books.first { it.id == book.id }.name)
        app.syncNow()
        assertEquals("Unit 1", backend.storedBooks().first { it.id == book.id }.name)
    }

    @Test
    fun signingOutClearsThisDevice() = runTest {
        val (app, _) = makeApp(history = true)
        app.start()
        assertTrue(app.model.state.value.events.isNotEmpty())
        app.signOut()
        val state = app.model.state.value
        assertEquals(VocabApp.AuthState.SignedOut, app.auth.value)
        assertTrue(state.words.isEmpty())
        assertTrue(state.events.isEmpty())
        assertTrue(state.favorites.isEmpty())
    }

    @Test
    fun deletingTheAccountClearsTheServerAndThisDevice() = runTest {
        val (app, backend) = makeApp(history = true)
        app.start()
        app.deleteAccount()
        assertEquals(VocabApp.AuthState.SignedOut, app.auth.value)
        assertTrue(app.model.state.value.events.isEmpty())
        assertTrue(backend.storedReviews().isEmpty())
    }

    @Test
    fun anotherAccountStartsFromAnEmptyStore() = runTest {
        val (app, _) = makeApp()
        app.start()
        val model = app.model
        val now = Instant.now()
        val state = model.state.value
        val event = ReviewEvent(headword = "abandon", rating = Rating.GOOD, mode = ReviewMode.CHOICE, reviewedAt = now, studyDay = state.today(now))
        model.record(event, state.scheduler.review(Card.of("abandon"), Rating.GOOD, now, state.today(now)))
        assertEquals(1, model.state.value.events.size)
        // The store belongs to the demo learner; syncing for someone else clears it first.
        app.sync.sync(Account(UUID.randomUUID(), "someone@example.com"))
        assertTrue(model.state.value.events.isEmpty())
        assertTrue(model.state.value.bookWords.isNotEmpty())
    }

    @Test
    fun anOfflineSyncKeepsTheAnswersAndSaysWhy() = runTest {
        val (app, _) = makeApp()
        app.start()
        app.backend.signOut() // the demo server now refuses every request, as a server that can't be reached
        val now = Instant.now()
        val state = app.model.state.value
        val event = ReviewEvent(headword = "ability", rating = Rating.GOOD, mode = ReviewMode.CHOICE, reviewedAt = now, studyDay = state.today(now))
        app.model.record(event, state.scheduler.review(Card.of("ability"), Rating.GOOD, now, state.today(now)))
        app.syncNow()
        assertTrue(app.sync.phase.value is SyncEngine.Phase.Failed)
        assertEquals(1, app.model.state.value.unsentCount)
    }

    @Test
    fun emailLinks() = runTest {
        val (app, _) = makeApp(signedIn = false)
        app.start()
        assertEquals(VocabApp.AuthState.SignedOut, app.auth.value)
        app.open("https://example.com")
        assertEquals(VocabApp.AuthState.SignedOut, app.auth.value)
        assertNull(app.linkMessage.value)

        app.open("io.github.hilbert0619.coursetable://auth-callback#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired")
        assertEquals(VocabApp.AuthState.SignedOut, app.auth.value)
        assertEquals(VocabApp.LinkMessage.Failed(VocabAuthError.LinkExpired), app.linkMessage.value)
        app.dismissLinkMessage()

        app.open("io.github.hilbert0619.coursetable://auth-callback#access_token=a&refresh_token=b&type=signup")
        assertNotNull(app.account)
        assertEquals(VocabApp.LinkMessage.Confirmed, app.linkMessage.value)

        app.open("io.github.hilbert0619.coursetable://auth-callback#access_token=a&refresh_token=b&type=recovery")
        assertTrue(app.needsNewPassword.value)
        app.setNewPassword("correct-horse-7")
        assertFalse(app.needsNewPassword.value)
    }

    @Test
    fun signUpNeedsTheEmailedLink() = runTest {
        val (app, _) = makeApp(signedIn = false)
        app.start()
        assertTrue(app.signUp(" New@Example.com ", "long-enough"))
        assertEquals(VocabApp.AuthState.SignedOut, app.auth.value)
        assertFailsWith<VocabAuthError.WeakPassword> { app.signUp("new@example.com", "short") }
    }

    @Test
    fun signingInSyncs() = runTest {
        val (app, _) = makeApp(signedIn = false, history = true)
        app.start()
        assertFailsWith<VocabAuthError.EmailNotConfirmed> { app.signIn("stranger@example.com", "long-enough") }
        app.signIn(" Learner@Example.com ", "long-enough")
        assertEquals(DemoBackend.DEMO_EMAIL, app.account?.email)
        assertTrue(app.model.state.value.events.isNotEmpty())
    }

    @Test
    fun authErrorCodes() {
        assertEquals(VocabAuthError.InvalidCredentials, VocabAuthError.fromCode("invalid_credentials", "x"))
        assertEquals(VocabAuthError.EmailNotAllowed, VocabAuthError.fromCode("email_address_not_authorized", "x"))
        assertEquals(VocabAuthError.RateLimited, VocabAuthError.fromCode("over_email_send_rate_limit", "x"))
        assertEquals(VocabAuthError.Other("Server said no"), VocabAuthError.fromCode("something_new", "Server said no"))
    }
}

/** The records and the rows exchanged with the server (iOS `VocabRecordTests`, plus the JSON the REST API uses). */
class VocabRecordTests {
    @Test
    fun recordsRoundTrip() {
        val event = ReviewEvent(headword = "abandon", rating = Rating.HARD, mode = ReviewMode.SPELLING, reviewedAt = Instant.now(), studyDay = 20_000, durationMs = 1234)
        assertEquals(event, ReviewLogRecord(event).event)
        assertEquals(event, RemoteReview.of(event).event)

        val settings = VocabSettingsRecord()
        assertEquals(VocabSettings.DEFAULTS, settings.settings)
        val changed = settings.settings.copy(examDate = LocalDate(2026, 12, 19), reviewMode = SessionMode.SPELLING)
        val record = settings.copy(settings = changed)
        assertEquals("2026-12-19", record.remote.examDate)
        assertEquals("spelling", record.remote.reviewMode)
        assertEquals(changed, VocabSettingsRecord.of(record.remote).settings)
    }

    @Test
    fun ownEntriesOverrideTheDictionary() {
        val dictionary = VocabDemo.words[0]
        val word = WordListImport.preview("Abandon\tv. 丢下\n") { true }.words[0]
        val record = BookEntryRecord.imported(word, UUID.randomUUID(), 1, 1, Instant.now())
        val entry = record.entry(dictionary)
        assertEquals("Abandon", entry.display)
        assertEquals("v. 丢下", entry.translation)
        assertEquals(dictionary.phonetic, entry.phonetic)
        assertEquals(dictionary.definition, entry.definition)
    }

    @Test
    fun demoHistoryLooksLikeTwoWeeksOfStudy() {
        val now = Instant.now()
        val events = VocabDemo.history(now)
        val today = StudyClock().day(now)
        assertTrue(events.size > 40)
        assertTrue(events.all { it.studyDay < today })
        assertEquals(13, events.map { it.studyDay }.toSet().size)
        assertEquals(events.size, events.map { it.id }.toSet().size)
        assertTrue(VocabStats.hardWords(Scheduler().replay(events).values).map { it.headword }.toSet().containsAll(listOf("abandon", "ability")))
        assertTrue("-12-" in VocabDemo.examDate(now))
    }

    @Test
    fun rowsReadAsTheRestApiWritesThem() {
        val text = """
            {"id": "6f1c1d3e-0000-4000-8000-000000000001", "book_id": "00000000-0000-4000-8000-000000000001", "position": 1,
             "headword": "government", "word_id": 42, "display": null, "meaning": null, "phonetic": null, "pos": null,
             "example": null, "example_translation": null, "updated_at": "2026-09-30T10:00:00.123456+00:00", "deleted_at": null,
             "words": {"id": 42, "headword": "government", "display": "government", "phonetic": "'gʌvәnmәnt",
                       "translation_zh": "n. 政府, 内阁", "definition_en": "n. the organization that is the governing authority",
                       "pos": "n:100", "collins": 5, "oxford": true, "tags": ["zk", "gk", "ky"], "bnc": 120, "frq": 95,
                       "exchange": "s:governments", "source": "ECDICT"}}
        """.trimIndent()
        val entry = VocabJson.decodeFromString(RemoteEntry.serializer(), text)
        assertEquals(42L, entry.wordId)
        assertEquals(Instant.parse("2026-09-30T10:00:00.123456Z"), entry.updatedAt)
        val word = assertNotNull(entry.word)
        assertEquals("n. 政府，内阁", word.entry.shortMeaning)
        assertEquals(95, word.entry.frequencyRank)
        assertEquals(listOf("zk", "gk", "ky"), word.entry.tags)
    }

    @Test
    fun uploadsCarryEveryColumnButTheServersOwn() {
        val entry = RemoteEntry(
            id = UUID.randomUUID(), bookId = UUID.randomUUID(), position = 3, headword = "tenacious", meaning = "顽强的",
            updatedAt = Instant.parse("2026-10-10T08:00:00.250Z"),
        )
        val entryJson = VocabJson.encodeToJsonElement(RemoteEntry.serializer(), entry).jsonObject
        assertFalse("words" in entryJson)
        assertEquals("null", entryJson["deleted_at"].toString())
        assertEquals("\"2026-10-10T08:00:00.250Z\"", entryJson["updated_at"].toString())

        val event = ReviewEvent(headword = "abandon", rating = Rating.GOOD, mode = ReviewMode.CHOICE, reviewedAt = Instant.parse("2026-10-10T08:00:00Z"), studyDay = 20_000)
        val reviewJson = VocabJson.encodeToJsonElement(RemoteReview.serializer(), RemoteReview.of(event)).jsonObject
        assertFalse("received_at" in reviewJson)
        assertEquals("3", reviewJson["rating"].toString())
        assertEquals("\"choice\"", reviewJson["mode"].toString())

        val settings = RemoteSettings(null, 20, "mixed", null, 240, 0.9, Instant.parse("2026-10-10T08:00:00Z"))
        val settingsJson = VocabJson.encodeToJsonElement(RemoteSettings.serializer(), settings).jsonObject
        assertEquals("null", settingsJson["book_id"].toString())
        assertEquals("null", settingsJson["exam_date"].toString())
    }
}
