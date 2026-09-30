import Foundation
import CourseKit

/// A word read from an imported list, before it is saved into a book.
public struct ImportedWord: Hashable, Sendable {
    public var headword: String
    public var display: String
    /// The meaning given in the file; `nil` means "use the dictionary's".
    public var meaning: String?
    public var phonetic: String?
    public var partOfSpeech: String?
    public var example: String?
    public var exampleTranslation: String?
    /// Whether the dictionary has this word.
    public var inDictionary: Bool
    /// 1-based line in the file.
    public var line: Int
}

/// What an import would do, shown before anything is saved.
public struct WordListPreview: Hashable, Sendable {
    public enum Format: String, Sendable {
        case text
        case csv
    }

    public var format: Format
    /// Words to import, in file order.
    public var words: [ImportedWord]
    /// Words the dictionary doesn't know and the file gives no meaning for; they are not imported.
    public var unknown: [ImportedWord]
    /// Repeats of a word already listed earlier in the file.
    public var duplicates: [ImportedWord]
    /// Lines that don't look like a word, with their line numbers.
    public var unreadable: [Int]

    public var matchedCount: Int { words.filter { $0.inDictionary && $0.meaning == nil }.count }
    public var customCount: Int { words.filter { $0.meaning != nil }.count }
}

/// Reads word lists. Plain text has one word per line, optionally followed by a meaning
/// (`abandon`, `abandon 放弃`, `abandon<TAB>v. 放弃`, `12. abandon`). CSV needs a header row naming a
/// `word` column (English or Chinese header names), plus optional meaning, phonetic, part of speech,
/// example and example translation columns.
public enum WordListImport {
    public static let maxWords = 20_000

    /// - Parameter isKnown: whether the dictionary has a word (by key).
    public static func preview(_ text: String, format: WordListPreview.Format? = nil,
                               isKnown: (String) -> Bool) -> WordListPreview {
        let format = format ?? detectFormat(text)
        var preview = WordListPreview(format: format, words: [], unknown: [], duplicates: [], unreadable: [])
        let rows: [(line: Int, word: ImportedWord?)] = switch format {
        case .text: textRows(text, isKnown: isKnown)
        case .csv: csvRows(text, isKnown: isKnown)
        }
        var seen: Set<String> = []
        for row in rows {
            guard let word = row.word else {
                preview.unreadable.append(row.line)
                continue
            }
            if seen.contains(word.headword) {
                preview.duplicates.append(word)
            } else if !word.inDictionary && word.meaning == nil {
                preview.unknown.append(word)
            } else if preview.words.count < maxWords {
                seen.insert(word.headword)
                preview.words.append(word)
            }
        }
        return preview
    }

    /// CSV if the first non-blank line is a header naming a word column, otherwise plain text.
    public static func detectFormat(_ text: String) -> WordListPreview.Format {
        let firstLine = text.split(whereSeparator: \.isNewline).first { !$0.allSatisfy(\.isWhitespace) } ?? ""
        let delimiter = CSV.detectDelimiter(in: String(firstLine))
        let fields = CSV.records(in: String(firstLine), delimiter: delimiter).first?.fields ?? []
        return fields.count > 1 && columns(fromHeader: fields) != nil ? .csv : .text
    }

    // MARK: - Plain text

