package io.github.hilbert0619.vocabkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A few ECDICT rows, in ECDICT's column order and escaping. */
object EcdictSample {
    val text = """
        word,phonetic,definition,translation,pos,collins,oxford,tag,bnc,frq,exchange,detail,audio
        abandon,ә'bændәn,"n. the trait of lacking restraint\nv. forsake, leave behind","vt. 放弃, 抛弃, 遗弃, 使屈从, 沉溺, 放纵\nn. 放任, 无拘束, 狂热",,3,1,gk cet4 cet6 ky toefl gre,2057,2182,d:abandoned/p:abandoned/i:abandoning/3:abandons,,
        abandoned,ә'bændәnd,a. forsaken,"a. 被抛弃的, 放纵的",,2,,cet6 ky,6154,5271,0:abandon/1:p,,
        ability,ә'biliti,n. the quality of being able to perform,"n. 能力, 才能",,5,1,zk gk cet4 ky,375,377,s:abilities,,
        China,'tʃainә,n. a communist nation,n. 中国,,,,ky,,,,,
        china,'tʃainә,n. porcelain,"n. 瓷器\n[网络] 中国；瓷；陶瓷",,2,,gk cet4 ky,6000,7000,,,
        desert,'dezәt,v. leave someone who needs you,"vt. 抛弃, 遗弃, 放弃\nn. 沙漠",,4,1,gk cet4 ky,2000,2500,,,
        zebra,'zi:brә,n. an animal,n. 斑马,,1,,zk gk,9000,9500,,,
        3D,,,n. 三维,,,,ky,,,,,
        abbey,'æbi,n. a monastery,,,,,ky,,,,,
        swiftly,'swiftli,adv. quickly,"ad. 迅速地, 很快地",,3,,cet4 ky,3000,3100,,,
        CORE,kɒ:,n. the center of an object,"n. 核心, 果心\nvt. 挖...的核",,4,1,cet4 ky,1500,1600,,,
    """.trimIndent()

    val entries: List<WordEntry> = ECDICT.select("ky", text).entries
}

class WordEntryTests {
    private val abandon = EcdictSample.entries.first { it.headword == "abandon" }

    @Test
    fun senses() {
        val senses = abandon.senses
        assertEquals(2, senses.size)
        assertEquals("vt.", senses[0].partOfSpeechTag)
        assertEquals(PartOfSpeech.VERB, senses[0].partOfSpeech)
        assertEquals(listOf("放弃", "抛弃", "遗弃", "使屈从", "沉溺", "放纵"), senses[0].meanings)
        assertEquals(PartOfSpeech.NOUN, senses[1].partOfSpeech)
        assertEquals(PartOfSpeech.VERB, abandon.primaryPartOfSpeech)
        assertEquals("vt. 放弃，抛弃，遗弃", abandon.shortMeaning)
        assertTrue("狂热" in abandon.meaningTokens)
    }

    @Test
    fun labelledLinesAndNotes() {
        val china = EcdictSample.entries.first { it.headword == "china" }
        assertEquals("网络", china.senses[1].label)
        assertNull(china.senses[1].partOfSpeech)
        assertEquals(listOf("中国", "瓷", "陶瓷"), china.senses[1].meanings)
        assertEquals("[网络] 中国，瓷，陶瓷", china.senses[1].formatted())
        assertEquals("n. 瓷器", china.shortMeaning)
        val entry = WordEntry(headword = "give", translation = "vt. （使）放弃；给予")
        assertEquals(setOf("放弃", "给予"), entry.meaningTokens)
        assertEquals("", WordEntry(headword = "x", translation = "").shortMeaning)
    }

