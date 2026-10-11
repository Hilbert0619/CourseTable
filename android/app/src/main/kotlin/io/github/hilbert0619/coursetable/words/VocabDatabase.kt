package io.github.hilbert0619.coursetable.words

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.vocabkit.Rating
import io.github.hilbert0619.vocabkit.ReviewEvent
import io.github.hilbert0619.vocabkit.ReviewMode
import io.github.hilbert0619.vocabkit.SessionMode
import io.github.hilbert0619.vocabkit.WordEntry
import io.github.hilbert0619.vocabkit.store.BookEntryRecord
import io.github.hilbert0619.vocabkit.store.BookRecord
import io.github.hilbert0619.vocabkit.store.FavoriteRecord
import io.github.hilbert0619.vocabkit.store.ReviewLogRecord
import io.github.hilbert0619.vocabkit.store.SyncState
import io.github.hilbert0619.vocabkit.store.VocabSettings
import io.github.hilbert0619.vocabkit.store.VocabSettingsRecord
import io.github.hilbert0619.vocabkit.store.VocabStore
import io.github.hilbert0619.vocabkit.store.WordRecord
import java.time.Instant
import java.util.UUID

// The Words tab's own database (`vocabulary.db`, iOS `Vocabulary.store`): the timetable's database is never migrated
// for it, and a problem here can't touch the timetable. Times are stored as epoch microseconds (the server's precision).

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val headword: String,
    val serverId: Long,
    val display: String,
    val phonetic: String,
    val translation: String,
    val definition: String,
    val posMix: String,
    val collins: Int,
    val oxford: Boolean,
    /** Space-separated, e.g. `cet4 cet6 ky`. */
    val tags: String,
    val bnc: Int?,
    val frq: Int?,
    val exchange: String,
) {
    fun toRecord() = WordRecord(
        serverId,
        WordEntry(
            headword = headword, display = display, phonetic = phonetic, translation = translation, definition = definition, posMix = posMix,
            collins = collins, oxford = oxford, tags = tags.split(' ').filter { it.isNotEmpty() }, bnc = bnc, frq = frq, exchange = exchange,
        ),
    )

    companion object {
        fun from(record: WordRecord) = record.entry.let { entry ->
            WordEntity(
                entry.headword, record.serverId, entry.display, entry.phonetic, entry.translation, entry.definition, entry.posMix,
                entry.collins, entry.oxford, entry.tags.joinToString(" "), entry.bnc, entry.frq, entry.exchange,
            )
        }
    }
}

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val ownerId: String?,
    val name: String,
    val detail: String,
    val source: String,
    val licence: String,
    val wordCount: Int,
    val updatedAt: Long,
    val deletedAt: Long?,
    val downloadedAt: Long?,
    val needsUpload: Boolean,
) {
    fun toRecord() = BookRecord(
        UUID.fromString(id), ownerId?.let(UUID::fromString), name, detail, source, licence, wordCount, instant(updatedAt),
        deletedAt?.let(::instant), downloadedAt?.let(::instant), needsUpload,
    )

    companion object {
        fun from(record: BookRecord) = BookEntity(
            record.id.toString(), record.ownerId?.toString(), record.name, record.detail, record.source, record.licence, record.wordCount,
            micros(record.updatedAt), record.deletedAt?.let(::micros), record.downloadedAt?.let(::micros), record.needsUpload,
        )
    }
}

@Entity(tableName = "book_entries", indices = [Index("bookId"), Index("needsUpload")])
data class BookEntryEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val position: Int,
    val headword: String,
    val wordId: Long?,
    val display: String?,
    val meaning: String?,
    val phonetic: String?,
    val pos: String?,
    val example: String?,
    val exampleTranslation: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
    val needsUpload: Boolean,
) {
    fun toRecord() = BookEntryRecord(
        UUID.fromString(id), UUID.fromString(bookId), position, headword, wordId, display, meaning, phonetic, pos, example,
        exampleTranslation, instant(updatedAt), deletedAt?.let(::instant), needsUpload,
    )

    companion object {
        fun from(record: BookEntryRecord) = BookEntryEntity(
            record.id.toString(), record.bookId.toString(), record.position, record.headword, record.wordId, record.display, record.meaning,
            record.phonetic, record.pos, record.example, record.exampleTranslation, micros(record.updatedAt), record.deletedAt?.let(::micros),
            record.needsUpload,
        )
    }
}

