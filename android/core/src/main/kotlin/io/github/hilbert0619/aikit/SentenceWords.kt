package io.github.hilbert0619.aikit

import io.github.hilbert0619.vocabkit.WordEntry

/**
 * The book's words in a pasted sentence, in order of appearance: "abandoned" finds *abandon*, "studies" finds *study*.
 * Shown under a sentence analysis so each can be opened (and favourited) with one tap.
 */
object SentenceWords {
    const val MINIMUM_LENGTH = 4
    const val MAXIMUM_COUNT = 15

    fun entries(text: String, lookup: (String) -> WordEntry?): List<WordEntry> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<WordEntry>()
        for (token in tokens(text)) {
            if (token.length < MINIMUM_LENGTH) continue
            val entry = candidates(token).firstNotNullOfOrNull(lookup) ?: continue
            if (!seen.add(entry.headword)) continue
            result += entry
            if (result.size == MAXIMUM_COUNT) break
        }
        return result
    }

    internal fun tokens(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z-]+"))
            .map { it.trim('-') }
            .filter { it.isNotEmpty() }

    /** The word itself, then the base forms it could be an inflection of. */
    internal fun candidates(word: String): List<String> {
        val forms = mutableListOf(word)
        fun add(form: String) {
            if (form.length >= 2 && form !in forms) forms += form
        }
        fun stem(suffix: String): String? = if (word.endsWith(suffix)) word.dropLast(suffix.length) else null
        stem("ies")?.let { add(it + "y") }
        stem("ied")?.let { add(it + "y") }
        stem("es")?.let { add(it) }
        stem("s")?.let { if (!word.endsWith("ss")) add(it) }
        for (suffix in listOf("ed", "ing", "er", "est", "ly")) {
            val base = stem(suffix) ?: continue
            add(base)
            add(base + "e")
            val last = base.lastOrNull()
            if (last != null && base.dropLast(1).lastOrNull() == last) add(base.dropLast(1)) // stopped → stop
            if (suffix == "ly" && base.endsWith("i")) add(base.dropLast(1) + "y") // happily → happy
        }
        return forms
    }
}
