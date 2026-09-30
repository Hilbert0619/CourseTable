import Foundation

/// A multiple-choice question: pick the meaning of `headword`.
public struct ChoiceQuestion: Hashable, Sendable {
    public var headword: String
    /// Short meanings (see `WordEntry.shortMeaning`), one of them correct.
    public var options: [String]
    public var correctIndex: Int

    public func isCorrect(_ index: Int) -> Bool { index == correctIndex }
}

/// The words wrong options are drawn from (usually the book being studied), parsed once so that
/// making a question only shuffles and compares.
public struct ChoicePool: Sendable {
    struct Candidate: Sendable {
        var headword: String
        var text: String
        var tokens: Set<String>
        var partOfSpeech: PartOfSpeech?

        init(_ entry: WordEntry) {
            headword = entry.headword
            text = entry.shortMeaning
            tokens = entry.meaningTokens
            partOfSpeech = entry.primaryPartOfSpeech
        }
    }

    let candidates: [Candidate]

    public init(_ entries: [WordEntry]) {
        candidates = entries.map(Candidate.init).filter { !$0.text.isEmpty }
    }

    public var count: Int { candidates.count }

    /// A question with up to `optionCount` options. Wrong options have the same part of speech as the
    /// word where possible, and never share a meaning with it or show the same text. The same seed
    /// always gives the same question. `nil` if there is no usable wrong option or the word has no
    /// meaning to show.
    public func question(for target: WordEntry, seed: UInt64, optionCount: Int = 4) -> ChoiceQuestion? {
        let answer = Candidate(target)
        guard !answer.text.isEmpty, optionCount >= 2 else { return nil }
        var random = SeededRandom(seed: seed)
        var samePOS: [String] = []
        var others: [String] = []
        var usedTexts: Set<String> = [answer.text]
        let wanted = optionCount - 1
        for index in candidates.indices.shuffled(using: &random) where samePOS.count < wanted {
            let candidate = candidates[index]
            guard candidate.headword != answer.headword, !usedTexts.contains(candidate.text),
                  candidate.tokens.isDisjoint(with: answer.tokens) else { continue }
            if answer.partOfSpeech != nil && candidate.partOfSpeech == answer.partOfSpeech {
                samePOS.append(candidate.text)
                usedTexts.insert(candidate.text)
            } else if others.count < wanted {
                others.append(candidate.text)
                usedTexts.insert(candidate.text)
            }
        }
        let wrong = Array((samePOS + others).prefix(wanted))
        guard !wrong.isEmpty else { return nil }
        let options = ([answer.text] + wrong).shuffled(using: &random)
        return ChoiceQuestion(headword: target.headword, options: options, correctIndex: options.firstIndex(of: answer.text)!)
    }
}

public enum ChoiceGenerator {
    /// One question from an unparsed pool; for many questions build a `ChoicePool` once instead.
    public static func question(for target: WordEntry, pool: [WordEntry], seed: UInt64,
                                optionCount: Int = 4) -> ChoiceQuestion? {
        ChoicePool(pool).question(for: target, seed: seed, optionCount: optionCount)
    }
}

/// SplitMix64: a small, fast generator whose sequence depends only on the seed, on every platform.
public struct SeededRandom: RandomNumberGenerator, Sendable {
    private var state: UInt64

    public init(seed: UInt64) {
        state = seed
    }

    public mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// A seed from text, the same on every run and device (unlike `Hasher`): FNV-1a over UTF-8.
    public static func seed(_ parts: String...) -> UInt64 {
        var hash: UInt64 = 0xCBF2_9CE4_8422_2325
        for byte in parts.joined(separator: "\u{1F}").utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 0x0000_0100_0000_01B3
        }
        return hash
    }
}
