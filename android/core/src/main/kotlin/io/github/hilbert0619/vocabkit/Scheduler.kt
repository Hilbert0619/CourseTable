package io.github.hilbert0619.vocabkit

import io.github.hilbert0619.coursekit.uuidString
import java.time.Instant

/**
 * Turns answers into card states with FSRS-6 and the learning steps, following the reference scheduler (ts-fsrs
 * `BasicScheduler`, FSRS-6) except that elapsed time is counted in study days, so a review at 23:00 and one at 01:00
 * fall on the same day. Fuzz is off: the same answers always give the same schedule, which keeps replaying events
 * deterministic.
 */
class Scheduler(parameters: FSRSParameters = FSRSParameters()) {
    val fsrs = FSRS(parameters)

    val parameters: FSRSParameters get() = fsrs.parameters

    /** The card after answering [rating] at [time], which falls on study day [day]. */
    fun review(card: Card, rating: Rating, time: Instant, day: Int): Card {
        val elapsed = if (card.state == CardState.NEW) 0 else maxOf(0, day - (card.lastReviewDay ?: day))
        val next = card.copy(
            reps = card.reps + 1,
            lastReview = time,
            lastReviewDay = day,
            firstReviewDay = card.firstReviewDay ?: day,
        )
        return when (card.state) {
            CardState.NEW -> applySteps(
                next.copy(memory = fsrs.initialState(rating)), rating, CardState.NEW, card.step, CardState.LEARNING, time, day,
            )
            CardState.LEARNING, CardState.RELEARNING -> applySteps(
                next.copy(memory = fsrs.nextState(card.memory, elapsed.toDouble(), rating)), rating, card.state, card.step, card.state,
                time, day,
            )
            CardState.REVIEW -> if (rating == Rating.AGAIN) {
                val forgotten = next.copy(memory = fsrs.nextState(card.memory, elapsed.toDouble(), Rating.AGAIN))
                applySteps(forgotten, Rating.AGAIN, CardState.REVIEW, card.step, CardState.RELEARNING, time, day)
                    .let { it.copy(lapses = it.lapses + 1) }
            } else {
                // Hard < Good < Easy by at least a day each, as in the reference scheduler.
                val states = listOf(Rating.HARD, Rating.GOOD, Rating.EASY).map { fsrs.nextState(card.memory, elapsed.toDouble(), it) }
                var hard = fsrs.interval(states[0].stability)
                var good = fsrs.interval(states[1].stability)
                hard = minOf(hard, good)
                good = maxOf(good, hard + 1)
                val easy = maxOf(fsrs.interval(states[2].stability), good + 1)
                val (memory, interval) = when (rating) {
                    Rating.HARD -> states[0] to hard
                    Rating.GOOD -> states[1] to good
                    else -> states[2] to easy
                }
                graduate(next.copy(memory = memory), interval, day)
            }
        }
    }

    /** Rebuilds one word's card from its events, oldest first. */
    fun replay(events: List<ReviewEvent>, headword: String): Card =
        ordered(events).fold(Card.of(headword)) { card, event -> review(card, event.rating, event.reviewedAt, event.studyDay) }

    /** Rebuilds the cards of every word that appears in [events]. */
    fun replay(events: List<ReviewEvent>): Map<String, Card> =
        events.groupBy { it.headword }.mapValues { (headword, wordEvents) -> replay(wordEvents, headword) }

    /** Chance of recalling the word on study day [day]; `null` for words never answered. */
    fun retrievability(card: Card, day: Int): Double? {
        val memory = card.memory
        val last = card.lastReviewDay
        if (card.state == CardState.NEW || memory == null || last == null) return null
        return fsrs.retrievability(maxOf(0, day - last).toDouble(), memory.stability)
    }

    // Learning steps

    private fun applySteps(card: Card, rating: Rating, state: CardState, step: Int, target: CardState, time: Instant, day: Int): Card {
        val outcome = stepOutcome(rating, state, step)
        return if (outcome != null && outcome.first > 0) {
            card.copy(state = target, step = outcome.second, scheduledDays = 0, due = Due.Moment(time.plusSeconds(outcome.first * 60L)))
        } else {
            graduate(card, fsrs.interval(card.memory?.stability ?: 0.0), day)
        }
    }

    private fun graduate(card: Card, interval: Int, day: Int): Card =
        card.copy(state = CardState.REVIEW, step = 0, scheduledDays = interval, due = Due.Day(day + interval))

    /**
     * Minutes until the word comes back and the step it will be on, or `null` when it graduates. Mirrors ts-fsrs's
     * `BasicLearningStepsStrategy`: Again restarts the steps, Hard repeats the current step with a longer wait, Good
     * moves to the next step, Easy always graduates.
     */
    private fun stepOutcome(rating: Rating, state: CardState, step: Int): Pair<Int, Int>? {
        val steps = if (state == CardState.RELEARNING || state == CardState.REVIEW) parameters.relearningSteps else parameters.learningSteps
        if (steps.isEmpty() || step >= steps.size) return null
        if (state == CardState.REVIEW) return if (rating == Rating.AGAIN) steps[maxOf(0, step)] to 0 else null
        return when (rating) {
            Rating.AGAIN -> steps[0] to 0
            Rating.HARD -> {
                val wait = if (steps.size == 1) steps[0] * 1.5 else (steps[0] + steps[1]) / 2.0
                roundedHalfAwayFromZero(wait).toInt() to step
            }
            Rating.GOOD -> if (step + 1 < steps.size) steps[step + 1] to step + 1 else null
            Rating.EASY -> null
        }
    }

    companion object {
        /** Replay order: by time, then by id so events recorded in the same instant sort the same way on every device. */
        fun ordered(events: List<ReviewEvent>): List<ReviewEvent> =
            events.sortedWith(compareBy<ReviewEvent>({ it.reviewedAt }, { it.id.uuidString }))
    }
}
