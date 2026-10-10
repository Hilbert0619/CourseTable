package io.github.hilbert0619.vocabkit

import io.github.hilbert0619.coursekit.CSV

/** A word read from an imported list, before it is saved into a book. */
data class ImportedWord(
    val headword: String,
    val display: String,
    /** The meaning given in the file; `null` means "use the dictionary's". */
    val meaning: String?,
    val phonetic: String? = null,
    val partOfSpeech: String? = null,
    val example: String? = null,
    val exampleTranslation: String? = null,
    /** Whether the dictionary has this word. */
    val inDictionary: Boolean,
    /** 1-based line in the file. */
    val line: Int,
) {
    /** The entry to show and quiz: the dictionary's, with anything the file gave taking its place. */
    fun entry(dictionary: WordEntry?): WordEntry {
        var entry = dictionary ?: WordEntry(headword = headword, display = display, translation = "")
        if (meaning != null) entry = entry.copy(translation = meaning)
        if (phonetic != null) entry = entry.copy(phonetic = phonetic.trim { it in "/[] " })
        return entry
    }
}

/** What an import would do, shown before anything is saved. */
data class WordListPreview(
    val format: Format,
    /** Words to import, in file order. */
    val words: List<ImportedWord>,
    /** Words the dictionary doesn't know and the file gives no meaning for; they are not imported. */
    val unknown: List<ImportedWord>,
    /** Repeats of a word already listed earlier in the file. */
    val duplicates: List<ImportedWord>,
    /** Lines that don't look like a word, with their line numbers. */
    val unreadable: List<Int>,
) {
    enum class Format { TEXT, CSV }

    val matchedCount: Int get() = words.count { it.inDictionary && it.meaning == null }
    val customCount: Int get() = words.count { it.meaning != null }
}

/**
 * Reads word lists. Plain text has one word per line, optionally followed by a meaning (`abandon`, `abandon 放弃`,
 * `abandon<TAB>v. 放弃`, `12. abandon`). CSV needs a header row naming a `word` column (English or Chinese header names),
 * plus optional meaning, phonetic, part of speech, example and example translation columns.
 */
object WordListImport {
    const val MAX_WORDS = 20_000

    /** @param isKnown whether the dictionary has a word (by key). */
    fun preview(text: String, format: WordListPreview.Format? = null, isKnown: (String) -> Boolean): WordListPreview {
        val actualFormat = format ?: detectFormat(text)
        val rows = when (actualFormat) {
            WordListPreview.Format.TEXT -> textRows(text, isKnown)
            WordListPreview.Format.CSV -> csvRows(text, isKnown)
        }
        val words = mutableListOf<ImportedWord>()
        val unknown = mutableListOf<ImportedWord>()
        val duplicates = mutableListOf<ImportedWord>()
        val unreadable = mutableListOf<Int>()
        val seen = mutableSetOf<String>()
        for ((line, word) in rows) {
            when {
                word == null -> unreadable += line
                word.headword in seen -> duplicates += word
                !word.inDictionary && word.meaning == null -> unknown += word
                words.size < MAX_WORDS -> {
                    seen += word.headword
                    words += word
                }
            }
        }
        return WordListPreview(actualFormat, words, unknown, duplicates, unreadable)
    }

    /** CSV if the first non-blank line is a header naming a word column, otherwise plain text. */
    fun detectFormat(text: String): WordListPreview.Format {
        val firstLine = text.swiftLines().firstOrNull { line -> !line.all(::isSwiftWhitespace) }.orEmpty()
        val delimiter = CSV.detectDelimiter(firstLine)
        val fields = CSV.records(firstLine, delimiter).firstOrNull()?.fields ?: emptyList()
        return if (fields.size > 1 && columns(fields) != null) WordListPreview.Format.CSV else WordListPreview.Format.TEXT
    }

    // Plain text

    internal fun textRows(text: String, isKnown: (String) -> Boolean): List<Pair<Int, ImportedWord?>> {
        val rows = mutableListOf<Pair<Int, ImportedWord?>>()
        text.swiftLines(omittingEmpty = false).forEachIndexed { offset, rawLine ->
            val line = rawLine.trimmingWhitespaces()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            rows += (offset + 1) to textWord(line, offset + 1, isKnown)
        }
        return rows
    }

