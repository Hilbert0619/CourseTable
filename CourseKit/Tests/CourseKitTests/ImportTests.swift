import Foundation
import Testing
@testable import CourseKit

@Suite struct CSVTests {
    @Test func detectsDelimiter() {
        #expect(CSV.detectDelimiter(in: "name\tweekday\n") == "\t")
        #expect(CSV.detectDelimiter(in: "\n\nname,weekday,periods\n") == ",")
        #expect(CSV.detectDelimiter(in: "name;weekday;periods") == ";")
        #expect(CSV.detectDelimiter(in: "课程，星期，节次") == "\u{FF0C}")
        #expect(CSV.detectDelimiter(in: "a;b,c;d") == ";")
        #expect(CSV.detectDelimiter(in: "just one column") == ",")
        #expect(CSV.detectDelimiter(in: "") == ",")
    }

    @Test func readsQuotedFieldsAndLineEndings() {
        let text = "a,\"b,c\",\"say \"\"hi\"\"\"\r\n1, \"two\" ,3\r4\n"
        let records = CSV.records(in: text, delimiter: ",")
        #expect(records == [
            CSV.Record(line: 1, fields: ["a", "b,c", "say \"hi\""]),
            CSV.Record(line: 2, fields: ["1", "two ", "3"]),
            CSV.Record(line: 3, fields: ["4"]),
        ])
    }

    @Test func lineBreakInsideQuotesKeepsLineNumbers() {
        let records = CSV.records(in: "name,room\n\"Lab\nPart 2\",A1\nMath,B2", delimiter: ",")
        #expect(records.map(\.line) == [1, 2, 4])
        #expect(records[1].fields == ["Lab\nPart 2", "A1"])
        #expect(records[2].fields == ["Math", "B2"])
    }

    @Test func strayQuoteInsideFieldIsText() {
        #expect(CSV.records(in: "5\" screen,x", delimiter: ",") == [CSV.Record(line: 1, fields: ["5\" screen", "x"])])
    }

    @Test func emptyAndTrailingFields() {
        #expect(CSV.records(in: "", delimiter: ",").isEmpty)
        #expect(CSV.records(in: "a,,\n", delimiter: ",") == [CSV.Record(line: 1, fields: ["a", "", ""])])
        #expect(CSV.records(in: "\"unterminated", delimiter: ",") == [CSV.Record(line: 1, fields: ["unterminated"])])
    }

    @Test func escapeRoundTrips() {
        let fields = ["plain", "a,b", "quote \"x\"", "two\nlines", ""]
        let line = fields.map { CSV.escape($0) }.joined(separator: ",")
        #expect(line == "plain,\"a,b\",\"quote \"\"x\"\"\",\"two\nlines\",")
        #expect(CSV.records(in: line, delimiter: ",").first?.fields == fields)
    }
}

@Suite struct FieldParsingTests {
    @Test(arguments: [
        ("1", Weekday.monday), ("7", .sunday), ("Mon", .monday), ("monday", .monday), ("Tue.", .tuesday),
        ("TUES", .tuesday), ("th", .thursday), ("Thurs", .thursday), ("sa", .saturday), ("Sun", .sunday),
        ("周一", .monday), ("星期三", .wednesday), ("礼拜五", .friday), ("六", .saturday), ("周日", .sunday),
        ("星期天", .sunday), ("周7", .sunday), ("２", .tuesday), (" 周 二 ", .tuesday),
    ])
    func weekdays(text: String, expected: Weekday) {
        #expect(CourseImporter.parseWeekday(text) == expected)
    }

    @Test(arguments: ["", "0", "8", "t", "s", "Funday", "周八", "星期", "1.5"])
    func invalidWeekdays(text: String) {
        #expect(CourseImporter.parseWeekday(text) == nil)
    }

    @Test(arguments: [
        ("3", [3...3]), ("1-2", [1...2]), ("第1-2节", [1...2]), ("1~2节", [1...2]), ("１－２", [1...2]),
        ("1,2", [1...2]), ("1、2,5", [1...2, 5...5]), ("3 - 5", [3...5]), ("11-12", [11...12]),
        ("08:00-09:40", [1...2]), ("8:00 ~ 9:35", [1...2]), ("14:00–17:40", [5...8]), ("08:00-08:45", [1...1]),
        ("0-1", [0...1]), ("13", [13...13]),
    ])
    func periods(text: String, expected: [ClosedRange<Int>]) {
        #expect(CourseImporter.parsePeriods(text, schedule: .standard) == expected)
    }

