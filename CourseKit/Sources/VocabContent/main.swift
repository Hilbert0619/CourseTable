import Foundation
import VocabKit

// Builds a word-list file from ECDICT's ecdict.csv:
//   swift run -c release vocab-content <ecdict.csv> <output.csv> [--tag ky]
// See docs/vocabulary-spec.md §4.

var arguments = Array(CommandLine.arguments.dropFirst())
var tag = "ky"
if let index = arguments.firstIndex(of: "--tag"), index + 1 < arguments.count {
    tag = arguments[index + 1]
    arguments.removeSubrange(index...(index + 1))
}
guard arguments.count == 2 else {
    FileHandle.standardError.write(Data("usage: vocab-content <ecdict.csv> <output.csv> [--tag ky]\n".utf8))
    exit(2)
}

let input = URL(fileURLWithPath: arguments[0])
let output = URL(fileURLWithPath: arguments[1])
let data: Data
do {
    data = try Data(contentsOf: input)
} catch {
    FileHandle.standardError.write(Data("cannot read \(input.path): \(error.localizedDescription)\n".utf8))
    exit(1)
}

let selection = ECDICT.select(tag: tag, from: String(decoding: data, as: UTF8.self))
do {
    try ContentFile.write(selection.entries).write(to: output, atomically: true, encoding: .utf8)
} catch {
    FileHandle.standardError.write(Data("cannot write \(output.path): \(error.localizedDescription)\n".utf8))
    exit(1)
}

print("Read \(selection.rowsRead) rows; \(selection.entries.count) entries tagged '\(tag)' written to \(output.path)")
let reasons = Dictionary(grouping: selection.skipped, by: \.reason)
for (reason, items) in reasons.sorted(by: { $0.key.rawValue < $1.key.rawValue }) {
    let examples = items.prefix(12).map(\.word).joined(separator: ", ")
    print("Skipped \(items.count) (\(reason.rawValue)): \(examples)\(items.count > 12 ? ", …" : "")")
}
let entries = selection.entries
print("First 15: " + entries.prefix(15).map(\.display).joined(separator: ", "))
print("Last 10: " + entries.suffix(10).map(\.display).joined(separator: ", "))
print("Without a corpus rank: \(entries.filter { $0.frequencyRank == nil }.count)")
print("Without phonetic: \(entries.filter { $0.phonetic.isEmpty }.count)")
print("Multi-word: \(entries.filter { $0.display.contains(" ") }.count) — "
      + entries.filter { $0.display.contains(" ") }.prefix(10).map(\.display).joined(separator: ", "))
print("Capitalised: \(entries.filter { $0.display != $0.headword }.count) — "
      + entries.filter { $0.display != $0.headword }.prefix(10).map(\.display).joined(separator: ", "))
let noPOS = entries.filter { $0.primaryPartOfSpeech == nil }
print("No part of speech in the meaning: \(noPOS.count) — " + noPOS.prefix(10).map(\.display).joined(separator: ", "))
