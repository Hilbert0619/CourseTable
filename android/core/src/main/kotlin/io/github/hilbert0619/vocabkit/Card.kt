package io.github.hilbert0619.vocabkit

import java.time.Instant
import java.util.UUID

/** Where a word is in the learning cycle (the FSRS card states). */
enum class CardState(val rawValue: Int) {
    /** Never answered. */
    NEW(0),

    /** Being learned today: comes back after minutes (learning steps). */
    LEARNING(1),

    /** Graduated: comes back after days. */
    REVIEW(2),

    /** Forgotten in a review: comes back after minutes, then graduates again. */
    RELEARNING(3),
}

/**
 * How a question is asked. The word has one card however it is asked; the mode only changes the question and how the
 * answer turns into a rating (see [AnswerGrader]).
 */
enum class ReviewMode(val rawValue: String) {
    /** See the word, recall the meaning, then rate yourself. */
    RECOGNITION("recognition"),

    /** Pick the meaning out of four. */
    CHOICE("choice"),

    /** Hear the word and see the meaning, then type it. */
    SPELLING("spelling"),
    ;

    companion object {
        fun of(rawValue: String): ReviewMode? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

/** When a word is due. Words in learning steps are due at a moment; graduated words on a study day. */
sealed interface Due {
    data class Moment(val instant: Instant) : Due

    /** A study day number (see [StudyClock]). */
    data class Day(val day: Int) : Due
}

/**
 * One answer to one word: the record that is stored, synced, and replayed to rebuild every card. The headword is
 * turned into its key ([Headword.key]) and a negative duration into 0, as on iOS.
 */
class ReviewEvent(
    /** Made on the device, so uploading the same event twice is harmless. */
    val id: UUID = UUID.randomUUID(),
    headword: String,
    val rating: Rating,
    val mode: ReviewMode,
    val reviewedAt: Instant,
    /** The study day the answer counts for, fixed when it was given (see [StudyClock]). */
    val studyDay: Int,
    /** Time from showing the question to answering it. */
    durationMs: Int = 0,
) {
    val headword: String = Headword.key(headword)
    val durationMs: Int = maxOf(0, durationMs)

    override fun equals(other: Any?): Boolean = other is ReviewEvent && id == other.id && headword == other.headword &&
        rating == other.rating && mode == other.mode && reviewedAt == other.reviewedAt && studyDay == other.studyDay &&
        durationMs == other.durationMs

    override fun hashCode(): Int = listOf(id, headword, rating, mode, reviewedAt, studyDay, durationMs).hashCode()

    override fun toString(): String = "ReviewEvent($headword, $rating, $mode, $reviewedAt, day $studyDay, ${durationMs}ms, $id)"
}

/**
 * A user's learning state for one word. It is derived: replaying the word's [ReviewEvent]s through the [Scheduler]
 * always gives the same card. [headword] is a word key ([Headword.key]); [Card.of] makes one from any spelling.
 */
data class Card(
    val headword: String,
    val state: CardState = CardState.NEW,
    /** `null` until the first answer. */
    val memory: MemoryState? = null,
    /** Index into the learning or relearning steps. */
    val step: Int = 0,
    val reps: Int = 0,
    /** Times the word was forgotten after graduating. */
    val lapses: Int = 0,
    /** Days between the last review and the due day (0 in learning steps). */
    val scheduledDays: Int = 0,
    /** `null` for new words. */
    val due: Due? = null,
    val lastReview: Instant? = null,
    val lastReviewDay: Int? = null,
    /** The study day of the first answer: the day the word was learned. */
    val firstReviewDay: Int? = null,
) {
    /**
     * Whether the word should be shown now: learning words once their moment has come, graduated words from their due
     * day on. New words are never "due".
     */
    fun isDue(at: Instant, today: Int): Boolean = when (val due = due) {
        is Due.Moment -> due.instant <= at
        is Due.Day -> due.day <= today
        null -> false
    }

    /** Whether the word needs to be answered at some point during study day [today]. */
    fun isDue(on: Int, dayEnd: Instant): Boolean = when (val due = due) {
        is Due.Moment -> due.instant < dayEnd
        is Due.Day -> due.day <= on
        null -> false
    }

    /** Study day the word is due, if it has been learned. Learning-step words count as due today. */
    fun dueDay(today: Int): Int? = when (val due = due) {
        is Due.Moment -> today
        is Due.Day -> due.day
        null -> null
    }

    companion object {
        /** A new card for [word] in any spelling (iOS `Card(headword:)`). */
        fun of(word: String): Card = Card(Headword.key(word))
    }
}

/** Word keys: the same word typed or listed in different ways maps to one key. */
object Headword {
    /** Lower-case, trimmed, single spaces, straight apostrophes: `" Don’t  Mind "` → `"don't mind"`. */
    fun key(word: String): String = word.replace('’', '\'').replace('‘', '\'')
        .lowercase()
        .splitWhere(isSeparator = ::isSwiftWhitespace)
        .joinToString(" ")
}