    @Test(arguments: ["", "abc", "2-1", "1-2-3", "-3", "periods", "06:00-07:00", "09:40-08:00", "8:00"])
    func invalidPeriods(text: String) {
        #expect(CourseImporter.parsePeriods(text, schedule: .standard) == nil)
    }

    @Test(arguments: [
        ("", [CourseWeeks(first: 1, last: 18)]),
        ("1-16", [CourseWeeks(first: 1, last: 16)]),
        ("1-16周", [CourseWeeks(first: 1, last: 16)]),
        ("第1-16周", [CourseWeeks(first: 1, last: 16)]),
        ("１－１６周", [CourseWeeks(first: 1, last: 16)]),
        ("1 - 16", [CourseWeeks(first: 1, last: 16)]),
        ("Weeks 1-16", [CourseWeeks(first: 1, last: 16)]),
        ("1-15单", [CourseWeeks(first: 1, last: 15, parity: .odd)]),
        ("1-16(单)", [CourseWeeks(first: 1, last: 15, parity: .odd)]),
        ("1-16周（单）", [CourseWeeks(first: 1, last: 15, parity: .odd)]),
        ("2-16双周", [CourseWeeks(first: 2, last: 16, parity: .even)]),
        ("1-16 even", [CourseWeeks(first: 2, last: 16, parity: .even)]),
        ("3-15 odd", [CourseWeeks(first: 3, last: 15, parity: .odd)]),
        ("单周", [CourseWeeks(first: 1, last: 17, parity: .odd)]),
        ("even", [CourseWeeks(first: 2, last: 18, parity: .even)]),
        ("1-8,10-16", [CourseWeeks(first: 1, last: 8), CourseWeeks(first: 10, last: 16)]),
        ("1-8、10-16周", [CourseWeeks(first: 1, last: 8), CourseWeeks(first: 10, last: 16)]),
        ("1,3,5,7", [CourseWeeks(first: 1, last: 7, parity: .odd)]),
        ("1-7单,8-12", [CourseWeeks(first: 1, last: 7, parity: .odd), CourseWeeks(first: 8, last: 12)]),
        ("5", [CourseWeeks(first: 5, last: 5)]),
        ("1-30", [CourseWeeks(first: 1, last: 30)]),
    ])
    func weeks(text: String, expected: [CourseWeeks]) {
        #expect(CourseImporter.parseWeeks(text, totalWeeks: 18) == expected)
    }

    @Test(arguments: ["abc", "0-3", "5-3", "1-31", "2 odd", "单双", "1-16单双", "1-2-3", "1-16x"])
    func invalidWeeks(text: String) {
        #expect(CourseImporter.parseWeeks(text, totalWeeks: 18) == nil)
    }

    @Test func weekSegments() {
        #expect(CourseWeeks.segments(covering: []) == [])
        #expect(CourseWeeks.segments(covering: [4]) == [CourseWeeks(first: 4, last: 4)])
        #expect(CourseWeeks.segments(covering: [1, 2, 3, 5, 7, 9]) == [
            CourseWeeks(first: 1, last: 3), CourseWeeks(first: 5, last: 9, parity: .odd),
        ])
        #expect(CourseWeeks.segments(covering: [2, 4, 6, 7, 8]) == [
            CourseWeeks(first: 2, last: 6, parity: .even), CourseWeeks(first: 7, last: 8),
        ])
        // Segments always cover exactly the input weeks.
        let weeks: Set<Int> = [1, 2, 4, 6, 9, 10, 11, 13, 15, 18]
        #expect(Set(CourseWeeks.segments(covering: weeks).flatMap(\.all)) == weeks)
    }

    @Test func headerMatching() {
        #expect(ImportColumn.matching(header: "Course Name") == .name)
        #expect(ImportColumn.matching(header: "day_of_week") == .weekday)
        #expect(ImportColumn.matching(header: "上课地点") == .room)
        #expect(ImportColumn.matching(header: " 周次 ") == .weeks)
        #expect(ImportColumn.matching(header: "Ｒｏｏｍ") == .room)
        #expect(ImportColumn.matching(header: "notes") == nil)
    }
}

