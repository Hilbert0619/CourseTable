import Foundation
import Testing
@testable import CourseKit

/// Term starts Monday 2026-08-31; the week of 2026-09-21 is week 4 (even), 2026-09-28 week 5 (odd).
enum Fixture {
    static let semester = Semester(startingOn: LocalDate(year: 2026, month: 8, day: 31), totalWeeks: 18)

    static let calculus = Course(name: "Calculus", teacher: "Li", room: "A101", weekday: .monday, startPeriod: 1, periodCount: 2,
                                 weeks: CourseWeeks(first: 1, last: 16))
    static let physics = Course(name: "Physics", room: "B204", weekday: .monday, startPeriod: 3, periodCount: 2,
                                weeks: CourseWeeks(first: 1, last: 16, parity: .odd))
    static let english = Course(name: "English", room: "C3", weekday: .monday, startPeriod: 3, periodCount: 2,
                                weeks: CourseWeeks(first: 2, last: 16, parity: .even))
    static let lab = Course(name: "Lab", weekday: .thursday, startPeriod: 5, periodCount: 3,
                            weeks: CourseWeeks(first: 4, last: 12))
    static let seminar = Course(name: "Seminar", weekday: .friday, startPeriod: 9, periodCount: 2,
                                weeks: CourseWeeks(first: 1, last: 18))

    static let timetable = Timetable(semester: semester, courses: [seminar, lab, english, physics, calculus])

    static func day(_ text: String) -> LocalDate { LocalDate(text)! }
    static func at(_ day: String, _ time: String) -> Moment { Moment(day: LocalDate(day)!, time: TimeOfDay(time)!) }
}

@Suite struct CourseTests {
    @Test func parity() {
        let weeks = CourseWeeks(first: 3, last: 10, parity: .odd)
        #expect(weeks.all == [3, 5, 7, 9])
        #expect(CourseWeeks(first: 3, last: 10, parity: .even).all == [4, 6, 8, 10])
        #expect(CourseWeeks(first: 3, last: 5).all == [3, 4, 5])
        #expect(weeks.contains(5))
        #expect(!weeks.contains(4))
        #expect(!weeks.contains(11))
        #expect(CourseWeeks(first: 5, last: 3).all.isEmpty)
    }

    @Test func periodRange() {
        #expect(Fixture.lab.periods == 5...7)
        #expect(Fixture.lab.endPeriod == 7)
    }

    @Test func oddAndEvenCoursesInSameSlotDoNotClash() {
        #expect(!Fixture.physics.clashes(with: Fixture.english))
        #expect(!Fixture.english.clashes(with: Fixture.physics))
    }

    @Test func overlappingSlotAndWeeksClash() {
        var makeup = Fixture.physics
        makeup.id = UUID()
        makeup.startPeriod = 4
        makeup.weeks = CourseWeeks(first: 5, last: 5)
        #expect(makeup.clashes(with: Fixture.physics))
        #expect(!makeup.clashes(with: Fixture.english))  // week 5 is odd; English is even-only
        #expect(!Fixture.physics.clashes(with: Fixture.physics))  // a course never clashes with itself
    }

    @Test func differentDaysOrPeriodsDoNotClash() {
        var other = Fixture.calculus
        other.id = UUID()
        other.weekday = .tuesday
        #expect(!other.clashes(with: Fixture.calculus))
        other.weekday = .monday
        other.startPeriod = 3
        #expect(!other.clashes(with: Fixture.calculus))
        other.startPeriod = 2
        #expect(other.clashes(with: Fixture.calculus))
    }

    @Test func disjointWeekRangesDoNotClash() {
        var other = Fixture.calculus
        other.id = UUID()
        other.weeks = CourseWeeks(first: 17, last: 18)
        #expect(!other.clashes(with: Fixture.calculus))
    }

    @Test func validation() {
        #expect(Fixture.calculus.issues(periodsPerDay: 12, totalWeeks: 18).isEmpty)

        var course = Fixture.calculus
        course.name = "   "
        course.startPeriod = 12
        #expect(course.issues(periodsPerDay: 12, totalWeeks: 18) == [.missingName, .periodsOutOfRange(periodsPerDay: 12)])

        course = Fixture.calculus
        course.weeks = CourseWeeks(first: 8, last: 4)
        #expect(course.issues(periodsPerDay: 12, totalWeeks: 18) == [.invalidWeekRange])

        course.weeks = CourseWeeks(first: 2, last: 2, parity: .odd)
        #expect(course.issues(periodsPerDay: 12, totalWeeks: 18) == [.noMatchingWeeks])

        course.weeks = CourseWeeks(first: 1, last: 20)
        let issues = course.issues(periodsPerDay: 12, totalWeeks: 18)
        #expect(issues == [.weeksBeyondSemester(totalWeeks: 18)])
        #expect(!issues.contains { $0.isBlocking })
        #expect(CourseIssue.missingName.isBlocking)
    }

