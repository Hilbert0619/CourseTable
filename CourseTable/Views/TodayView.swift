import SwiftUI
import CourseKit

struct TodayView: View {
    let timetable: Timetable

    var body: some View {
        NavigationStack {
            TimelineView(.everyMinute) { context in
                TodayContent(timetable: timetable, now: Moment(context.date))
            }
            .navigationTitle("Today")
        }
    }
}

private struct TodayContent: View {
    let timetable: Timetable
    let now: Moment

    var body: some View {
        let schedule = timetable.daySchedule(on: now.day)
        let nowAndNext = timetable.currentAndNext(at: now)
        let current = nowAndNext.current
        let next = nowAndNext.next
        List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(now.day.date().formatted(date: .complete, time: .omitted))
                        .font(.headline)
                    Text(schedule.semesterStatus.summary)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }

            if schedule.sessions.isEmpty {
                Section {
                    VStack(spacing: 8) {
                        Image(systemName: "cup.and.saucer")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text("No classes today")
                            .font(.headline)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                }
            } else {
                Section("Classes") {
                    ForEach(schedule.sessions) { session in
                        SessionRow(
                            session: session,
                            status: session.status(at: now),
                            isNext: session.id == next?.id && current == nil
                        )
                    }
                }
            }

            if let next, next.day != now.day {
                Section("Next class") {
                    SessionRow(session: next, status: .upcoming, isNext: false, showsDay: true)
                }
            }
        }
    }
}

struct SessionRow: View {
    let session: ClassSession
    let status: SessionStatus
    let isNext: Bool
    var showsDay = false

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(session.course.color)
                .frame(width: 6)
            VStack(alignment: .leading, spacing: 3) {
                Text(session.course.name)
                    .font(.headline)
                Group {
                    if showsDay {
                        Text(verbatim: "\(session.course.weekday.fullName) \(session.timeRange)")
                    } else {
                        Text(verbatim: session.timeRange)
                    }
                }
                .font(.subheadline.monospacedDigit())
                Text("Periods \(session.course.startPeriod)–\(session.course.endPeriod)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if !session.details.isEmpty {
                    Text(verbatim: session.details)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
            if status == .inProgress {
                Badge(text: String(localized: "Now"), color: session.course.color)
            } else if isNext {
                Badge(text: String(localized: "Next"), color: .secondary)
            }
        }
        .padding(.vertical, 2)
        .opacity(status == .finished ? 0.45 : 1)
    }
}

private struct Badge: View {
    let text: String
    let color: Color

    var body: some View {
        Text(verbatim: text)
            .font(.caption.bold())
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundStyle(color)
            .background(color.opacity(0.15), in: Capsule())
    }
}

#Preview {
    let today = LocalDate(.now)
    let timetable = Timetable(
        semester: Semester(startingOn: today.adding(days: -28)),
        courses: SampleCourses.all(today: today)
    )
    return TodayView(timetable: timetable)
}
