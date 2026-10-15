package io.github.hilbert0619.coursetable

import io.github.hilbert0619.coursetable.ui.ai.InlineMarkdown
import io.github.hilbert0619.coursetable.ui.ai.InlineMarkdown.Span
import org.junit.Assert.assertEquals
import org.junit.Test

/** Bold, italics, code and links inside an AI answer's lines (plain JVM). */
class InlineMarkdownTest {
    @Test
    fun stylesAndPlainText() {
        assertEquals(
            listOf(Span("abandon", bold = true), Span(" means to leave "), Span("completely", italic = true), Span(".")),
            InlineMarkdown.parse("**abandon** means to leave *completely*."),
        )
        assertEquals(listOf(Span("Use "), Span("let x = 1", code = true)), InlineMarkdown.parse("Use `let x = 1`"))
        assertEquals(listOf(Span("both and "), Span("italic", bold = true, italic = true)), InlineMarkdown.parse("both and ***italic***"))
    }

    @Test
    fun linksNeedAWebAddress() {
        assertEquals(
            listOf(Span("See "), Span("ECDICT", link = "https://github.com/skywind3000/ECDICT")),
            InlineMarkdown.parse("See [ECDICT](https://github.com/skywind3000/ECDICT)"),
        )
        assertEquals(listOf(Span("[not](a link)")), InlineMarkdown.parse("[not](a link)"))
    }

    @Test
    fun strayMarksStayAsTyped() {
        assertEquals(listOf(Span("2 * 3 = 6")), InlineMarkdown.parse("2 * 3 = 6"))
        assertEquals(listOf(Span("**half")), InlineMarkdown.parse("**half"))
        assertEquals(listOf(Span("snake_case_name")), InlineMarkdown.parse("snake_case_name"))
        assertEquals(listOf(Span("a *literal* star")), InlineMarkdown.parse("a \\*literal\\* star"))
    }

    @Test
    fun chineseNextToMarks() {
        assertEquals(
            listOf(Span("abandon", bold = true), Span(" 指"), Span("彻底", italic = true), Span("放弃")),
            InlineMarkdown.parse("**abandon** 指*彻底*放弃"),
        )
    }
}
