import Foundation

/// What the learner did with a question.
public enum Answer: Hashable, Sendable {
    /// Recognition: the learner rated themselves.
    case recognition(Rating)
    /// Multiple choice: right or wrong.
    case choice(correct: Bool)
    case spelling(SpellingResult)
    /// "I know this" on a new word's page: the word skips the learning steps.
    case alreadyKnown

    public enum SpellingResult: Hashable, Sendable {
        case correct
        /// Correct after showing the first letter.
        case correctWithHint
        case wrong
        case gaveUp
    }
}

/// Turns answers into FSRS ratings (the table in docs/vocabulary-spec.md §7) and checks spelling.
/// Answer time is recorded but doesn't change the rating.
public enum AnswerGrader {
    public static func rating(for answer: Answer) -> Rating {
        switch answer {
        case .recognition(let rating): rating
        case .choice(let correct): correct ? .good : .again
        case .spelling(.correct): .good
        case .spelling(.correctWithHint): .hard
        case .spelling(.wrong), .spelling(.gaveUp): .again
        case .alreadyKnown: .easy
        }
    }

    /// Case, surrounding spaces, repeated spaces, curly apostrophes and full-width letters (from a
    /// Chinese keyboard) don't count as mistakes.
    public static func isCorrectSpelling(_ typed: String, of word: String) -> Bool {
        let answer = normalize(word)
        return !answer.isEmpty && normalize(typed) == answer
    }

    static func normalize(_ text: String) -> String {
        let halfWidth = String(String.UnicodeScalarView(text.unicodeScalars.map { scalar in
            // Full-width ASCII (U+FF01…U+FF5E) and the ideographic space.
            if (0xFF01...0xFF5E).contains(scalar.value) { return Unicode.Scalar(scalar.value - 0xFEE0)! }
            if scalar.value == 0x3000 { return " " }
            return scalar
        }))
        return Headword.key(halfWidth)
    }

    /// The first letter, then one underscore per remaining letter, keeping spaces and punctuation:
    /// `give up` → `g___ __`.
    public static func hint(for word: String) -> String {
        var result = ""
        var first = true
        for character in word {
            if character.isLetter {
                result.append(first ? character : "_")
                first = false
            } else {
                result.append(character)
            }
        }
        return result
    }

    /// Where a wrong spelling differs from the word, for showing after the answer.
    public static func diff(typed: String, word: String) -> [DiffPart] {
        let a = Array(normalize(typed))
        let b = Array(Headword.key(word))
        // Longest common subsequence table, then walk it to build the parts.
        var table = Array(repeating: Array(repeating: 0, count: b.count + 1), count: a.count + 1)
        for i in stride(from: a.count - 1, through: 0, by: -1) {
            for j in stride(from: b.count - 1, through: 0, by: -1) {
                table[i][j] = a[i] == b[j] ? table[i + 1][j + 1] + 1 : max(table[i + 1][j], table[i][j + 1])
            }
        }
        var parts: [DiffPart] = []
        func append(_ character: Character, _ make: (String) -> DiffPart) {
            if let last = parts.last, let merged = last.appending(character, ifSameKindAs: make("")) {
                parts[parts.count - 1] = merged
            } else {
                parts.append(make(String(character)))
            }
        }
        var i = 0
        var j = 0
        while i < a.count || j < b.count {
            if i < a.count, j < b.count, a[i] == b[j] {
                append(a[i], DiffPart.same)
                i += 1
                j += 1
            } else if j < b.count, i == a.count || table[i][j + 1] >= table[i + 1][j] {
                append(b[j], DiffPart.missing)
                j += 1
            } else {
                append(a[i], DiffPart.extra)
                i += 1
            }
        }
        return parts
    }
}

/// Part of a spelling comparison.
public enum DiffPart: Hashable, Sendable {
    /// Typed correctly.
    case same(String)
    /// In the word but not typed.
    case missing(String)
    /// Typed but not in the word.
    case extra(String)

    fileprivate func appending(_ character: Character, ifSameKindAs other: DiffPart) -> DiffPart? {
        switch (self, other) {
        case (.same(let text), .same): .same(text + String(character))
        case (.missing(let text), .missing): .missing(text + String(character))
        case (.extra(let text), .extra): .extra(text + String(character))
        default: nil
        }
    }
}
