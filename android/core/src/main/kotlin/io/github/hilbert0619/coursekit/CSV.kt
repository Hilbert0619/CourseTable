package io.github.hilbert0619.coursekit

/**
 * A small, forgiving CSV/TSV reader and writer (RFC 4180 quoting).
 *
 * Handles quoted fields with doubled quotes, delimiters and line breaks inside quotes, and `\n`, `\r\n` or `\r` line
 * endings. A quote in the middle of an unquoted field is kept as text.
 */
object CSV {
    data class Record(
        /** 1-based line in the input where the record starts. */
        val line: Int,
        val fields: List<String>,
    )

    const val TAB = '\t'
    const val COMMA = ','
    const val SEMICOLON = ';'
    const val FULL_WIDTH_COMMA = '，'

    /**
     * Tab if the first non-empty line has one (a spreadsheet paste), otherwise the most frequent of comma, semicolon
     * and full-width comma, defaulting to comma.
     */
    fun detectDelimiter(text: String): Char {
        val firstLine = text.split(NEWLINE).firstOrNull { line -> line.isNotEmpty() && !line.all { it.isWhitespace() } }.orEmpty()
        if (TAB in firstLine) return TAB
        val candidates = listOf(COMMA, SEMICOLON, FULL_WIDTH_COMMA)
        val counts = candidates.map { candidate -> firstLine.count { it == candidate } }
        val best = counts.indices.maxByOrNull { counts[it] } ?: return COMMA
        return if (counts[best] > 0) candidates[best] else COMMA
    }

    fun records(text: String, delimiter: Char): List<Record> {
        val records = mutableListOf<Record>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordLine = 1
        var index = 0

        fun endField() {
            fields += field.toString()
            field.setLength(0)
        }

        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            if (inQuotes) {
                when (char) {
                    '"' -> if (next == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = false
                    }
                    '\r', '\n' -> {
                        if (char == '\r' && next == '\n') index++
                        field.append('\n')
                        line++
                    }
                    else -> field.append(char)
                }
            } else if (char == '"' && field.all { it.isWhitespace() }) {
                field.setLength(0)
                inQuotes = true
            } else if (char == delimiter) {
                endField()
            } else if (char == '\r' || char == '\n') {
                if (char == '\r' && next == '\n') index++
                endField()
                records += Record(recordLine, fields)
                fields = mutableListOf()
                line++
                recordLine = line
            } else {
                field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || fields.isNotEmpty() || inQuotes) {
            endField()
            records += Record(recordLine, fields)
        }
        return records
    }

    /** [field] quoted if it contains the delimiter, a quote or a line break. */
    fun escape(field: String, delimiter: Char = COMMA): String {
        val needsQuotes = field.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }

    /** What Swift counts as a line break between `Character`s (`\r\n` is one). */
    internal val NEWLINE = Regex("\r\n|[\n\r\u000B\u000C\u0085  ]")
}
