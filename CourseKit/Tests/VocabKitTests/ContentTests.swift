import Foundation
import Testing
@testable import VocabKit

extension VocabFixture {
    /// A few ECDICT rows, in ECDICT's column order and escaping.
    static let ecdictSample = #"""
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
    """#

    static let sampleEntries: [WordEntry] = ECDICT.select(tag: "ky", from: ecdictSample).entries
}

@Suite struct WordEntryTests {
    let abandon = VocabFixture.sampleEntries.first { $0.headword == "abandon" }!

    @Test func senses() {
        let senses = abandon.senses
        #expect(senses.count == 2)
        #expect(senses[0].partOfSpeechTag == "vt.")
        #expect(senses[0].partOfSpeech == .verb)
        #expect(senses[0].meanings == ["放弃", "抛弃", "遗弃", "使屈从", "沉溺", "放纵"])
        #expect(senses[1].partOfSpeech == .noun)
        #expect(abandon.primaryPartOfSpeech == .verb)
        #expect(abandon.shortMeaning == "vt. 放弃，抛弃，遗弃")
        #expect(abandon.meaningTokens.contains("狂热"))
    }

    @Test func labelledLinesAndNotes() {
        let china = VocabFixture.sampleEntries.first { $0.headword == "china" }!
        #expect(china.senses[1].label == "网络")
        #expect(china.senses[1].partOfSpeech == nil)
        #expect(china.senses[1].meanings == ["中国", "瓷", "陶瓷"])
        #expect(china.senses[1].formatted() == "[网络] 中国，瓷，陶瓷")
        #expect(china.shortMeaning == "n. 瓷器")
        let entry = WordEntry(headword: "give", translation: "vt. （使）放弃；给予")
        #expect(entry.meaningTokens == ["放弃", "给予"])
        #expect(WordEntry(headword: "x", translation: "").shortMeaning == "")
    }

    @Test func formsAndLemma() {
        #expect(abandon.forms.map(\.word) == ["abandoned", "abandoned", "abandoning", "abandons"])
        #expect(abandon.forms.map(\.kind) == [.pastParticiple, .past, .presentParticiple, .thirdPerson])
        let abandoned = VocabFixture.sampleEntries.first { $0.headword == "abandoned" }!
        #expect(abandoned.lemma == "abandon")
        #expect(abandoned.forms.isEmpty)
    }

    @Test func frequencyRank() {
        #expect(abandon.frequencyRank == 2057)
        #expect(WordEntry(headword: "x", translation: "n. x", bnc: 0, frq: 0).frequencyRank == nil)
        #expect(WordEntry(headword: "x", translation: "n. x", bnc: nil, frq: 40).frequencyRank == 40)
    }

    @Test func partOfSpeechAbbreviations() {
        #expect(PartOfSpeech(abbreviation: "adj.") == .adjective)
        #expect(PartOfSpeech(abbreviation: "a.") == .adjective)
        #expect(PartOfSpeech(abbreviation: "ad.") == .adverb)
        #expect(PartOfSpeech(abbreviation: "VI.") == .verb)
        #expect(PartOfSpeech(abbreviation: "abandon") == nil)
        #expect(PartOfSpeech(abbreviation: "vt.vi.") == .verb)
        #expect(PartOfSpeech(abbreviation: "vt.&vi.") == .verb)
        #expect(PartOfSpeech(abbreviation: "a.&n.") == .adjective)
        #expect(PartOfSpeech(abbreviation: "vt.vi") == nil)
        #expect(PartOfSpeech(abbreviation: "x.y.") == nil)
        #expect(PartOfSpeech(abbreviation: "a.m.") == nil)
        #expect(PartOfSpeech(abbreviation: "n.x.") == nil)
        let combined = WordEntry(headword: "scrutinize", translation: "vt.vi. 细看, 仔细检查")
        #expect(combined.primaryPartOfSpeech == .verb)
        #expect(combined.senses.first?.meanings == ["细看", "仔细检查"])
    }
}

@Suite struct ECDICTTests {
    let selection = ECDICT.select(tag: "ky", from: VocabFixture.ecdictSample)

    @Test func selectsTaggedWordsInFrequencyOrder() {
        #expect(selection.rowsRead == 11)
        // The lower of the two ranks: ability 375, core 1500, desert 2000, abandon 2057, swiftly 3000, abandoned 5271, china 6000.
        #expect(selection.entries.map(\.headword) == ["ability", "core", "desert", "abandon", "swiftly", "abandoned", "china"])
        #expect(!selection.entries.contains { $0.headword == "zebra" })
    }

    @Test func skipsWhatCantBeStudied() {
        let skipped = Dictionary(grouping: selection.skipped, by: \.reason).mapValues { $0.map(\.word) }
        #expect(skipped[.noTranslation] == ["abbey"])
        #expect(skipped[.notAWord] == ["3D"])
        #expect(skipped[.duplicate] == ["China"])
        #expect(selection.entries.first { $0.headword == "china" }?.display == "china")
        #expect(selection.entries.first { $0.headword == "core" }?.display == "core")
    }

    @Test func readsFields() {
        let abandon = selection.entries.first { $0.headword == "abandon" }!
        #expect(abandon.phonetic == "ә'bændәn")
        #expect(abandon.definition == "n. the trait of lacking restraint\nv. forsake, leave behind")
        #expect(abandon.collins == 3)
        #expect(abandon.oxford)
        #expect(abandon.tags == ["gk", "cet4", "cet6", "ky", "toefl", "gre"])
        #expect(abandon.bnc == 2057)
        #expect(abandon.frq == 2182)
    }

    @Test func wordCheck() {
        #expect(ECDICT.isWord("well-known"))
        #expect(ECDICT.isWord("a.m."))
        #expect(ECDICT.isWord("give up"))
        #expect(ECDICT.isWord("o'clock"))
        #expect(!ECDICT.isWord("3D"))
        #expect(!ECDICT.isWord("-ism"))
        #expect(!ECDICT.isWord("café"))
        #expect(!ECDICT.isWord(""))
    }

    @Test func contentFileRoundTrip() throws {
        let text = ContentFile.write(selection.entries)
        let lines = text.split(separator: "\n")
        #expect(lines.count == selection.entries.count + 1)
        #expect(lines[0] == "position,word,phonetic,translation,definition,pos,collins,oxford,tags,bnc,frq,exchange")
        #expect(lines[1].hasPrefix("1,ability,"))
        #expect(try ContentFile.read(text) == selection.entries)
        #expect(throws: ContentFile.ReadError.missingColumns(["position", "tags"])) {
            try ContentFile.read("word,phonetic,translation,definition,pos,collins,oxford,bnc,frq,exchange\n")
        }
        #expect(try ContentFile.read("") == [])
    }
}

@Suite struct WordListImportTests {
    let known: Set<String> = ["abandon", "ability", "give up", "a.m.", "desert"]
    func preview(_ text: String, _ format: WordListPreview.Format? = nil) -> WordListPreview {
        WordListImport.preview(text, format: format) { known.contains($0) }
    }

    @Test func plainTextLines() {
        let result = preview("""
        # my list
        abandon
        1. Ability
        3、give up 放弃

