package io.github.hilbert0619.vocabkit

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A value with the time it was last changed, for "newest edit wins" syncing (settings, favourites, own books). */
data class Versioned<T>(val value: T, val updatedAt: Instant)

/**
 * The sync rules that don't need the network (docs/vocabulary-spec.md §8). Answers are never edited, so merging them
 * is taking the union by id and replaying the affected words.
 */
object SyncMerge {
    /** Pulls start this far before the cursor, so rows that were committed late aren't missed. */
    val PULL_OVERLAP: Duration = Duration.ofMinutes(10)
    const val PUSH_BATCH_SIZE = 500

    /** Downloaded events the device doesn't have yet. */
    fun newEvents(incoming: List<ReviewEvent>, knownIds: Set<UUID>): List<ReviewEvent> {
        val seen = knownIds.toMutableSet()
        return incoming.filter { seen.add(it.id) }
    }

    /** Words whose cards must be rebuilt after receiving [events]. */
    fun affectedWords(events: List<ReviewEvent>): Set<String> = events.map { it.headword }.toSet()

    /** Rebuilds the cards of the words touched by [newEvents], from all of those words' events. */
    fun rebuiltCards(allEvents: List<ReviewEvent>, newEvents: List<ReviewEvent>, scheduler: Scheduler): Map<String, Card> {
        val words = affectedWords(newEvents)
        return scheduler.replay(allEvents.filter { it.headword in words })
    }

    /** Where the next pull starts: `received_at > start` (`null`: from the beginning). */
    fun pullStart(cursor: Instant?): Instant? = cursor?.minus(PULL_OVERLAP)

    /** The cursor after a pull: the newest server time seen. */
    fun advance(cursor: Instant?, receivedAt: List<Instant>): Instant? = (listOfNotNull(cursor) + receivedAt).maxOrNull()

    enum class Resolution {
        UNCHANGED,

        /** Upload the device's value. */
        PUSH_LOCAL,

        /** Keep the server's value. */
        TAKE_REMOTE,
    }

    /** Newest edit wins; on a tie nothing changes. */
    fun <T> resolve(local: Versioned<T>?, remote: Versioned<T>?): Resolution = when {
        local == null && remote == null -> Resolution.UNCHANGED
        remote == null -> Resolution.PUSH_LOCAL
        local == null -> Resolution.TAKE_REMOTE
        local.updatedAt > remote.updatedAt -> Resolution.PUSH_LOCAL
        remote.updatedAt > local.updatedAt -> Resolution.TAKE_REMOTE
        else -> Resolution.UNCHANGED
    }

    /** Splits uploads into batches of [size]. */
    fun <T> batches(items: List<T>, size: Int = PUSH_BATCH_SIZE): List<List<T>> = items.chunked(maxOf(1, size))
}
