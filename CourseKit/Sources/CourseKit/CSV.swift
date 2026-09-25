import Foundation

/// A small, forgiving CSV/TSV reader and writer (RFC 4180 quoting).
///
/// Handles quoted fields with doubled quotes, delimiters and line breaks inside quotes, and
/// `\n`, `\r\n` or `\r` line endings. A quote in the middle of an unquoted field is kept as text.
enum CSV {
    struct Record: Hashable {
        /// 1-based line in the input where the record starts.
        var line: Int
        var fields: [String]
    }

    static let tab: Unicode.Scalar = "\t"
    static let comma: Unicode.Scalar = ","
    static let semicolon: Unicode.Scalar = ";"
    static let fullWidthComma: Unicode.Scalar = "\u{FF0C}"

    /// Tab if the first non-empty line has one (a spreadsheet paste), otherwise the most frequent of
    /// comma, semicolon and full-width comma, defaulting to comma.
    static func detectDelimiter(in text: String) -> Unicode.Scalar {
        let firstLine = text.split(whereSeparator: \.isNewline).first { !$0.allSatisfy(\.isWhitespace) } ?? ""
        let scalars = firstLine.unicodeScalars
        if scalars.contains(tab) { return tab }
        let candidates = [comma, semicolon, fullWidthComma]
        let counts = candidates.map { candidate in scalars.filter { $0 == candidate }.count }
        guard let best = counts.indices.max(by: { counts[$0] < counts[$1] }), counts[best] > 0 else { return comma }
        return candidates[best]
    }

    static func records(in text: String, delimiter: Unicode.Scalar) -> [Record] {
        let scalars = Array(text.unicodeScalars)
        var records: [Record] = []
        var fields: [String] = []
        var field = String.UnicodeScalarView()
        var inQuotes = false
        var line = 1
        var recordLine = 1
        var index = 0

        func endField() {
            fields.append(String(field))
            field = String.UnicodeScalarView()
        }

        while index < scalars.count {
            let scalar = scalars[index]
            let next = index + 1 < scalars.count ? scalars[index + 1] : nil
            if inQuotes {
                if scalar == "\"" {
                    if next == "\"" {
                        field.append("\"")
                        index += 1
                    } else {
                        inQuotes = false
                    }
                } else if scalar == "\r" || scalar == "\n" {
                    if scalar == "\r", next == "\n" { index += 1 }
                    field.append("\n")
                    line += 1
                } else {
                    field.append(scalar)
                }
            } else if scalar == "\"", String(field).allSatisfy(\.isWhitespace) {
                field = String.UnicodeScalarView()
                inQuotes = true
            } else if scalar == delimiter {
                endField()
            } else if scalar == "\r" || scalar == "\n" {
                if scalar == "\r", next == "\n" { index += 1 }
                endField()
                records.append(Record(line: recordLine, fields: fields))
                fields = []
                line += 1
                recordLine = line
            } else {
                field.append(scalar)
            }
            index += 1
        }
        if !field.isEmpty || !fields.isEmpty || inQuotes {
            endField()
            records.append(Record(line: recordLine, fields: fields))
        }
        return records
    }

    /// `field` quoted if it contains the delimiter, a quote or a line break.
    static func escape(_ field: String, delimiter: Unicode.Scalar = comma) -> String {
        let needsQuotes = field.unicodeScalars.contains { $0 == delimiter || $0 == "\"" || $0 == "\n" || $0 == "\r" }
        guard needsQuotes else { return field }
        return "\"" + field.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }
}
