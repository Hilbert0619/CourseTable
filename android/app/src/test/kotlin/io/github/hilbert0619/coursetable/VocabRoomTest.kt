package io.github.hilbert0619.coursetable

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursetable.words.RoomVocabStore
import io.github.hilbert0619.coursetable.words.VocabDatabase
import io.github.hilbert0619.vocabkit.Rating
import io.github.hilbert0619.vocabkit.ReviewEvent
import io.github.hilbert0619.vocabkit.ReviewMode
import io.github.hilbert0619.vocabkit.SessionMode
import io.github.hilbert0619.vocabkit.WordListImport
import io.github.hilbert0619.vocabkit.backend.DemoBackend
import io.github.hilbert0619.vocabkit.backend.VocabDemo
import io.github.hilbert0619.vocabkit.store.BookEntryRecord
import io.github.hilbert0619.vocabkit.store.BookRecord
import io.github.hilbert0619.vocabkit.store.FavoriteRecord
import io.github.hilbert0619.vocabkit.store.ReviewLogRecord
import io.github.hilbert0619.vocabkit.store.SyncState
import io.github.hilbert0619.vocabkit.store.VocabApp
import io.github.hilbert0619.vocabkit.store.VocabModel
import io.github.hilbert0619.vocabkit.store.VocabSettings
import io.github.hilbert0619.vocabkit.store.VocabSettingsRecord
import io.github.hilbert0619.vocabkit.store.WordRecord
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Words data through Room (`vocabulary.db`, in memory): every record comes back as it went in. */
@RunWith(AndroidJUnit4::class)
class VocabRoomTest {
    private lateinit var database: VocabDatabase
    private lateinit var store: RoomVocabStore

    @Before
    fun setUp() {
        database = VocabDatabase.inMemory(ApplicationProvider.getApplicationContext())
        store = RoomVocabStore(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun recordsRoundTrip() = runBlocking {
        // Microseconds survive (the server's precision).
        val time = Instant.parse("2026-10-10T08:00:00.123456Z")
        val word = WordRecord(7, VocabDemo.words[0])
        store.saveWords(listOf(word))
        assertEquals(listOf(word), store.words())

        val book = BookRecord(UUID.randomUUID(), UUID.randomUUID(), "Mine", "", "", "", 2, time, null, time, needsUpload = true)
        store.saveBooks(listOf(book))
        assertEquals(listOf(book), store.books())

        val imported = WordListImport.preview("Abandon\tv. 丢下\n") { true }.words[0].copy(phonetic = "/x/", example = "An example.")
        val entry = BookEntryRecord.imported(imported, book.id, 1, 7, time)
        store.saveEntries(listOf(entry))
        assertEquals(listOf(entry), store.entries(book.id))
        assertEquals(listOf(entry), store.entriesToUpload())

        val event = ReviewEvent(UUID.randomUUID(), "abandon", Rating.HARD, ReviewMode.SPELLING, time, 20_000, 1234)
        store.saveReviews(listOf(ReviewLogRecord(event)))
        assertEquals(listOf(ReviewLogRecord(event)), store.reviews())
        store.markPushed(setOf(event.id))
        assertEquals(true, store.reviews().single().pushed)

        val favorite = FavoriteRecord("abandon", true, time, needsUpload = true)
        store.saveFavorites(listOf(favorite))
        assertEquals(listOf(favorite), store.favorites())

        assertEquals(VocabSettingsRecord(), store.settings())
        val settings = VocabSettingsRecord(
            VocabSettings(book.id, 30, SessionMode.SPELLING, LocalDate(2026, 12, 19), TimeOfDay(5, 0), 0.85),
            updatedAt = time,
            needsUpload = true,
        )
        store.saveSettings(settings)
        assertEquals(settings, store.settings())

        val state = SyncState(UUID.randomUUID(), time, time)
        store.saveSyncState(state)
        assertEquals(state, store.syncState())

        store.wipe()
        assertTrue(store.words().isEmpty() && store.books().isEmpty() && store.reviews().isEmpty() && store.favorites().isEmpty())
        assertEquals(SyncState(), store.syncState())
    }

    @Test
    fun aFirstSyncIsReadBackByANewModel() = runBlocking {
        val app = VocabApp(DemoBackend(signedIn = true, history = true), VocabModel(store))
        app.start()
        val synced = app.model.state.value
        assertEquals(VocabDemo.words.size, synced.bookWords.size)

        // The app opened again: the same words, answers, cards, favourites and settings, offline.
        val again = VocabModel(RoomVocabStore(database))
        again.load()
        val reloaded = again.state.value
        assertEquals(synced.words, reloaded.words)
        assertEquals(synced.bookWords, reloaded.bookWords)
        assertEquals(synced.events.map { it.id }.toSet(), reloaded.events.map { it.id }.toSet())
        assertEquals(synced.cards, reloaded.cards)
        assertEquals(synced.favorites, reloaded.favorites)
        assertEquals(synced.settings, reloaded.settings)
        assertEquals(0, reloaded.unsentCount)
    }
}
