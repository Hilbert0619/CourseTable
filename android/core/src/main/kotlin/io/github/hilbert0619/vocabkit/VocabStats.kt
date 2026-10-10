package io.github.hilbert0619.vocabkit

/** One study day's numbers. */
data class DayStats(
    val day: Int,
    /** Words answered for the first time. */
    val newWords: Int = 0,
    /** Words learned earlier that were answered. */
    val reviewedWords: Int = 0,
    val answers: Int = 0,
    /** Answers that weren't Again. */
    val correct: Int = 0,
    val durationMs: Int = 0,
) {
    val accuracy: Double? get() = if (answers == 0) null else correct.toDouble() / answers
    val studied: Boolean get() = answers > 0
}

/** Review status of learned words. */
data class StatusBuckets(
    /** Due today (including learning steps). */
    val needsReview: Int = 0,
    /** Due within the next [VocabStats.APPROACHING_DAYS] days. */
    val approaching: Int = 0,
    /** Due later than that. */
    val stable: Int = 0,
)

/** A book's progress. */
data class BookProgress(
    val total: Int,
    /** Answered at least once. */
    val learned: Int,
    /** Stability of at least [VocabStats.MASTERED_STABILITY] days. */
    val mastered: Int,
    /** Learned and due today. */
    val due: Int,
)

/** Consecutive study days up to today, and whether today is one of them. */
data class Streak(val days: Int, val includesToday: Boolean)

/**
 * Statistics from the stored answers and cards only. Every threshold here is a display rule, shown in the app next to
 * the number it produces; none of them affects scheduling.
 */
object VocabStats {
    /** A word counts as mastered once FSRS expects it to stay above 90% recall for this many days. */
    const val MASTERED_STABILITY = 30.0
    const val APPROACHING_DAYS = 3

    /** A word forgotten this many times after learning it is a "hard word". */
    const val HARD_WORD_LAPSES = 2

    /** Numbers for every study day with answers. */
    fun days(events: List<ReviewEvent>): Map<Int, DayStats> {
        val firstDay = events.groupBy { it.headword }.mapValues { (_, wordEvents) -> wordEvents.minOf { it.studyDay } }
        val result = mutableMapOf<Int, DayStats>()
        val wordsOnDay = mutableMapOf<Int, MutableSet<String>>()
        for (event in events) {
            var stats = result[event.studyDay] ?: DayStats(event.studyDay)
            stats = stats.copy(
                answers = stats.answers + 1,
                correct = stats.correct + if (event.rating.isRecalled) 1 else 0,
                durationMs = stats.durationMs + event.durationMs,
            )
            if (wordsOnDay.getOrPut(event.studyDay) { mutableSetOf() }.add(event.headword)) {
                stats = if (firstDay[event.headword] == event.studyDay) {
                    stats.copy(newWords = stats.newWords + 1)
                } else {
                    stats.copy(reviewedWords = stats.reviewedWords + 1)
                }
            }
            result[event.studyDay] = stats
        }
        return result
    }

    /** The [count] days ending on [today], oldest first, with empty days included. */
    fun series(events: List<ReviewEvent>, today: Int, count: Int = 30): List<DayStats> {
        val byDay = days(events)
        return (maxOf(0, count) - 1 downTo 0).map { offset ->
            val day = today - offset
            byDay[day] ?: DayStats(day)
        }
    }

    /**
     * Consecutive study days up to today. Before the first answer of the day the streak still counts up to yesterday,
     * so it only breaks once a whole day is missed.
     */
    fun streak(events: List<ReviewEvent>, today: Int): Streak {
        val studied = events.map { it.studyDay }.toSet()
        val includesToday = today in studied
        var day = if (includesToday) today else today - 1
        var length = 0
        while (day in studied) {
            length++
            day--
        }
        return Streak(length, includesToday)
    }

    fun buckets(cards: Iterable<Card>, today: Int): StatusBuckets {
        var needsReview = 0
        var approaching = 0
        var stable = 0
        for (card in cards) {
            val dueDay = card.dueDay(today) ?: continue
            when {
                dueDay <= today -> needsReview++
                dueDay <= today + APPROACHING_DAYS -> approaching++
                else -> stable++
            }
        }
        return StatusBuckets(needsReview, approaching, stable)
    }

    fun progress(book: List<String>, cards: Map<String, Card>, today: Int): BookProgress {
        val keys = book.map(Headword::key).toSet()
        val learned = keys.mapNotNull { cards[it] }.filter { it.state != CardState.NEW }
        return BookProgress(
            total = keys.size,
            learned = learned.size,
            mastered = learned.count { (it.memory?.stability ?: 0.0) >= MASTERED_STABILITY },
            due = learned.count { (it.dueDay(today) ?: Int.MAX_VALUE) <= today },
        )
    }

    /** Most-forgotten first. */
    fun hardWords(cards: Iterable<Card>): List<Card> =
        cards.filter { it.lapses >= HARD_WORD_LAPSES }.sortedWith(compareByDescending<Card> { it.lapses }.thenBy { it.headword })

    /** Learned words that will be due by study day [day] if nothing is reviewed before then, e.g. tomorrow's reviews. */
    fun dueCount(cards: Iterable<Card>, day: Int, today: Int): Int = cards.count { (it.dueDay(today) ?: Int.MAX_VALUE) <= day }
}