    /**
     * Splits a line into word and meaning: at a tab if there is one, otherwise before the first non-Latin character or
     * part-of-speech abbreviation (`abandon v. 放弃`, `abandon放弃`).
     */
    internal fun textWord(line: String, number: Int, isKnown: (String) -> Boolean): ImportedWord? {
        var body = line
        // Leading numbering: "12. abandon", "3、abandon", "(4) abandon".
        val numbering = body.takeWhile { isSwiftNumber(it) || it in " .、)）(（" }
        if (numbering.any(::isSwiftNumber)) body = body.drop(numbering.length)

        val word: String
        val meaning: String
        val tab = body.indexOf('\t')
        if (tab >= 0) {
            word = body.substring(0, tab)
            meaning = body.substring(tab + 1)
        } else {
            val cut = body.indexOfFirst { it.code > 0x7F }.let { if (it < 0) body.length else it }
            val tokens = body.substring(0, cut).splitWhere { it == ' ' }
            val wordTokens = tokens.takeWhile { PartOfSpeech.of(it) == null }
            word = wordTokens.joinToString(" ")
            meaning = (tokens.drop(wordTokens.size) + body.substring(cut)).joinToString(" ")
        }
        val display = word.trimming(" :：-—=")
        if (!ECDICT.isWord(display) || display.length > 60) return null
        val key = Headword.key(display)
        val cleanMeaning = meaning.trimming(" :：-—=")
        return ImportedWord(key, display, cleanMeaning.ifEmpty { null }, inDictionary = isKnown(key), line = number)
    }

    // CSV

    internal enum class Column(val names: Set<String>) {
        WORD(setOf("word", "words", "headword", "english", "单词", "词汇", "英文", "英语")),
        MEANING(setOf("meaning", "meanings", "translation", "chinese", "definition", "释义", "中文", "词义", "意思", "中文释义", "翻译")),
        PHONETIC(setOf("phonetic", "ipa", "pronunciation", "音标")),
        PART_OF_SPEECH(setOf("pos", "part of speech", "part_of_speech", "词性")),
        EXAMPLE(setOf("example", "sentence", "例句")),
        EXAMPLE_TRANSLATION(setOf("example_translation", "example translation", "translation of example", "例句翻译", "例句译文", "译文")),
    }

    internal fun columns(fields: List<String>): List<Column?>? {
        val columns = fields.map { field ->
            val name = field.trimmingWhitespaces().lowercase()
            Column.entries.firstOrNull { name in it.names }
        }
        return if (Column.WORD in columns) columns else null
    }

    internal fun csvRows(text: String, isKnown: (String) -> Boolean): List<Pair<Int, ImportedWord?>> {
        val delimiter = CSV.detectDelimiter(text)
        val records = CSV.records(text, delimiter).filter { record -> !record.fields.all { it.trimmingWhitespaces().isEmpty() } }
        val header = records.firstOrNull() ?: return emptyList()
        val columns = columns(header.fields) ?: return emptyList()
        return records.drop(1).mapNotNull { record ->
            fun value(column: Column): String? {
                val index = columns.indexOf(column)
                if (index < 0 || index >= record.fields.size) return null
                return record.fields[index].trimmingWhitespacesAndNewlines().ifEmpty { null }
            }
            val display = value(Column.WORD).orEmpty()
            if (display.startsWith("#")) return@mapNotNull null
            if (!ECDICT.isWord(display) || display.length > 60) return@mapNotNull record.line to null
            val key = Headword.key(display)
            record.line to ImportedWord(
                headword = key, display = display, meaning = value(Column.MEANING), phonetic = value(Column.PHONETIC),
                partOfSpeech = value(Column.PART_OF_SPEECH), example = value(Column.EXAMPLE),
                exampleTranslation = value(Column.EXAMPLE_TRANSLATION), inDictionary = isKnown(key), line = record.line,
            )
        }
    }
}
