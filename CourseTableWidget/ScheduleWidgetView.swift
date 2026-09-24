import SwiftUI
import WidgetKit
import CourseKit

struct ScheduleWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: ScheduleEntry

    var body: some View {
        switch entry.content {
        case .needsSetup:
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: "calendar.badge.plus")
                    .font(.title2)
                    .foregroundStyle(Color.accentColor)
                Text("Open Course Table to add your classes.")
                    .font(.footnote)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        case .schedule(let day, let current, let next):
            if family == .systemSmall {
                SmallScheduleView(now: Moment(entry.date), day: day, current: current, next: next)
            } else {
                MediumScheduleView(now: Moment(entry.date), day: day, current: current, next: next)
            }
        }
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
