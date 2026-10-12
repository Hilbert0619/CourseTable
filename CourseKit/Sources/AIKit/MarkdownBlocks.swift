import Foundation

/// The block structure of an AI answer's Markdown (SwiftUI's `Text` renders only inline Markdown): headings, list
/// items, quotes, code and paragraphs. Inline styling (bold, italics, code, links) stays in each block's text.
public enum MarkdownBlock: Hashable, Sendable {
    case heading(level: Int, text: String)
    /// `- item`, `* item`, `+ item`; `depth` counts indentation steps (two spaces or a tab each).
    case bullet(depth: Int, text: String)
    /// `1. item` or `1) item`.
    case numbered(depth: Int, number: String, text: String)
    case quote(String)
    case code(String)
    case rule
    case paragraph(String)

    public static func parse(_ markdown: String) -> [MarkdownBlock] {
        var blocks: [MarkdownBlock] = []
        var paragraph: [String] = []
        var code: [String]?

        func flush() {
            if !paragraph.isEmpty { blocks.append(.paragraph(paragraph.joined(separator: "\n"))) }
            paragraph.removeAll()
        }

        for rawLine in markdown.replacingOccurrences(of: "\r\n", with: "\n").split(separator: "\n", omittingEmptySubsequences: false) {
            let line = String(rawLine)
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("```") {
                if let lines = code {
                    blocks.append(.code(lines.joined(separator: "\n")))
                    code = nil
                } else {
                    flush()
                    code = []
                }
                continue
            }
            if code != nil {
                code?.append(line)
                continue
            }
            if trimmed.isEmpty {
                flush()
                continue
            }
            let depth = indentation(of: line)
            if let heading = heading(trimmed) {
                flush()
                blocks.append(heading)
            } else if trimmed.count >= 3, Set(trimmed.filter { $0 != " " }).count == 1, let mark = trimmed.first,
                      "-*_".contains(mark) {
                flush()
                blocks.append(.rule)
            } else if let first = trimmed.first, "-*+•".contains(first), trimmed.dropFirst().first == " " {
                flush()
                blocks.append(.bullet(depth: depth, text: String(trimmed.dropFirst(2)).trimmingCharacters(in: .whitespaces)))
            } else if let (number, text) = numberedItem(trimmed) {
                flush()
                blocks.append(.numbered(depth: depth, number: number, text: text))
            } else if trimmed.hasPrefix(">") {
                flush()
                blocks.append(.quote(String(trimmed.dropFirst()).trimmingCharacters(in: .whitespaces)))
            } else if paragraph.isEmpty, depth > 0, let item = blocks.last, let continued = item.continued(with: trimmed) {
                // An indented line right after a list item belongs to it, e.g. an example's translation.
                blocks[blocks.count - 1] = continued
            } else {
                paragraph.append(trimmed)
            }
        }
        if let lines = code { blocks.append(.code(lines.joined(separator: "\n"))) }  // an answer cut off mid-block
        flush()
        return blocks
    }

    /// A list item with `line` added on a new line; `nil` for other blocks.
    private func continued(with line: String) -> MarkdownBlock? {
        switch self {
        case .bullet(let depth, let text): .bullet(depth: depth, text: text + "\n" + line)
        case .numbered(let depth, let number, let text): .numbered(depth: depth, number: number, text: text + "\n" + line)
        default: nil
        }
    }

    private static func indentation(of line: String) -> Int {
        var spaces = 0
        for character in line {
            if character == " " { spaces += 1 } else if character == "\t" { spaces += 2 } else { break }
        }
        return min(spaces / 2, 3)
    }

    private static func heading(_ line: String) -> MarkdownBlock? {
        let hashes = line.prefix { $0 == "#" }.count
        guard (1...6).contains(hashes), line.dropFirst(hashes).first == " " else { return nil }
        var text = line.dropFirst(hashes).trimmingCharacters(in: .whitespaces)
        while text.hasSuffix("#") { text.removeLast() }
        return .heading(level: hashes, text: text.trimmingCharacters(in: .whitespaces))
    }

    private static func numberedItem(_ line: String) -> (String, String)? {
        let digits = line.prefix { $0.isASCII && $0.isNumber }
        guard !digits.isEmpty, digits.count <= 3 else { return nil }
        let rest = line.dropFirst(digits.count)
        guard let mark = rest.first, mark == "." || mark == ")", rest.dropFirst().first == " " else { return nil }
        return (String(digits), rest.dropFirst(2).trimmingCharacters(in: .whitespaces))
    }
}
