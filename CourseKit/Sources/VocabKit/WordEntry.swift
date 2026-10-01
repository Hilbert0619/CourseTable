import Foundation

/// A dictionary entry: the shared, read-only facts about a word (from ECDICT, or typed in by the user
/// for an imported word the dictionary doesn't know).
public struct WordEntry: Hashable, Sendable, Codable {
    /// Lookup key, see `Headword.key`.
    public var headword: String
    /// The word as the source writes it, e.g. `China`, `e.g.`.
    public var display: String
    /// IPA without slashes, e.g. `ə'bændən`. May be empty.
    public var phonetic: String
    /// Chinese meanings, one line per part of speech, e.g. `vt. 放弃, 抛弃\nn. 放任`.
    public var translation: String
    /// English definitions, one line per sense. May be empty.
    public var definition: String
    /// ECDICT's part-of-speech mix, e.g. `n:46/v:54`. May be empty.
    public var posMix: String
    /// Collins COBUILD stars, 0–5.
    public var collins: Int
    /// In the Oxford 3000 core words.
    public var oxford: Bool
    /// Exam lists, e.g. `["cet4", "cet6", "ky"]`.
    public var tags: [String]
    /// Rank in the British National Corpus word list (`nil` if not ranked).
    public var bnc: Int?
    /// Rank in the contemporary corpus word list (`nil` if not ranked).
    public var frq: Int?
    /// Inflections in ECDICT form, e.g. `p:abandoned/d:abandoned/i:abandoning/3:abandons`.
    public var exchange: String

    public init(
        headword: String,
        display: String? = nil,
        phonetic: String = "",
        translation: String,
        definition: String = "",
        posMix: String = "",
        collins: Int = 0,
        oxford: Bool = false,
        tags: [String] = [],
        bnc: Int? = nil,
        frq: Int? = nil,
        exchange: String = ""
    ) {
        self.headword = Headword.key(headword)
        self.display = (display ?? headword).trimmingCharacters(in: .whitespaces)
        self.phonetic = phonetic
        self.translation = translation
        self.definition = definition
        self.posMix = posMix
        self.collins = collins
        self.oxford = oxford
        self.tags = tags
        self.bnc = bnc.flatMap { $0 > 0 ? $0 : nil }
        self.frq = frq.flatMap { $0 > 0 ? $0 : nil }
        self.exchange = exchange
    }

    /// The Chinese meanings, line by line.
    public var senses: [SenseLine] {
        translation.split(whereSeparator: \.isNewline).compactMap { SenseLine(String($0)) }
    }

    /// Inflected forms (not the lemma entries), e.g. past tense `abandoned`.
    public var forms: [WordForm] {
        WordForm.parse(exchange).filter { $0.kind != .lemma }
    }

    /// For an inflected entry (e.g. `abandoned`), the word it comes from.
    public var lemma: String? {
        WordForm.parse(exchange).first { $0.kind == .lemma }?.word
    }

    /// The part of speech of the first meaning line that has one.
    public var primaryPartOfSpeech: PartOfSpeech? {
        senses.lazy.compactMap(\.partOfSpeech).first
    }

    /// A short meaning for quiz options and lists: the first meaning line with at most three meanings,
    /// e.g. `vt. 放弃，抛弃，遗弃` (full-width commas, as on the word page).
    public var shortMeaning: String {
        let lines = senses
        guard let line = lines.first(where: { $0.label == nil }) ?? lines.first else { return "" }
        return line.formatted(maxMeanings: 3)
    }

    /// Every meaning as bare words, for telling apart words that mean the same thing.
    public var meaningTokens: Set<String> {
        Set(senses.flatMap(\.meanings).map(SenseLine.bare).filter { !$0.isEmpty })
    }

    /// The lower of the two corpus ranks; words without a rank sort last.
    public var frequencyRank: Int? {
        switch (frq, bnc) {
        case let (f?, b?): min(f, b)
        case let (f?, nil): f
        case let (nil, b?): b
        case (nil, nil): nil
        }
    }
}

/// One line of Chinese meanings: `vt. 放弃, 抛弃` or `[计] 异常中止`.
public struct SenseLine: Hashable, Sendable {
    /// The abbreviation as written, e.g. `vt.`, or `nil`.
    public var partOfSpeechTag: String?
    /// A subject label in brackets, e.g. `计` or `网络`, or `nil`.
    public var label: String?
    public var meanings: [String]