    @Test func suggestedColourIsLeastUsed() {
        #expect(Course.suggestedColorIndex(avoiding: []) == 0)
        var courses = [Fixture.calculus, Fixture.physics]  // both colour 0
        courses[1].colorIndex = 1
        #expect(Course.suggestedColorIndex(avoiding: courses) == 2)
        courses = (0..<Course.paletteSize).map { var c = Fixture.lab; c.colorIndex = $0; return c }
        courses[3].colorIndex = 4
        #expect(Course.suggestedColorIndex(avoiding: courses) == 3)
    }
}

@Suite struct TimetableTests {
    let timetable = Fixture.timetable

    @Test func coursesInWeekRespectParityAndRange() {
        #expect(timetable.courses(inWeek: 4).map(\.name) == ["Calculus", "English", "Lab", "Seminar"])
        #expect(timetable.courses(inWeek: 5).map(\.name) == ["Calculus", "Physics", "Lab", "Seminar"])
        #expect(timetable.courses(inWeek: 17).map(\.name) == ["Seminar"])
        #expect(timetable.courses(on: .monday, inWeek: 1).map(\.name) == ["Calculus", "Physics"])
    }

    @Test func sessionsHaveConcreteTimes() throws {
        let sessions = timetable.sessions(on: Fixture.day("2026-09-24"))  // Thursday, week 4
        let lab = try #require(sessions.first)
        #expect(sessions.count == 1)
        #expect(lab.course.name == "Lab")
        #expect(lab.week == 4)
        #expect(lab.start.description == "14:00")
        #expect(lab.end.description == "16:45")
    }

    @Test func noSessionsOutsideTerm() {
        #expect(timetable.sessions(on: Fixture.day("2026-08-24")).isEmpty)  // Monday before term
        #expect(timetable.sessions(on: Fixture.day("2027-01-04")).isEmpty)  // Monday after term
    }

    @Test func sessionsSkipPeriodsMissingFromSchedule() {
        var short = timetable
        short.periods = PeriodSchedule(periods: Array(PeriodSchedule.standard.periods.prefix(8)))
        #expect(short.sessions(on: Fixture.day("2026-09-25")).isEmpty)  // Seminar is periods 9–10
    }

    @Test func daySchedule() throws {
        let monday = timetable.daySchedule(on: Fixture.day("2026-09-28"))  // week 5 (odd)
        #expect(monday.semesterStatus == .inProgress(week: 5))
        #expect(monday.sessions.map(\.course.name) == ["Calculus", "Physics"])

        let nine = TimeOfDay("09:00")!
        #expect(monday.current(at: nine)?.course.name == "Calculus")
        #expect(monday.next(after: nine)?.course.name == "Physics")
        #expect(monday.remaining(at: nine).count == 2)

        let breakTime = TimeOfDay("09:50")!
        #expect(monday.current(at: breakTime) == nil)
        #expect(monday.next(after: breakTime)?.course.name == "Physics")

        #expect(monday.current(at: TimeOfDay("09:40")!) == nil)  // end time is exclusive
        #expect(monday.remaining(at: TimeOfDay("12:00")!).isEmpty)
    }

    @Test func currentOrNextFindsInProgressClass() {
        let session = timetable.currentOrNextSession(at: Fixture.at("2026-09-28", "10:30"))
        #expect(session?.course.name == "Physics")
        #expect(session?.status(at: Fixture.at("2026-09-28", "10:30")) == .inProgress)
    }

    @Test func currentOrNextLooksAheadAcrossDays() {
        // After Monday's last class, the next is Thursday's lab (no Tuesday/Wednesday classes).
        let afterMonday = timetable.currentOrNextSession(at: Fixture.at("2026-09-28", "12:00"))
        #expect(afterMonday?.course.name == "Lab")
        #expect(afterMonday?.day == Fixture.day("2026-10-01"))
        #expect(afterMonday?.status(at: Fixture.at("2026-09-28", "12:00")) == .upcoming)

        // After Friday's evening seminar, the next class is Monday's first class of week 6.
        let weekend = timetable.currentOrNextSession(at: Fixture.at("2026-10-02", "21:00"))
        #expect(weekend?.course.name == "Calculus")
        #expect(weekend?.day == Fixture.day("2026-10-05"))
    }

