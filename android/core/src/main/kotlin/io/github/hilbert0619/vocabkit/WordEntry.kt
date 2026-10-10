package io.github.hilbert0619.vocabkit

/**
 * A dictionary entry: the shared, read-only facts about a word (from ECDICT, or typed in by the user for an imported
 * word the dictionary doesn't know). As on iOS, the headword becomes its key ([Headword.key]), the display spelling is
 * trimmed, and corpus ranks of 0 or less mean "not ranked".
 */
class WordEntry(
    headword: String,
    display: String? = null,
    /** IPA without slashes, e.g. `ə'bændən`. May be empty. */
    val phonetic: String = "",
    /** Chinese meanings, one line per part of speech, e.g. `vt. 放弃, 抛弃\nn. 放任`. */
    val translation: String,
    /** English definitions, one line per sense. May be empty. */
    val definition: String = "",
    /** ECDICT's part-of-speech mix, e.g. `n:46/v:54`. May be empty. */
    val posMix: String = "",
    /** Collins COBUILD stars, 0–5. */
    val collins: Int = 0,
    /** In the Oxford 3000 core words. */
    val oxford: Boolean = false,
    /** Exam lists, e.g. `["cet4", "cet6", "ky"]`. */
    val tags: List<String> = emptyList(),
    bnc: Int? = null,
    frq: Int? = null,
    /** Inflections in ECDICT form, e.g. `p:abandoned/d:abandoned/i:abandoning/3:abandons`. */
    val exchange: String = "",
) {
    /** Lookup key, see [Headword.key]. */
    val headword: String = Headword.key(headword)

    /** The word as the source writes it, e.g. `China`, `e.g.`. */
    val display: String = (display ?: headword).trimmingWhitespaces()

    /** Rank in the British National Corpus word list (`null` if not ranked). */
    val bnc: Int? = bnc?.takeIf { it > 0 }

    /** Rank in the contemporary corpus word list (`null` if not ranked). */
    val frq: Int? = frq?.takeIf { it > 0 }

    fun copy(
        headword: String = this.headword,
        display: String = this.display,
        phonetic: String = this.phonetic,
        translation: String = this.translation,
        definition: String = this.definition,
        posMix: String = this.posMix,
        collins: Int = this.collins,
        oxford: Boolean = this.oxford,
        tags: List<String> = this.tags,
        bnc: Int? = this.bnc,
        frq: Int? = this.frq,
        exchange: String = this.exchange,
    ) = WordEntry(headword, display, phonetic, translation, definition, posMix, collins, oxford, tags, bnc, frq, exchange)

    /** The Chinese meanings, line by line. */
    val senses: List<SenseLine> get() = translation.swiftLines().mapNotNull(SenseLine::parse)

    /** Inflected forms (not the lemma entries), e.g. past tense `abandoned`. */
    val forms: List<WordForm> get() = WordForm.parse(exchange).filter { it.kind != WordForm.Kind.LEMMA }

    /** For an inflected entry (e.g. `abandoned`), the word it comes from. */
    val lemma: String? get() = WordForm.parse(exchange).firstOrNull { it.kind == WordForm.Kind.LEMMA }?.word

    /** The part of speech of the first meaning line that has one. */
    val primaryPartOfSpeech: PartOfSpeech? get() = senses.firstNotNullOfOrNull { it.partOfSpeech }

    /**
     * A short meaning for quiz options and lists: the first meaning line with at most three meanings, e.g.
     * `vt. 放弃，抛弃，遗弃` (full-width commas, as on the word page).
     */
    val shortMeaning: String
        get() {
            val lines = senses
            val line = lines.firstOrNull { it.label == null } ?: lines.firstOrNull() ?: return ""
            return line.formatted(maxMeanings = 3)
        }

    /** Every meaning as bare words, for telling apart words that mean the same thing. */
    val meaningTokens: Set<String> get() = senses.flatMap { it.meanings }.map(SenseLine::bare).filter { it.isNotEmpty() }.toSet()

    /** The lower of the two corpus ranks; words without a rank sort last. */
    val frequencyRank: Int?
        get() = when {
            frq != null && bnc != null -> minOf(frq, bnc)
            else -> frq ?: bnc
        }

    private val fields: List<Any?>
        get() = listOf(headword, display, phonetic, translation, definition, posMix, collins, oxford, tags, bnc, frq, exchange)

    override fun equals(other: Any?): Boolean = other is WordEntry && fields == other.fields
    override fun hashCode(): Int = fields.hashCode()
    override fun toString(): String = "WordEntry($display, $translation)"
}

