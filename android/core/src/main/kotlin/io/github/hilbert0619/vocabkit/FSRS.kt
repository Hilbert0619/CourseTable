package io.github.hilbert0619.vocabkit

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.truncate

/** How well a word was remembered, as FSRS grades a review. */
enum class Rating(val rawValue: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
    ;

    /** Hard, Good and Easy all mean the word was recalled; only Again is a miss. */
    val isRecalled: Boolean get() = this != AGAIN

    companion object {
        fun of(rawValue: Int): Rating? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

/**
 * FSRS settings. The defaults are the published FSRS-6 weights with 90% target retention and the Anki-style learning
 * steps (1 min, 10 min) and relearning step (10 min). Values out of range are brought into range, as on iOS.
 */
class FSRSParameters(
    weights: List<Double> = DEFAULT_WEIGHTS,
    desiredRetention: Double = 0.9,
    maximumInterval: Int = 36_500,
    learningSteps: List<Int> = listOf(1, 10),
    relearningSteps: List<Int> = listOf(10),
    /** Whether same-day reviews change stability (FSRS "short-term" scheduling). */
    val enableShortTerm: Boolean = true,
) {
    /** The 21 FSRS-6 model weights. */
    val weights: List<Double> = weights.also { require(it.size == 21) { "FSRS-6 needs 21 weights" } }

    /** Probability of recall the schedule aims for when a word comes up again, `0.7..0.97`. */
    val desiredRetention: Double = desiredRetention.coerceIn(0.7, 0.97)

    /** Longest interval between reviews, in days. */
    val maximumInterval: Int = maxOf(1, maximumInterval)

    /** Minutes between the first answers to a new word before it graduates, each under a day. */
    val learningSteps: List<Int> = learningSteps.filter { it in 1 until 1440 }

    /** Minutes before a forgotten word comes back, each under a day. */
    val relearningSteps: List<Int> = relearningSteps.filter { it in 1 until 1440 }

    override fun equals(other: Any?): Boolean = other is FSRSParameters && weights == other.weights &&
        desiredRetention == other.desiredRetention && maximumInterval == other.maximumInterval &&
        learningSteps == other.learningSteps && relearningSteps == other.relearningSteps && enableShortTerm == other.enableShortTerm

    override fun hashCode(): Int = listOf(weights, desiredRetention, maximumInterval, learningSteps, relearningSteps, enableShortTerm).hashCode()

    override fun toString(): String =
        "FSRSParameters(desiredRetention=$desiredRetention, maximumInterval=$maximumInterval, learningSteps=$learningSteps, " +
            "relearningSteps=$relearningSteps, enableShortTerm=$enableShortTerm)"

    companion object {
        /** FSRS-6.0 default weights (open-spaced-repetition, matching ts-fsrs and swift-fsrs). */
        val DEFAULT_WEIGHTS: List<Double> = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133,
            0.8334, 3.0194, 0.001, 1.8722, 0.1666,
            0.796, 1.4835, 0.0614, 0.2629, 1.6483,
            0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
            0.1542,
        )
    }
}

/** What FSRS knows about one word's memory: stability (days until recall drops to 90%) and difficulty (1–10). */
data class MemoryState(val stability: Double, val difficulty: Double)

/**
 * The FSRS-6 memory model: the formulas from https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm,
 * rounded to 8 decimals at the same points as the reference implementations (ts-fsrs, swift-fsrs) so results match
 * theirs.
 */
class FSRS(val parameters: FSRSParameters = FSRSParameters()) {
    /** Exponent of the forgetting curve, `-w[20]`. */
    internal val decay: Double = -parameters.weights[20]

    /** Chosen so that recall is 90% after `stability` days. */
    internal val factor: Double = rounded8(exp(ln(0.9) / decay) - 1)

    /** Scales stability to an interval that hits the desired retention (1 at 90%). */
    internal val intervalModifier: Double = rounded8((parameters.desiredRetention.pow(1 / decay) - 1) / factor)

    private val w: List<Double> get() = parameters.weights

    /** Memory after the very first answer to a word. */
    fun initialState(rating: Rating): MemoryState = MemoryState(initialStability(rating), initialDifficulty(rating))

    /** Probability of recalling a word [elapsedDays] after its last review. */
    fun retrievability(elapsedDays: Double, stability: Double): Double =
        rounded8((1 + factor * elapsedDays / stability).pow(decay))

    /**
     * Memory after answering [rating], [elapsedDays] whole study days after the previous answer. A `null` state means
     * the word has never been answered.
     */
    fun nextState(state: MemoryState?, elapsedDays: Double, rating: Rating): MemoryState {
        if (state == null || state.stability <= 0) return initialState(rating)
        val s = maxOf(state.stability, MIN_STABILITY)
        val d = state.difficulty.coerceIn(1.0, 10.0)
        val r = retrievability(elapsedDays, s)

        var newStability = recallStability(d, s, r, rating)
        if (rating == Rating.AGAIN) {
            val (w17, w18) = if (parameters.enableShortTerm) w[17] to w[18] else 0.0 to 0.0
            val ceiling = forgetStability(d, s, r)
            newStability = minOf(maxOf(s / exp(w17 * w18), MIN_STABILITY), ceiling)
        }
        if (elapsedDays == 0.0 && parameters.enableShortTerm) {
            newStability = shortTermStability(s, rating)
        }
        return MemoryState(newStability, nextDifficulty(d, rating))
    }

    /** Days until the next review for a word with this stability. */
    fun interval(stability: Double): Int {
        val days = roundedHalfAwayFromZero(stability * intervalModifier)
        return minOf(maxOf(1.0, days), parameters.maximumInterval.toDouble()).toInt()
    }

    internal fun initialStability(rating: Rating): Double = maxOf(w[rating.rawValue - 1], 0.1)

    internal fun initialDifficulty(rating: Rating): Double = clampDifficulty(rawInitialDifficulty(rating))

    internal fun rawInitialDifficulty(rating: Rating): Double = rounded8(w[4] - exp((rating.rawValue - 1) * w[5]) + 1)

    internal fun nextDifficulty(d: Double, rating: Rating): Double {
        val delta = -w[6] * (rating.rawValue - 3)
        val damped = d + rounded8(delta * (10 - d) / 9)
        val reverted = rounded8(w[7] * rawInitialDifficulty(Rating.EASY) + (1 - w[7]) * damped)
        return clampDifficulty(reverted)
    }

    internal fun recallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
        val growth = exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus
        return clampStability(s * (1 + growth))
    }

    internal fun forgetStability(d: Double, s: Double, r: Double): Double =
        clampStability(w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14]))

    internal fun shortTermStability(s: Double, rating: Rating): Double {
        var increase = s.pow(-w[19]) * exp(w[17] * (rating.rawValue - 3 + w[18]))
        if (rating >= Rating.HARD) increase = maxOf(increase, 1.0)
        return clampStability(s * increase)
    }

    private fun clampDifficulty(d: Double): Double = rounded8(d).coerceIn(1.0, 10.0)

    private fun clampStability(s: Double): Double = rounded8(s.coerceIn(MIN_STABILITY, MAX_STABILITY))

    companion object {
        internal const val MIN_STABILITY = 0.001
        internal const val MAX_STABILITY = 36_500.0
    }
}

/** Rounds to 8 decimal places, as the reference implementations do between steps. */
internal fun rounded8(value: Double): Double = roundedHalfAwayFromZero(value * 100_000_000) / 100_000_000

/** Swift's `rounded()`: to the nearest whole number, halves away from zero (`Math.round` takes −2.5 to −2). */
internal fun roundedHalfAwayFromZero(value: Double): Double {
    val whole = truncate(value)
    return if (abs(value - whole) >= 0.5) whole + sign(value) else whole
}
