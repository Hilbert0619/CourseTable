package io.github.hilbert0619.vocabkit.store

import io.github.hilbert0619.vocabkit.Card
import io.github.hilbert0619.vocabkit.CardState
import io.github.hilbert0619.vocabkit.ChoicePool
import io.github.hilbert0619.vocabkit.DailyPlan
import io.github.hilbert0619.vocabkit.ExamCountdown
import io.github.hilbert0619.vocabkit.FSRSParameters
import io.github.hilbert0619.vocabkit.Headword
import io.github.hilbert0619.vocabkit.ImportedWord
import io.github.hilbert0619.vocabkit.ReviewEvent
import io.github.hilbert0619.vocabkit.Scheduler
import io.github.hilbert0619.vocabkit.StudyClock
import io.github.hilbert0619.vocabkit.SyncMerge
import io.github.hilbert0619.vocabkit.VocabStats
import io.github.hilbert0619.vocabkit.WordEntry
import io.github.hilbert0619.vocabkit.WordSearch
import io.github.hilbert0619.vocabkit.backend.RemoteReview
import java.text.Collator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What the Words tab shows, loaded from the store (iOS `VocabModel`'s properties). Values derived from it (the
 * scheduler, search, the choice pool…) are worked out once per state.
 */
data class VocabState(
    /** Dictionary entries by word key. */
    val words: Map<String, WordEntry> = emptyMap(),
    /** Books that aren't deleted: system books first, then the learner's own, by name. */
    val books: List<BookRecord> = emptyList(),
    val settings: VocabSettings = VocabSettings.DEFAULTS,
    val events: List<ReviewEvent> = emptyList(),
    val cards: Map<String, Card> = emptyMap(),
    val favorites: Set<String> = emptySet(),
    /** The current book's words in study order. */
    val bookWords: List<String> = emptyList(),
    /** The current book's entries: dictionary entries with the learner's own meanings on top. */
    val bookEntries: Map<String, WordEntry> = emptyMap(),
    /** Answers recorded on this device and not uploaded yet. */
    val unsentCount: Int = 0,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** The chosen book, or the first system book. */
    val currentBook: BookRecord? get() = books.firstOrNull { it.id == settings.bookId } ?: books.firstOrNull { it.isSystem } ?: books.firstOrNull()

    val clock: StudyClock by lazy { StudyClock(zone, settings.dayStart) }

    val scheduler: Scheduler by lazy { Scheduler(FSRSParameters(desiredRetention = settings.desiredRetention)) }

    fun today(now: Instant): Int = clock.day(now)

    fun entry(headword: String): WordEntry? {
        val key = Headword.key(headword)
        return bookEntries[key] ?: words[key]
    }

    fun plan(now: Instant): DailyPlan = DailyPlan(cards, events, bookWords, settings.dailyNewWords, now, clock)

    fun examCountdown(now: Instant): ExamCountdown? {
        val examDate = settings.examDate ?: return null
        val unseen = bookWords.count { (cards[it]?.state ?: CardState.NEW) == CardState.NEW }
        return ExamCountdown.of(examDate, clock.day(now), unseen)
    }

    /** Searches every word on the device: the current book and all downloaded dictionary words. */
    val search: WordSearch by lazy { WordSearch((words + bookEntries).values.toList()) }

    /** Wrong options for multiple choice come from the current book (or every word if it's tiny). */
    val choicePool: ChoicePool by lazy {
        ChoicePool(if (bookEntries.size >= 8) bookEntries.values.toList() else words.values.toList() + bookEntries.values)
    }

    val hardWords: List<Card> by lazy { VocabStats.hardWords(cards.values) }
}

/**
 * The Words tab's data and every change to it (iOS `VocabModel`): answers, downloads merged in, favourites, settings
 * and the learner's own books. Scheduling, plans and statistics come from vocabkit. Changes are saved at once.
 */
