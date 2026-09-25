import Foundation
import Testing
@testable import CourseKit

@Suite struct CourseListParsingTests {
    func summary(_ templates: [CourseTemplate]) -> [String] {
        templates.map { "\($0.name)|\($0.room)|\($0.teacher)" }
    }

    @Test func readsNameRoomTeacherLines() {
        let list = CourseTemplate.parseList("""
            高等数学 A101 李老师
            大学英语，B204，王老师
            College English, B204, Wang
            College Physics C3 Zhao
            Data Structures\tC305\tChen
            线性代数
            体育 操场

            # comments and blank lines are skipped
              高等数学   A101  李老师
            , A1, nobody
            Math,,Li
            Art | Studio 2 | Wu | extra
            Music、R1
            """)
        #expect(summary(list) == [
            "高等数学|A101|李老师",
            "大学英语|B204|王老师",
            "College English|B204|Wang",
            "College Physics|C3|Zhao",
            "Data Structures|C305|Chen",
            "线性代数||",
            "体育|操场|",
            "Math||Li",
            "Art|Studio 2|Wu",
            "Music|R1|",
        ])
        #expect(Set(list.map(\.id)).count == list.count)
        #expect(list.allSatisfy { $0.colorIndex == 0 })
    }

    @Test func emptyInput() {
        #expect(CourseTemplate.parseList("").isEmpty)
        #expect(CourseTemplate.parseList("\n \r\n# just a comment\n").isEmpty)
    }

    @Test func matchingDetails() {
        let template = CourseTemplate(name: "Calculus", room: "A101", teacher: "Li")
        #expect(template.describes(Fixture.calculus))
        #expect(!template.describes(Fixture.physics))
        #expect(template.hasSameDetails(as: CourseTemplate(name: "Calculus", room: "A101", teacher: "Li", colorIndex: 5)))
        #expect(!template.hasSameDetails(as: CourseTemplate(name: "Calculus", room: "B1", teacher: "Li")))

        let fromCourse = CourseTemplate(Fixture.calculus)
        #expect(fromCourse.id == Fixture.calculus.id)
        #expect(fromCourse.describes(Fixture.calculus))
    }
}

@Suite struct PlacementTests {
    let timetable = Fixture.timetable

    @Test func savedTemplatesComeFirstThenCoursesNotInTheList() {
        let saved = [
            CourseTemplate(name: "Calculus", room: "A101", teacher: "Li", colorIndex: 4),
            CourseTemplate(name: "Biology"),
        ]
        var withRepeat = timetable
        var calculusWednesday = Fixture.calculus
        calculusWednesday.id = UUID()
        calculusWednesday.weekday = .wednesday
        withRepeat.courses.append(calculusWednesday)
        var physicsFriday = Fixture.physics
        physicsFriday.id = UUID()
        physicsFriday.weekday = .friday
        withRepeat.courses.append(physicsFriday)

        let templates = withRepeat.placementTemplates(saved: saved)
        #expect(templates.map(\.name) == ["Calculus", "Biology", "English", "Physics", "Lab", "Seminar"])
        #expect(templates[0] == saved[0])
        #expect(templates[2].id == Fixture.english.id)
        #expect(templates[3].id == Fixture.physics.id)  // the earlier of the two Physics courses
        #expect(timetable.placementTemplates(saved: []).count == 5)
        #expect(Timetable(semester: Fixture.semester).placementTemplates(saved: []).isEmpty)
    }

    @Test func newTemplatesReuseColoursByName() {
        var calculus = Fixture.calculus
        calculus.colorIndex = 3
        var physics = Fixture.physics
        physics.colorIndex = 5
        let coloured = Timetable(semester: Fixture.semester, courses: [calculus, physics])
        let saved = [CourseTemplate(name: "Biology", colorIndex: 1)]
        let new = [
            CourseTemplate(name: "Calculus", room: "B1"),
            CourseTemplate(name: "Biology", room: "Lab"),
            CourseTemplate(name: "Chemistry"),
            CourseTemplate(name: "Drama"),
            CourseTemplate(name: "Chemistry", room: "Lab 2"),
        ]
        let result = coloured.assigningColors(to: new, saved: saved)
        #expect(result.map(\.colorIndex) == [3, 1, 0, 2, 0])
        #expect(result.map(\.id) == new.map(\.id))
    }