    init?(_ line: String) {
        var rest = Substring(line.trimmingCharacters(in: .whitespaces))
        if rest.first == "[", let close = rest.firstIndex(of: "]") {
            label = String(rest[rest.index(after: rest.startIndex)..<close]).trimmingCharacters(in: .whitespaces)
            rest = rest[rest.index(after: close)...].drop { $0 == " " }
        }
        if let space = rest.firstIndex(of: " "), PartOfSpeech(abbreviation: String(rest[..<space])) != nil {
            partOfSpeechTag = String(rest[..<space])
            rest = rest[space...].drop { $0 == " " }
        } else if PartOfSpeech(abbreviation: String(rest)) != nil {
            return nil
        }
        meanings = rest.split(whereSeparator: { "，,；;、".contains($0) })
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        if meanings.isEmpty { return nil }
    }

    public var partOfSpeech: PartOfSpeech? { partOfSpeechTag.flatMap(PartOfSpeech.init(abbreviation:)) }

    /// `vt. 放弃，抛弃，遗弃`, keeping at most `maxMeanings` meanings.
    public func formatted(maxMeanings: Int = .max) -> String {
        let text = meanings.prefix(maxMeanings).joined(separator: "，")
        return [label.map { "[\($0)]" }, partOfSpeechTag, text].compactMap { $0 }.joined(separator: " ")
    }

    /// A meaning without notes in brackets: `（使）放弃` → `放弃`.
    static func bare(_ meaning: String) -> String {
        var result = ""
        var depth = 0
        for character in meaning {
            if "(（<《".contains(character) { depth += 1 } else if ")）>》".contains(character) { depth = max(0, depth - 1) } else if depth == 0 { result.append(character) }
        }
        return result.trimmingCharacters(in: .whitespaces)
    }
}

/// Part of speech, from the abbreviations used in Chinese dictionaries.
public enum PartOfSpeech: String, Codable, Sendable, CaseIterable {
    case noun, verb, adjective, adverb, preposition, conjunction, pronoun, numeral, interjection, article, auxiliary, abbreviation

    /// Recognises combined tags like `vt.vi.` or `vt.&vi.` by their first part, as long as every part is
    /// a tag (so `a.m.` stays a word).
    public init?(abbreviation: String) {
        if let single = Self(single: abbreviation) {
            self = single
            return
        }
        let parts = abbreviation.split(whereSeparator: { ".&/".contains($0) }).map { Self(single: $0 + ".") }
        guard abbreviation.hasSuffix("."), parts.count > 1, !parts.contains(nil), let first = parts[0] else { return nil }
        self = first
    }

    private init?(single abbreviation: String) {
        switch abbreviation.lowercased() {
        case "n.", "pl.": self = .noun
        case "v.", "vt.", "vi.": self = .verb
        case "a.", "adj.": self = .adjective
        case "ad.", "adv.": self = .adverb
        case "prep.": self = .preposition
        case "conj.": self = .conjunction
        case "pron.": self = .pronoun
        case "num.": self = .numeral
        case "int.", "interj.": self = .interjection
        case "art.": self = .article
        case "aux.", "modal.": self = .auxiliary
        case "abbr.": self = .abbreviation
        default: return nil
        }
    }
}

/// An inflected form listed in ECDICT's `exchange` field.
public struct WordForm: Hashable, Sendable {
    public enum Kind: String, Sendable, CaseIterable {
        case past = "p"
        case pastParticiple = "d"
        case presentParticiple = "i"
        case thirdPerson = "3"
        case comparative = "r"
        case superlative = "t"
        case plural = "s"
        /// On an inflected entry: the word it is a form of.
        case lemma = "0"
    }

    public var kind: Kind
    public var word: String

    /// `p:abandoned/i:abandoning` → past `abandoned`, present participle `abandoning`. Unknown kinds
    /// (like ECDICT's `1:` markers) are skipped.
    static func parse(_ exchange: String) -> [WordForm] {
        exchange.split(separator: "/").compactMap { item in
            let parts = item.split(separator: ":", maxSplits: 1)
            guard parts.count == 2, let kind = Kind(rawValue: String(parts[0])) else { return nil }
            let word = parts[1].trimmingCharacters(in: .whitespaces)
            return word.isEmpty ? nil : WordForm(kind: kind, word: word)
        }
    }
}
