/// Everything needed to answer "what's on when": the term, the bell schedule and the courses.
public struct Timetable: Hashable, Codable, Sendable {
    public var semester: Semester
    public var periods: PeriodSchedule
    public var courses: [Course]

    public init(semester: Semester, periods: PeriodSchedule = .standard, courses: [Course] = []) {
        self.semester = semester
        self.periods = periods
        self.courses = courses
    }

    /// Courses meeting in teaching week `week`, ordered by day then period.
    public func courses(inWeek week: Int) -> [Course] {
        courses.filter { $0.meets(inWeek: week) }.sorted(by: Self.slotOrder)
    }

    /// Courses meeting on `weekday` of teaching week `week`, ordered by period.
    public func courses(on weekday: Weekday, inWeek week: Int) -> [Course] {
        courses(inWeek: week).filter { $0.weekday == weekday }
    }

    /// Other courses that would occupy the same slot as `course` in some week.
    public func clashes(for course: Course) -> [Course] {
        courses.filter { course.clashes(with: $0) }.sorted(by: Self.slotOrder)
    }

    public func issues(for course: Course) -> [CourseIssue] {
        course.issues(periodsPerDay: periods.count, totalWeeks: semester.totalWeeks)
    }

    public var hasWeekendCourses: Bool {
        courses.contains { $0.weekday.isWeekend }
    }

    /// Classes on a calendar day, with concrete times. Empty outside the term.
    public func sessions(on day: LocalDate) -> [ClassSession] {
        guard case .inProgress(let week) = semester.status(on: day) else { return [] }
        return courses(on: day.weekday, inWeek: week).compactMap { course in
            guard let span = periods.timeSpan(of: course.periods) else { return nil }
            return ClassSession(course: course, day: day, week: week, start: span.start, end: span.end)
        }
    }

    public func daySchedule(on day: LocalDate) -> DaySchedule {
        DaySchedule(day: day, semesterStatus: semester.status(on: day), sessions: sessions(on: day))
    }

    /// The class in progress at `moment`, or else the next one to start, looking up to `days` ahead.
    public func currentOrNextSession(at moment: Moment, searchingDays days: Int = 14) -> ClassSession? {
        for offset in 0...max(days, 0) {
            let day = moment.day.adding(days: offset)
            let daySessions = sessions(on: day)
            let candidate = offset == 0 ? daySessions.first(where: { $0.end > moment.time }) : daySessions.first
            if let candidate { return candidate }
        }
        return nil
    }

    /// The class in progress at `moment` (if any) and the one after it.
    public func currentAndNext(at moment: Moment, searchingDays days: Int = 14) -> (current: ClassSession?, next: ClassSession?) {
        let current = sessions(on: moment.day).first { $0.status(at: moment) == .inProgress }
        let next = currentOrNextSession(at: current?.endMoment ?? moment, searchingDays: days)
        return (current, next)
    }

    /// Moments on `day` when "current / next class" changes: every class start and end, plus the next midnight.
    public func refreshMoments(on day: LocalDate) -> [Moment] {
        let times = Set(sessions(on: day).flatMap { [$0.start, $0.end] })
        return times.sorted().map { Moment(day: day, time: $0) } + [Moment(day: day.adding(days: 1), time: .midnight)]
    }

    /// Widget timeline points: `moment` itself, then each later change today, ending at midnight.
    public func timelineMoments(from moment: Moment) -> [Moment] {
        [moment] + refreshMoments(on: moment.day).filter { $0 > moment }
    }

    static func slotOrder(_ a: Course, _ b: Course) -> Bool {
        (a.weekday.rawValue, a.startPeriod, a.endPeriod, a.name) < (b.weekday.rawValue, b.startPeriod, b.endPeriod, b.name)
    }
}

/// One concrete meeting of a course on a specific day.
public struct ClassSession: Hashable, Identifiable, Sendable {
    public var course: Course
    public var day: LocalDate
    public var week: Int
    public var start: TimeOfDay
    public var end: TimeOfDay

    public init(course: Course, day: LocalDate, week: Int, start: TimeOfDay, end: TimeOfDay) {
        self.course = course
        self.day = day
        self.week = week
        self.start = start
        self.end = end
    }

    public var id: String { "\(course.id.uuidString)@\(day)" }

    public var startMoment: Moment { Moment(day: day, time: start) }
    public var endMoment: Moment { Moment(day: day, time: end) }

    public func status(at moment: Moment) -> SessionStatus {
        if moment < startMoment { return .upcoming }
        if moment < endMoment { return .inProgress }
        return .finished
    }
}

public enum SessionStatus: Hashable, Sendable {
    case upcoming, inProgress, finished
}

/// The classes on one day, for the "Today" screen and the widget.
public struct DaySchedule: Hashable, Sendable {
    public var day: LocalDate
    public var semesterStatus: SemesterStatus
    public var sessions: [ClassSession]

    public init(day: LocalDate, semesterStatus: SemesterStatus, sessions: [ClassSession]) {
        self.day = day
        self.semesterStatus = semesterStatus
        self.sessions = sessions
    }

    public func current(at time: TimeOfDay) -> ClassSession? {
        sessions.first { $0.start <= time && time < $0.end }
    }

    /// The first class that hasn't started by `time`.
    public func next(after time: TimeOfDay) -> ClassSession? {
        sessions.first { $0.start > time }
    }

    /// Classes not yet finished at `time`.
    public func remaining(at time: TimeOfDay) -> [ClassSession] {
        sessions.filter { $0.end > time }
    }
}
