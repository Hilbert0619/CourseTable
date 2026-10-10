package io.github.hilbert0619.vocabkit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checks on the committed word list, content/kaoyan-ecdict.csv (built by `vocab-content`). */
class RealContentTests {
    private val entries: List<WordEntry> get() = Companion.entries

    @Test
    fun fileReads() {
        assertEquals(4801, entries.size)
        assertEquals(entries.size, entries.map { it.headword }.toSet().size)
        assertTrue(entries.all { it.translation.isNotEmpty() && it.shortMeaning.isNotEmpty() })
        assertTrue(entries.all { "ky" in it.tags })
        assertEquals(listOf("government", "system", "state"), entries.take(3).map { it.headword })
        val ranks = entries.mapNotNull { it.frequencyRank }
        assertEquals(ranks.sorted(), ranks)
        assertTrue(entries.count { it.primaryPartOfSpeech == null } <= 10)
    }

    @Test
    fun knownEntries() {
        val abandon = entries.first { it.headword == "abandon" }
        assertEquals("vt. 放弃，抛弃，遗弃", abandon.shortMeaning)
        assertTrue(WordForm(WordForm.Kind.PAST, "abandoned") in abandon.forms)
        val within = entries.first { it.headword == "within" }
        assertEquals(listOf(PartOfSpeech.NOUN, PartOfSpeech.ADVERB, PartOfSpeech.PREPOSITION), within.senses.map { it.partOfSpeech })
    }

    @Test
    fun everyWordGetsAFullQuestion() {
        val pool = ChoicePool(entries)
        assertEquals(entries.size, pool.count)
        val incomplete = mutableListOf<String>()
        entries.forEachIndexed { index, entry ->
            val question = pool.question(entry, seed = index.toULong())
            if (question == null || question.options.size != 4 || question.options.toSet().size != 4 ||
                question.options[question.correctIndex] != entry.shortMeaning
            ) {
                incomplete += entry.headword
            }
        }
        assertTrue(incomplete.isEmpty(), "${incomplete.take(20)}")
    }

    @Test
    fun wrongOptionsMatchThePartOfSpeech() {
        val pool = ChoicePool(entries)
        // The first entry with a meaning wins, as Swift's `uniquingKeysWith: { first, _ in first }`.
        val byMeaning = entries.reversed().associateBy { it.shortMeaning }
        var mismatched = 0
        var checked = 0
        entries.forEachIndexed { index, entry ->
            if (index % 7 != 0) return@forEachIndexed
            val pos = entry.primaryPartOfSpeech ?: return@forEachIndexed
            val question = pool.question(entry, seed = 99u) ?: return@forEachIndexed
            for (option in question.options) {
                if (option == entry.shortMeaning) continue
                checked++
                if (byMeaning[option]?.primaryPartOfSpeech != pos) mismatched++
            }
        }
        // Only the rare parts of speech (a dozen prepositions, conjunctions…) run out of same-kind options.
        assertTrue(checked > 1500)
        assertTrue(mismatched.toDouble() / checked < 0.01)
    }

    @Test
    fun search() {
        val search = WordSearch(entries)
        assertEquals(WordSearch.Result("abandon", WordSearch.Match.Exact), search.search("abandon").first())
        assertEquals(WordSearch.Result("government", WordSearch.Match.Form("governments")), search.search("governments").first())
        assertTrue("government" in search.search("goverment").map { it.headword })
        val chinese = search.search("放弃").map { it.headword }
        assertTrue("abandon" in chinese)
        assertTrue(chinese.size >= 3)
    }

    @Test
    fun importAgainstTheDictionary() {
        val known = entries.map { it.headword }.toSet()
        val preview = WordListImport.preview("abandon\nGovernment\nqwertyuiop\nstate 状态\n") { it in known }
        assertEquals(listOf("abandon", "government", "state"), preview.words.map { it.headword })
        assertEquals(listOf("qwertyuiop"), preview.unknown.map { it.headword })
    }

    companion object {
        /** Read once for all the tests. */
        val entries: List<WordEntry> by lazy {
            val directory = File(System.getProperty("content.dir") ?: error("The test task sets content.dir"))
            ContentFile.read(File(directory, "kaoyan-ecdict.csv").readText())
        }
    }
}
