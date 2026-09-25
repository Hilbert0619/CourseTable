import SwiftUI
import WidgetKit
import CourseKit

struct ScheduleEntry: TimelineEntry, Sendable {
    let date: Date
    let content: Content

    enum Content: Sendable {
        /// No snapshot from the app yet (never opened, or the App Group isn't available).
        case needsSetup
        case schedule(DaySchedule, current: ClassSession?, next: ClassSession?)
    }
}

struct ScheduleProvider: TimelineProvider {
    func placeholder(in context: Context) -> ScheduleEntry {
        .preview
    }

    func getSnapshot(in context: Context, completion: @escaping (ScheduleEntry) -> Void) {
        if context.isPreview {
            completion(.preview)
        } else {
            completion(Self.entries(from: .now, snapshot: SnapshotStore.read()).first ?? .preview)
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ScheduleEntry>) -> Void) {
        // Entries at every class start/end today; the last one is midnight, after which WidgetKit asks again.
        let entries = Self.entries(from: .now, snapshot: SnapshotStore.read())
        completion(Timeline(entries: entries, policy: .atEnd))
    }

    static func entries(from now: Date, snapshot: ScheduleSnapshot?, calendar: Calendar = .current) -> [ScheduleEntry] {
        guard let timetable = snapshot?.timetable else {
            return [ScheduleEntry(date: now, content: .needsSetup)]
        }
        return timetable.timelineMoments(from: Moment(now, calendar: calendar)).map { moment in
            let nowAndNext = timetable.currentAndNext(at: moment)
            return ScheduleEntry(
                date: moment.date(calendar: calendar),
                content: .schedule(timetable.daySchedule(on: moment.day), current: nowAndNext.current, next: nowAndNext.next)
            )
        }
    }
}

struct ScheduleWidget: Widget {
    let kind = "ScheduleWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ScheduleProvider()) { entry in
            ScheduleWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Class Schedule")
        .description("Your current or next class, and today's classes.")
        // StandBy shows the small size; the accessory sizes are for the Lock Screen.
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline, .accessoryCircular])
    }
}

extension ScheduleEntry {
    /// Sample content for the widget gallery and previews.
    static var preview: ScheduleEntry {
        let today = LocalDate(.now)
        let timetable = Timetable(semester: Semester(startingOn: today.adding(days: -28)), courses: SampleCourses.all(today: today))
        let sessions = timetable.sessions(on: today)
        return ScheduleEntry(
            date: .now,
            content: .schedule(timetable.daySchedule(on: today), current: sessions.first, next: sessions.dropFirst().first)
        )
    }
}

@main
struct CourseTableWidgetBundle: WidgetBundle {
    var body: some Widget {
        ScheduleWidget()
    }
}