/** One line of Chinese meanings: `vt. 放弃, 抛弃` or `[计] 异常中止`. */
data class SenseLine(
    /** The abbreviation as written, e.g. `vt.`, or `null`. */
    val partOfSpeechTag: String?,
    /** A subject label in brackets, e.g. `计` or `网络`, or `null`. */
    val label: String?,
    val meanings: List<String>,
) {
    val partOfSpeech: PartOfSpeech? get() = partOfSpeechTag?.let(PartOfSpeech::of)

    /** `vt. 放弃，抛弃，遗弃`, keeping at most [maxMeanings] meanings. */
    fun formatted(maxMeanings: Int = Int.MAX_VALUE): String {
        val text = meanings.take(maxMeanings).joinToString("，")
        return listOfNotNull(label?.let { "[$it]" }, partOfSpeechTag, text).joinToString(" ")
    }

    companion object {
        /** `null` for a line with no meanings, or only a part of speech. */
        fun parse(line: String): SenseLine? {
            var rest = line.trimmingWhitespaces()
            var label: String? = null
            var tag: String? = null
            val close = rest.indexOf(']')
            if (rest.startsWith("[") && close >= 0) {
                label = rest.substring(1, close).trimmingWhitespaces()
                rest = rest.substring(close + 1).trimStart(' ')
            }
            val space = rest.indexOf(' ')
            if (space >= 0 && PartOfSpeech.of(rest.substring(0, space)) != null) {
                tag = rest.substring(0, space)
                rest = rest.substring(space).trimStart(' ')
            } else if (PartOfSpeech.of(rest) != null) {
                return null
            }
            val meanings = rest.splitWhere { it in "，,；;、" }.map { it.trimmingWhitespaces() }.filter { it.isNotEmpty() }
            if (meanings.isEmpty()) return null
            return SenseLine(tag, label, meanings)
        }

        /** A meaning without notes in brackets: `（使）放弃` → `放弃`. */
        fun bare(meaning: String): String {
            val result = StringBuilder()
            var depth = 0
            for (char in meaning) {
                when {
                    char in "(（<《" -> depth += 1
                    char in ")）>》" -> depth = maxOf(0, depth - 1)
                    depth == 0 -> result.append(char)
                }
            }
            return result.toString().trimmingWhitespaces()
        }
    }
}

/** Part of speech, from the abbreviations used in Chinese dictionaries. */
enum class PartOfSpeech(val rawValue: String) {
    NOUN("noun"), VERB("verb"), ADJECTIVE("adjective"), ADVERB("adverb"), PREPOSITION("preposition"),
    CONJUNCTION("conjunction"), PRONOUN("pronoun"), NUMERAL("numeral"), INTERJECTION("interjection"), ARTICLE("article"),
    AUXILIARY("auxiliary"), ABBREVIATION("abbreviation"),
    ;

    companion object {
        /**
         * Recognises combined tags like `vt.vi.` or `vt.&vi.` by their first part, as long as every part is a tag (so
         * `a.m.` stays a word).
         */
        fun of(abbreviation: String): PartOfSpeech? {
            single(abbreviation)?.let { return it }
            val parts = abbreviation.splitWhere { it in ".&/" }.map { single("$it.") }
            if (!abbreviation.endsWith(".") || parts.size <= 1 || parts.contains(null)) return null
            return parts[0]
        }

        private fun single(abbreviation: String): PartOfSpeech? = when (abbreviation.lowercase()) {
            "n.", "pl." -> NOUN
            "v.", "vt.", "vi." -> VERB
            "a.", "adj." -> ADJECTIVE
            "ad.", "adv." -> ADVERB
            "prep." -> PREPOSITION
            "conj." -> CONJUNCTION
            "pron." -> PRONOUN
            "num." -> NUMERAL
            "int.", "interj." -> INTERJECTION
            "art." -> ARTICLE
            "aux.", "modal." -> AUXILIARY
            "abbr." -> ABBREVIATION
            else -> null
        }
    }
}

/** An inflected form listed in ECDICT's `exchange` field. */
data class WordForm(val kind: Kind, val word: String) {
    enum class Kind(val rawValue: String) {
        PAST("p"),
        PAST_PARTICIPLE("d"),
        PRESENT_PARTICIPLE("i"),
        THIRD_PERSON("3"),
        COMPARATIVE("r"),
        SUPERLATIVE("t"),
        PLURAL("s"),

        /** On an inflected entry: the word it is a form of. */
        LEMMA("0"),
    }

    companion object {
        /**
         * `p:abandoned/i:abandoning` → past `abandoned`, present participle `abandoning`. Unknown kinds (like ECDICT's
         * `1:` markers) are skipped.
         */
        internal fun parse(exchange: String): List<WordForm> = exchange.splitWhere { it == '/' }.mapNotNull { item ->
            val colon = item.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val kind = Kind.entries.firstOrNull { it.rawValue == item.substring(0, colon) } ?: return@mapNotNull null
            val word = item.substring(colon + 1).trimmingWhitespaces()
            if (word.isEmpty()) null else WordForm(kind, word)
        }
    }
}
