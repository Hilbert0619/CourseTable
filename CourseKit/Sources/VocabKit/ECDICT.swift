import Foundation
import CourseKit

/// Reads ECDICT (https://github.com/skywind3000/ECDICT, MIT licence) and picks the words of a word
/// list by tag, e.g. `ky` (考研).
public enum ECDICT {
    /// Why a tagged row was left out.
    public enum SkipReason: String, Sendable {
        case noTranslation = "no Chinese translation"
        case duplicate = "duplicate of an earlier entry"
        case notAWord = "not a word"
    }

    /// Entries that ECDICT only has in a capitalised spelling although their meanings are those of the
    /// ordinary word (`CORE`: 核心, `Pole`: 杆, 极点). They are shown in lower case.
    public static let displayOverrides = [
        "CORE": "core", "FAX": "fax", "Pole": "pole", "Polish": "polish", "Conservative": "conservative", "Saint": "saint",
    ]

    public struct Selection: Sendable {
        /// In frequency order (see `frequencyOrder`).
        public var entries: [WordEntry]
        public var skipped: [(word: String, reason: SkipReason)]
        /// Rows read, not counting the header.
        public var rowsRead: Int
    }

    /// The entries of `csv` (the text of `ecdict.csv`) tagged `tag`. ECDICT keeps each entry on one
    /// line and writes line breaks inside fields as `\n`, so the file is read line by line.
    public static func select(tag: String, from csv: String) -> Selection {
        var lines = csv.split(omittingEmptySubsequences: true, whereSeparator: \.isNewline).makeIterator()
        guard let headerLine = lines.next() else { return Selection(entries: [], skipped: [], rowsRead: 0) }
        let header = fields(of: headerLine).map { $0.lowercased() }
        var rowsRead = 0
        var byKey: [String: WordEntry] = [:]
        var order: [String] = []
        var skipped: [(String, SkipReason)] = []

        while let line = lines.next() {
            rowsRead += 1
            // Cheap filter first: almost no line mentions the tag.
            guard line.contains(tag) else { continue }
            let row = fields(of: line)
            guard var entry = entry(fields: row, header: header), entry.tags.contains(tag) else { continue }
            if let display = displayOverrides[entry.display] { entry.display = display }
            if entry.translation.isEmpty {
                skipped.append((entry.display, .noTranslation))
            } else if !isWord(entry.display) {
                skipped.append((entry.display, .notAWord))
            } else if let existing = byKey[entry.headword] {
                // `China` and `china`: keep the lower-case spelling, which is the common word.
                if entry.display == entry.headword && existing.display != existing.headword {
                    byKey[entry.headword] = entry
                    skipped.append((existing.display, .duplicate))
                } else {
                    skipped.append((entry.display, .duplicate))
                }
            } else {
                byKey[entry.headword] = entry
                order.append(entry.headword)
            }
        }
        return Selection(entries: frequencyOrder(order.compactMap { byKey[$0] }), skipped: skipped, rowsRead: rowsRead)
    }

    /// One ECDICT row as an entry, using the header to find the columns.
    public static func entry(fields: [String], header: [String]) -> WordEntry? {
        func value(_ column: String) -> String {
            guard let index = header.firstIndex(of: column), index < fields.count else { return "" }
            return fields[index].trimmingCharacters(in: .whitespaces)
        }
        let word = value("word")
        guard !word.isEmpty else { return nil }
        return WordEntry(
            headword: word,
            display: word,
            phonetic: value("phonetic"),
            translation: unescape(value("translation")),
            definition: unescape(value("definition")),
            posMix: value("pos"),
            collins: Int(value("collins")) ?? 0,
            oxford: value("oxford") == "1",
            tags: value("tag").split(separator: " ").map(String.init),
            bnc: Int(value("bnc")),
            frq: Int(value("frq")),
            exchange: value("exchange")
        )
    }

    /// Most frequent first (lower corpus rank), unranked words last, ties alphabetical. New words are
    /// introduced in this order, so the words met most often in reading come first.
    public static func frequencyOrder(_ entries: [WordEntry]) -> [WordEntry] {
        entries.sorted { a, b in
            switch (a.frequencyRank, b.frequencyRank) {
            case let (x?, y?) where x != y: x < y
            case (_?, nil): true
            case (nil, _?): false
            default: a.headword < b.headword
            }
        }
    }

    /// ECDICT writes line breaks as `\n` and trims each line.
    static func unescape(_ text: String) -> String {
        text.replacingOccurrences(of: "\\r", with: "")
            .replacingOccurrences(of: "\\n", with: "\n")
            .split(separator: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: "\n")
    }

    /// Letters, with spaces, hyphens, apostrophes or dots between them (`well-known`, `a.m.`, `give up`).
    static func isWord(_ text: String) -> Bool {
        guard let first = text.unicodeScalars.first, CharacterSet.letters.contains(first) else { return false }
        let allowed = CharacterSet.letters.union(CharacterSet(charactersIn: " -'."))
        return text.unicodeScalars.allSatisfy { allowed.contains($0) && $0.isASCII }
    }

    private static func fields(of line: Substring) -> [String] {
        CSV.records(in: String(line), delimiter: CSV.comma).first?.fields ?? []
    }
}

/// The word-list file committed to the repository (`content/kaoyan-ecdict.csv`): one entry per line in
/// study order, line breaks inside fields written as `\n` like ECDICT does.
public enum ContentFile {
    public static let header = ["position", "word", "phonetic", "translation", "definition", "pos",
                                "collins", "oxford", "tags", "bnc", "frq", "exchange"]

    public static func write(_ entries: [WordEntry]) -> String {
        var lines = [header.joined(separator: ",")]
        for (index, entry) in entries.enumerated() {
            let fields = [
                String(index + 1), entry.display, entry.phonetic, escape(entry.translation), escape(entry.definition),
                entry.posMix, String(entry.collins), entry.oxford ? "1" : "0", entry.tags.joined(separator: " "),
                entry.bnc.map(String.init) ?? "", entry.frq.map(String.init) ?? "", entry.exchange,
            ]
            lines.append(fields.map { CSV.escape($0) }.joined(separator: ","))
        }
        return lines.joined(separator: "\n") + "\n"
    }

    public enum ReadError: Error, Equatable {
        case missingColumns([String])
    }

    /// Entries in file order.
    public static func read(_ text: String) throws -> [WordEntry] {
        let records = CSV.records(in: text, delimiter: CSV.comma)
        guard let first = records.first else { return [] }
        let columns = first.fields.map { $0.lowercased() }
        let missing = header.filter { !columns.contains($0) }
        guard missing.isEmpty else { throw ReadError.missingColumns(missing) }
        let ecdictHeader = columns.map { $0 == "tags" ? "tag" : $0 }
        return records.dropFirst().compactMap { ECDICT.entry(fields: $0.fields, header: ecdictHeader) }
    }

    private static func escape(_ text: String) -> String {
        text.replacingOccurrences(of: "\n", with: "\\n")
    }
}
