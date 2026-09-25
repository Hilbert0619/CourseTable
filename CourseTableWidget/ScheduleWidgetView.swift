import SwiftUI
import WidgetKit
import CourseKit

struct ScheduleWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: ScheduleEntry

    var body: some View {
        switch entry.content {
        case .needsSetup:
            NeedsSetupView(family: family)
        case .schedule(let day, let current, let next):
            let now = Moment(entry.date)
            switch family {
            case .systemSmall:
                SmallScheduleView(now: now, day: day, current: current, next: next)
            case .accessoryRectangular:
                RectangularScheduleView(now: now, day: day, current: current, next: next)
            case .accessoryInline:
                InlineScheduleView(now: now, current: current, next: next)
            case .accessoryCircular:
                CircularScheduleView(now: now, current: current, next: next)
            default:
                MediumScheduleView(now: now, day: day, current: current, next: next)
            }
        }
    }
}

/// Shown until the app has written its first snapshot.
private struct NeedsSetupView: View {
    let family: WidgetFamily

    var body: some View {
        switch family {
        case .accessoryInline:
            Label("Open Course Table", systemImage: "calendar.badge.plus")
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "calendar.badge.plus")
                    .font(.title3)
            }
        case .accessoryRectangular:
            Text("Open Course Table to add your classes.")
                .font(.caption)
                .frame(maxWidth: .infinity, alignment: .leading)
        default:
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: "calendar.badge.plus")
                    .font(.title2)
                    .foregroundStyle(Color.accentColor)
                Text("Open Course Table to add your classes.")
                    .font(.footnote)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }
}

/// Lock Screen, rectangular: "Now · 08:00–09:40", the course name, then room and teacher.
private struct RectangularScheduleView: View {
    let now: Moment
    let day: DaySchedule
    let current: ClassSession?
    let next: ClassSession?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let session = current ?? next {
                Text(verbatim: headline(for: session))
                    .font(.caption.weight(.semibold))
                    .widgetAccentable()
                Text(verbatim: session.course.name)
                    .font(.headline)
                    .lineLimit(1)
                Text(verbatim: session.details.isEmpty ? session.course.periodsLabel : session.details)
                    .font(.caption)
                    .lineLimit(1)
            } else {
                Text(verbatim: day.semesterStatus.summary)
                    .font(.caption.weight(.semibold))
                    .widgetAccentable()
                Text("No upcoming classes")
                    .font(.headline)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func headline(for session: ClassSession) -> String {
        let label = current != nil ? String(localized: "Now") : String(localized: "Next")
        let time = session.day == now.day ? session.timeRange : "\(session.course.weekday.shortName) \(session.timeRange)"
        return "\(label) · \(time)"
    }
}

/// Lock Screen, one line above the clock: "Calculus until 09:40" or "10:00 Physics".
private struct InlineScheduleView: View {
    let now: Moment
    let current: ClassSession?
    let next: ClassSession?

    var body: some View {
        if let current {
            Label {
                Text(verbatim: String(localized: "\(current.course.name) until \(current.end.description)"))
            } icon: {
                Image(systemName: "book.closed")
            }
        } else if let next {
            let time = next.day == now.day ? next.start.description : "\(next.course.weekday.shortName) \(next.start)"
            Label {
                Text(verbatim: "\(time) \(next.course.name)")
            } icon: {
                Image(systemName: "clock")
            }
        } else {
            Label("No upcoming classes", systemImage: "checkmark.circle")
        }
    }
}

/// Lock Screen, circular: progress through the current class, or the next class's start time.
private struct CircularScheduleView: View {
    let now: Moment
    let current: ClassSession?
    let next: ClassSession?