@Suite struct CourseImporterTests {
    let importer = CourseImporter(periods: .standard, totalWeeks: 18)

    @Test func englishAndChineseExamplesMatch() {
        let english = importer.parse(CourseCSV.englishExample)
        let chinese = importer.parse(CourseCSV.chineseExample)
        #expect(english.problems.isEmpty)
        #expect(chinese.problems.isEmpty)
        #expect(english.rowCount == 4)
        // "1-8,10-16" becomes two courses.
        #expect(english.courses.count == 5)
        #expect(english.courses.map(\.name) == [
            "Advanced Mathematics", "College English", "Physics Lab", "Data Structures", "Data Structures",
        ])
        #expect(english.courses[2].weeks == CourseWeeks(first: 1, last: 15, parity: .odd))
        #expect(english.courses[2].periods == 5...7)
        #expect(english.courses[2].weekday == .thursday)
        #expect(english.courses[4].weeks == CourseWeeks(first: 10, last: 16))

        func slots(_ courses: [Course]) -> [String] {
            courses.map { "\($0.weekday.rawValue) \($0.periods) \($0.weeks.all)" }
        }
        #expect(slots(chinese.courses) == slots(english.courses))
        #expect(chinese.courses[0].name == "高等数学")
        #expect(chinese.courses[0].teacher == "李老师")
        #expect(chinese.courses[0].room == "A101")
    }

    @Test func headerColumnsInAnyOrderWithExtraColumns() {
        let result = importer.parse("""
            Weeks,Notes,Periods,Day,Course,Room
            1-16,bring calculator,3-4,Wed,Calculus,A1
            ,,1,Fri,Seminar,
            """)
        #expect(result.problems.isEmpty)
        #expect(result.courses.count == 2)
        let calculus = result.courses[0]
        #expect(calculus.name == "Calculus")
        #expect(calculus.room == "A1")
        #expect(calculus.teacher == "")
        #expect(calculus.weekday == .wednesday)
        #expect(calculus.periods == 3...4)
        #expect(result.courses[1].weeks == CourseWeeks(first: 1, last: 18))
        #expect(result.courses[1].periodCount == 1)
    }

    @Test func headerlessRowsUseDefaultOrder() {
        let result = importer.parse("Calculus,Li,A101,1,1-2,1-16\nPhysics,,B2,3,3-4,")
        #expect(result.problems.isEmpty)
        #expect(result.rowCount == 2)
        #expect(result.courses.map(\.name) == ["Calculus", "Physics"])
        #expect(result.courses[0].teacher == "Li")
        #expect(result.courses[1].weekday == .wednesday)
        #expect(result.courses[1].weeks == CourseWeeks(first: 1, last: 18))
    }

    @Test func spreadsheetPasteWithChineseHeader() {
        let result = importer.parse("课程名称\t任课教师\t上课地点\t星期\t节次\t周次\r\n线性代数\t陈\tA203\t星期二\t第5-6节\t2-16双周\r\n")
        #expect(result.problems.isEmpty)
        #expect(result.courses == [Course(
            id: result.courses.first?.id ?? UUID(), name: "线性代数", teacher: "陈", room: "A203", weekday: .tuesday,
            startPeriod: 5, periodCount: 2, weeks: CourseWeeks(first: 2, last: 16, parity: .even)
        )])
    }

    @Test func timeRangesMapToPeriods() {
        let result = importer.parse("name,weekday,time\nEvening Class,Thu,19:00-20:40")
        #expect(result.problems.isEmpty)
        #expect(result.courses.first?.periods == 9...10)
    }

    @Test func missingRequiredColumnStopsImport() {
        let result = importer.parse("name,room,weeks\nCalculus,A1,1-16")
        #expect(result.courses.isEmpty)
        #expect(result.problems == [ImportProblem(line: 1, kind: .missingColumns([.weekday, .periods]))])
    }

    @Test func badRowsAreReportedAndOthersStillImport() {
        let result = importer.parse("""
            name,weekday,periods,weeks
            Calculus,Mon,1-2,1-16

            # comment lines are skipped
            Oops,Funday,1-2,1-16
            ,Tue,3-4,
            Late,Fri,13-14,
            Physics,Wed,x,1-16 odd
            Chemistry,Thu,,99
            English,Fri,3-4,1-16
            """)
        #expect(result.courses.map(\.name) == ["Calculus", "English"])
        #expect(result.rowCount == 7)
        #expect(result.problems == [
            ImportProblem(line: 5, kind: .invalidValue(.weekday, "Funday")),
            ImportProblem(line: 6, kind: .missingValue(.name)),
            ImportProblem(line: 7, kind: .periodsOutOfRange("13-14", periodsPerDay: 12)),
            ImportProblem(line: 8, kind: .invalidValue(.periods, "x")),
            ImportProblem(line: 9, kind: .missingValue(.periods)),
            ImportProblem(line: 9, kind: .invalidValue(.weeks, "99")),
        ])
    }

    @Test func shortRowsReportMissingValues() {
        let result = importer.parse("Calculus,Li")
        #expect(result.courses.isEmpty)
        #expect(result.problems == [
            ImportProblem(line: 1, kind: .missingValue(.weekday)),
            ImportProblem(line: 1, kind: .missingValue(.periods)),
        ])
    }

    @Test func splitPeriodsAndWeeksMakeSeveralCourses() {
        let result = importer.parse("name,weekday,periods,weeks\nLab,Mon,\"1,2,5\",\"1-4,9-12\"")
        #expect(result.problems.isEmpty)
        #expect(result.courses.map { "\($0.periods) \($0.weeks.first)-\($0.weeks.last)" } == [
            "1...2 1-4", "1...2 9-12", "5...5 1-4", "5...5 9-12",
        ])
    }

    @Test func ignoresByteOrderMarkBlankInputAndCommentsOnly() {
        #expect(importer.parse("\u{FEFF}name,weekday,periods\nMath,1,1").courses.count == 1)
        #expect(importer.parse("") == CourseImportResult())
        #expect(importer.parse("\n  \n# nothing here\n") == CourseImportResult())
        #expect(importer.parse("name,weekday,periods\n").rowCount == 0)
    }

    @Test func usesTimetableSettings() {
        let short = Timetable(
            semester: Semester(startingOn: LocalDate(year: 2026, month: 9, day: 7), totalWeeks: 10),
            periods: PeriodSchedule(periods: Array(PeriodSchedule.standard.periods.prefix(4)))
        )
        let result = CourseImporter(for: short).parse("name,weekday,periods\nMath,1,5")
        #expect(result.problems == [ImportProblem(line: 2, kind: .periodsOutOfRange("5", periodsPerDay: 4))])
        #expect(CourseImporter(for: short).parse("name,weekday,periods\nMath,1,1").courses.first?.weeks == CourseWeeks(first: 1, last: 10))
    }
}

