package io.github.hilbert0619.vocabkit

// Swift's character classes and string helpers, so texts are split and trimmed as on iOS.

/** Swift `Character.isWhitespace`: the Unicode White_Space property. */
internal fun isSwiftWhitespace(char: Char): Boolean = Character.isWhitespace(char) || Character.isSpaceChar(char) || char == '\u0085'

/** Swift `Character.isNewline`. */
internal fun isSwiftNewline(char: Char): Boolean = char in '\n'..'\r' || char == '\u0085' || char == ' ' || char == ' '

/** Swift `Character.isNumber` (Unicode numeric categories). */
internal fun isSwiftNumber(char: Char): Boolean = when (Character.getType(char).toByte()) {
    Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
    else -> false
}

/** `CharacterSet.whitespaces`: space separators and tab. */
internal fun isWhitespaceInSet(char: Char): Boolean = char == '\t' || Character.getType(char) == Character.SPACE_SEPARATOR.toInt()

/** `CharacterSet.whitespacesAndNewlines`. */
internal fun isWhitespaceOrNewlineInSet(char: Char): Boolean = isWhitespaceInSet(char) || isSwiftNewline(char)

/** `trimmingCharacters(in: .whitespaces)` */
internal fun String.trimmingWhitespaces(): String = trim(::isWhitespaceInSet)

/** `trimmingCharacters(in: .whitespacesAndNewlines)` */
internal fun String.trimmingWhitespacesAndNewlines(): String = trim(::isWhitespaceOrNewlineInSet)

/** `trimmingCharacters(in: CharacterSet(charactersIn: characters).union(.whitespaces))` */
internal fun String.trimming(characters: String): String = trim { it in characters || isWhitespaceInSet(it) }

/** Swift `split(whereSeparator:)`: by default without empty pieces. */
internal fun String.splitWhere(omittingEmpty: Boolean = true, isSeparator: (Char) -> Boolean): List<String> {
    val pieces = mutableListOf<String>()
    var start = 0
    for (index in indices) {
        if (isSeparator(this[index])) {
            pieces += substring(start, index)
            start = index + 1
        }
    }
    pieces += substring(start)
    return if (omittingEmpty) pieces.filter { it.isNotEmpty() } else pieces
}

/** Swift `split(whereSeparator: \.isNewline)`, where `\r\n` is one line break. */
internal fun String.swiftLines(omittingEmpty: Boolean = true): List<String> =
    replace("\r\n", "\n").splitWhere(omittingEmpty, ::isSwiftNewline)
