package io.github.hilbert0619.vocabkit.store

import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.vocabkit.Headword
import io.github.hilbert0619.vocabkit.ImportedWord
import io.github.hilbert0619.vocabkit.ReviewEvent
import io.github.hilbert0619.vocabkit.SessionMode
import io.github.hilbert0619.vocabkit.StudyClock
import io.github.hilbert0619.vocabkit.WordEntry
import io.github.hilbert0619.vocabkit.backend.RemoteBook
import io.github.hilbert0619.vocabkit.backend.RemoteEntry
import io.github.hilbert0619.vocabkit.backend.RemoteFavorite
import io.github.hilbert0619.vocabkit.backend.RemoteSettings
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The vocabulary store's records (iOS VocabSchemaV1, docs/vocabulary-spec.md §5.3) as values. The app keeps them in
// their own Room database, so the timetable's is never migrated for them.

/** A dictionary entry downloaded with a book. */
data class WordRecord(val serverId: Long, val entry: WordEntry) {
    val headword: String get() = entry.headword
}

/** A system book or one of the learner's own. */
data class BookRecord(
    val id: UUID,
    /** `null` for system books. */
    val ownerId: UUID?,
    val name: String,
    val detail: String,
    val source: String,
    val licence: String,
    val wordCount: Int,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    /** When the entries were last downloaded; `null` until they are on the device. */
    val downloadedAt: Instant?,
    /** Changed on this device and not yet uploaded. */
    val needsUpload: Boolean,
) {
    val isSystem: Boolean get() = ownerId == null

    val remote: RemoteBook
        get() = RemoteBook(id, ownerId, name, detail, source, licence, wordCount, updatedAt, deletedAt)

    /** The server's newer version; a book that changed must be downloaded again. */
    fun updated(remote: RemoteBook): BookRecord = copy(
        name = remote.name, detail = remote.description, source = remote.source, licence = remote.licence,
        wordCount = remote.wordCount, deletedAt = remote.deletedAt, updatedAt = remote.updatedAt,
        downloadedAt = if (remote.updatedAt > updatedAt) null else downloadedAt,
    )

    companion object {
        fun of(remote: RemoteBook) = BookRecord(
            remote.id, remote.ownerId, remote.name, remote.description, remote.source, remote.licence, remote.wordCount,
            remote.updatedAt, remote.deletedAt, downloadedAt = null, needsUpload = false,
        )

        fun own(name: String, owner: UUID, wordCount: Int, now: Instant) = BookRecord(
            UUID.randomUUID(), owner, name, detail = "", source = "", licence = "", wordCount = wordCount, updatedAt = now,
            deletedAt = null, downloadedAt = now, needsUpload = true,
        )
    }
}

/** A word in a book, in study order, with the meaning the learner gave it if any. */
data class BookEntryRecord(
    val id: UUID,
    val bookId: UUID,
    val position: Int,
    val headword: String,
    val wordId: Long?,
    val display: String?,
    val meaning: String?,
    val phonetic: String?,
    val pos: String?,
    val example: String?,
    val exampleTranslation: String?,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val needsUpload: Boolean,
) {
    /** The dictionary's entry with anything the learner gave taking its place. */
    fun entry(dictionary: WordEntry?): WordEntry {
        var entry = dictionary ?: WordEntry(headword = headword, display = display ?: headword, translation = "")
        if (display != null) entry = entry.copy(display = display)
        if (!meaning.isNullOrEmpty()) entry = entry.copy(translation = meaning)
        if (!phonetic.isNullOrEmpty()) entry = entry.copy(phonetic = phonetic.trim { it in "/[] " })
        return entry
    }

    val remote: RemoteEntry
        get() = RemoteEntry(id, bookId, position, headword, wordId, display, meaning, phonetic, pos, example, exampleTranslation, updatedAt, deletedAt)

    fun updated(remote: RemoteEntry): BookEntryRecord = copy(
        position = remote.position, wordId = remote.wordId, display = remote.display, meaning = remote.meaning,
        phonetic = remote.phonetic, pos = remote.pos, example = remote.example, exampleTranslation = remote.exampleTranslation,
        updatedAt = remote.updatedAt, deletedAt = remote.deletedAt,
    )

    companion object {
        fun of(remote: RemoteEntry) = BookEntryRecord(
            remote.id, remote.bookId, remote.position, Headword.key(remote.headword), remote.wordId, remote.display, remote.meaning,
            remote.phonetic, remote.pos, remote.example, remote.exampleTranslation, remote.updatedAt, remote.deletedAt, needsUpload = false,
        )

        fun imported(word: ImportedWord, book: UUID, position: Int, wordId: Long?, now: Instant) = BookEntryRecord(
            id = UUID.randomUUID(), bookId = book, position = position, headword = word.headword, wordId = wordId,
            display = if (word.display == word.headword) null else word.display, meaning = word.meaning, phonetic = word.phonetic,
            pos = word.partOfSpeech, example = word.example, exampleTranslation = word.exampleTranslation, updatedAt = now,
            deletedAt = null, needsUpload = true,
        )
    }
}

/** One answer, recorded on this device or downloaded from another. */
data class ReviewLogRecord(
    val event: ReviewEvent,
    /** Uploaded (or downloaded, so already on the server). */
    val pushed: Boolean = false,
    val receivedAt: Instant? = null,
)

data class FavoriteRecord(val headword: String, val isFavorite: Boolean, val updatedAt: Instant, val needsUpload: Boolean) {
    val remote: RemoteFavorite get() = RemoteFavorite(headword, isFavorite, updatedAt)
}

