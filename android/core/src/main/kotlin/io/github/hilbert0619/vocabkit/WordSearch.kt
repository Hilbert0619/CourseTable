package io.github.hilbert0619.vocabkit

import kotlin.math.abs

/**
 * Searches the words of the downloaded books on the device. English queries match the word, its inflections
 * (`abandoned` finds `abandon`), words starting with the query, and near-misses one typo away. Queries with Chinese
 * match the meanings.
 */
class WordSearch(entries: List<WordEntry>) {
    sealed interface Match {
        data object Exact : Match

        /** The query is an inflected form of this word. */
        data class Form(val form: String) : Match

        data object Prefix : Match

        /** One letter off: a typo, a missing or extra letter, or two letters swapped. */
        data object Similar : Match

        data object Meaning : Match
    }

    data class Result(val headword: String, val match: Match)

    private val entries: Map<String, WordEntry>

    /** Keys in alphabetical order, for prefix lookups. */
    private val sortedKeys: List<String>

    /** Inflected form → words it belongs to. */
    private val lemmas: Map<String, List<String>>

    init {
        val byKey = linkedMapOf<String, WordEntry>()
        val lemmas = mutableMapOf<String, MutableList<String>>()
        for (entry in entries) {
            if (entry.headword in byKey) continue
            byKey[entry.headword] = entry
            for (form in entry.forms) lemmas.getOrPut(Headword.key(form.word)) { mutableListOf() } += entry.headword
        }
        this.entries = byKey
        sortedKeys = byKey.keys.sorted()
        this.lemmas = lemmas
    }

    fun entry(headword: String): WordEntry? = entries[Headword.key(headword)]

    fun search(query: String, limit: Int = 50): List<Result> {
        val key = Headword.key(query)
        if (key.isEmpty() || limit <= 0) return emptyList()
        if (key.any { it.code > 0x7F }) return meaningResults(key).take(limit)

        val results = mutableListOf<Result>()
        val seen = mutableSetOf<String>()
        fun add(headword: String, match: Match) {
            if (seen.add(headword)) results += Result(headword, match)
        }
        if (key in entries) add(key, Match.Exact)
        for (lemma in lemmas[key].orEmpty().sorted()) add(lemma, Match.Form(key))
        val prefixed = prefixMatches(key).sortedWith(compareBy<String>({ it.length }, { rank(it) }, { it }))
        for (headword in prefixed) if (results.size < limit) add(headword, Match.Prefix)
        if (results.size < limit && key.length >= 4) {
            val similar = sortedKeys.filter { isOneEditAway(it, key) }.sortedWith(compareBy<String>({ rank(it) }, { it }))
            for (headword in similar) if (results.size < limit) add(headword, Match.Similar)
        }
        return results.take(limit)
    }

    private fun rank(headword: String): Int = entries[headword]?.frequencyRank ?: Int.MAX_VALUE

    private fun prefixMatches(prefix: String): List<String> {
        // Binary search for the first key ≥ prefix, then walk while keys still start with it.
        var low = 0
        var high = sortedKeys.size
        while (low < high) {
            val mid = (low + high) / 2
            if (sortedKeys[mid] < prefix) low = mid + 1 else high = mid
        }
        val matches = mutableListOf<String>()
        var index = low
        while (index < sortedKeys.size && sortedKeys[index].startsWith(prefix)) {
            if (sortedKeys[index] != prefix) matches += sortedKeys[index]
            index++
        }
        return matches
    }

    /**
     * Words whose meanings contain the query: a meaning that is exactly the query first, then one that starts with it,
     * then any that contain it; more frequent words first within each group.
     */
    private fun meaningResults(query: String): List<Result> {
        data class Scored(val score: Int, val rank: Int, val headword: String)

        val scored = entries.values.filter { query in it.translation }.map { entry ->
            val meanings = entry.senses.flatMap { it.meanings }.map(SenseLine::bare)
            val score = when {
                query in meanings -> 0
                meanings.any { it.startsWith(query) } -> 1
                else -> 2
            }
            Scored(score, entry.frequencyRank ?: Int.MAX_VALUE, entry.headword)
        }
        return scored.sortedWith(compareBy<Scored>({ it.score }, { it.rank }, { it.headword })).map { Result(it.headword, Match.Meaning) }
    }
}

/** True if [a] and [b] differ by one inserted, deleted or replaced character, or two neighbours swapped. */
internal fun isOneEditAway(a: String, b: String): Boolean {
    if (abs(a.length - b.length) > 1 || a == b) return false
    var i = 0
    while (i < minOf(a.length, b.length) && a[i] == b[i]) i++
    if (a.length == b.length) {
        // Replace one character, or swap it with the next.
        if (a.substring(i + 1) == b.substring(i + 1)) return true
        return i + 1 < a.length && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2)
    }
    // One extra character in the longer word at position i.
    return if (a.length > b.length) a.substring(i + 1) == b.substring(i) else a.substring(i) == b.substring(i + 1)
}
