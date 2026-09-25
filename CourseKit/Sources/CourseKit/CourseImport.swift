import Foundation

/// A column of the course import format.
public enum ImportColumn: String, CaseIterable, Hashable, Sendable {
    case name, teacher, room, weekday, periods, weeks

    /// Columns every row needs. A missing `weeks` value means the whole term.
    public static let required: [ImportColumn] = [.name, .weekday, .periods]

    /// Column order assumed when the text has no header row; also the order `CourseCSV` exports.
    public static let defaultOrder: [ImportColumn] = [.name, .teacher, .room, .weekday, .periods, .weeks]

    /// Header names recognised for each column, compared after lowercasing and removing spaces,
    /// underscores and hyphens.
    static let aliases: [ImportColumn: [String]] = [
        .name: ["name", "course", "coursename", "subject", "title", "课程", "课程名", "课程名称", "科目", "名称"],
        .teacher: ["teacher", "instructor", "lecturer", "教师", "老师", "任课教师", "授课教师", "教师姓名"],
        .room: ["room", "location", "classroom", "place", "venue", "教室", "地点", "上课地点", "上课教室"],
        .weekday: ["weekday", "day", "dayofweek", "星期", "星期几", "周几", "上课星期"],
        .periods: ["periods", "period", "time", "节次", "节", "上课节次", "时间", "上课时间"],
        .weeks: ["weeks", "week", "周次", "周数", "上课周次", "起止周", "周"],
    ]

    static func matching(header: String) -> ImportColumn? {
        let key = header.foldingFullWidth.lowercased().filter { !$0.isWhitespace && $0 != "_" && $0 != "-" }
        return allCases.first { aliases[$0, default: []].contains(key) }
    }
}

/// Something wrong with one line of imported text.
public struct ImportProblem: Hashable, Sendable {
    /// 1-based line number in the imported text.
    public var line: Int
    public var kind: Kind

    public init(line: Int, kind: Kind) {
        self.line = line
        self.kind = kind
    }

    public enum Kind: Hashable, Sendable {
        /// The header row has no column for these required fields.
        case missingColumns([ImportColumn])
        /// A required field is empty.
        case missingValue(ImportColumn)
        /// A field that couldn't be understood, with its text.
        case invalidValue(ImportColumn, String)
        /// Periods beyond the last period of the day.
        case periodsOutOfRange(String, periodsPerDay: Int)
    }
}

public struct CourseImportResult: Hashable, Sendable {
    /// Parsed courses, all with colour index 0 (see `Timetable.planningImport`).
    public var courses: [Course]
    public var problems: [ImportProblem]
    /// Data rows read, not counting the header, blank lines and `#` comments.
    public var rowCount: Int

    public init(courses: [Course] = [], problems: [ImportProblem] = [], rowCount: Int = 0) {
        self.courses = courses
        self.problems = problems
        self.rowCount = rowCount
    }
}

/// Reads courses from CSV or tab-separated text, e.g. a spreadsheet paste or a file exported from a
/// school system.
///
/// - The delimiter is detected (tab, comma, semicolon or full-width comma).
/// - The first row is a header if it names at least two known columns, in English or Chinese
///   (`name`/`课程`, `teacher`/`教师`, `room`/`教室`, `weekday`/`星期`, `periods`/`节次`, `weeks`/`周次`),
///   in any order. Without a header the columns are `ImportColumn.defaultOrder`.
/// - Weekdays: `1`–`7`, `Mon`/`Monday`, `周一`/`星期一`/`一`, `周日`/`星期天`.
/// - Periods: `3`, `1-2`, `第1-2节`, `1,2`, or a time range like `08:00-09:40` that is matched
///   against the bell schedule.
/// - Weeks: `1-16`, `1-16周`, `1-15单`, `2-16(双)`, `1-8,10-16`, `1,3,5`, `odd`; empty means the whole term.
///   Week lists that don't fit one range become several courses.
public struct CourseImporter: Sendable {
    public var periods: PeriodSchedule
    public var totalWeeks: Int

    public init(periods: PeriodSchedule, totalWeeks: Int) {
        self.periods = periods
        self.totalWeeks = totalWeeks
    }

    public init(for timetable: Timetable) {
        self.init(periods: timetable.periods, totalWeeks: timetable.semester.totalWeeks)
    }