    var body: some View {
        if let current, let interval = current.dateInterval {
            ProgressView(timerInterval: interval, countsDown: false) {
                Text(verbatim: current.course.name)
            } currentValueLabel: {
                Text(verbatim: current.end.description)
                    .monospacedDigit()
            }
            .progressViewStyle(.circular)
            .widgetAccentable()
        } else {
            ZStack {
                AccessoryWidgetBackground()
                if let next {
                    VStack(spacing: 0) {
                        Text("Next")
                            .font(.system(size: 10, weight: .semibold))
                        Text(verbatim: next.start.description)
                            .font(.system(size: 15, weight: .semibold).monospacedDigit())
                            .minimumScaleFactor(0.6)
                        if next.day != now.day {
                            Text(verbatim: next.course.weekday.shortName)
                                .font(.system(size: 10))
                        }
                    }
                    .widgetAccentable()
                } else {
                    Image(systemName: "checkmark")
                        .font(.title3)
                }
            }
        }
    }
}

extension ClassSession {
    /// Start to end as dates, or `nil` if the period's times are the wrong way round.
    var dateInterval: ClosedRange<Date>? {
        let start = startMoment.date()
        let end = endMoment.date()
        return start < end ? start...end : nil
    }
}

/// Small: the class in progress, or else the next one.
private struct SmallScheduleView: View {
    let now: Moment
    let day: DaySchedule
    let current: ClassSession?
    let next: ClassSession?

    var body: some View {
        if let session = current ?? next {
            let isCurrent = current != nil
            VStack(alignment: .leading, spacing: 4) {
                Text(isCurrent ? String(localized: "Now") : String(localized: "Next"))
                    .font(.caption.bold())
                    .foregroundStyle(session.course.color)
                Text(verbatim: session.course.name)
                    .font(.headline)
                    .lineLimit(3)
                Spacer(minLength: 0)
                Text(verbatim: timeLabel(for: session))
                    .font(.caption.monospacedDigit())
                if !session.course.room.isEmpty {
                    Text(verbatim: session.course.room)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: day.semesterStatus.summary)
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
                Text("No upcoming classes")
                    .font(.headline)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }

    private func timeLabel(for session: ClassSession) -> String {
        session.day == now.day ? session.timeRange : "\(session.course.weekday.shortName) \(session.timeRange)"
    }
}

/// Medium: today's classes, finished ones dimmed; tomorrow onwards if today is done.
private struct MediumScheduleView: View {
    let now: Moment
    let day: DaySchedule
    let current: ClassSession?
    let next: ClassSession?

    private let maxRows = 4

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Today")
                    .font(.headline)
                Spacer()
                Text(verbatim: day.semesterStatus.summary)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            let remaining = day.remaining(at: now.time)
            if remaining.isEmpty {
                Spacer(minLength: 0)
                Group {
                    if day.sessions.isEmpty {
                        Text("No classes today")
                    } else {
                        Text("No more classes today")
                    }
                }
                .font(.subheadline)
                if let next {
                    Text(verbatim: "\(String(localized: "Next")): \(next.course.name) · \(next.course.weekday.shortName) \(next.timeRange)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
            } else {
                ForEach(remaining.prefix(maxRows)) { session in
                    WidgetSessionRow(session: session, isCurrent: session.id == current?.id)
                }
                if remaining.count > maxRows {
                    Text("+\(remaining.count - maxRows) more")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct WidgetSessionRow: View {
    let session: ClassSession
    let isCurrent: Bool

    var body: some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 2)
                .fill(session.course.color)
                .frame(width: 4, height: 16)
            Text(verbatim: session.start.description)
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            Text(verbatim: session.course.name)
                .font(.caption.weight(isCurrent ? .bold : .regular))
                .lineLimit(1)
            Spacer(minLength: 4)
            Text(verbatim: session.course.room)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }
}

#Preview(as: .systemSmall) {
    ScheduleWidget()
} timeline: {
    ScheduleEntry.preview
    ScheduleEntry(date: .now, content: .needsSetup)
}

#Preview(as: .systemMedium) {
    ScheduleWidget()
} timeline: {
    ScheduleEntry.preview
}

#Preview(as: .accessoryRectangular) {
    ScheduleWidget()
} timeline: {
    ScheduleEntry.preview
    ScheduleEntry(date: .now, content: .needsSetup)
}

#Preview(as: .accessoryInline) {
    ScheduleWidget()
} timeline: {
    ScheduleEntry.preview
}

#Preview(as: .accessoryCircular) {
    ScheduleWidget()
} timeline: {
    ScheduleEntry.preview
}