    @Test func placingCreatesAWholeTermCourse() {
        let template = CourseTemplate(name: "Biology", room: "Lab", teacher: "Sun", colorIndex: 6)
        let course = timetable.placing(template, on: .tuesday, at: 3, periodCount: 2)
        #expect(course.name == "Biology")
        #expect(course.room == "Lab")
        #expect(course.teacher == "Sun")
        #expect(course.colorIndex == 6)
        #expect(course.weekday == .tuesday)
        #expect(course.periods == 3...4)
        #expect(course.weeks == CourseWeeks(first: 1, last: 18))
        #expect(course.id != template.id)
        #expect(timetable.issues(for: course).isEmpty)

        let odd = timetable.placing(template, on: .tuesday, at: 3, periodCount: 2, parity: .odd)
        #expect(odd.weeks == CourseWeeks(first: 1, last: 18, parity: .odd))
    }

    @Test func placingFitsTheDay() {
        let template = CourseTemplate(name: "Late")
        #expect(timetable.placing(template, on: .tuesday, at: 12, periodCount: 3).periods == 12...12)
        #expect(timetable.placing(template, on: .tuesday, at: 11, periodCount: 3).periods == 11...12)
        #expect(timetable.placing(template, on: .tuesday, at: 20, periodCount: 2).periods == 12...12)
        #expect(timetable.placing(template, on: .tuesday, at: 0, periodCount: 0).periods == 1...1)
    }

    @Test func placingStopsBeforeTheNextCourse() {
        let template = CourseTemplate(name: "Biology")
        // Lab is Thursday 5–7, Seminar Friday 9–10.
        #expect(timetable.placing(template, on: .thursday, at: 4, periodCount: 3).periods == 4...4)
        #expect(timetable.placing(template, on: .thursday, at: 3, periodCount: 3).periods == 3...4)
        #expect(timetable.placing(template, on: .friday, at: 8, periodCount: 2).periods == 8...8)
        // Monday 3–4 is taken in every week (Physics odd, English even): keep the requested length.
        let taken = timetable.placing(template, on: .monday, at: 3, periodCount: 2)
        #expect(taken.periods == 3...4)
        #expect(!timetable.clashes(for: taken).isEmpty)
    }

    @Test func oppositeParityFitsBesideAnOddWeekCourse() {
        let oddOnly = Timetable(semester: Fixture.semester, courses: [Fixture.physics])  // Monday 3–4, odd weeks
        let even = oddOnly.placing(CourseTemplate(name: "English"), on: .monday, at: 3, periodCount: 2, parity: .even)
        #expect(even.periods == 3...4)
        #expect(oddOnly.clashes(for: even).isEmpty)
        let every = oddOnly.placing(CourseTemplate(name: "English"), on: .monday, at: 2, periodCount: 2)
        #expect(every.periods == 2...2)
    }

    @Test func parityWithoutMatchingWeekFallsBackToEveryWeek() {
        let oneWeek = Timetable(semester: Semester(startingOn: Fixture.semester.firstMonday, totalWeeks: 1))
        let course = oneWeek.placing(CourseTemplate(name: "Talk"), on: .monday, at: 1, periodCount: 1, parity: .even)
        #expect(course.weeks == CourseWeeks(first: 1, last: 1))
    }
}

@Suite struct AllWeeksGridTests {
    @Test func includesCoursesFromOtherWeeksDimmed() {
        let week5 = Fixture.timetable.gridPlacements(inWeek: 5, includingOtherWeeks: true)
        #expect(week5.count == 5)
        let byName = Dictionary(uniqueKeysWithValues: week5.map { ($0.course.name, $0) })
        #expect(byName["English"]?.isInWeek == false)  // even weeks only
        #expect(byName.values.filter(\.isInWeek).count == 4)
        // Physics and English share Monday 3–4 side by side.
        #expect(byName["Physics"]?.laneCount == 2)
        #expect(byName["English"]?.laneCount == 2)
        #expect(byName["Calculus"]?.laneCount == 1)

        let week2 = Fixture.timetable.gridPlacements(inWeek: 2, includingOtherWeeks: true)
        #expect(week2.first { $0.course.name == "Lab" }?.isInWeek == false)  // weeks 4–12
        #expect(week2.first { $0.course.name == "Physics" }?.isInWeek == false)
    }

    @Test func currentWeekOnlyByDefault() {
        let placements = Fixture.timetable.gridPlacements(inWeek: 5)
        #expect(placements.count == 4)
        #expect(placements.filter(\.isInWeek).count == 4)
    }

    @Test func skipsCoursesThatNeverMeet() {
        var timetable = Fixture.timetable
        timetable.courses.append(Course(name: "Never", weekday: .tuesday, startPeriod: 1,
                                        weeks: CourseWeeks(first: 2, last: 2, parity: .odd)))
        #expect(timetable.gridPlacements(inWeek: 5, includingOtherWeeks: true).count == 5)
    }
}
