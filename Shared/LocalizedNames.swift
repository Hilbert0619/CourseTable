import Foundation
import CourseKit

// User-facing names for CourseKit values. Translations live in Localizable.xcstrings.

extension Weekday {
    var shortName: String {
        switch self {
        case .monday: String(localized: "Mon")
        case .tuesday: String(localized: "Tue")
        case .wednesday: String(localized: "Wed")
        case .thursday: String(localized: "Thu")
        case .friday: String(localized: "Fri")
        case .saturday: String(localized: "Sat")
        case .sunday: String(localized: "Sun")
        }
    }

    var fullName: String {
        switch self {
        case .monday: String(localized: "Monday")
        case .tuesday: String(localized: "Tuesday")
        case .wednesday: String(localized: "Wednesday")
        case .thursday: String(localized: "Thursday")
        case .friday: String(localized: "Friday")
        case .saturday: String(localized: "Saturday")
        case .sunday: String(localized: "Sunday")
        }
    }
}

extension WeekParity {
    var displayName: String {
        switch self {
        case .every: String(localized: "Every week")
        case .odd: String(localized: "Odd weeks")
        case .even: String(localized: "Even weeks")
        }
    }
}

extension CourseWeeks {
    /// e.g. "Weeks 1–16", "Week 5", "Weeks 1–15 · Odd weeks"
    var summary: String {
        let range = first == last ? String(localized: "Week \(first)") : String(localized: "Weeks \(first)–\(last)")
        return parity == .every ? range : "\(range) · \(parity.displayName)"
    }
}

extension Course {
    /// e.g. "Periods 3–4", or "Period 5" for a single period.
    var periodsLabel: String {
        periodCount > 1 ? String(localized: "Periods \(startPeriod)–\(endPeriod)") : String(localized: "Period \(startPeriod)")
    }

    /// Warning about this course, shown when it clashes with the one being edited or imported.
    var clashMessage: String {
        String(localized: "Clashes with \(name) (\(weekday.shortName), periods \(startPeriod)–\(endPeriod))")
    }
}

extension SemesterStatus {
    var summary: String {
        switch self {
        case .notStarted(let days): String(localized: "Term starts in \(days) days")
        case .inProgress(let week): String(localized: "Week \(week)")
        case .finished: String(localized: "Term has ended")
        }
    }
}

extension ClassSession {
    /// e.g. "10:00–11:40"
    var timeRange: String { "\(start)–\(end)" }

    /// Room and teacher, whichever are filled in, e.g. "A101 · Li".
    var details: String {
        [course.room, course.teacher].filter { !$0.isEmpty }.joined(separator: " · ")
    }
}