/** The learner's study settings. */
data class VocabSettings(
    val bookId: UUID? = null,
    val dailyNewWords: Int = 20,
    val reviewMode: SessionMode = SessionMode.RECOGNITION,
    val examDate: LocalDate? = null,
    val dayStart: TimeOfDay = StudyClock.DEFAULT_DAY_START,
    /** FSRS target retention, `0.80..0.95` in the settings screen. */
    val desiredRetention: Double = 0.9,
) {
    companion object {
        val DEFAULTS = VocabSettings()
    }
}

/** The single settings row. [updatedAt] is `null` until the learner changes something (iOS `.distantPast`). */
data class VocabSettingsRecord(
    val settings: VocabSettings = VocabSettings.DEFAULTS,
    val updatedAt: Instant? = null,
    val needsUpload: Boolean = false,
) {
    val remote: RemoteSettings
        get() = RemoteSettings(
            bookId = settings.bookId, dailyNewWords = settings.dailyNewWords, reviewMode = settings.reviewMode.rawValue,
            examDate = settings.examDate?.toString(), dayStartMinutes = settings.dayStart.minutes,
            desiredRetention = settings.desiredRetention, updatedAt = updatedAt ?: Instant.EPOCH,
        )

    companion object {
        fun of(remote: RemoteSettings) = VocabSettingsRecord(
            settings = VocabSettings(
                bookId = remote.bookId, dailyNewWords = remote.dailyNewWords,
                reviewMode = SessionMode.of(remote.reviewMode) ?: SessionMode.RECOGNITION,
                examDate = remote.examDate?.let(LocalDate::parse), dayStart = TimeOfDay(remote.dayStartMinutes),
                desiredRetention = remote.desiredRetention,
            ),
            updatedAt = remote.updatedAt,
            needsUpload = false,
        )
    }
}

/** The single sync bookkeeping row. */
data class SyncState(
    /** The account whose data this store holds. */
    val userId: UUID? = null,
    /** Newest `received_at` pulled so far. */
    val pullCursor: Instant? = null,
    val lastSyncAt: Instant? = null,
)

/** Where the Words tab keeps its data: Room in the app, [InMemoryVocabStore] in tests. Saves replace by key. */
interface VocabStore {
    suspend fun words(): List<WordRecord>

    /** By headword. */
    suspend fun saveWords(words: List<WordRecord>)
    suspend fun books(): List<BookRecord>
    suspend fun saveBooks(books: List<BookRecord>)

    /** A book's entries (deleted ones too), by position. */
    suspend fun entries(bookId: UUID): List<BookEntryRecord>
    suspend fun entriesToUpload(): List<BookEntryRecord>
    suspend fun saveEntries(entries: List<BookEntryRecord>)
    suspend fun reviews(): List<ReviewLogRecord>

    /** By event id. */
    suspend fun saveReviews(reviews: List<ReviewLogRecord>)
    suspend fun markPushed(ids: Set<UUID>)
    suspend fun favorites(): List<FavoriteRecord>
    suspend fun saveFavorites(favorites: List<FavoriteRecord>)
    suspend fun settings(): VocabSettingsRecord
    suspend fun saveSettings(record: VocabSettingsRecord)
    suspend fun syncState(): SyncState
    suspend fun saveSyncState(state: SyncState)

    /** Deletes everything (signing out, or another account signing in). */
    suspend fun wipe()
}

/** A [VocabStore] in memory, for tests, screenshots, and when the database can't be opened. */
class InMemoryVocabStore : VocabStore {
    private val mutex = Mutex()
    private val words = linkedMapOf<String, WordRecord>()
    private val books = linkedMapOf<UUID, BookRecord>()
    private val entries = linkedMapOf<UUID, BookEntryRecord>()
    private val reviews = linkedMapOf<UUID, ReviewLogRecord>()
    private val favorites = linkedMapOf<String, FavoriteRecord>()
    private var settings = VocabSettingsRecord()
    private var syncState = SyncState()

    override suspend fun words() = mutex.withLock { words.values.toList() }
    override suspend fun saveWords(words: List<WordRecord>) = mutex.withLock { words.forEach { this.words[it.headword] = it } }
    override suspend fun books() = mutex.withLock { books.values.toList() }
    override suspend fun saveBooks(books: List<BookRecord>) = mutex.withLock { books.forEach { this.books[it.id] = it } }
    override suspend fun entries(bookId: UUID) = mutex.withLock { entries.values.filter { it.bookId == bookId }.sortedBy { it.position } }
    override suspend fun entriesToUpload() = mutex.withLock { entries.values.filter { it.needsUpload } }
    override suspend fun saveEntries(entries: List<BookEntryRecord>) = mutex.withLock { entries.forEach { this.entries[it.id] = it } }
    override suspend fun reviews() = mutex.withLock { reviews.values.toList() }
    override suspend fun saveReviews(reviews: List<ReviewLogRecord>) = mutex.withLock { reviews.forEach { this.reviews[it.event.id] = it } }
    override suspend fun markPushed(ids: Set<UUID>) = mutex.withLock {
        for (id in ids) reviews[id]?.let { reviews[id] = it.copy(pushed = true) }
    }
    override suspend fun favorites() = mutex.withLock { favorites.values.toList() }
    override suspend fun saveFavorites(favorites: List<FavoriteRecord>) = mutex.withLock { favorites.forEach { this.favorites[it.headword] = it } }
    override suspend fun settings() = mutex.withLock { settings }
    override suspend fun saveSettings(record: VocabSettingsRecord) = mutex.withLock { settings = record }
    override suspend fun syncState() = mutex.withLock { syncState }
    override suspend fun saveSyncState(state: SyncState) = mutex.withLock { syncState = state }
    override suspend fun wipe() = mutex.withLock {
        words.clear()
        books.clear()
        entries.clear()
        reviews.clear()
        favorites.clear()
        settings = VocabSettingsRecord()
        syncState = SyncState()
    }
}
