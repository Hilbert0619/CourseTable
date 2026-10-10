package io.github.hilbert0619.vocabkit

import io.github.hilbert0619.coursekit.LocalDate
import java.time.Instant
import kotlin.math.ceil

/** Today's task: new words to learn and words to review, worked out from the cards and the history. */
class DailyPlan(
    cards: Map<String, Card>,
    history: List<ReviewEvent>,
    book: List<String>,
    newWordsPerDay: Int,
    now: Instant,
    clock: StudyClock,
) {
    val newTarget: Int = maxOf(0, newWordsPerDay)

    /** Words answered for the first time today (from any book). */
    val newDone: Int

    /** New words still to learn today, limited by what is left in the book. */
    val newRemaining: Int

    /** Words learned before today that were reviewed today. */
    val reviewsDone: Int

    /** Words learned before today that are due by the end of today. */
    val reviewsRemaining: Int

    /** Words of the book never answered. */
    val unseenInBook: Int

    /** The next new words of the book, in book order ([newRemaining] of them). */
    val newWords: List<String>

    /** Rough time for what is left, from the learner's own answer times; `null` until there are enough answers. */
    val estimatedMinutes: Int?

    init {
        val today = clock.day(now)
        val dayEnd = clock.end(today)
        val learnedBefore = cards.values.filter { (it.firstReviewDay ?: Int.MAX_VALUE) < today }

        newDone = cards.values.count { it.firstReviewDay == today }
        val unseen = book.map(Headword::key).filter { (cards[it]?.state ?: CardState.NEW) == CardState.NEW }
        unseenInBook = unseen.toSet().size
        newRemaining = maxOf(0, minOf(newTarget - newDone, unseenInBook))
        newWords = unseen.distinct().take(newRemaining)

        val learnedKeys = learnedBefore.map { it.headword }.toSet()
        reviewsDone = history.filter { it.studyDay == today && it.headword in learnedKeys }.map { it.headword }.toSet().size
        reviewsRemaining = learnedBefore.count { it.isDue(today, dayEnd) }
        estimatedMinutes = estimate(history, today, newRemaining, reviewsRemaining)
    }

    /** Done ÷ (done + remaining), 1 when there is nothing to do. */
    val progress: Double
        get() {
            val done = newDone + reviewsDone
            val total = done + newRemaining + reviewsRemaining
            return if (total == 0) 1.0 else done.toDouble() / total
        }

    val isComplete: Boolean get() = newRemaining == 0 && reviewsRemaining == 0

    companion object {
        /** Answers needed before a time estimate is shown. */
        const val ANSWERS_FOR_ESTIMATE = 50

        /**
         * Median answer time × answers expected: for new words the average number of answers on the day a word was
         * learned, for reviews the average answers per word per review day.
         */
        internal fun estimate(history: List<ReviewEvent>, today: Int, newWords: Int, reviews: Int): Int? {
            val past = history.filter { it.studyDay < today }
            if (past.size < ANSWERS_FOR_ESTIMATE) return null
            val durations = past.map { it.durationMs }.sorted()
            val median = durations[durations.size / 2] / 1000.0

            val firstDay = past.groupBy { it.headword }.mapValues { (_, events) -> events.minOf { it.studyDay } }
            var answersOnFirstDay = 0
            var answersOnReviewDays = 0
            val reviewDays = mutableSetOf<String>()
            for (event in past) {
                if (event.studyDay == firstDay[event.headword]) {
                    answersOnFirstDay++
                } else {
                    answersOnReviewDays++
                    reviewDays += "${event.headword}|${event.studyDay}"
                }
            }
            val perNewWord = if (firstDay.isEmpty()) 1.0 else answersOnFirstDay.toDouble() / firstDay.size
            val perReview = if (reviewDays.isEmpty()) 1.0 else answersOnReviewDays.toDouble() / reviewDays.size
            val seconds = median * (newWords * perNewWord + reviews * perReview)
            return ceil(seconds / 60).toInt()
        }
    }
}

/** Days left until the exam and the pace needed to finish the book. An estimate, not a promise. */
data class ExamCountdown(
    val examDate: LocalDate,
    /** Study days before the exam day, counting today. */
    val daysLeft: Int,
    val wordsLeft: Int,
    /** New words a day needed to see every word of the book before the exam. */
    val newWordsPerDayNeeded: Int,
) {
    companion object {
        /** `null` once the exam day has come. */
        fun of(examDate: LocalDate, today: Int, wordsLeft: Int): ExamCountdown? {
            val days = examDate.daysSinceEpoch - today
            if (days <= 0) return null
            return ExamCountdown(examDate, days, wordsLeft, (wordsLeft + days - 1) / days)
        }
    }
}