@Suite struct ImportPlanTests {
    let timetable = Fixture.timetable

    @Test func skipsCoursesAlreadyInTimetableOrRepeated() {
        var copy = Fixture.calculus
        copy.id = UUID()
        copy.colorIndex = 7
        let newCourse = Course(name: "Biology", weekday: .tuesday, startPeriod: 1, weeks: CourseWeeks(first: 1, last: 16))
        let plan = timetable.planningImport(of: [copy, newCourse, newCourse])
        #expect(plan.added.map(\.name) == ["Biology"])
        #expect(plan.duplicates.count == 2)
        #expect(plan.timetable.courses.count == timetable.courses.count + 1)
        #expect(!plan.replacesExisting)
    }

    @Test func weeksCompareByMeetingWeeks() {
        var sameMeetings = Fixture.physics  // weeks 1–16 odd
        sameMeetings.id = UUID()
        sameMeetings.weeks = CourseWeeks(first: 1, last: 15, parity: .odd)
        #expect(sameMeetings.isSameClass(as: Fixture.physics))
        sameMeetings.room = "Other"
        #expect(!sameMeetings.isSameClass(as: Fixture.physics))
    }

    @Test func coloursFollowCourseNames() {
        var calculusTuesday = Fixture.calculus
        calculusTuesday.id = UUID()
        calculusTuesday.weekday = .tuesday
        let existing = Timetable(semester: Fixture.semester, courses: [
            { var c = Fixture.calculus; c.colorIndex = 3; return c }(),
        ])
        let biology = Course(name: "Biology", weekday: .friday, startPeriod: 1, weeks: CourseWeeks(first: 1, last: 16))
        var biologyLab = biology
        biologyLab.id = UUID()
        biologyLab.startPeriod = 5
        let chemistry = Course(name: "Chemistry", weekday: .friday, startPeriod: 3, weeks: CourseWeeks(first: 1, last: 16))

        let plan = existing.planningImport(of: [calculusTuesday, biology, biologyLab, chemistry])
        #expect(plan.added.map(\.colorIndex) == [3, 0, 0, 1])
    }

