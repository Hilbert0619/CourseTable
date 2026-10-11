package io.github.hilbert0619.vocabkit.store

import io.github.hilbert0619.vocabkit.SyncMerge
import io.github.hilbert0619.vocabkit.Versioned
import io.github.hilbert0619.vocabkit.backend.Account
import io.github.hilbert0619.vocabkit.backend.RemoteEntry
import io.github.hilbert0619.vocabkit.backend.RemoteFavorite
import io.github.hilbert0619.vocabkit.backend.RemoteReview
import io.github.hilbert0619.vocabkit.backend.VocabBackend
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Keeps the vocabulary store and the server in step (docs/vocabulary-spec.md §8, iOS `SyncEngine`). Everything works
 * offline; a sync uploads what changed here and downloads what changed elsewhere: own books → book list → the current
 * book's words → answers up → answers down → favourites → settings.
 */
class SyncEngine(val backend: VocabBackend, val model: VocabModel) {
    sealed interface Phase {
        data object Idle : Phase
        data object Syncing : Phase

        /** The last sync stopped here; the app words [cause] (offline, or the server's message). */
        data class Failed(val cause: Throwable) : Phase
    }

    /** Words downloaded so far while fetching a book, and the book's size. */
    data class Download(val done: Int, val total: Int)

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()
    private val _lastSync = MutableStateFlow<Instant?>(null)
    val lastSync: StateFlow<Instant?> = _lastSync.asStateFlow()
    private val _download = MutableStateFlow<Download?>(null)
    val download: StateFlow<Download?> = _download.asStateFlow()

    private val running = Mutex()
    private val store: VocabStore get() = model.store

    /** Reads when the last sync was. */
    suspend fun loadLastSync() {
        _lastSync.value = store.syncState().lastSyncAt
    }

    /** Syncs for [account], first clearing the store if it holds another account's data. Does nothing if one is running. */
    suspend fun sync(account: Account) {
        if (!running.tryLock()) return
        _phase.value = Phase.Syncing
        try {
            val owner = store.syncState().userId
            if (owner != null && owner != account.id) model.wipe()
            store.saveSyncState(store.syncState().copy(userId = account.id))

            pushBooks()
            pullBooks()
            downloadCurrentBook()
            pushReviews()
            pullReviews()
            syncFavorites()
            syncSettings()

            val now = model.now()
            store.saveSyncState(store.syncState().copy(lastSyncAt = now))
            _lastSync.value = now
            _phase.value = Phase.Idle
        } catch (error: CancellationException) {
            _phase.value = Phase.Idle
            throw error
        } catch (error: Exception) {
            _download.value = null
            _phase.value = Phase.Failed(error)
        } finally {
            model.refresh()
            running.unlock()
        }
    }

    // Books

    /** The learner's own books and their words; books first, so every entry's book exists on the server. */
    private suspend fun pushBooks() {
        val books = store.books().filter { it.needsUpload }
        backend.saveBooks(books.map { it.remote })
        store.saveBooks(books.map { it.copy(needsUpload = false) })
        for (batch in SyncMerge.batches(store.entriesToUpload())) {
            backend.saveEntries(batch.map { it.remote })
            store.saveEntries(batch.map { it.copy(needsUpload = false) })
        }
    }

    private suspend fun pullBooks() {
        val remote = backend.books()
        val local = store.books().associateBy { it.id }
        val changed = remote.mapNotNull { book ->
            val existing = local[book.id]
            when {
                existing == null -> BookRecord.of(book)
                !existing.needsUpload && book.updatedAt >= existing.updatedAt -> existing.updated(book)
                else -> null
            }
        }
        store.saveBooks(changed)
        model.refresh()
    }

    private suspend fun downloadCurrentBook() {
        val book = model.state.value.currentBook ?: return
        if (book.downloadedAt != null || book.deletedAt != null) return
        _download.value = Download(0, book.wordCount)
        val fetched = mutableListOf<RemoteEntry>()
        while (true) {
            val page = backend.entries(book.id, fetched.size, fetched.size + PAGE_SIZE - 1)
            fetched += page
            _download.value = Download(fetched.size, maxOf(book.wordCount, fetched.size))
            if (page.size < PAGE_SIZE) break
        }
        store.saveWords(fetched.mapNotNull { remote -> remote.word?.let { WordRecord(it.id, it.entry) } })
        val existing = store.entries(book.id).associateBy { it.id }
        store.saveEntries(fetched.mapNotNull { remote ->
            val entry = existing[remote.id]
            when {
                entry == null -> BookEntryRecord.of(remote)
                !entry.needsUpload -> entry.updated(remote)
                else -> null
            }
        })
        store.saveBooks(listOf(book.copy(downloadedAt = model.now())))
        _download.value = null
        model.refresh()
    }

    // Answers

    private suspend fun pushReviews() {
        val unsent = store.reviews().filter { !it.pushed }.map { it.event }
        for (batch in SyncMerge.batches(unsent)) {
            backend.uploadReviews(batch.map(RemoteReview::of))
            model.markSent(batch.map { it.id }.toSet())
        }
    }

    private suspend fun pullReviews() {
        val cursor = store.syncState().pullCursor
        val start = SyncMerge.pullStart(cursor)
        var offset = 0
        val received = mutableListOf<Instant>()
        while (true) {
            val page = backend.reviews(start, offset, offset + PAGE_SIZE - 1)
            model.merge(page)
            received += page.mapNotNull { it.receivedAt }
            offset += page.size
            if (page.size < PAGE_SIZE) break
        }
        store.saveSyncState(store.syncState().copy(pullCursor = SyncMerge.advance(cursor, received)))
    }

    // Favourites and settings (newest edit wins)

    private suspend fun syncFavorites() {
        val remote = backend.favorites().associateBy { it.headword }
        val local = store.favorites().associateBy { it.headword }
        val upload = mutableListOf<RemoteFavorite>()
        val taken = mutableListOf<FavoriteRecord>()
        for (word in remote.keys + local.keys) {
            val mine = local[word]?.let { Versioned(it.isFavorite, it.updatedAt) }
            val theirs = remote[word]?.let { Versioned(it.isFavorite, it.updatedAt) }
            when (SyncMerge.resolve(mine, theirs)) {
                SyncMerge.Resolution.PUSH_LOCAL -> local[word]?.let { upload += it.remote }
                SyncMerge.Resolution.TAKE_REMOTE -> remote[word]?.let { taken += FavoriteRecord(word, it.isFavorite, it.updatedAt, needsUpload = false) }
                SyncMerge.Resolution.UNCHANGED -> Unit
            }
        }
        backend.saveFavorites(upload)
        val takenWords = taken.map { it.headword }.toSet()
        store.saveFavorites(taken + local.values.filter { it.headword !in takenWords }.map { it.copy(needsUpload = false) })
    }

    private suspend fun syncSettings() {
        val record = store.settings()
        val remote = backend.settings()
        val mine = record.updatedAt?.let { Versioned(record.remote, it) }
        when (SyncMerge.resolve(mine, remote?.let { Versioned(it, it.updatedAt) })) {
            SyncMerge.Resolution.PUSH_LOCAL -> {
                backend.saveSettings(record.remote)
                store.saveSettings(record.copy(needsUpload = false))
            }
            SyncMerge.Resolution.TAKE_REMOTE -> if (remote != null) store.saveSettings(VocabSettingsRecord.of(remote))
            SyncMerge.Resolution.UNCHANGED -> store.saveSettings(record.copy(needsUpload = false))
        }
    }

    companion object {
        const val PAGE_SIZE = 1000
    }
}
