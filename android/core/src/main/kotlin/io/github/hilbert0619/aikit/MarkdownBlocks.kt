package io.github.hilbert0619.aikit

import io.github.hilbert0619.vocabkit.trimmingWhitespaces

/**
 * The block structure of an AI answer's Markdown: headings, list items, quotes, code and paragraphs. Inline styling
 * (bold, italics, code, links) stays in each block's text.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    /** `- item`, `* item`, `+ item`; [depth] counts indentation steps (two spaces or a tab each). */
    data class Bullet(val depth: Int, val text: String) : MarkdownBlock

    /** `1. item` or `1) item`. */
    data class Numbered(val depth: Int, val number: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock

    /** A list item with [line] added on a new line; `null` for other blocks. */
    private fun continued(line: String): MarkdownBlock? = when (this) {
        is Bullet -> copy(text = text + "\n" + line)
        is Numbered -> copy(text = text + "\n" + line)
        else -> null
    }

    companion object {
        fun parse(markdown: String): List<MarkdownBlock> {
            val blocks = mutableListOf<MarkdownBlock>()
            val paragraph = mutableListOf<String>()
            var code: MutableList<String>? = null

            fun flush() {
                if (paragraph.isNotEmpty()) blocks += Paragraph(paragraph.joinToString("\n"))
                paragraph.clear()
            }

            for (line in markdown.replace("\r\n", "\n").split('\n')) {
                val trimmed = line.trimmingWhitespaces()
                if (trimmed.startsWith("```")) {
                    val lines = code
                    if (lines != null) {
                        blocks += Code(lines.joinToString("\n"))
                        code = null
                    } else {
                        flush()
                        code = mutableListOf()
                    }
                    continue
                }
                code?.let {
                    it += line
                    continue
                }
                if (trimmed.isEmpty()) {
                    flush()
                    continue
                }
                val depth = indentation(line)
                val heading = heading(trimmed)
                val numbered = numberedItem(trimmed)
                val last = blocks.lastOrNull()
                when {
                    heading != null -> {
                        flush()
                        blocks += heading
                    }
                    trimmed.characterCount >= 3 && trimmed.filter { it != ' ' }.toSet().size == 1 && trimmed[0] in "-*_" -> {
                        flush()
                        blocks += Rule
                    }
                    trimmed[0] in "-*+•" && trimmed.getOrNull(1) == ' ' -> {
                        flush()
                        blocks += Bullet(depth, trimmed.substring(2).trimmingWhitespaces())
                    }
                    numbered != null -> {
                        flush()
                        blocks += Numbered(depth, numbered.first, numbered.second)
                    }
                    trimmed.startsWith(">") -> {
                        flush()
                        blocks += Quote(trimmed.substring(1).trimmingWhitespaces())
                    }
                    // An indented line right after a list item belongs to it, e.g. an example's translation.
                    paragraph.isEmpty() && depth > 0 && last != null && last.continued(trimmed) != null ->
                        blocks[blocks.size - 1] = last.continued(trimmed)!!
                    else -> paragraph += trimmed
                }
            }
            code?.let { blocks += Code(it.joinToString("\n")) } // an answer cut off mid-block
            flush()
            return blocks
        }

        private fun indentation(line: String): Int {
            var spaces = 0
            for (character in line) {
                when (character) {
                    ' ' -> spaces += 1
                    '\t' -> spaces += 2
                    else -> break
                }
            }
            return minOf(spaces / 2, 3)
        }

        private fun heading(line: String): MarkdownBlock? {
            val hashes = line.takeWhile { it == '#' }.length
            if (hashes !in 1..6 || line.getOrNull(hashes) != ' ') return null
            var text = line.substring(hashes).trimmingWhitespaces()
            while (text.endsWith("#")) text = text.dropLast(1)
            return Heading(hashes, text.trimmingWhitespaces())
        }

        private fun numberedItem(line: String): Pair<String, String>? {
            val digits = line.takeWhile { it in '0'..'9' }
            if (digits.isEmpty() || digits.length > 3) return null
            val mark = line.getOrNull(digits.length)
            if ((mark != '.' && mark != ')') || line.getOrNull(digits.length + 1) != ' ') return null
            return digits to line.substring(digits.length + 2).trimmingWhitespaces()
        }
    }
}
