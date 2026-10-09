import SwiftUI
import CourseKit

/// One day's classes and to-dos, today by default; the arrows and the date switch days.
struct TodayView: View {
    let timetable: Timetable
    @Environment(PlannerModel.self) private var planner
    /// The day shown; `nil` follows the clock (today).
    @State private var pickedDay: LocalDate?
    @State private var editing: TodoEditorTarget?

    var body: some View {
        NavigationStack {
            TimelineView(.everyMinute) { context in
                let now = Moment(context.date)
                TodayContent(
                    timetable: timetable, now: now, day: pickedDay ?? now.day, todos: planner.todos,
                    pickDay: { day in pickedDay = day == now.day ? nil : day },
                    edit: { editing = $0 }
                )
            }
            .navigationTitle("Today")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        editing = .new(day: pickedDay ?? LocalDate(.now))
                    } label: {
                        Label("Add To-Do", systemImage: "plus")
                    }
                    .accessibilityIdentifier("addTodo")
                }
            }
            .sheet(item: $editing) { target in
                TodoEditorView(timetable: timetable, target: target)
            }
        }
    }
}

private struct TodayContent: View {
    let timetable: Timetable
    let now: Moment
    let day: LocalDate
    let todos: [Todo]
    let pickDay: (LocalDate) -> Void
    let edit: (TodoEditorTarget) -> Void

    var body: some View {
        let isToday = day == now.day
        let agenda = timetable.agenda(on: day, todos: todos)
        let nowAndNext = timetable.currentAndNext(at: now)
        List {
            Section {
                DayHeader(day: day, isToday: isToday, status: timetable.semester.status(on: day).summary, pickDay: pickDay, today: now.day)
            }

            if !agenda.anytime.isEmpty {
                Section("Any time") {
                    ForEach(agenda.anytime) { todo in
                        TodoRow(todo: todo, timetable: timetable, edit: edit)
                    }
                }
            }

            if agenda.items.isEmpty {
                if agenda.anytime.isEmpty {
                    Section {
                        VStack(spacing: 8) {
                            Image(systemName: "cup.and.saucer")
                                .font(.largeTitle)
                                .foregroundStyle(.secondary)
                            (isToday ? Text("No classes today") : Text("No classes on this day"))
                                .font(.headline)
                            Button("Add To-Do") { edit(.new(day: day)) }
                                .buttonStyle(.bordered)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                    }
                }
            } else {
                Section("Schedule") {
                    ForEach(agenda.items) { item in
                        switch item {
                        case .session(let session, let after):
                            SessionRow(
                                session: session,
                                status: status(of: session, isToday: isToday),
                                isNext: isToday && session.id == nowAndNext.next?.id && nowAndNext.current == nil
                            )
                            ForEach(after) { todo in
                                TodoRow(todo: todo, timetable: timetable, edit: edit, isUnderItsClass: true)
                            }
                        case .todo(let todo):
                            TodoRow(todo: todo, timetable: timetable, edit: edit)
                        }
                    }
                }
            }

            if isToday, let next = nowAndNext.next, next.day != now.day {
                Section("Next class") {
                    SessionRow(session: next, status: .upcoming, isNext: false, showsDay: true)
                }
            }
        }
    }

    /// Classes of another day are all past or all ahead.
    private func status(of session: ClassSession, isToday: Bool) -> SessionStatus {
        if isToday { return session.status(at: now) }
        return day < now.day ? .finished : .upcoming
    }
}

/// The date, the term week, and the buttons to move between days.
private struct DayHeader: View {
    let day: LocalDate
    let isToday: Bool
    let status: String
    let pickDay: (LocalDate) -> Void
    let today: LocalDate

    var body: some View {
        HStack(spacing: 12) {
            Button {
                pickDay(day.adding(days: -1))
            } label: {
                Image(systemName: "chevron.left")
            }
            .accessibilityLabel(Text("Previous Day"))
            VStack(alignment: .leading, spacing: 4) {
                Text(day.date().formatted(date: .complete, time: .omitted))
                    .font(.headline)
                Text(verbatim: status)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !isToday {
                Button("Today") { pickDay(today) }
                    .font(.subheadline)
            }
            Button {
                pickDay(day.adding(days: 1))
            } label: {
                Image(systemName: "chevron.right")
            }
            .accessibilityLabel(Text("Next Day"))
        }
        .buttonStyle(.borderless)
        .padding(.vertical, 4)
    }
}

/// A to-do: tick it off with the circle, tap to edit, swipe to delete.
private struct TodoRow: View {
    let todo: ResolvedTodo
    let timetable: Timetable
    let edit: (TodoEditorTarget) -> Void
    /// Listed right under its class: the time line says "After class".
    var isUnderItsClass = false
    @Environment(PlannerModel.self) private var planner

    var body: some View {
        HStack(spacing: 12) {
            Button {
                planner.setDone(todo.id, !todo.todo.isDone)
            } label: {
                Image(systemName: todo.todo.isDone ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(todo.todo.isDone ? Color.accentColor : Color.secondary)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(todo.todo.isDone ? Text("Mark as Not Done") : Text("Mark as Done"))
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: todo.todo.title)
                    .strikethrough(todo.todo.isDone)
                    .foregroundStyle(todo.todo.isDone ? .secondary : .primary)
                if let detail {
                    Text(verbatim: detail)
                        .font(.caption)
                        .foregroundStyle(todo.isMissingItsClass ? Color.orange : Color.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.leading, isUnderItsClass ? 18 : 0)
        .contentShape(Rectangle())
        .onTapGesture { edit(.edit(todo.todo)) }
        .swipeActions {
            Button("Delete", role: .destructive) { planner.delete(todo.id) }
        }
        .accessibilityIdentifier("todo-\(todo.todo.title)")
    }

    /// "12:00", "After class · 09:40", "After Calculus · 09:40", or a note that its class isn't on that day.
    private var detail: String? {
        if todo.isMissingItsClass {
            if case .afterClass(let courseID) = todo.todo.timing,
               let course = timetable.courses.first(where: { $0.id == courseID }) {
                return String(localized: "No \(course.name) class on this day")
            }
            return String(localized: "Its class was removed")
        }
        guard let due = todo.due else { return nil }
        if isUnderItsClass {
            return "\(String(localized: "After class")) · \(due.time)"
        }
        return TodoReminders.when(todo)
    }
}

/// What the to-do editor sheet is showing.
enum TodoEditorTarget: Identifiable {
    case new(day: LocalDate)
    case edit(Todo)

    var id: String {
        switch self {
        case .new(let day): "new-\(day)"
        case .edit(let todo): todo.id.uuidString
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
    let courses = SampleCourses.all(today: today)
    let timetable = Timetable(semester: Semester(startingOn: today.adding(days: -28)), courses: courses)
    return TodayView(timetable: timetable)
        .environment(PlannerModel.preview(courses: courses, today: today))
}
