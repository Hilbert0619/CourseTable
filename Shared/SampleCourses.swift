import CourseKit

/// Demo courses for SwiftUI previews and the widget gallery.
enum SampleCourses {
    /// A handful of courses on today's weekday and the next, so previews always show something.
    static func all(today: LocalDate) -> [Course] {
        let day = today.weekday
        let tomorrow = today.adding(days: 1).weekday
        let weeks = CourseWeeks(first: 1, last: 18)
        return [
            Course(name: "Advanced Mathematics", teacher: "Li", room: "A101", weekday: day, startPeriod: 1, weeks: weeks, colorIndex: 0),
            Course(name: "College English", teacher: "Wang", room: "B204", weekday: day, startPeriod: 3, weeks: weeks, colorIndex: 5),
            Course(name: "Physics Lab", room: "Lab 3", weekday: day, startPeriod: 5, periodCount: 3,
                   weeks: CourseWeeks(first: 1, last: 18, parity: .odd), colorIndex: 3),
            Course(name: "Data Structures", teacher: "Zhao", room: "C305", weekday: tomorrow, startPeriod: 1, weeks: weeks, colorIndex: 7),
            Course(name: "Linear Algebra", teacher: "Chen", room: "A203", weekday: tomorrow, startPeriod: 5, weeks: weeks, colorIndex: 4),
        ]
    }
}