    public func parse(_ text: String) -> CourseImportResult {
        var scalars = text.unicodeScalars[...]
        if scalars.first == "\u{FEFF}" { scalars = scalars.dropFirst() }
        let text = String(String.UnicodeScalarView(scalars))

        let records = CSV.records(in: text, delimiter: CSV.detectDelimiter(in: text))
            .map { CSV.Record(line: $0.line, fields: $0.fields.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }) }
            .filter { !$0.fields.allSatisfy(\.isEmpty) && !Self.isComment($0) }
        guard let first = records.first else { return CourseImportResult() }

        var columns: [ImportColumn?] = ImportColumn.defaultOrder
        var rows = records[...]
        if let header = Self.headerColumns(first.fields) {
            let missing = ImportColumn.required.filter { !header.contains($0) }
            guard missing.isEmpty else {
                return CourseImportResult(problems: [ImportProblem(line: first.line, kind: .missingColumns(missing))])
            }
            columns = header
            rows = rows.dropFirst()
        }

        var result = CourseImportResult(rowCount: rows.count)
        for row in rows {
            let parsed = courses(from: row, columns: columns)
            result.courses += parsed.courses
            result.problems += parsed.problems
        }
        return result
    }

    private func courses(from row: CSV.Record, columns: [ImportColumn?]) -> (courses: [Course], problems: [ImportProblem]) {
        func value(_ column: ImportColumn) -> String {
            guard let index = columns.firstIndex(of: column), row.fields.indices.contains(index) else { return "" }
            return row.fields[index]
        }
        var problems: [ImportProblem] = []
        func report(_ kind: ImportProblem.Kind) { problems.append(ImportProblem(line: row.line, kind: kind)) }

        let name = value(.name)
        if name.isEmpty { report(.missingValue(.name)) }

        let weekdayText = value(.weekday)
        let weekday = Self.parseWeekday(weekdayText)
        if weekdayText.isEmpty {
            report(.missingValue(.weekday))
        } else if weekday == nil {
            report(.invalidValue(.weekday, weekdayText))
        }

        let periodsText = value(.periods)
        let periodRuns = Self.parsePeriods(periodsText, schedule: periods)
        if periodsText.isEmpty {
            report(.missingValue(.periods))
        } else if let periodRuns {
            if periodRuns.contains(where: { $0.lowerBound < 1 || $0.upperBound > periods.count }) {
                report(.periodsOutOfRange(periodsText, periodsPerDay: periods.count))
            }
        } else {
            report(.invalidValue(.periods, periodsText))
        }

        let weeksText = value(.weeks)
        let weeks = Self.parseWeeks(weeksText, totalWeeks: totalWeeks)
        if weeks == nil { report(.invalidValue(.weeks, weeksText)) }

        guard problems.isEmpty, let weekday, let periodRuns, let weeks else { return ([], problems) }
        let courses = periodRuns.flatMap { run in
            weeks.map { weekRange in
                Course(
                    name: name, teacher: value(.teacher), room: value(.room), weekday: weekday,
                    startPeriod: run.lowerBound, periodCount: run.count, weeks: weekRange
                )
            }
        }
        return (courses, [])
    }

    static func isComment(_ record: CSV.Record) -> Bool {
        let first = record.fields.first ?? ""
        return first.hasPrefix("#") || first.hasPrefix("//")
    }

    /// The column of each field if `fields` looks like a header row (at least two known column
    /// names), otherwise `nil`. A column named twice counts only the first time.
    static func headerColumns(_ fields: [String]) -> [ImportColumn?]? {
        var seen: Set<ImportColumn> = []
        let columns = fields.map { field -> ImportColumn? in
            guard let column = ImportColumn.matching(header: field), seen.insert(column).inserted else { return nil }
            return column
        }
        return seen.count >= 2 ? columns : nil
    }

    // MARK: - Field parsers

    private static let englishWeekdays = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
    private static let chineseWeekdays: [Character: Weekday] = [
        "一": .monday, "二": .tuesday, "三": .wednesday, "四": .thursday, "五": .friday,
        "六": .saturday, "日": .sunday, "天": .sunday, "七": .sunday,
    ]

    static func parseWeekday(_ text: String) -> Weekday? {
        var key = text.foldingFullWidth.lowercased().filter { !$0.isWhitespace && $0 != "." }
        for prefix in ["星期", "礼拜", "周"] where key.hasPrefix(prefix) {
            key.removeFirst(prefix.count)
            break
        }
        if let number = Int(key) { return Weekday(rawValue: number) }
        if key.count == 1, let day = chineseWeekdays[key.first!] { return day }
        guard key.count >= 2, key.allSatisfy(\.isLetter) else { return nil }
        return englishWeekdays.firstIndex { $0.hasPrefix(key) }.flatMap { Weekday(rawValue: $0 + 1) }
    }

    /// Runs of consecutive periods, e.g. `"1,2,5"` → `[1...2, 5...5]`. `nil` if unreadable; the
    /// runs may still lie outside the bell schedule.
    static func parsePeriods(_ text: String, schedule: PeriodSchedule) -> [ClosedRange<Int>]? {
        var key = text.foldingFullWidth.lowercased().normalizingDashes
        if key.contains(":") {
            return periods(coveringTimes: key, schedule: schedule).map { [$0] }
        }
        for word in ["第", "节次", "节", "periods", "period"] {
            key = key.replacingOccurrences(of: word, with: "")
        }
        var numbers: Set<Int> = []
        for token in key.tokens {
            guard let range = parseRange(token) else { return nil }
            numbers.formUnion(range)
        }
        let sorted = numbers.sorted()
        guard let first = sorted.first else { return nil }
        var runs: [ClosedRange<Int>] = []
        var runStart = first
        for (previous, number) in zip(sorted, sorted.dropFirst()) where number != previous + 1 {
            runs.append(runStart...previous)
            runStart = number
        }
        runs.append(runStart...sorted.last!)
        return runs
    }

    /// The periods overlapping a time range like `08:00-09:40`, or `nil` if there are none.
    private static func periods(coveringTimes text: String, schedule: PeriodSchedule) -> ClosedRange<Int>? {
        let parts = text.split(separator: "-").map { $0.trimmingCharacters(in: .whitespaces) }
        guard parts.count == 2, let start = TimeOfDay(parts[0]), let end = TimeOfDay(parts[1]), start < end else { return nil }
        let numbers = schedule.periods.indices
            .filter { schedule.periods[$0].start < end && schedule.periods[$0].end > start }
            .map { $0 + 1 }
        guard let first = numbers.first, let last = numbers.last else { return nil }
        return first...last
    }

    /// Teaching weeks as one or more week ranges. Empty text means every week of the term;
    /// `nil` if unreadable or no week remains.
    static func parseWeeks(_ text: String, totalWeeks: Int) -> [CourseWeeks]? {
        var key = text.foldingFullWidth.lowercased().normalizingDashes
        for bracket in ["(", ")", "[", "]", "【", "】"] {
            key = key.replacingOccurrences(of: bracket, with: " ")
        }
        for word in ["第", "周次", "weeks", "week", "wks", "wk", "周", "每", "全", "every", "all"] {
            key = key.replacingOccurrences(of: word, with: "")
        }

        var ranges: [(weeks: ClosedRange<Int>, parity: WeekParity?)] = []
        var sharedParity: WeekParity?
        for token in key.tokens {
            var rest = token
            var parity: WeekParity?
            for (markers, value) in [(["单", "odd"], WeekParity.odd), (["双", "even"], WeekParity.even)] {
                for marker in markers where rest.contains(marker) {
                    guard parity == nil || parity == value else { return nil }
                    parity = value
                    rest = rest.replacingOccurrences(of: marker, with: "")
                }
            }
            if rest.isEmpty {
                // A bare "单"/"odd" applies to every range that doesn't name its own parity.
                guard sharedParity == nil || sharedParity == parity else { return nil }
                sharedParity = parity
                continue
            }
            guard let range = parseRange(rest), range.lowerBound >= 1, range.upperBound <= Semester.maxWeeks else { return nil }
            ranges.append((range, parity))
        }
        if ranges.isEmpty {
            ranges = [(1...max(totalWeeks, 1), nil)]
        }

        var weeks: Set<Int> = []
        for (range, parity) in ranges {
            weeks.formUnion(range.filter((parity ?? sharedParity ?? .every).matches))
        }
        let segments = CourseWeeks.segments(covering: weeks)
        return segments.isEmpty ? nil : segments
    }

    /// `"3"` → `3...3`, `"1-4"` → `1...4`; `nil` for anything else or a backwards range.
    private static func parseRange(_ token: String) -> ClosedRange<Int>? {
        let bounds = token.split(separator: "-", omittingEmptySubsequences: false).map { Int($0) }
        switch bounds.count {
        case 1:
            guard let value = bounds[0] else { return nil }
            return value...value
        case 2:
            guard let low = bounds[0], let high = bounds[1], low <= high else { return nil }
            return low...high
        default:
            return nil
        }
    }
}

