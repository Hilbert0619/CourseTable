import Foundation
import Testing
@testable import VocabKit

/// Checks on the committed word list, content/kaoyan-ecdict.csv (built by `vocab-content`).
@Suite struct RealContentTests {
    static let url = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("content/kaoyan-ecdict.csv")
    static let entries: [WordEntry] = {
        guard let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return (try? ContentFile.read(text)) ?? []
    }()
    var entries: [WordEntry] { Self.entries }

    @Test func fileReads() throws {
        #expect(entries.count == 4801)
        #expect(Set(entries.map(\.headword)).count == entries.count)
        #expect(entries.allSatisfy { !$0.translation.isEmpty && !$0.shortMeaning.isEmpty })
        #expect(entries.allSatisfy { $0.tags.contains("ky") })
        #expect(entries.prefix(3).map(\.headword) == ["government", "system", "state"])
        let ranks = entries.compactMap(\.frequencyRank)
        #expect(ranks == ranks.sorted())
        #expect(entries.filter { $0.primaryPartOfSpeech == nil }.count <= 10)
    }

    @Test func knownEntries() throws {
        let abandon = try #require(entries.first { $0.headword == "abandon" })
        #expect(abandon.shortMeaning == "vt. 放弃, 抛弃, 遗弃")
        #expect(abandon.forms.contains(WordForm(kind: .past, word: "abandoned")))
        let within = try #require(entries.first { $0.headword == "within" })
        #expect(within.senses.map(\.partOfSpeech) == [.noun, .adverb, .preposition])
    }

    @Test func everyWordGetsAFullQuestion() {
        let pool = ChoicePool(entries)
        #expect(pool.count == entries.count)
        var incomplete: [String] = []
        for (index, entry) in entries.enumerated() {
            guard let question = pool.question(for: entry, seed: UInt64(index)) else {
                incomplete.append(entry.headword)
                continue
            }
            if question.options.count != 4 || Set(question.options).count != 4 || question.options[question.correctIndex] != entry.shortMeaning {
                incomplete.append(entry.headword)
            }
        }
        #expect(incomplete.isEmpty, "\(incomplete.prefix(20))")
    }

    @Test func wrongOptionsMatchThePartOfSpeech() {
        let pool = ChoicePool(entries)
        let byMeaning = Dictionary(entries.map { ($0.shortMeaning, $0) }, uniquingKeysWith: { first, _ in first })
        var mismatched = 0
        var checked = 0
        for (index, entry) in entries.enumerated() where index % 7 == 0 {
            guard let pos = entry.primaryPartOfSpeech, let question = pool.question(for: entry, seed: 99) else { continue }
            for option in question.options where option != entry.shortMeaning {
                checked += 1
                if byMeaning[option]?.primaryPartOfSpeech != pos { mismatched += 1 }
            }
        }
        // Only the rare parts of speech (a dozen prepositions, conjunctions…) run out of same-kind options.
        #expect(checked > 1500)
        #expect(Double(mismatched) / Double(checked) < 0.01)
    }

    @Test func search() {
        let search = WordSearch(entries: entries)
        #expect(search.search("abandon").first == WordSearch.Result(headword: "abandon", match: .exact))
        #expect(search.search("governments").first == WordSearch.Result(headword: "government", match: .form("governments")))
        #expect(search.search("goverment").map(\.headword).contains("government"))
        let chinese = search.search("放弃").map(\.headword)
        #expect(chinese.contains("abandon"))
        #expect(chinese.count >= 3)
    }

    @Test func importAgainstTheDictionary() {
        let known = Set(entries.map(\.headword))
        let preview = WordListImport.preview("abandon\nGovernment\nqwertyuiop\nstate 状态\n") { known.contains($0) }
        #expect(preview.words.map(\.headword) == ["abandon", "government", "state"])
        #expect(preview.unknown.map(\.headword) == ["qwertyuiop"])
    }
}
