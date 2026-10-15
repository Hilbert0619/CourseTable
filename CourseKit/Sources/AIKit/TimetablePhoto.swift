import CourseKit
import Foundation

/// A timetable from a picture (docs/ai-agent-spec.md §2): either the picture goes to a model that reads
/// pictures, or the phone reads its text with the position of each piece (Vision) and a text model rebuilds
/// the grid. Either way the answer is the CSV import format, which goes into the import preview.
public enum TimetablePhoto {
    /// A piece of text the phone found in a picture. Positions are fractions of the picture (0–1) from its
    /// top left corner.
    public struct TextBox: Hashable, Sendable {
        public var text: String
        public var x: Double
        public var y: Double
        public var width: Double
        public var height: Double

        public init(text: String, x: Double, y: Double, width: Double, height: Double) {
            self.text = text
            self.x = x
            self.y = y
            self.width = width
            self.height = height
        }
    }

    /// The most text sent for one picture.
    public static let maxOCRCharacters = 12_000

    /// What the position lines mean, before them in a prompt.
    static let ocrExplanation = """
        The phone read this text from a timetable photo. Each line is one text box: [x y w h] are its left edge, \
        top edge, width and height as fractions of the picture (0–1, from the top left), so boxes in one column \
        have similar x and boxes in one row similar y. Column headers are usually the weekdays (星期一… or Mon…), \
        row headers the class periods (第1-2节) or times. A cell may hold several lines, and sometimes two courses.
        """

    /// The boxes as lines like `[x 0.12 y 0.31 w 0.08 h 0.02] 高等数学`, top to bottom and left to right within
    /// a row (boxes whose middles are within half a box height count as one row).
    public static func ocrText(_ boxes: [TextBox]) -> String {
        let cleaned = boxes.compactMap { box -> TextBox? in
            var box = box
            box.text = box.text.split(whereSeparator: \.isNewline).joined(separator: " ").trimmingCharacters(in: .whitespaces)
            return box.text.isEmpty ? nil : box
        }
        var rows: [[TextBox]] = []
        for box in cleaned.sorted(by: { ($0.y + $0.height / 2) < ($1.y + $1.height / 2) }) {
            let middle = box.y + box.height / 2
            if let last = rows.last?.last, abs((last.y + last.height / 2) - middle) <= max(last.height, box.height) / 2 {
                rows[rows.count - 1].append(box)
            } else {
                rows.append([box])
            }
        }
        var text = ""
        for box in rows.flatMap({ $0.sorted { $0.x < $1.x } }) {
            let line = "[x \(fraction(box.x)) y \(fraction(box.y)) w \(fraction(box.width)) h \(fraction(box.height))] \(box.text)"
            if text.count + line.count + 1 > maxOCRCharacters { break }
            text += text.isEmpty ? line : "\n" + line
        }
        return text
    }

    /// Two decimals, e.g. `0.07`.
    static func fraction(_ value: Double) -> String {
        let hundredths = Int((min(max(value, 0), 1) * 100).rounded())
        return hundredths == 100 ? "1.00" : "0.\(hundredths < 10 ? "0" : "")\(hundredths)"
    }

    /// What the model wrote: the CSV, and the period times when it found them.
    public struct Reading: Hashable, Sendable {
        public var csv: String
        public var periods: PeriodSchedule?
    }

    /// Reads the answer to `AIPrompts.timetableFromPhoto`: the ```csv block (or the first code block, or the
    /// whole answer) and a ```periods block. `nil` when there is no CSV at all.
    public static func reading(from answer: String) -> Reading? {
        let blocks = codeBlocks(answer)
        let csv = (blocks.first { $0.language == "csv" } ?? blocks.first { $0.language != "periods" })?.text
            ?? (blocks.isEmpty ? answer : nil)
        guard let csv = csv?.trimmingCharacters(in: .whitespacesAndNewlines), !csv.isEmpty else { return nil }
        let periods = blocks.first { $0.language == "periods" }.flatMap { periodSchedule($0.text) }
        return Reading(csv: csv, periods: periods)
    }

    private static func codeBlocks(_ text: String) -> [(language: String, text: String)] {
        var blocks: [(String, String)] = []
        var language: String?
        var lines: [String] = []
        for line in text.components(separatedBy: .newlines) {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("```") {
                if let open = language {
                    blocks.append((open, lines.joined(separator: "\n")))
                    language = nil
                    lines = []
                } else {
                    language = trimmed.dropFirst(3).trimmingCharacters(in: .whitespaces).lowercased()
                }
            } else if language != nil {
                lines.append(line)
            }
        }
        if let open = language, !lines.isEmpty { blocks.append((open, lines.joined(separator: "\n"))) }
        return blocks
    }

    /// Lines `number,start,end`: periods 1, 2, … in order, each ending after it starts and before the next starts.
    static func periodSchedule(_ text: String) -> PeriodSchedule? {
        var periods: [ClassPeriod] = []
        for line in text.components(separatedBy: .newlines) {
            let fields = line.replacingOccurrences(of: "，", with: ",").split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespaces) }
            guard fields.count == 3, let number = Int(fields[0]) else { continue }
            guard number == periods.count + 1, let start = TimeOfDay(fields[1]), let end = TimeOfDay(fields[2]),
                  start < end, periods.last.map({ $0.end <= start }) ?? true
            else { return nil }
            periods.append(ClassPeriod(start: start, end: end))
        }
        guard periods.count >= 2, periods.count <= PeriodSchedule.maxPeriods else { return nil }
        return PeriodSchedule(periods: periods)
    }
}
