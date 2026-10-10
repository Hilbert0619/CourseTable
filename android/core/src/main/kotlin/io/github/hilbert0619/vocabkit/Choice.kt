package io.github.hilbert0619.vocabkit

/** A multiple-choice question: pick the meaning of [headword]. */
data class ChoiceQuestion(
    val headword: String,
    /** Short meanings (see [WordEntry.shortMeaning]), one of them correct. */
    val options: List<String>,
    val correctIndex: Int,
) {
    fun isCorrect(index: Int): Boolean = index == correctIndex
}

/**
 * The words wrong options are drawn from (usually the book being studied), parsed once so that making a question only
 * shuffles and compares.
 */
class ChoicePool(entries: List<WordEntry>) {
    private class Candidate(entry: WordEntry) {
        val headword = entry.headword
        val text = entry.shortMeaning
        val tokens = entry.meaningTokens
        val partOfSpeech = entry.primaryPartOfSpeech
    }

    private val candidates: List<Candidate> = entries.map(::Candidate).filter { it.text.isNotEmpty() }

    val count: Int get() = candidates.size

    /**
     * A question with up to [optionCount] options. Wrong options have the same part of speech as the word where
     * possible, and never share a meaning with it or show the same text. The same seed always gives the same question,
     * on iOS and Android. `null` if there is no usable wrong option or the word has no meaning to show.
     */
    fun question(target: WordEntry, seed: ULong, optionCount: Int = 4): ChoiceQuestion? {
        val answer = Candidate(target)
        if (answer.text.isEmpty() || optionCount < 2) return null
        val random = SeededRandom(seed)
        val samePos = mutableListOf<String>()
        val others = mutableListOf<String>()
        val usedTexts = mutableSetOf(answer.text)
        val wanted = optionCount - 1
        for (index in candidates.indices.toList().shuffled(random)) {
            if (samePos.size >= wanted) break
            val candidate = candidates[index]
            if (candidate.headword == answer.headword || candidate.text in usedTexts || candidate.tokens.any { it in answer.tokens }) continue
            if (answer.partOfSpeech != null && candidate.partOfSpeech == answer.partOfSpeech) {
                samePos += candidate.text
                usedTexts += candidate.text
            } else if (others.size < wanted) {
                others += candidate.text
                usedTexts += candidate.text
            }
        }
        val wrong = (samePos + others).take(wanted)
        if (wrong.isEmpty()) return null
        val options = (listOf(answer.text) + wrong).shuffled(random)
        return ChoiceQuestion(target.headword, options, options.indexOf(answer.text))
    }
}

object ChoiceGenerator {
    /** One question from an unparsed pool; for many questions build a [ChoicePool] once instead. */
    fun question(target: WordEntry, pool: List<WordEntry>, seed: ULong, optionCount: Int = 4): ChoiceQuestion? =
        ChoicePool(pool).question(target, seed, optionCount)
}

/** SplitMix64: a small, fast generator whose sequence depends only on the seed, on every platform. */
class SeededRandom(seed: ULong) {
    private var state: ULong = seed

    fun next(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }

    /**
     * A number in `0 until upperBound`, as Swift's `RandomNumberGenerator.next(upperBound:)` makes it (Lemire's "nearly
     * divisionless" method), so shuffles match iOS.
     */
    fun next(upperBound: ULong): ULong {
        require(upperBound != 0uL) { "upperBound cannot be zero." }
        var random = next()
        var low = random * upperBound
        if (low < upperBound) {
            val threshold = (0uL - upperBound) % upperBound
            while (low < threshold) {
                random = next()
                low = random * upperBound
            }
        }
        return multiplyHigh(random, upperBound)
    }

    /** Swift `Int.random(in: 0..<bound, using:)`. */
    fun nextInt(bound: Int): Int = next(bound.toULong()).toInt()

    companion object {
        /** A seed from text, the same on every run and device: FNV-1a over UTF-8, parts joined by U+001F. */
        fun seed(vararg parts: String): ULong {
            var hash = 0xCBF29CE484222325uL
            for (byte in parts.joinToString("\u001F").encodeToByteArray()) {
                hash = hash xor byte.toUByte().toULong()
                hash *= 0x00000100000001B3uL
            }
            return hash
        }

        /** The high 64 bits of the 128-bit product. */
        private fun multiplyHigh(a: ULong, b: ULong): ULong {
            val x = a.toLong()
            val y = b.toLong()
            // Math.multiplyHigh is signed; correct it for unsigned operands.
            val high = Math.multiplyHigh(x, y) + ((x shr 63) and y) + ((y shr 63) and x)
            return high.toULong()
        }
    }
}

/** Swift's `shuffled(using:)`: the same Fisher–Yates order and draws, so a seed gives the same order as on iOS. */
fun <T> List<T>.shuffled(random: SeededRandom): List<T> {
    val result = toMutableList()
    var amount = result.size
    var current = 0
    while (amount > 1) {
        val offset = random.nextInt(amount)
        amount -= 1
        val swap = result[current]
        result[current] = result[current + offset]
        result[current + offset] = swap
        current += 1
    }
    return result
}
