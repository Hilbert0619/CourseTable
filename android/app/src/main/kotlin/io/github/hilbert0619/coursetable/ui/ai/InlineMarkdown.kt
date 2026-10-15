package io.github.hilbert0619.coursetable.ui.ai

/**
 * Inline Markdown in an AI answer's block ([io.github.hilbert0619.aikit.MarkdownBlock]): `**bold**`, `*italics*` or
 * `_italics_`, `` `code` `` and `[links](https://…)`, with `\` escaping a mark. Anything unmatched stays as typed, so a
 * stray `*` or a half-streamed `**bo` reads as text.
 */
object InlineMarkdown {
    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val link: String? = null,
    )

    fun parse(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        parse(text, Span(""), spans)
        return merged(spans)
    }

    private fun parse(text: String, style: Span, output: MutableList<Span>) {
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) output += style.copy(text = plain.toString())
            plain.clear()
        }
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '\\' && index + 1 < text.length && text[index + 1] in MARKS -> {
                    plain.append(text[index + 1])
                    index += 2
                }
                char == '`' -> {
                    val end = text.indexOf('`', index + 1)
                    if (end > index + 1) {
                        flush()
                        output += style.copy(text = text.substring(index + 1, end), code = true)
                        index = end + 1
                    } else {
                        plain.append(char)
                        index++
                    }
                }
                text.startsWith("***", index) && text.indexOf("***", index + 3) > index + 3 -> {
                    val end = text.indexOf("***", index + 3)
                    flush()
                    parse(text.substring(index + 3, end), style.copy(bold = true, italic = true), output)
                    index = end + 3
                }
                text.startsWith("**", index) || text.startsWith("__", index) -> {
                    val mark = text.substring(index, index + 2)
                    val end = text.indexOf(mark, index + 2)
                    if (end > index + 2) {
                        flush()
                        parse(text.substring(index + 2, end), style.copy(bold = true), output)
                        index = end + 2
                    } else {
                        plain.append(mark)
                        index += 2
                    }
                }
                (char == '*' || char == '_') && opensEmphasis(text, index) -> {
                    val end = closingEmphasis(text, index)
                    if (end > 0) {
                        flush()
                        parse(text.substring(index + 1, end), style.copy(italic = true), output)
                        index = end + 1
                    } else {
                        plain.append(char)
                        index++
                    }
                }
                char == '[' -> {
                    val close = text.indexOf("](", index + 1)
                    val end = if (close > index) text.indexOf(')', close + 2) else -1
                    val url = if (end > close && close > index) text.substring(close + 2, end) else ""
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        flush()
                        parse(text.substring(index + 1, close), style.copy(link = url), output)
                        index = end + 1
                    } else {
                        plain.append(char)
                        index++
                    }
                }
                else -> {
                    plain.append(char)
                    index++
                }
            }
        }
        flush()
    }

    /** `*word*` opens before a non-space; `_` only at a word's start (not inside snake_case). */
    private fun opensEmphasis(text: String, index: Int): Boolean {
        val next = text.getOrNull(index + 1) ?: return false
        if (next.isWhitespace() || next == text[index]) return false
        return text[index] == '*' || index == 0 || !text[index - 1].isLetterOrDigit()
    }

    private fun closingEmphasis(text: String, index: Int): Int {
        val mark = text[index]
        var search = index + 1
        while (true) {
            val end = text.indexOf(mark, search)
            if (end < 0) return -1
            val before = text[end - 1]
            val after = text.getOrNull(end + 1)
            val closes = !before.isWhitespace() && end > index + 1 && (mark == '*' || after == null || !after.isLetterOrDigit())
            if (closes) return end
            search = end + 1
        }
    }

    /** Neighbouring spans with the same style become one. */
    private fun merged(spans: List<Span>): List<Span> {
        val result = mutableListOf<Span>()
        for (span in spans) {
            val last = result.lastOrNull()
            if (last != null && last.copy(text = "") == span.copy(text = "")) {
                result[result.size - 1] = last.copy(text = last.text + span.text)
            } else {
                result += span
            }
        }
        return result
    }

    private const val MARKS = "\\`*_[]()#+-.!"
}
