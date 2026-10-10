package io.github.hilbert0619.vocabkit

/** What the learner did with a question. */
sealed interface Answer {
    /** Recognition: the learner rated themselves. */
    data class Recognition(val rating: Rating) : Answer

    /** Multiple choice: right or wrong. */
    data class Choice(val correct: Boolean) : Answer

    data class Spelling(val result: SpellingResult) : Answer

    /** "I know this" on a new word's page: the word skips the learning steps. */
    data object AlreadyKnown : Answer

    enum class SpellingResult {
        CORRECT,

        /** Correct after showing the first letter. */
        CORRECT_WITH_HINT,
        WRONG,
        GAVE_UP,
    }
}

/** Part of a spelling comparison. */
sealed interface DiffPart {
    val text: String

    /** Typed correctly. */
    data class Same(override val text: String) : DiffPart

    /** In the word but not typed. */
    data class Missing(override val text: String) : DiffPart

    /** Typed but not in the word. */
    data class Extra(override val text: String) : DiffPart
}

/**
 * Turns answers into FSRS ratings (the table in docs/vocabulary-spec.md §7) and checks spelling. Answer time is
 * recorded but doesn't change the rating.
 */
object AnswerGrader {
    fun rating(answer: Answer): Rating = when (answer) {
        is Answer.Recognition -> answer.rating
        is Answer.Choice -> if (answer.correct) Rating.GOOD else Rating.AGAIN
        is Answer.Spelling -> when (answer.result) {
            Answer.SpellingResult.CORRECT -> Rating.GOOD
            Answer.SpellingResult.CORRECT_WITH_HINT -> Rating.HARD
            Answer.SpellingResult.WRONG, Answer.SpellingResult.GAVE_UP -> Rating.AGAIN
        }
        Answer.AlreadyKnown -> Rating.EASY
    }

    /**
     * Case, surrounding spaces, repeated spaces, curly apostrophes and full-width letters (from a Chinese keyboard)
     * don't count as mistakes.
     */
    fun isCorrectSpelling(typed: String, word: String): Boolean {
        val answer = normalize(word)
        return answer.isNotEmpty() && normalize(typed) == answer
    }

    internal fun normalize(text: String): String {
        val halfWidth = buildString {
            for (char in text) {
                // Full-width ASCII (U+FF01…U+FF5E) and the ideographic space.
                when {
                    char in '！'..'～' -> append((char.code - 0xFEE0).toChar())
                    char == '　' -> append(' ')
                    else -> append(char)
                }
            }
        }
        return Headword.key(halfWidth)
    }

    /** The first letter, then one underscore per remaining letter, keeping spaces and punctuation: `give up` → `g___ __`. */
    fun hint(word: String): String = buildString {
        var first = true
        for (char in word) {
            if (char.isLetter()) {
                append(if (first) char else '_')
                first = false
            } else {
                append(char)
            }
        }
    }

    /** Where a wrong spelling differs from the word, for showing after the answer. */
    fun diff(typed: String, word: String): List<DiffPart> {
        val a = normalize(typed)
        val b = Headword.key(word)
        // Longest common subsequence table, then walk it to build the parts.
        val table = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in a.length - 1 downTo 0) {
            for (j in b.length - 1 downTo 0) {
                table[i][j] = if (a[i] == b[j]) table[i + 1][j + 1] + 1 else maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
        val parts = mutableListOf<DiffPart>()
        fun append(char: Char, make: (String) -> DiffPart) {
            val last = parts.lastOrNull()
            val piece = make(char.toString())
            if (last != null && last::class == piece::class) parts[parts.lastIndex] = make(last.text + char) else parts += piece
        }
        var i = 0
        var j = 0
        while (i < a.length || j < b.length) {
            if (i < a.length && j < b.length && a[i] == b[j]) {
                append(a[i], DiffPart::Same)
                i++
                j++
            } else if (j < b.length && (i == a.length || table[i][j + 1] >= table[i + 1][j])) {
                append(b[j], DiffPart::Missing)
                j++
            } else {
                append(a[i], DiffPart::Extra)
                i++
            }
        }
        return parts
    }
}