extension CourseWeeks {
    /// The fewest ranges this greedy pass finds that cover exactly `weeks`: runs of consecutive weeks
    /// become "every week" ranges, runs stepping by two become odd- or even-week ranges.
    public static func segments(covering weeks: Set<Int>) -> [CourseWeeks] {
        let sorted = weeks.sorted()
        var segments: [CourseWeeks] = []
        var index = 0
        while index < sorted.count {
            var consecutiveEnd = index
            while consecutiveEnd + 1 < sorted.count, sorted[consecutiveEnd + 1] == sorted[consecutiveEnd] + 1 { consecutiveEnd += 1 }
            var alternateEnd = index
            while alternateEnd + 1 < sorted.count, sorted[alternateEnd + 1] == sorted[alternateEnd] + 2 { alternateEnd += 1 }

            let first = sorted[index]
            if alternateEnd > consecutiveEnd {
                segments.append(CourseWeeks(first: first, last: sorted[alternateEnd], parity: first % 2 == 0 ? .even : .odd))
                index = alternateEnd + 1
            } else {
                segments.append(CourseWeeks(first: first, last: sorted[consecutiveEnd]))
                index = consecutiveEnd + 1
            }
        }
        return segments
    }
}

/// Turns the bytes of an imported file into text.
public enum ImportedText {
    /// Larger files are rejected before decoding; a timetable is a few kilobytes.
    public static let maxBytes = 1_000_000

