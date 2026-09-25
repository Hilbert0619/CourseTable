import Foundation

/// Launch arguments set by `CourseTableUITests`. A normal launch has neither.
enum UITestLaunch {
    /// Use an in-memory store and skip side effects outside the app (widget snapshot, notifications).
    static let isActive = ProcessInfo.processInfo.arguments.contains("-uiTesting")
    /// Start with the demo timetable instead of an empty one.
    static let wantsDemoData = ProcessInfo.processInfo.arguments.contains("-demoData")
}