    @Test func currentOrNextBeforeTermStartsReturnsFirstClass() {
        let beforeTerm = timetable.currentOrNextSession(at: Fixture.at("2026-08-29", "12:00"))
        #expect(beforeTerm?.course.name == "Calculus")
        #expect(beforeTerm?.day == Fixture.day("2026-08-31"))
        #expect(timetable.currentOrNextSession(at: Fixture.at("2027-06-01", "08:00")) == nil)
    }

    @Test func refreshMomentsCoverStartsEndsAndMidnight() {
        let moments = timetable.refreshMoments(on: Fixture.day("2026-09-28"))
        #expect(moments.map(\.time.description) == ["08:00", "09:40", "10:00", "11:40", "00:00"])
        #expect(moments.last?.day == Fixture.day("2026-09-29"))
        #expect(timetable.refreshMoments(on: Fixture.day("2026-09-29")).count == 1)  // no classes: just midnight
    }

    @Test func clashesForCourse() {
        var makeup = Fixture.english
        makeup.id = UUID()
        makeup.name = "Makeup"
        makeup.weeks = CourseWeeks(first: 4, last: 5)
        #expect(timetable.clashes(for: makeup).map(\.name) == ["English", "Physics"])
        #expect(timetable.clashes(for: Fixture.calculus).isEmpty)
    }

    @Test func weekendDetection() {
        #expect(!timetable.hasWeekendCourses)
        var weekend = timetable
        weekend.courses.append(Course(name: "Club", weekday: .saturday, startPeriod: 1, weeks: CourseWeeks(first: 1, last: 2)))
        #expect(weekend.hasWeekendCourses)
    }
}

@Suite struct WeekGridTests {
    @Test func nonOverlappingCoursesGetOneLane() {
        let placements = Fixture.timetable.gridPlacements(inWeek: 5)
        #expect(placements.count == 4)
        #expect(placements.allSatisfy { $0.lane == 0 && $0.laneCount == 1 })
    }

    @Test func clashingCoursesShareTheColumn() {
        var timetable = Fixture.timetable
        var makeup = Fixture.physics
        makeup.id = UUID()
        makeup.name = "Makeup"
        makeup.startPeriod = 4
        makeup.periodCount = 2
        timetable.courses.append(makeup)

        let monday = timetable.gridPlacements(inWeek: 5).filter { $0.weekday == .monday }
        let byName = Dictionary(uniqueKeysWithValues: monday.map { ($0.course.name, $0) })
        #expect(byName["Calculus"]?.laneCount == 1)
        #expect(byName["Physics"]?.lane == 0)
        #expect(byName["Physics"]?.laneCount == 2)
        #expect(byName["Makeup"]?.lane == 1)
        #expect(byName["Makeup"]?.laneCount == 2)
    }

    @Test func freedLaneIsReusedWithinCluster() {
        let a = Course(name: "A", weekday: .tuesday, startPeriod: 1, periodCount: 4, weeks: CourseWeeks(first: 1, last: 1))
        let b = Course(name: "B", weekday: .tuesday, startPeriod: 1, periodCount: 1, weeks: CourseWeeks(first: 1, last: 1))
        let c = Course(name: "C", weekday: .tuesday, startPeriod: 3, periodCount: 1, weeks: CourseWeeks(first: 1, last: 1))
        let timetable = Timetable(semester: Fixture.semester, courses: [a, b, c])
        let byName = Dictionary(uniqueKeysWithValues: timetable.gridPlacements(inWeek: 1).map { ($0.course.name, $0) })
        // Sorted by start then end: B (1–1) takes lane 0, A (1–4) lane 1, C (3–3) reuses lane 0 once B has ended.
        #expect(byName["B"]?.lane == 0)
        #expect(byName["A"]?.lane == 1)
        #expect(byName["C"]?.lane == 0)
        #expect(byName.values.allSatisfy { $0.laneCount == 2 })
    }
}

@Suite struct SnapshotTests {
    @Test func roundTrips() throws {
        let snapshot = ScheduleSnapshot(timetable: Fixture.timetable)
        let data = try snapshot.encoded()
        #expect(try ScheduleSnapshot.decode(from: data) == snapshot)

        let json = String(decoding: data, as: UTF8.self)
        #expect(json.contains(#""firstMonday":"2026-08-31""#))
        #expect(json.contains(#""version":1"#))
    }

    @Test func rejectsNewerVersions() throws {
        var snapshot = ScheduleSnapshot(timetable: Fixture.timetable)
        snapshot.version = ScheduleSnapshot.currentVersion + 1
        let data = try snapshot.encoded()
        #expect(throws: SnapshotError.unsupportedVersion(ScheduleSnapshot.currentVersion + 1)) {
            try ScheduleSnapshot.decode(from: data)
        }
    }
}