    @Test
    fun formsAndLemma() {
        assertEquals(listOf("abandoned", "abandoned", "abandoning", "abandons"), abandon.forms.map { it.word })
        assertEquals(
            listOf(WordForm.Kind.PAST_PARTICIPLE, WordForm.Kind.PAST, WordForm.Kind.PRESENT_PARTICIPLE, WordForm.Kind.THIRD_PERSON),
            abandon.forms.map { it.kind },
        )
        val abandoned = EcdictSample.entries.first { it.headword == "abandoned" }
        assertEquals("abandon", abandoned.lemma)
        assertTrue(abandoned.forms.isEmpty())
    }

    @Test
    fun frequencyRank() {
        assertEquals(2057, abandon.frequencyRank)
        assertNull(WordEntry(headword = "x", translation = "n. x", bnc = 0, frq = 0).frequencyRank)
        assertEquals(40, WordEntry(headword = "x", translation = "n. x", bnc = null, frq = 40).frequencyRank)
    }

    @Test
    fun partOfSpeechAbbreviations() {
        assertEquals(PartOfSpeech.ADJECTIVE, PartOfSpeech.of("adj."))
        assertEquals(PartOfSpeech.ADJECTIVE, PartOfSpeech.of("a."))
        assertEquals(PartOfSpeech.ADVERB, PartOfSpeech.of("ad."))
        assertEquals(PartOfSpeech.VERB, PartOfSpeech.of("VI."))
        assertNull(PartOfSpeech.of("abandon"))
        assertEquals(PartOfSpeech.VERB, PartOfSpeech.of("vt.vi."))
        assertEquals(PartOfSpeech.VERB, PartOfSpeech.of("vt.&vi."))
        assertEquals(PartOfSpeech.ADJECTIVE, PartOfSpeech.of("a.&n."))
        assertNull(PartOfSpeech.of("vt.vi"))
        assertNull(PartOfSpeech.of("x.y."))
        assertNull(PartOfSpeech.of("a.m."))
        assertNull(PartOfSpeech.of("n.x."))
        val combined = WordEntry(headword = "scrutinize", translation = "vt.vi. 细看, 仔细检查")
        assertEquals(PartOfSpeech.VERB, combined.primaryPartOfSpeech)
        assertEquals(listOf("细看", "仔细检查"), combined.senses.first().meanings)
    }
}

class ECDICTTests {
    private val selection = ECDICT.select("ky", EcdictSample.text)

    @Test
    fun selectsTaggedWordsInFrequencyOrder() {
        assertEquals(11, selection.rowsRead)
        // The lower of the two ranks: ability 375, core 1500, desert 2000, abandon 2057, swiftly 3000, abandoned 5271, china 6000.
        assertEquals(listOf("ability", "core", "desert", "abandon", "swiftly", "abandoned", "china"), selection.entries.map { it.headword })
        assertFalse(selection.entries.any { it.headword == "zebra" })
    }

    @Test
    fun skipsWhatCantBeStudied() {
        val skipped = selection.skipped.groupBy({ it.second }, { it.first })
        assertEquals(listOf("abbey"), skipped[ECDICT.SkipReason.NO_TRANSLATION])
        assertEquals(listOf("3D"), skipped[ECDICT.SkipReason.NOT_A_WORD])
        assertEquals(listOf("China"), skipped[ECDICT.SkipReason.DUPLICATE])
        assertEquals("china", selection.entries.first { it.headword == "china" }.display)
        assertEquals("core", selection.entries.first { it.headword == "core" }.display)
    }

    @Test
    fun readsFields() {
        val abandon = selection.entries.first { it.headword == "abandon" }
        assertEquals("ә'bændәn", abandon.phonetic)
        assertEquals("n. the trait of lacking restraint\nv. forsake, leave behind", abandon.definition)
        assertEquals(3, abandon.collins)
        assertTrue(abandon.oxford)
        assertEquals(listOf("gk", "cet4", "cet6", "ky", "toefl", "gre"), abandon.tags)
        assertEquals(2057, abandon.bnc)
        assertEquals(2182, abandon.frq)
    }