@Entity(tableName = "review_logs", indices = [Index("pushed")])
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val headword: String,
    val rating: Int,
    val mode: String,
    val reviewedAt: Long,
    val studyDay: Int,
    val durationMs: Int,
    /** Uploaded (or downloaded, so already on the server). */
    val pushed: Boolean,
    val receivedAt: Long?,
) {
    fun toRecord(): ReviewLogRecord? {
        val rating = Rating.of(rating) ?: return null
        val mode = ReviewMode.of(mode) ?: return null
        return ReviewLogRecord(ReviewEvent(UUID.fromString(id), headword, rating, mode, instant(reviewedAt), studyDay, durationMs), pushed, receivedAt?.let(::instant))
    }

    companion object {
        fun from(record: ReviewLogRecord) = record.event.let { event ->
            ReviewLogEntity(
                event.id.toString(), event.headword, event.rating.rawValue, event.mode.rawValue, micros(event.reviewedAt), event.studyDay,
                event.durationMs, record.pushed, record.receivedAt?.let(::micros),
            )
        }
    }
}

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val headword: String, val isFavorite: Boolean, val updatedAt: Long, val needsUpload: Boolean) {
    fun toRecord() = FavoriteRecord(headword, isFavorite, instant(updatedAt), needsUpload)

    companion object {
        fun from(record: FavoriteRecord) = FavoriteEntity(record.headword, record.isFavorite, micros(record.updatedAt), record.needsUpload)
    }
}

/** The single settings row (id 0). */
@Entity(tableName = "vocab_settings")
data class VocabSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val bookId: String?,
    val dailyNewWords: Int,
    val reviewMode: String,
    /** `yyyy-MM-dd` */
    val examDate: String?,
    val dayStartMinutes: Int,
    val desiredRetention: Double,
    val updatedAt: Long?,
    val needsUpload: Boolean,
) {
    fun toRecord() = VocabSettingsRecord(
        VocabSettings(
            bookId = bookId?.let(UUID::fromString), dailyNewWords = dailyNewWords,
            reviewMode = SessionMode.of(reviewMode) ?: SessionMode.RECOGNITION, examDate = examDate?.let(LocalDate::parse),
            dayStart = TimeOfDay(dayStartMinutes), desiredRetention = desiredRetention,
        ),
        updatedAt?.let(::instant),
        needsUpload,
    )

    companion object {
        fun from(record: VocabSettingsRecord) = record.settings.let { settings ->
            VocabSettingsEntity(
                bookId = settings.bookId?.toString(), dailyNewWords = settings.dailyNewWords, reviewMode = settings.reviewMode.rawValue,
                examDate = settings.examDate?.toString(), dayStartMinutes = settings.dayStart.minutes,
                desiredRetention = settings.desiredRetention, updatedAt = record.updatedAt?.let(::micros), needsUpload = record.needsUpload,
            )
        }
    }
}