class VocabModel(
    val store: VocabStore,
    private val clock: Clock = Clock.systemDefaultZone(),
    /** Replaying answers can take a moment with years of them: off the main thread. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(VocabState(zone = clock.zone))
    val state: StateFlow<VocabState> = _state.asStateFlow()

    /** Now, to the millisecond (the server keeps microseconds; comparing edit times must not depend on nanoseconds). */
    fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)

    // Loading

    suspend fun load() = mutex.withLock { reload() }

    private suspend fun reload() {
        val words = store.words().associate { it.headword to it.entry }
        val collator = Collator.getInstance()
        val books = store.books().filter { it.deletedAt == null }
            .sortedWith(compareBy<BookRecord> { !it.isSystem }.thenComparator { a, b -> collator.compare(a.name, b.name) })
        val settings = store.settings().settings
        val records = store.reviews()
        val events = records.map { it.event }
        val favorites = store.favorites().filter { it.isFavorite }.map { it.headword }.toSet()
        val base = VocabState(
            words = words, books = books, settings = settings, events = events, favorites = favorites,
            unsentCount = records.count { !it.pushed }, zone = clock.zone,
        )
        val cards = withContext(dispatcher) { base.scheduler.replay(events) }
        _state.value = withBook(base.copy(cards = cards))
    }

    /** [state] with the current book's words and entries. */
    private suspend fun withBook(state: VocabState): VocabState {
        val book = state.currentBook ?: return state.copy(bookWords = emptyList(), bookEntries = emptyMap())
        val entries = store.entries(book.id).filter { it.deletedAt == null }
        val bookEntries = linkedMapOf<String, WordEntry>()
        for (entry in entries) bookEntries.getOrPut(entry.headword) { entry.entry(state.words[entry.headword]) }
        return state.copy(bookWords = entries.map { it.headword }, bookEntries = bookEntries)
    }

    // Changes

    /** Stores an answer given in a study session and the card it produced. */
    suspend fun record(event: ReviewEvent, card: Card) = mutex.withLock {
        store.saveReviews(listOf(ReviewLogRecord(event)))
        val state = _state.value
        _state.value = state.copy(events = state.events + event, cards = state.cards + (event.headword to card), unsentCount = state.unsentCount + 1)
    }

    /** Adds answers downloaded from the server and rebuilds the cards of the words they touch. */
    suspend fun merge(downloaded: List<RemoteReview>) = mutex.withLock {
        val state = _state.value
        val known = state.events.map { it.id }.toMutableSet()
        val added = mutableListOf<ReviewEvent>()
        val records = mutableListOf<ReviewLogRecord>()
        for (row in downloaded) {
            if (row.id in known) continue
            val event = row.event ?: continue
            known += row.id
            added += event
            records += ReviewLogRecord(event, pushed = true, receivedAt = row.receivedAt)
        }
        if (added.isEmpty()) return@withLock
        store.saveReviews(records)
        val events = state.events + added
        val rebuilt = withContext(dispatcher) { SyncMerge.rebuiltCards(events, added, state.scheduler) }
        _state.value = state.copy(events = events, cards = state.cards + rebuilt)
    }

    suspend fun markSent(ids: Set<UUID>) = mutex.withLock {
        store.markPushed(ids)
        _state.value = _state.value.copy(unsentCount = store.reviews().count { !it.pushed })
    }

    suspend fun setFavorite(headword: String, on: Boolean) = mutex.withLock {
        val key = Headword.key(headword)
        store.saveFavorites(listOf(FavoriteRecord(key, on, now(), needsUpload = true)))
        val state = _state.value
        _state.value = state.copy(favorites = if (on) state.favorites + key else state.favorites - key)
    }

    suspend fun updateSettings(change: (VocabSettings) -> VocabSettings) = mutex.withLock {
        val record = store.settings()
        val value = change(record.settings)
        if (value == record.settings) return@withLock
        store.saveSettings(VocabSettingsRecord(value, updatedAt = now(), needsUpload = true))
        val state = _state.value
        var next = state.copy(settings = value)
        if (value.desiredRetention != record.settings.desiredRetention) {
            next = next.copy(cards = withContext(dispatcher) { next.scheduler.replay(next.events) })
        }
        if (value.bookId != record.settings.bookId) next = withBook(next)
        _state.value = next
    }

    // Own books

    /** Creates a book from an imported list; words the dictionary knows link to it. */
    suspend fun importBook(name: String, words: List<ImportedWord>, owner: UUID): BookRecord = mutex.withLock {
        val now = now()
        val book = BookRecord.own(name, owner, words.size, now)
        store.saveBooks(listOf(book))
        val serverIds = store.words().associate { it.headword to it.serverId }
        store.saveEntries(words.mapIndexed { index, word -> BookEntryRecord.imported(word, book.id, index + 1, serverIds[word.headword], now) })
        reload()
        book
    }

    suspend fun renameBook(book: BookRecord, name: String) = mutex.withLock {
        val trimmed = name.trim()
        if (book.isSystem || trimmed.isEmpty() || trimmed == book.name) return@withLock
        store.saveBooks(listOf(book.copy(name = trimmed.take(100), updatedAt = now(), needsUpload = true)))
        reload()
    }

    suspend fun deleteBook(book: BookRecord) = mutex.withLock {
        if (book.isSystem) return@withLock
        val now = now()
        store.saveBooks(listOf(book.copy(deletedAt = now, updatedAt = now, needsUpload = true)))
        val settings = store.settings()
        if (settings.settings.bookId == book.id) {
            store.saveSettings(settings.copy(settings = settings.settings.copy(bookId = null), updatedAt = now, needsUpload = true))
        }
        reload()
    }

    /** Everything on this device, for signing out. */
    suspend fun wipe() = mutex.withLock {
        store.wipe()
        reload()
    }

    /** Reloads after a sync step changed the store. */
    internal suspend fun refresh() = mutex.withLock { reload() }
}