    @Test
    fun wordCheck() {
        assertTrue(ECDICT.isWord("well-known"))
        assertTrue(ECDICT.isWord("a.m."))
        assertTrue(ECDICT.isWord("give up"))
        assertTrue(ECDICT.isWord("o'clock"))
        assertFalse(ECDICT.isWord("3D"))
        assertFalse(ECDICT.isWord("-ism"))
        assertFalse(ECDICT.isWord("café"))
        assertFalse(ECDICT.isWord(""))
    }

    @Test
    fun contentFileRoundTrip() {
        val text = ContentFile.write(selection.entries)
        val lines = text.split("\n").filter { it.isNotEmpty() }
        assertEquals(selection.entries.size + 1, lines.size)
        assertEquals("position,word,phonetic,translation,definition,pos,collins,oxford,tags,bnc,frq,exchange", lines[0])
        assertTrue(lines[1].startsWith("1,ability,"))
        assertEquals(selection.entries, ContentFile.read(text))
        val error = assertFailsWith<ContentFile.MissingColumnsException> {
            ContentFile.read("word,phonetic,translation,definition,pos,collins,oxford,bnc,frq,exchange\n")
        }
        assertEquals(listOf("position", "tags"), error.columns)
        assertEquals(emptyList(), ContentFile.read(""))
    }
}

class WordListImportTests {
    private val known = setOf("abandon", "ability", "give up", "a.m.", "desert")

    private fun preview(text: String, format: WordListPreview.Format? = null) = WordListImport.preview(text, format) { it in known }

    @Test
    fun plainTextLines() {
        val result = preview(
            """
            # my list
            abandon
            1. Ability
            3、give up 放弃

            desert v. 抛弃；遗弃
            serendipity${"\t"}意外发现珍奇事物的本领
            a.m.
            abandon
            zzyzx
            你好
            abandon放弃
            """.trimIndent(),
        )
        assertEquals(WordListPreview.Format.TEXT, result.format)
        assertEquals(listOf("abandon", "ability", "give up", "desert", "serendipity", "a.m."), result.words.map { it.headword })
        assertEquals(listOf(null, null, "放弃", "v. 抛弃；遗弃", "意外发现珍奇事物的本领", null), result.words.map { it.meaning })
        assertEquals(listOf(2, 3, 4, 6, 7, 8), result.words.map { it.line })
        assertEquals("Ability", result.words[1].display)
        assertEquals(listOf(true, true, true, true, false, true), result.words.map { it.inDictionary })
        assertEquals(listOf(9, 12), result.duplicates.map { it.line })
        assertEquals(listOf("zzyzx"), result.unknown.map { it.headword })
        assertEquals(listOf(11), result.unreadable)
        assertEquals(3, result.matchedCount)
        assertEquals(3, result.customCount)
    }

    @Test
    fun csvWithChineseHeaders() {
        val result = preview(
            """
            单词,释义,音标,例句,例句翻译
            abandon,,,"He abandoned the project, sadly.",他放弃了这个项目。
            tenacious,"adj. 顽强的, 坚韧的",/tɪˈneɪʃəs/,,
            12345,数字,,,
            unknownword,,,,
            """.trimIndent(),
        )
        assertEquals(WordListPreview.Format.CSV, result.format)
        assertEquals(listOf("abandon", "tenacious"), result.words.map { it.headword })
        assertNull(result.words[0].meaning)
        assertEquals("He abandoned the project, sadly.", result.words[0].example)
        assertEquals("他放弃了这个项目。", result.words[0].exampleTranslation)
        assertEquals("adj. 顽强的, 坚韧的", result.words[1].meaning)
        assertEquals("/tɪˈneɪʃəs/", result.words[1].phonetic)
        assertEquals(listOf(4), result.unreadable)
        assertEquals(listOf("unknownword"), result.unknown.map { it.headword })
    }

