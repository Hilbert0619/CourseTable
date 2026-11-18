import AppIntents
import SwiftUI
import WidgetKit
import CourseKit

/// Today's to-dos on the Home Screen, ticked off right there (`ToggleTodoIntent`, which runs in the app).
struct TodoEntry: TimelineEntry, Sendable {
    let date: Date
    let content: Content

    enum Content: Sendable {
        /// No planner snapshot from the app yet (not opened since 0.7.2, or no App Group).
        case needsSetup
        case list(TodoWidgetList)
    }
}

struct TodoProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodoEntry {
        .preview
    }

    func getSnapshot(in context: Context, completion: @escaping (TodoEntry) -> Void) {
        if context.isPreview {
            completion(.preview)
        } else {
            completion(Self.entries(from: .now, planner: SnapshotStore.readPlanner(), schedule: SnapshotStore.read()).first ?? .preview)
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodoEntry>) -> Void) {
        // An entry at each to-do's time today (it turns overdue), the last at midnight; then WidgetKit asks again.
        let entries = Self.entries(from: .now, planner: SnapshotStore.readPlanner(), schedule: SnapshotStore.read())
        completion(Timeline(entries: entries, policy: .atEnd))
    }

    static func entries(from now: Date, planner: PlannerSnapshot?, schedule: ScheduleSnapshot?, calendar: Calendar = .current) -> [TodoEntry] {
        guard let planner else {
            return [TodoEntry(date: now, content: .needsSetup)]
        }
        let timetable = schedule?.timetable
        return planner.todoTimelineMoments(from: Moment(now, calendar: calendar), timetable: timetable).enumerated().map { index, moment in
            TodoEntry(date: index == 0 ? now : moment.date(calendar: calendar),
                      content: .list(planner.todoList(at: moment, timetable: timetable)))
        }
    }
}

struct TodoWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKinds.todos, provider: TodoProvider()) { entry in
            TodoWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("To-Dos")
        .description("Today's to-dos. Tick them off right on the Home Screen.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct TodoWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TodoEntry

    var body: some View {
        switch entry.content {
        case .needsSetup:
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: "checklist")
                    .font(.title2)
                    .foregroundStyle(Color.accentColor)
                    .widgetAccentable()
                Text("Open Course Table to see your to-dos.")
                    .font(.footnote)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        case .list(let list):
            TodoListView(list: list, family: family)
        }
    }
}

private struct TodoListView: View {
    let list: TodoWidgetList
    let family: WidgetFamily

    private var isSmall: Bool { family == .systemSmall }
    private var maxRows: Int {
        switch family {
        case .systemSmall: 3
        case .systemLarge: 10
        default: 4
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: isSmall ? 7 : 6) {
            HStack(alignment: .firstTextBaseline) {
                Text("To-Dos")
                    .font(.headline)
                Spacer(minLength: 4)
                if list.remaining > 0 {
                    Text("\(list.remaining) left")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.accentColor)
                        .widgetAccentable()
                } else if !list.isEmpty {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(.green)
                        .widgetAccentable()
                }
            }

            if list.isEmpty || list.remaining == 0 {
                Spacer(minLength: 0)
                (list.isEmpty ? Text("Nothing due today") : Text("All done for today"))
                    .font(isSmall ? .footnote : .subheadline)
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
            } else {
                // Unfinished ones first; the done ones fill what room is left.
                ForEach(list.items.prefix(maxRows)) { item in
                    TodoRow(item: item, showsDue: !isSmall)
                }
                if list.items.count > maxRows {
                    Text("+\(list.items.count - maxRows) more")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct TodoRow: View {
    let item: TodoWidgetItem
    let showsDue: Bool

    private var isDone: Bool { item.todo.isDone }

    var body: some View {
        HStack(spacing: 7) {
            Button(intent: ToggleTodoIntent(id: item.id, isDone: !isDone)) {
                Image(systemName: isDone ? "checkmark.circle.fill" : "circle")
                    .font(.body)
                    .foregroundStyle(isDone ? AnyShapeStyle(Color.accentColor) : AnyShapeStyle(.secondary))
                    .widgetAccentable(isDone)
                    .invalidatableContent()
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isDone ? Text("Not Done") : Text("Done"))

            Text(verbatim: item.todo.title)
                .font(.footnote)
                .strikethrough(isDone)
                .foregroundStyle(isDone ? .secondary : .primary)
                .lineLimit(1)

            Spacer(minLength: 4)

            if showsDue, let due = item.due {
                HStack(spacing: 4) {
                    // A to-do after a class: that course's colour.
                    if let session = item.session {
                        Circle()
                            .fill(session.course.color)
                            .frame(width: 6, height: 6)
                    }
                    Text(verbatim: due.time.description)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(item.isOverdue ? AnyShapeStyle(.red) : AnyShapeStyle(.secondary))
                }
            }
        }
    }
}

extension TodoEntry {
    /// Sample content for the widget gallery and previews.
    static var preview: TodoEntry {
        let today = LocalDate(.now)
        let courses = SampleCourses.all(today: today)
        let timetable = Timetable(semester: Semester(startingOn: today.adding(days: -28)), courses: courses)
        let first = timetable.sessions(on: today).first
        let chinese = Locale.current.language.languageCode?.identifier == "zh"
        var todos = [
            Todo(title: chinese ? "取快递" : "Pick up the package", day: today, timing: .at(TimeOfDay(hour: 12, minute: 10))),
            Todo(title: chinese ? "买牛奶" : "Buy milk", day: today),
            Todo(title: chinese ? "还图书馆的书" : "Return the library book", day: today, timing: .at(TimeOfDay(hour: 7, minute: 30)), isDone: true),
        ]
        if let first {
            todos.insert(Todo(title: chinese ? "交论文" : "Hand in the paper", day: today, timing: .afterClass(courseID: first.course.id)), at: 0)
        }
        let snapshot = PlannerSnapshot(todos: todos, focusDays: [], run: nil)
        return TodoEntry(date: .now, content: .list(snapshot.todoList(at: Moment(day: today, time: TimeOfDay(hour: 7, minute: 0)), timetable: timetable)))
    }
}

#Preview(as: .systemSmall) {
    TodoWidget()
} timeline: {
    TodoEntry.preview
    TodoEntry(date: .now, content: .needsSetup)
}

#Preview(as: .systemMedium) {
    TodoWidget()
} timeline: {
    TodoEntry.preview
}

#Preview(as: .systemLarge) {
    TodoWidget()
} timeline: {
    TodoEntry.preview
}