        desert v. 抛弃；遗弃
        serendipity\t意外发现珍奇事物的本领
        a.m.
        abandon
        zzyzx
        你好
        abandon放弃
        """)
        #expect(result.format == .text)
        #expect(result.words.map(\.headword) == ["abandon", "ability", "give up", "desert", "serendipity", "a.m."])
        #expect(result.words.map(\.meaning) == [nil, nil, "放弃", "v. 抛弃；遗弃", "意外发现珍奇事物的本领", nil])
        #expect(result.words.map(\.line) == [2, 3, 4, 6, 7, 8])
        #expect(result.words[1].display == "Ability")
        #expect(result.words.map(\.inDictionary) == [true, true, true, true, false, true])
        #expect(result.duplicates.map(\.line) == [9, 12])
        #expect(result.unknown.map(\.headword) == ["zzyzx"])
        #expect(result.unreadable == [11])
        #expect(result.matchedCount == 3)
        #expect(result.customCount == 3)
    }

    @Test func csvWithChineseHeaders() {
        let result = preview("""
        单词,释义,音标,例句,例句翻译
        abandon,,,"He abandoned the project, sadly.",他放弃了这个项目。
        tenacious,"adj. 顽强的, 坚韧的",/tɪˈneɪʃəs/,,
        12345,数字,,,
        unknownword,,,,
        """)
        #expect(result.format == .csv)
        #expect(result.words.map(\.headword) == ["abandon", "tenacious"])
        #expect(result.words[0].meaning == nil)
        #expect(result.words[0].example == "He abandoned the project, sadly.")
        #expect(result.words[0].exampleTranslation == "他放弃了这个项目。")
        #expect(result.words[1].meaning == "adj. 顽强的, 坚韧的")
        #expect(result.words[1].phonetic == "/tɪˈneɪʃəs/")
        #expect(result.unreadable == [4])
        #expect(result.unknown.map(\.headword) == ["unknownword"])
    }

    @Test func csvWithEnglishHeadersAndTabs() {
        let result = preview("word\tmeaning\tpos\nabandon\t放弃\tv.\n")
        #expect(result.format == .csv)
        #expect(result.words.first?.meaning == "放弃")
        #expect(result.words.first?.partOfSpeech == "v.")
    }

    @Test func formatDetection() {
        #expect(WordListImport.detectFormat("word,meaning\nabandon,放弃") == .csv)
        #expect(WordListImport.detectFormat("\n单词，释义\nabandon，放弃") == .csv)
        #expect(WordListImport.detectFormat("abandon\nability") == .text)
        #expect(WordListImport.detectFormat("abandon, 放弃") == .text)
        #expect(WordListImport.detectFormat("") == .text)
        #expect(preview("word\n", .csv).words.isEmpty)
        #expect(preview("no header here\n", .csv).words.isEmpty)
    }

    @Test func importedWordEntries() {
        let dictionary = WordEntry(headword: "abandon", phonetic: "ə'bændən", translation: "vt. 放弃")
        let plain = ImportedWord(headword: "abandon", display: "abandon", meaning: nil, inDictionary: true, line: 1)
        #expect(plain.entry(dictionary: dictionary) == dictionary)
        var custom = ImportedWord(headword: "abandon", display: "abandon", meaning: "v. 丢下", inDictionary: true, line: 1)
        custom.phonetic = "/əˈbændən/"
        let entry = custom.entry(dictionary: dictionary)
        #expect(entry.translation == "v. 丢下")
        #expect(entry.phonetic == "əˈbændən")
        let unknown = ImportedWord(headword: "tenacious", display: "Tenacious", meaning: "adj. 顽强的", inDictionary: false, line: 2)
        #expect(unknown.entry(dictionary: nil).display == "Tenacious")
        #expect(unknown.entry(dictionary: nil).shortMeaning == "adj. 顽强的")
    }
}

@Suite struct WordSearchTests {
    let search = WordSearch(entries: VocabFixture.sampleEntries + [
        WordEntry(headword: "abandonment", translation: "n. 放弃, 遗弃", frq: 9000),
        WordEntry(headword: "abound", translation: "vi. 大量存在", frq: 8000),
    ])

    @Test func englishQueries() {
        let results = search.search("abandon")
        #expect(results.first == WordSearch.Result(headword: "abandon", match: .exact))
        #expect(results.map(\.headword) == ["abandon", "abandoned", "abandonment"])
        #expect(search.search("ABANDONS").first == WordSearch.Result(headword: "abandon", match: .form("abandons")))
        #expect(search.search("aba").map(\.headword) == ["abandon", "abandoned", "abandonment"])
        #expect(search.search("ab").map(\.headword) == ["abound", "ability", "abandon", "abandoned", "abandonment"])
        #expect(search.search("ab", limit: 2).count == 2)
    }

    @Test func typos() {
        #expect(search.search("abandn") == [WordSearch.Result(headword: "abandon", match: .similar)])
        #expect(search.search("abiltiy") == [WordSearch.Result(headword: "ability", match: .similar)])
        #expect(search.search("desret").map(\.headword) == ["desert"])
        #expect(search.search("abx").isEmpty)
        #expect(isOneEditAway("abandon", "abandons"))
        #expect(isOneEditAway("abandons", "abandon"))
        #expect(isOneEditAway("abandon", "abamdon"))
        #expect(!isOneEditAway("abandon", "abandon"))
        #expect(!isOneEditAway("abandon", "abnadno"))
    }

    @Test func chineseQueries() {
        let results = search.search("放弃")
        #expect(results.allSatisfy { $0.match == .meaning })
        // Exact meaning first (desert 2000, abandon 2057, abandonment 9000), then the rest.
        #expect(results.map(\.headword) == ["desert", "abandon", "abandonment"])
        #expect(search.search("遗弃").map(\.headword) == ["desert", "abandon", "abandonment"])
        #expect(search.search("放").map(\.headword) == ["desert", "abandon", "abandoned", "abandonment"])
        #expect(search.search("  ").isEmpty)
        #expect(search.entry("Abandon")?.headword == "abandon")
    }
}

@Suite struct ChoiceTests {
    let pool = VocabFixture.sampleEntries + [
        WordEntry(headword: "adopt", translation: "vt. 采用, 收养"),
        WordEntry(headword: "adapt", translation: "vt. 使适应, 改编"),
        WordEntry(headword: "abolish", translation: "vt. 废除, 废止"),
        WordEntry(headword: "abundant", translation: "a. 丰富的, 充裕的"),
    ]
    var abandon: WordEntry { pool.first { $0.headword == "abandon" }! }

    @Test func questionShape() throws {
        let question = try #require(ChoiceGenerator.question(for: abandon, pool: pool, seed: 42))
        #expect(question.options.count == 4)
        #expect(Set(question.options).count == 4)
        #expect(question.options[question.correctIndex] == "vt. 放弃，抛弃，遗弃")
        #expect(question.isCorrect(question.correctIndex))
        // desert shares 抛弃/遗弃/放弃, so it would be a second right answer.
        #expect(!question.options.contains("vt. 抛弃, 遗弃, 放弃"))
        // Enough other verbs: every wrong option is a verb.
        #expect(question.options.allSatisfy { $0.hasPrefix("vt.") })
    }

    @Test func sameSeedSameQuestion() {
        let a = ChoiceGenerator.question(for: abandon, pool: pool, seed: 7)
        let b = ChoiceGenerator.question(for: abandon, pool: pool, seed: 7)
        #expect(a == b)
        let others = (1...20).compactMap { ChoiceGenerator.question(for: abandon, pool: pool, seed: UInt64($0)) }
        #expect(Set(others.map(\.correctIndex)).count > 1)
    }

    @Test func smallPools() {
        let tiny = [abandon, WordEntry(headword: "abundant", translation: "a. 丰富的")]
        let question = ChoiceGenerator.question(for: abandon, pool: tiny, seed: 1)
        #expect(question?.options.count == 2)
        #expect(ChoiceGenerator.question(for: abandon, pool: [abandon], seed: 1) == nil)
        #expect(ChoiceGenerator.question(for: WordEntry(headword: "x", translation: ""), pool: pool, seed: 1) == nil)
    }

    @Test func seededRandomIsStable() {
        var random = SeededRandom(seed: 1)
        #expect(random.next() == 0x910A_2DEC_8902_5CC1)
        #expect(SeededRandom.seed("abc") == SeededRandom.seed("abc"))
        #expect(SeededRandom.seed("a", "bc") != SeededRandom.seed("ab", "c"))
        #expect(SeededRandom.seed("") == 0xCBF2_9CE4_8422_2325)
    }
}

@Suite struct AnswerGraderTests {
    @Test func ratings() {
        #expect(AnswerGrader.rating(for: .recognition(.hard)) == .hard)
        #expect(AnswerGrader.rating(for: .choice(correct: true)) == .good)
        #expect(AnswerGrader.rating(for: .choice(correct: false)) == .again)
        #expect(AnswerGrader.rating(for: .spelling(.correct)) == .good)
        #expect(AnswerGrader.rating(for: .spelling(.correctWithHint)) == .hard)
        #expect(AnswerGrader.rating(for: .spelling(.wrong)) == .again)
        #expect(AnswerGrader.rating(for: .spelling(.gaveUp)) == .again)
        #expect(AnswerGrader.rating(for: .alreadyKnown) == .easy)
    }

    @Test func spelling() {
        #expect(AnswerGrader.isCorrectSpelling(" Abandon ", of: "abandon"))
        #expect(AnswerGrader.isCorrectSpelling("ａｂａｎｄｏｎ", of: "abandon"))
        #expect(AnswerGrader.isCorrectSpelling("give\u{3000} up", of: "give up"))
        #expect(AnswerGrader.isCorrectSpelling("don’t", of: "don't"))
        #expect(AnswerGrader.isCorrectSpelling("china", of: "China"))
        #expect(!AnswerGrader.isCorrectSpelling("abandom", of: "abandon"))
        #expect(!AnswerGrader.isCorrectSpelling("", of: ""))
    }

    @Test func hints() {
        #expect(AnswerGrader.hint(for: "abandon") == "a______")
        #expect(AnswerGrader.hint(for: "give up") == "g___ __")
        #expect(AnswerGrader.hint(for: "well-known") == "w___-_____")
    }

    @Test func differences() {
        #expect(AnswerGrader.diff(typed: "abandon", word: "abandon") == [.same("abandon")])
        #expect(AnswerGrader.diff(typed: "abandn", word: "abandon") == [.same("aband"), .missing("o"), .same("n")])
        #expect(AnswerGrader.diff(typed: "abanddon", word: "abandon") == [.same("aband"), .extra("d"), .same("on")])
        #expect(AnswerGrader.diff(typed: "abamdon", word: "abandon") == [.same("aba"), .missing("n"), .extra("m"), .same("don")])
        #expect(AnswerGrader.diff(typed: "", word: "ab") == [.missing("ab")])
        #expect(AnswerGrader.diff(typed: "xy", word: "") == [.extra("xy")])
    }
}
