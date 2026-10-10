package io.github.hilbert0619.vocabkit

import io.github.hilbert0619.coursekit.CSV

/**
 * Reads ECDICT (https://github.com/skywind3000/ECDICT, MIT licence) and picks the words of a word list by tag, e.g.
 * `ky` (考研). The word list itself is built on the PC by the Swift tool `vocab-content` (docs/vocabulary-spec.md §4);
 * Android reads the result ([ContentFile]) and imported lists ([WordListImport]).
 */
object ECDICT {
    /** Why a tagged row was left out. */
    enum class SkipReason(val rawValue: String) {
        NO_TRANSLATION("no Chinese translation"),
        DUPLICATE("duplicate of an earlier entry"),
        NOT_A_WORD("not a word"),
    }

    /**
     * Entries that ECDICT only has in a capitalised spelling although their meanings are those of the ordinary word
     * (`CORE`: 核心, `Pole`: 杆, 极点). They are shown in lower case.
     */
    val displayOverrides: Map<String, String> = mapOf(
        "CORE" to "core", "FAX" to "fax", "Pole" to "pole", "Polish" to "polish", "Conservative" to "conservative", "Saint" to "saint",
    )

    data class Selection(
        /** In frequency order (see [frequencyOrder]). */
        val entries: List<WordEntry>,
        val skipped: List<Pair<String, SkipReason>>,
        /** Rows read, not counting the header. */
        val rowsRead: Int,
    )

    /**
     * The entries of [csv] (the text of `ecdict.csv`) tagged [tag]. ECDICT keeps each entry on one line and writes line
     * breaks inside fields as `\n`, so the file is read line by line.
     */
    fun select(tag: String, csv: String): Selection {
        val lines = csv.swiftLines()
        val headerLine = lines.firstOrNull() ?: return Selection(emptyList(), emptyList(), 0)
        val header = fields(headerLine).map { it.lowercase() }
        var rowsRead = 0
        val byKey = mutableMapOf<String, WordEntry>()
        val order = mutableListOf<String>()
        val skipped = mutableListOf<Pair<String, SkipReason>>()

        for (line in lines.drop(1)) {
            rowsRead++
            // Cheap filter first: almost no line mentions the tag.
            if (tag !in line) continue
            var entry = entry(fields(line), header) ?: continue
            if (tag !in entry.tags) continue
            displayOverrides[entry.display]?.let { entry = entry.copy(display = it) }
            val existing = byKey[entry.headword]
            when {
                entry.translation.isEmpty() -> skipped += entry.display to SkipReason.NO_TRANSLATION
                !isWord(entry.display) -> skipped += entry.display to SkipReason.NOT_A_WORD
                existing != null -> {
                    // `China` and `china`: keep the lower-case spelling, which is the common word.
                    if (entry.display == entry.headword && existing.display != existing.headword) {
                        byKey[entry.headword] = entry
                        skipped += existing.display to SkipReason.DUPLICATE
                    } else {
                        skipped += entry.display to SkipReason.DUPLICATE
                    }
                }
                else -> {
                    byKey[entry.headword] = entry
                    order += entry.headword
                }
            }
        }
        return Selection(frequencyOrder(order.mapNotNull { byKey[it] }), skipped, rowsRead)
    }

    /** One ECDICT row as an entry, using the header to find the columns. */
    fun entry(fields: List<String>, header: List<String>): WordEntry? {
        fun value(column: String): String {
            val index = header.indexOf(column)
            if (index < 0 || index >= fields.size) return ""
            return fields[index].trimmingWhitespaces()
        }
        val word = value("word")
        if (word.isEmpty()) return null
        return WordEntry(
            headword = word,
            display = word,
            phonetic = value("phonetic"),
            translation = unescape(value("translation")),
            definition = unescape(value("definition")),
            posMix = value("pos"),
            collins = value("collins").toIntOrNull() ?: 0,
            oxford = value("oxford") == "1",
            tags = value("tag").splitWhere { it == ' ' },
            bnc = value("bnc").toIntOrNull(),
            frq = value("frq").toIntOrNull(),
            exchange = value("exchange"),
        )
    }

    /**
     * Most frequent first (lower corpus rank), unranked words last, ties alphabetical. New words are introduced in this
     * order, so the words met most often in reading come first.
     */
    fun frequencyOrder(entries: List<WordEntry>): List<WordEntry> = entries.sortedWith { a, b ->
        val x = a.frequencyRank
        val y = b.frequencyRank
        when {
            x != null && y != null && x != y -> x.compareTo(y)
            x != null && y == null -> -1
            x == null && y != null -> 1
            else -> a.headword.compareTo(b.headword)
        }
    }

    /** ECDICT writes line breaks as `\n` and trims each line. */
    internal fun unescape(text: String): String = text.replace("\\r", "").replace("\\n", "\n")
        .splitWhere { it == '\n' }
        .map { it.trimmingWhitespaces() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    /** Letters, with spaces, hyphens, apostrophes or dots between them (`well-known`, `a.m.`, `give up`). */
    fun isWord(text: String): Boolean {
        val first = text.firstOrNull() ?: return false
        if (!first.isAsciiLetter()) return false
        return text.all { it.isAsciiLetter() || it in " -'." }
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun fields(line: String): List<String> = CSV.records(line, CSV.COMMA).firstOrNull()?.fields ?: emptyList()
}

/**
 * The word-list file committed to the repository (`content/kaoyan-ecdict.csv`): one entry per line in study order,
 * line breaks inside fields written as `\n` like ECDICT does.
 */
object ContentFile {
    val header: List<String> = listOf(
        "position", "word", "phonetic", "translation", "definition", "pos", "collins", "oxford", "tags", "bnc", "frq", "exchange",
    )

    fun write(entries: List<WordEntry>): String {
        val lines = mutableListOf(header.joinToString(","))
        entries.forEachIndexed { index, entry ->
            val fields = listOf(
                "${index + 1}", entry.display, entry.phonetic, escape(entry.translation), escape(entry.definition), entry.posMix,
                "${entry.collins}", if (entry.oxford) "1" else "0", entry.tags.joinToString(" "), entry.bnc?.toString().orEmpty(),
                entry.frq?.toString().orEmpty(), entry.exchange,
            )
            lines += fields.joinToString(",") { CSV.escape(it) }
        }
        return lines.joinToString("\n") + "\n"
    }

    /** The file lacks columns of [header]. */
    class MissingColumnsException(val columns: List<String>) : Exception("Missing columns: ${columns.joinToString()}")

    /** Entries in file order. Throws [MissingColumnsException]. */
    fun read(text: String): List<WordEntry> {
        val records = CSV.records(text, CSV.COMMA)
        val first = records.firstOrNull() ?: return emptyList()
        val columns = first.fields.map { it.lowercase() }
        val missing = header.filter { it !in columns }
        if (missing.isNotEmpty()) throw MissingColumnsException(missing)
        val ecdictHeader = columns.map { if (it == "tags") "tag" else it }
        return records.drop(1).mapNotNull { ECDICT.entry(it.fields, ecdictHeader) }
    }

    private fun escape(text: String): String = text.replace("\n", "\\n")
}
