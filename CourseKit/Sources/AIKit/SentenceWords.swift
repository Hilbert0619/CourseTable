import Foundation
import VocabKit

/// The book's words in a pasted sentence, in order of appearance: "abandoned" finds *abandon*, "studies" finds
/// *study*. Shown under a sentence analysis so each can be opened (and favourited) with one tap.
public enum SentenceWords {
    public static let minimumLength = 4
    public static let maximumCount = 15

    public static func entries(in text: String, lookup: (String) -> WordEntry?) -> [WordEntry] {
        var seen: Set<String> = []
        var result: [WordEntry] = []
        for token in tokens(text) where token.count >= minimumLength {
            guard let entry = candidates(token).lazy.compactMap(lookup).first, seen.insert(entry.headword).inserted else {
                continue
            }
            result.append(entry)
            if result.count == maximumCount { break }
        }
        return result
    }

    static func tokens(_ text: String) -> [String] {
        text.lowercased()
            .split { !($0.isASCII && $0.isLetter) && $0 != "-" }
            .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "-")) }
            .filter { !$0.isEmpty }
    }

    /// The word itself, then the base forms it could be an inflection of.
    static func candidates(_ word: String) -> [String] {
        var forms = [word]
        func add(_ form: String) { if form.count >= 2, !forms.contains(form) { forms.append(form) } }
        func stem(_ suffix: String) -> String? { word.hasSuffix(suffix) ? String(word.dropLast(suffix.count)) : nil }
        if let base = stem("ies") { add(base + "y") }
        if let base = stem("ied") { add(base + "y") }
        if let base = stem("es") { add(base) }
        if let base = stem("s"), !word.hasSuffix("ss") { add(base) }
        for suffix in ["ed", "ing", "er", "est", "ly"] {
            guard let base = stem(suffix) else { continue }
            add(base)
            add(base + "e")
            if let last = base.last, base.dropLast().last == last { add(String(base.dropLast())) }  // stopped → stop
            if suffix == "ly", base.hasSuffix("i") { add(base.dropLast() + "y") }  // happily → happy
        }
        return forms
    }
}
