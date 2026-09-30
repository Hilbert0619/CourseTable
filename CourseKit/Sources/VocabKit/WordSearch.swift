import Foundation

/// Searches the words of the downloaded books on the device. English queries match the word, its
/// inflections (`abandoned` finds `abandon`), words starting with the query, and near-misses one typo
/// away. Queries with Chinese match the meanings.
public struct WordSearch: Sendable {
    public enum Match: Hashable, Sendable {
        case exact
        /// The query is an inflected form of this word.
        case form(String)
        case prefix
        /// One letter off: a typo, a missing or extra letter, or two letters swapped.
        case similar
        case meaning
    }

    public struct Result: Hashable, Sendable {
        public var headword: String
        public var match: Match
    }

    private let entries: [String: WordEntry]
    /// Keys in alphabetical order, for prefix lookups.
    private let sortedKeys: [String]
    /// Inflected form → words it belongs to.
    private let lemmas: [String: [String]]

    public init(entries: [WordEntry]) {
        var byKey: [String: WordEntry] = [:]
        var lemmas: [String: [String]] = [:]
        for entry in entries where byKey[entry.headword] == nil {
            byKey[entry.headword] = entry
            for form in entry.forms {
                lemmas[Headword.key(form.word), default: []].append(entry.headword)
            }
        }
        self.entries = byKey
        self.sortedKeys = byKey.keys.sorted()
        self.lemmas = lemmas
    }

    public func entry(_ headword: String) -> WordEntry? {
        entries[Headword.key(headword)]
    }

    public func search(_ query: String, limit: Int = 50) -> [Result] {
        let key = Headword.key(query)
        guard !key.isEmpty, limit > 0 else { return [] }
        if key.unicodeScalars.contains(where: { !$0.isASCII }) {
            return Array(meaningResults(key).prefix(limit))
        }

        var results: [Result] = []
        var seen: Set<String> = []
        func add(_ headword: String, _ match: Match) {
            if seen.insert(headword).inserted { results.append(Result(headword: headword, match: match)) }
        }
        if entries[key] != nil { add(key, .exact) }
        for lemma in (lemmas[key] ?? []).sorted() { add(lemma, .form(key)) }
        let prefixed = prefixMatches(key).sorted { a, b in
            (a.count, rank(a), a) < (b.count, rank(b), b)
        }
        for headword in prefixed where results.count < limit { add(headword, .prefix) }
        if results.count < limit && key.count >= 4 {
            let similar = sortedKeys.filter { isOneEditAway($0, key) }.sorted { (rank($0), $0) < (rank($1), $1) }
            for headword in similar where results.count < limit { add(headword, .similar) }
        }
        return Array(results.prefix(limit))
    }

    private func rank(_ headword: String) -> Int {
        entries[headword]?.frequencyRank ?? .max
    }

    private func prefixMatches(_ prefix: String) -> [String] {
        // Binary search for the first key ≥ prefix, then walk while keys still start with it.
        var low = 0
        var high = sortedKeys.count
        while low < high {
            let mid = (low + high) / 2
            if sortedKeys[mid] < prefix { low = mid + 1 } else { high = mid }
        }
        var matches: [String] = []
        var index = low
        while index < sortedKeys.count, sortedKeys[index].hasPrefix(prefix) {
            if sortedKeys[index] != prefix { matches.append(sortedKeys[index]) }
            index += 1
        }
        return matches
    }

    /// Words whose meanings contain the query: a meaning that is exactly the query first, then one
    /// that starts with it, then any that contain it; more frequent words first within each group.
    private func meaningResults(_ query: String) -> [Result] {
        var scored: [(score: Int, rank: Int, headword: String)] = []
        for entry in entries.values where entry.translation.contains(query) {
            let meanings = entry.senses.flatMap(\.meanings).map(SenseLine.bare)
            let score = meanings.contains(query) ? 0 : meanings.contains { $0.hasPrefix(query) } ? 1 : 2
            scored.append((score, entry.frequencyRank ?? .max, entry.headword))
        }
        return scored.sorted { ($0.score, $0.rank, $0.headword) < ($1.score, $1.rank, $1.headword) }
            .map { Result(headword: $0.headword, match: .meaning) }
    }
}

/// True if `a` and `b` differ by one inserted, deleted or replaced character, or two neighbours swapped.
func isOneEditAway(_ a: String, _ b: String) -> Bool {
    let x = Array(a)
    let y = Array(b)
    guard abs(x.count - y.count) <= 1, x != y else { return false }
    var i = 0
    while i < min(x.count, y.count) && x[i] == y[i] { i += 1 }
    if x.count == y.count {
        // Replace one character, or swap it with the next.
        if x[(i + 1)...] == y[(i + 1)...] { return true }
        return i + 1 < x.count && x[i] == y[i + 1] && x[i + 1] == y[i] && x[(i + 2)...] == y[(i + 2)...]
    }
    // One extra character in the longer word at position i.
    return x.count > y.count ? x[(i + 1)...] == y[i...] : x[i...] == y[(i + 1)...]
}