    @Test
    fun csvWithEnglishHeadersAndTabs() {
        val result = preview("word\tmeaning\tpos\nabandon\t放弃\tv.\n")
        assertEquals(WordListPreview.Format.CSV, result.format)
        assertEquals("放弃", result.words.first().meaning)
        assertEquals("v.", result.words.first().partOfSpeech)
    }

    @Test
    fun formatDetection() {
        assertEquals(WordListPreview.Format.CSV, WordListImport.detectFormat("word,meaning\nabandon,放弃"))
        assertEquals(WordListPreview.Format.CSV, WordListImport.detectFormat("\n单词，释义\nabandon，放弃"))
        assertEquals(WordListPreview.Format.TEXT, WordListImport.detectFormat("abandon\nability"))
        assertEquals(WordListPreview.Format.TEXT, WordListImport.detectFormat("abandon, 放弃"))
        assertEquals(WordListPreview.Format.TEXT, WordListImport.detectFormat(""))
        assertTrue(preview("word\n", WordListPreview.Format.CSV).words.isEmpty())
        assertTrue(preview("no header here\n", WordListPreview.Format.CSV).words.isEmpty())
    }

    @Test
    fun importedWordEntries() {
        val dictionary = WordEntry(headword = "abandon", phonetic = "ə'bændən", translation = "vt. 放弃")
        val plain = ImportedWord(headword = "abandon", display = "abandon", meaning = null, inDictionary = true, line = 1)
        assertEquals(dictionary, plain.entry(dictionary))
        val custom = ImportedWord(headword = "abandon", display = "abandon", meaning = "v. 丢下", inDictionary = true, line = 1)
            .copy(phonetic = "/əˈbændən/")
        val entry = custom.entry(dictionary)
        assertEquals("v. 丢下", entry.translation)
        assertEquals("əˈbændən", entry.phonetic)
        val unknown = ImportedWord(headword = "tenacious", display = "Tenacious", meaning = "adj. 顽强的", inDictionary = false, line = 2)
        assertEquals("Tenacious", unknown.entry(null).display)
        assertEquals("adj. 顽强的", unknown.entry(null).shortMeaning)
    }
}

class WordSearchTests {
    private val search = WordSearch(
        EcdictSample.entries + listOf(
            WordEntry(headword = "abandonment", translation = "n. 放弃, 遗弃", frq = 9000),
            WordEntry(headword = "abound", translation = "vi. 大量存在", frq = 8000),
        ),
    )

    @Test
    fun englishQueries() {
        val results = search.search("abandon")
        assertEquals(WordSearch.Result("abandon", WordSearch.Match.Exact), results.first())
        assertEquals(listOf("abandon", "abandoned", "abandonment"), results.map { it.headword })
        assertEquals(WordSearch.Result("abandon", WordSearch.Match.Form("abandons")), search.search("ABANDONS").first())
        assertEquals(listOf("abandon", "abandoned", "abandonment"), search.search("aba").map { it.headword })
        assertEquals(listOf("abound", "ability", "abandon", "abandoned", "abandonment"), search.search("ab").map { it.headword })
        assertEquals(2, search.search("ab", limit = 2).size)
    }

    @Test
    fun typos() {
        assertEquals(listOf(WordSearch.Result("abandon", WordSearch.Match.Similar)), search.search("abandn"))
        assertEquals(listOf(WordSearch.Result("ability", WordSearch.Match.Similar)), search.search("abiltiy"))
        assertEquals(listOf("desert"), search.search("desret").map { it.headword })
        assertTrue(search.search("abx").isEmpty())
        assertTrue(isOneEditAway("abandon", "abandons"))
        assertTrue(isOneEditAway("abandons", "abandon"))
        assertTrue(isOneEditAway("abandon", "abamdon"))
        assertFalse(isOneEditAway("abandon", "abandon"))
        assertFalse(isOneEditAway("abandon", "abnadno"))
    }