    /// UTF-8 (with or without a byte-order mark) or UTF-16 with a byte-order mark. On Apple
    /// platforms, text that isn't valid UTF-8 is read as GB18030, which Excel on Chinese Windows
    /// uses when saving CSV.
    public static func decode(_ data: Data) -> String? {
        if data.starts(with: [0xEF, 0xBB, 0xBF]) { return String(data: data.dropFirst(3), encoding: .utf8) }
        if data.starts(with: [0xFF, 0xFE]) { return String(data: data.dropFirst(2), encoding: .utf16LittleEndian) }
        if data.starts(with: [0xFE, 0xFF]) { return String(data: data.dropFirst(2), encoding: .utf16BigEndian) }
        if let text = String(data: data, encoding: .utf8) { return text }
        #if canImport(Darwin)
        let gb18030 = CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue))
        return String(data: data, encoding: String.Encoding(rawValue: gb18030))
        #else
        return nil
        #endif
    }
}

extension String {
    /// Full-width ASCII forms (as typed with a Chinese input method) folded to ASCII.
    var foldingFullWidth: String {
        String(String.UnicodeScalarView(unicodeScalars.map { scalar -> Unicode.Scalar in
            switch scalar.value {
            case 0xFF01...0xFF5E: Unicode.Scalar(scalar.value - 0xFEE0)!
            case 0x3000: " "
            default: scalar
            }
        }))
    }

    /// Range dashes and words ("~", "–", "至", " to ") replaced with "-", without spaces around it.
    var normalizingDashes: String {
        var text = self
        for dash in [" to ", "~", "\u{301C}", "\u{2013}", "\u{2014}", "\u{2212}", "至", "到"] {
            text = text.replacingOccurrences(of: dash, with: "-")
        }
        while text.contains(" -") || text.contains("- ") {
            text = text.replacingOccurrences(of: " -", with: "-").replacingOccurrences(of: "- ", with: "-")
        }
        return text
    }

    /// Pieces between commas, `、`, semicolons, slashes and whitespace, with empty pieces dropped.
    var tokens: [String] {
        split { $0 == "," || $0 == "、" || $0 == ";" || $0 == "/" || $0.isWhitespace }.map(String.init)
    }
}
