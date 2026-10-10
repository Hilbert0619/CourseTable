package io.github.hilbert0619.vocabkit

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Which question type a session uses for learning steps and reviews. */
enum class SessionMode(val rawValue: String) {
    RECOGNITION("recognition"),
    CHOICE("choice"),
    SPELLING("spelling"),

    /** A different type per question, picked by the session seed. */
    MIXED("mixed"),
    ;

    companion object {
        fun of(rawValue: String): SessionMode? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

/** One question in a session. */
data class StudyItem(val headword: String, val kind: Kind, val mode: ReviewMode) {
    enum class Kind {
        /** First time: show the word page, then a multiple-choice check. */
        NEW,

        /** A learning or relearning step. */
        LEARNING,

        /** A graduated word due today. */
        REVIEW,
    }
}

/**
 * A study session. Order: learning steps that are due, then due reviews (shuffled), then new words in book order. When
 * nothing else is left, learning steps due within [LEARN_AHEAD] are shown early. Words that come back later the same
 * day (learning steps) are answered again in the session.
 *
 * @param cards all of the learner's cards.
 * @param newWords words to introduce, in order (usually [DailyPlan.newWords]).
 * @param reviewLimit at most this many due reviews (`null`: all of them).
 */
class StudySession(
    cards: Map<String, Card>,
    newWords: List<String>,
    now: Instant,
    val scheduler: Scheduler,
    val clock: StudyClock,
    val mode: SessionMode,
    val seed: ULong,
    reviewLimit: Int? = null,
) {
    private val _cards = cards.toMutableMap()
    private val _events = mutableListOf<ReviewEvent>()

    /** The session's cards, updated after each answer. */
    val cards: Map<String, Card> get() = _cards

    /** Every answer given, oldest first: these are stored and synced. */
    val events: List<ReviewEvent> get() = _events

    private val reviewQueue: MutableList<String>
    private val newQueue: MutableList<String>
    private val learning: MutableSet<String>
    private val introduced = mutableSetOf<String>()
    private val reviewed = mutableSetOf<String>()

    init {
        val today = clock.day(now)
        val due = cards.values.filter { it.state == CardState.REVIEW && it.isDue(now, today) }.map { it.headword }.sorted()
        reviewQueue = due.shuffled(SeededRandom(seed)).take(maxOf(0, reviewLimit ?: Int.MAX_VALUE)).toMutableList()
        newQueue = newWords.map(Headword::key).filter { (cards[it]?.state ?: CardState.NEW) == CardState.NEW }.distinct().toMutableList()
        learning = cards.values.filter { it.state == CardState.LEARNING || it.state == CardState.RELEARNING }.map { it.headword }.toMutableSet()
    }

    /** The next question, or `null` when the session is done. */
    fun next(now: Instant): StudyItem? {
        learningWord(now)?.let { return item(it, StudyItem.Kind.LEARNING) }
        reviewQueue.firstOrNull()?.let { return item(it, StudyItem.Kind.REVIEW) }
        newQueue.firstOrNull()?.let { return item(it, StudyItem.Kind.NEW) }
        learningWord(now.plus(LEARN_AHEAD))?.let { return item(it, StudyItem.Kind.LEARNING) }
        return null
    }

    /** Records an answer to [item] and returns the event to store. */
    fun answer(item: StudyItem, rating: Rating, time: Instant, durationMs: Int, id: UUID = UUID.randomUUID()): ReviewEvent {
        val day = clock.day(time)
        val card = scheduler.review(_cards[item.headword] ?: Card.of(item.headword), rating, time, day)
        _cards[item.headword] = card
        reviewQueue.remove(item.headword)
        newQueue.remove(item.headword)
        if (card.state == CardState.LEARNING || card.state == CardState.RELEARNING) learning += item.headword else learning -= item.headword
        when (item.kind) {
            StudyItem.Kind.NEW -> introduced += item.headword
            StudyItem.Kind.REVIEW -> reviewed += item.headword
            StudyItem.Kind.LEARNING -> Unit
        }
        val event = ReviewEvent(id, item.headword, rating, item.mode, time, day, durationMs)
        _events += event
        return event
    }

    /** Questions left, not counting learning steps due later than [LEARN_AHEAD]. */
    fun remaining(now: Instant): Remaining {
        val limit = now.plus(LEARN_AHEAD)
        return Remaining(newQueue.size, reviewQueue.size, learning.count { word -> dueMoment(word)?.let { it <= limit } ?: false })
    }

    /** Learning steps that will be due after the session ends, and when the first one is. */
    fun waiting(now: Instant): Waiting {
        val limit = now.plus(LEARN_AHEAD)
        val later = learning.mapNotNull(::dueMoment).filter { it > limit }
        return Waiting(later.size, later.minOrNull())
    }

    val summary: SessionSummary
        get() = SessionSummary(
            newWords = introduced.size, reviewedWords = reviewed.size, answers = _events.size,
            correct = _events.count { it.rating.isRecalled }, durationMs = _events.sumOf { it.durationMs },
        )

    data class Remaining(val new: Int, val review: Int, val learning: Int)

    data class Waiting(val count: Int, val firstDue: Instant?)

    private fun item(headword: String, kind: StudyItem.Kind): StudyItem {
        val reps = _cards[headword]?.reps ?: 0
        val questionMode = when {
            kind == StudyItem.Kind.NEW -> ReviewMode.CHOICE
            mode == SessionMode.RECOGNITION -> ReviewMode.RECOGNITION
            mode == SessionMode.CHOICE -> ReviewMode.CHOICE
            mode == SessionMode.SPELLING -> ReviewMode.SPELLING
            else -> {
                val modes = ReviewMode.entries
                modes[(SeededRandom.seed(seed.toString(), headword, reps.toString()) % modes.size.toULong()).toInt()]
            }
        }
        return StudyItem(headword, kind, questionMode)
    }

    private fun dueMoment(headword: String): Instant? = (_cards[headword]?.due as? Due.Moment)?.instant

    private fun learningWord(dueBy: Instant): String? = learning
        .mapNotNull { word -> dueMoment(word)?.let { word to it } }
        .filter { it.second <= dueBy }
        .minWithOrNull(compareBy<Pair<String, Instant>>({ it.second }, { it.first }))
        ?.first

    companion object {
        /** How early a learning step may be shown when there is nothing else to study. */
        val LEARN_AHEAD: Duration = Duration.ofMinutes(20)
    }
}

/** The end-of-session numbers. */
data class SessionSummary(val newWords: Int, val reviewedWords: Int, val answers: Int, val correct: Int, val durationMs: Int) {
    /** Share of answers that weren't Again; `null` with no answers. */
    val accuracy: Double? get() = if (answers == 0) null else correct.toDouble() / answers
}