    @Test
    fun chineseQueries() {
        val results = search.search("放弃")
        assertTrue(results.all { it.match == WordSearch.Match.Meaning })
        // Exact meaning first (desert 2000, abandon 2057, abandonment 9000), then the rest.
        assertEquals(listOf("desert", "abandon", "abandonment"), results.map { it.headword })
        assertEquals(listOf("desert", "abandon", "abandonment"), search.search("遗弃").map { it.headword })
        assertEquals(listOf("desert", "abandon", "abandoned", "abandonment"), search.search("放").map { it.headword })
        assertTrue(search.search("  ").isEmpty())
        assertEquals("abandon", search.entry("Abandon")?.headword)
    }
}

class ChoiceTests {
    private val pool = EcdictSample.entries + listOf(
        WordEntry(headword = "adopt", translation = "vt. 采用, 收养"),
        WordEntry(headword = "adapt", translation = "vt. 使适应, 改编"),
        WordEntry(headword = "abolish", translation = "vt. 废除, 废止"),
        WordEntry(headword = "abundant", translation = "a. 丰富的, 充裕的"),
    )
    private val abandon get() = pool.first { it.headword == "abandon" }

    @Test
    fun questionShape() {
        val question = assertNotNull(ChoiceGenerator.question(abandon, pool, seed = 42u))
        assertEquals(4, question.options.size)
        assertEquals(4, question.options.toSet().size)
        assertEquals("vt. 放弃，抛弃，遗弃", question.options[question.correctIndex])
        assertTrue(question.isCorrect(question.correctIndex))
        // desert shares 抛弃/遗弃/放弃, so it would be a second right answer.
        assertFalse("vt. 抛弃, 遗弃, 放弃" in question.options)
        // Enough other verbs: every wrong option is a verb.
        assertTrue(question.options.all { it.startsWith("vt.") })
    }

    @Test
    fun sameSeedSameQuestion() {
        val a = ChoiceGenerator.question(abandon, pool, seed = 7u)
        val b = ChoiceGenerator.question(abandon, pool, seed = 7u)
        assertEquals(a, b)
        val others = (1..20).mapNotNull { ChoiceGenerator.question(abandon, pool, seed = it.toULong()) }
        assertTrue(others.map { it.correctIndex }.toSet().size > 1)
    }

    @Test
    fun smallPools() {
        val tiny = listOf(abandon, WordEntry(headword = "abundant", translation = "a. 丰富的"))
        assertEquals(2, ChoiceGenerator.question(abandon, tiny, seed = 1u)?.options?.size)
        assertNull(ChoiceGenerator.question(abandon, listOf(abandon), seed = 1u))
        assertNull(ChoiceGenerator.question(WordEntry(headword = "x", translation = ""), pool, seed = 1u))
    }

    @Test
    fun seededRandomIsStable() {
        val random = SeededRandom(1u)
        assertEquals(0x910A2DEC89025CC1uL, random.next())
        assertEquals(SeededRandom.seed("abc"), SeededRandom.seed("abc"))
        assertTrue(SeededRandom.seed("a", "bc") != SeededRandom.seed("ab", "c"))
        assertEquals(0xCBF29CE484222325uL, SeededRandom.seed(""))
    }
}

class AnswerGraderTests {
    @Test
    fun ratings() {
        assertEquals(Rating.HARD, AnswerGrader.rating(Answer.Recognition(Rating.HARD)))
        assertEquals(Rating.GOOD, AnswerGrader.rating(Answer.Choice(correct = true)))
        assertEquals(Rating.AGAIN, AnswerGrader.rating(Answer.Choice(correct = false)))
        assertEquals(Rating.GOOD, AnswerGrader.rating(Answer.Spelling(Answer.SpellingResult.CORRECT)))
        assertEquals(Rating.HARD, AnswerGrader.rating(Answer.Spelling(Answer.SpellingResult.CORRECT_WITH_HINT)))
        assertEquals(Rating.AGAIN, AnswerGrader.rating(Answer.Spelling(Answer.SpellingResult.WRONG)))
        assertEquals(Rating.AGAIN, AnswerGrader.rating(Answer.Spelling(Answer.SpellingResult.GAVE_UP)))
        assertEquals(Rating.EASY, AnswerGrader.rating(Answer.AlreadyKnown))
    }