    static func textRows(_ text: String, isKnown: (String) -> Bool) -> [(line: Int, word: ImportedWord?)] {
        var rows: [(Int, ImportedWord?)] = []
        for (offset, rawLine) in text.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline).enumerated() {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty || line.hasPrefix("#") { continue }
            rows.append((offset + 1, textWord(line, line: offset + 1, isKnown: isKnown)))
        }
        return rows
    }

    /// Splits a line into word and meaning: at a tab if there is one, otherwise before the first
    /// non-Latin character or part-of-speech abbreviation (`abandon v. 放弃`, `abandon放弃`).
    static func textWord(_ line: String, line number: Int, isKnown: (String) -> Bool) -> ImportedWord? {
        var body = Substring(line)
        // Leading numbering: "12. abandon", "3、abandon", "(4) abandon".
        let numbering = body.prefix { $0.isNumber || " .、)）(（".contains($0) }
        if numbering.contains(where: \.isNumber) { body = body.dropFirst(numbering.count) }

        let word: String
        let meaning: String
        if let tab = body.firstIndex(of: "\t") {
            word = String(body[..<tab])
            meaning = String(body[body.index(after: tab)...])
        } else {
            let cut = body.firstIndex { !$0.isASCII } ?? body.endIndex
            let tokens = body[..<cut].split(separator: " ", omittingEmptySubsequences: true)
            let wordTokens = tokens.prefix { PartOfSpeech(abbreviation: String($0)) == nil }
            word = wordTokens.joined(separator: " ")
            meaning = (tokens.dropFirst(wordTokens.count).map(String.init) + [String(body[cut...])]).joined(separator: " ")
        }
        let display = word.trimmingCharacters(in: CharacterSet(charactersIn: " :：-—=").union(.whitespaces))
        guard ECDICT.isWord(display), display.count <= 60 else { return nil }
        let key = Headword.key(display)
        let cleanMeaning = meaning.trimmingCharacters(in: CharacterSet(charactersIn: " :：-—=").union(.whitespaces))
        return ImportedWord(headword: key, display: display, meaning: cleanMeaning.isEmpty ? nil : cleanMeaning,
                            inDictionary: isKnown(key), line: number)
    }

    // MARK: - CSV

    enum Column: CaseIterable {
        case word, meaning, phonetic, partOfSpeech, example, exampleTranslation

        var names: Set<String> {
            switch self {
            case .word: ["word", "words", "headword", "english", "单词", "词汇", "英文", "英语"]
            case .meaning: ["meaning", "meanings", "translation", "chinese", "definition", "释义", "中文", "词义", "意思", "中文释义", "翻译"]
            case .phonetic: ["phonetic", "ipa", "pronunciation", "音标"]
            case .partOfSpeech: ["pos", "part of speech", "part_of_speech", "词性"]
            case .example: ["example", "sentence", "例句"]
            case .exampleTranslation: ["example_translation", "example translation", "translation of example", "例句翻译", "例句译文", "译文"]
            }
        }
    }

    static func columns(fromHeader fields: [String]) -> [Column?]? {
        let columns = fields.map { field -> Column? in
            let name = field.trimmingCharacters(in: .whitespaces).lowercased()
            return Column.allCases.first { $0.names.contains(name) }
        }
        return columns.contains(.word) ? columns : nil
    }

    static func csvRows(_ text: String, isKnown: (String) -> Bool) -> [(line: Int, word: ImportedWord?)] {
        let delimiter = CSV.detectDelimiter(in: text)
        let records = CSV.records(in: text, delimiter: delimiter)
            .filter { !$0.fields.allSatisfy { $0.trimmingCharacters(in: .whitespaces).isEmpty } }
        guard let header = records.first, let columns = columns(fromHeader: header.fields) else { return [] }
        return records.dropFirst().compactMap { record in
            func value(_ column: Column) -> String? {
                guard let index = columns.firstIndex(of: column), index < record.fields.count else { return nil }
                let text = record.fields[index].trimmingCharacters(in: .whitespacesAndNewlines)
                return text.isEmpty ? nil : text
            }
            let display = value(.word) ?? ""
            if display.hasPrefix("#") { return nil }
            guard ECDICT.isWord(display), display.count <= 60 else { return (record.line, nil) }
            let key = Headword.key(display)
            let word = ImportedWord(
                headword: key, display: display, meaning: value(.meaning), phonetic: value(.phonetic),
                partOfSpeech: value(.partOfSpeech), example: value(.example),
                exampleTranslation: value(.exampleTranslation), inDictionary: isKnown(key), line: record.line
            )
            return (record.line, word)
        }
    }
}

extension ImportedWord {
    init(headword: String, display: String, meaning: String?, inDictionary: Bool, line: Int) {
        self.init(headword: headword, display: display, meaning: meaning, phonetic: nil, partOfSpeech: nil,
                  example: nil, exampleTranslation: nil, inDictionary: inDictionary, line: line)
    }

    /// The entry to show and quiz: the dictionary's, with anything the file gave taking its place.
    public func entry(dictionary: WordEntry?) -> WordEntry {
        var entry = dictionary ?? WordEntry(headword: headword, display: display, translation: "")
        if let meaning { entry.translation = meaning }
        if let phonetic { entry.phonetic = phonetic.trimmingCharacters(in: CharacterSet(charactersIn: "/[] ")) }
        return entry
    }
}