    @Test func replacingDropsExistingCourses() {
        let biology = Course(name: "Calculus", weekday: .friday, startPeriod: 1, weeks: CourseWeeks(first: 1, last: 16))
        let plan = timetable.planningImport(of: [Fixture.calculus, biology], replacingExisting: true)
        #expect(plan.replacesExisting)
        #expect(plan.duplicates.isEmpty)
        #expect(plan.added.count == 2)
        #expect(plan.timetable.courses == plan.added)
        #expect(plan.added.map(\.colorIndex) == [0, 0])
        #expect(plan.timetable.semester == timetable.semester)
    }

    @Test func planTimetableShowsClashes() {
        let clashing = Course(name: "Makeup", weekday: .monday, startPeriod: 2, periodCount: 2, weeks: CourseWeeks(first: 5, last: 5))
        let plan = timetable.planningImport(of: [clashing])
        #expect(plan.timetable.clashes(for: plan.added[0]).map(\.name) == ["Calculus", "Physics"])
    }
}

@Suite struct CourseCSVTests {
    @Test func exportsInSlotOrderWithEscaping() {
        let course = Course(name: "Lab, Part 1", teacher: "Zhao \"Z\"", room: "", weekday: .thursday, startPeriod: 5,
                            periodCount: 1, weeks: CourseWeeks(first: 3, last: 3))
        let csv = CourseCSV.export([Fixture.seminar, course, Fixture.english])
        #expect(csv == """
            name,teacher,room,weekday,periods,weeks\r
            English,,C3,1,3-4,2-16 even\r
            "Lab, Part 1","Zhao ""Z""\",,4,5,3\r
            Seminar,,,5,9-10,1-18\r

            """)
    }

    @Test func exportImportRoundTrip() {
        let original = Fixture.timetable.courses.sorted(by: Timetable.slotOrder)
        let result = CourseImporter(for: Fixture.timetable).parse(CourseCSV.export(original))
        #expect(result.problems.isEmpty)
        #expect(result.courses.count == original.count)
        for (imported, course) in zip(result.courses, original) {
            #expect(imported.isSameClass(as: course))
        }
    }

    @Test func exportDataHasByteOrderMarkAndDecodes() {
        let data = CourseCSV.exportData([Fixture.calculus])
        #expect(data.starts(with: [0xEF, 0xBB, 0xBF]))
        #expect(ImportedText.decode(data) == CourseCSV.export([Fixture.calculus]))
    }
}

@Suite struct ImportedTextTests {
    @Test func decodesUnicodeEncodings() {
        let text = "课程,星期\n高等数学,周一"
        #expect(ImportedText.decode(Data(text.utf8)) == text)
        #expect(ImportedText.decode(Data([0xEF, 0xBB, 0xBF]) + Data(text.utf8)) == text)

        let utf16LE = Data([0xFF, 0xFE]) + Data(text.utf16.flatMap { [UInt8($0 & 0xFF), UInt8($0 >> 8)] })
        #expect(ImportedText.decode(utf16LE) == text)
        let utf16BE = Data([0xFE, 0xFF]) + Data(text.utf16.flatMap { [UInt8($0 >> 8), UInt8($0 & 0xFF)] })
        #expect(ImportedText.decode(utf16BE) == text)
        #expect(ImportedText.decode(Data()) == "")
    }

    #if !canImport(Darwin)
    @Test func invalidUTF8WithoutFallbackIsRejected() {
        // "高等" in GB18030; Apple platforms decode this instead (tested in the app's test target).
        #expect(ImportedText.decode(Data([0xB8, 0xDF, 0xB5, 0xC8])) == nil)
    }
    #endif
}