    @Test
    fun spelling() {
        assertTrue(AnswerGrader.isCorrectSpelling(" Abandon ", "abandon"))
        assertTrue(AnswerGrader.isCorrectSpelling("ａｂａｎｄｏｎ", "abandon"))
        assertTrue(AnswerGrader.isCorrectSpelling("give　 up", "give up"))
        assertTrue(AnswerGrader.isCorrectSpelling("don’t", "don't"))
        assertTrue(AnswerGrader.isCorrectSpelling("china", "China"))
        assertFalse(AnswerGrader.isCorrectSpelling("abandom", "abandon"))
        assertFalse(AnswerGrader.isCorrectSpelling("", ""))
    }

    @Test
    fun hints() {
        assertEquals("a______", AnswerGrader.hint("abandon"))
        assertEquals("g___ __", AnswerGrader.hint("give up"))
        assertEquals("w___-_____", AnswerGrader.hint("well-known"))
    }

    @Test
    fun differences() {
        assertEquals(listOf(DiffPart.Same("abandon")), AnswerGrader.diff("abandon", "abandon"))
        assertEquals(listOf(DiffPart.Same("aband"), DiffPart.Missing("o"), DiffPart.Same("n")), AnswerGrader.diff("abandn", "abandon"))
        assertEquals(listOf(DiffPart.Same("aband"), DiffPart.Extra("d"), DiffPart.Same("on")), AnswerGrader.diff("abanddon", "abandon"))
        assertEquals(
            listOf(DiffPart.Same("aba"), DiffPart.Missing("n"), DiffPart.Extra("m"), DiffPart.Same("don")),
            AnswerGrader.diff("abamdon", "abandon"),
        )
        assertEquals(listOf(DiffPart.Missing("ab")), AnswerGrader.diff("", "ab"))
        assertEquals(listOf(DiffPart.Extra("xy")), AnswerGrader.diff("xy", ""))
    }
}

class AuthLinkTests {
    private fun link(text: String) = AuthLink.of(text)

    @Test
    fun sessionLinks() {
        val base = "io.github.hilbert0619.coursetable://auth-callback#access_token=abc&expires_at=1&refresh_token=def&token_type=bearer"
        assertEquals(AuthLink.SignUpConfirmed, link("$base&type=signup"))
        assertEquals(AuthLink.PasswordRecovery, link("$base&type=recovery"))
        assertEquals(AuthLink.SignedIn, link("$base&type=magiclink"))
        assertEquals(AuthLink.SignedIn, link(base))
        assertEquals(AuthLink.SignedIn, link("io.github.hilbert0619.coursetable://auth-callback?code=xyz"))
        assertTrue(link("$base&type=recovery").signsIn)
    }

    @Test
    fun failedLinks() {
        val expired = link(
            "io.github.hilbert0619.coursetable://auth-callback#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired",
        )
        assertEquals(AuthLink.Failed("otp_expired", "Email link is invalid or has expired"), expired)
        assertFalse(expired.signsIn)
        assertEquals(AuthLink.Failed("server_error", "server_error"), link("io.github.hilbert0619.coursetable://auth-callback?error=server_error"))
    }

    @Test
    fun otherLinks() {
        assertEquals(AuthLink.NotAuthLink, link("https://example.com/#access_token=abc"))
        assertEquals(AuthLink.NotAuthLink, link("io.github.hilbert0619.coursetable://auth-callback"))
        assertEquals(AuthLink.SignUpConfirmed, link("IO.GITHUB.HILBERT0619.COURSETABLE://auth-callback#access_token=a&type=signup"))
        assertEquals("io.github.hilbert0619.coursetable://auth-callback", AuthLink.CALLBACK)
    }
}