/** The single sync bookkeeping row (id 0). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val id: Int = 0, val userId: String?, val pullCursor: Long?, val lastSyncAt: Long?) {
    fun toRecord() = SyncState(userId?.let(UUID::fromString), pullCursor?.let(::instant), lastSyncAt?.let(::instant))

    companion object {
        fun from(state: SyncState) = SyncStateEntity(0, state.userId?.toString(), state.pullCursor?.let(::micros), state.lastSyncAt?.let(::micros))
    }
}

@Dao
interface VocabDao {
    @Query("SELECT * FROM words")
    suspend fun words(): List<WordEntity>

    @Upsert
    suspend fun upsertWords(words: List<WordEntity>)

    @Query("SELECT * FROM books")
    suspend fun books(): List<BookEntity>

    @Upsert
    suspend fun upsertBooks(books: List<BookEntity>)

    @Query("SELECT * FROM book_entries WHERE bookId = :bookId ORDER BY position")
    suspend fun entries(bookId: String): List<BookEntryEntity>

    @Query("SELECT * FROM book_entries WHERE needsUpload = 1")
    suspend fun entriesToUpload(): List<BookEntryEntity>

    @Upsert
    suspend fun upsertEntries(entries: List<BookEntryEntity>)

    @Query("SELECT * FROM review_logs")
    suspend fun reviews(): List<ReviewLogEntity>

    @Upsert
    suspend fun upsertReviews(reviews: List<ReviewLogEntity>)

    @Query("UPDATE review_logs SET pushed = 1 WHERE id IN (:ids)")
    suspend fun markPushed(ids: List<String>)

    @Query("SELECT * FROM favorites")
    suspend fun favorites(): List<FavoriteEntity>

    @Upsert
    suspend fun upsertFavorites(favorites: List<FavoriteEntity>)

    @Query("SELECT * FROM vocab_settings WHERE id = 0")
    suspend fun settings(): VocabSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: VocabSettingsEntity)

    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun syncState(): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Query("DELETE FROM words") suspend fun deleteWords()
    @Query("DELETE FROM books") suspend fun deleteBooks()
    @Query("DELETE FROM book_entries") suspend fun deleteEntries()
    @Query("DELETE FROM review_logs") suspend fun deleteReviews()
    @Query("DELETE FROM favorites") suspend fun deleteFavorites()
    @Query("DELETE FROM vocab_settings") suspend fun deleteSettings()
    @Query("DELETE FROM sync_state") suspend fun deleteSyncState()

    @Transaction
    suspend fun wipe() {
        deleteWords()
        deleteBooks()
        deleteEntries()
        deleteReviews()
        deleteFavorites()
        deleteSettings()
        deleteSyncState()
    }
}

/** The Words data (`vocabulary.db`). Version 1; a change bumps the version and adds a migration (schemas in app/schemas). */
@Database(
    entities = [
        WordEntity::class, BookEntity::class, BookEntryEntity::class, ReviewLogEntity::class, FavoriteEntity::class,
        VocabSettingsEntity::class, SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun dao(): VocabDao

    companion object {
        fun open(context: Context): VocabDatabase = Room.databaseBuilder(context, VocabDatabase::class.java, "vocabulary.db").build()

        /** For tests. */
        fun inMemory(context: Context): VocabDatabase =
            Room.inMemoryDatabaseBuilder(context, VocabDatabase::class.java).allowMainThreadQueries().build()
    }
}

/** [VocabStore] on Room. */
class RoomVocabStore(private val database: VocabDatabase) : VocabStore {
    private val dao = database.dao()

    override suspend fun words() = dao.words().map { it.toRecord() }
    override suspend fun saveWords(words: List<WordRecord>) = dao.upsertWords(words.map(WordEntity::from))
    override suspend fun books() = dao.books().map { it.toRecord() }
    override suspend fun saveBooks(books: List<BookRecord>) = dao.upsertBooks(books.map(BookEntity::from))
    override suspend fun entries(bookId: UUID) = dao.entries(bookId.toString()).map { it.toRecord() }
    override suspend fun entriesToUpload() = dao.entriesToUpload().map { it.toRecord() }
    override suspend fun saveEntries(entries: List<BookEntryRecord>) = dao.upsertEntries(entries.map(BookEntryEntity::from))
    override suspend fun reviews() = dao.reviews().mapNotNull { it.toRecord() }
    override suspend fun saveReviews(reviews: List<ReviewLogRecord>) = dao.upsertReviews(reviews.map(ReviewLogEntity::from))

    override suspend fun markPushed(ids: Set<UUID>) {
        // SQLite allows 999 parameters per statement.
        for (chunk in ids.map { it.toString() }.chunked(500)) dao.markPushed(chunk)
    }

    override suspend fun favorites() = dao.favorites().map { it.toRecord() }
    override suspend fun saveFavorites(favorites: List<FavoriteRecord>) = dao.upsertFavorites(favorites.map(FavoriteEntity::from))
    override suspend fun settings() = dao.settings()?.toRecord() ?: VocabSettingsRecord()
    override suspend fun saveSettings(record: VocabSettingsRecord) = dao.upsertSettings(VocabSettingsEntity.from(record))
    override suspend fun syncState() = dao.syncState()?.toRecord() ?: SyncState()
    override suspend fun saveSyncState(state: SyncState) = dao.upsertSyncState(SyncStateEntity.from(state))
    override suspend fun wipe() = dao.wipe()
}

private fun micros(instant: Instant): Long = instant.epochSecond * 1_000_000 + instant.nano / 1_000

private fun instant(micros: Long): Instant = Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000)
