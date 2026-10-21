import SwiftUI
import CourseKit

/// One day's classes and to-dos, today by default; the arrows and the date switch days.
struct TodayView: View {
    let timetable: Timetable
    @Environment(PlannerModel.self) private var planner
    /// The day shown; `nil` follows the clock (today).
    @State private var pickedDay: LocalDate?
    @State private var editing: TodoEditorTarget?
    @State private var showingAssistant = false

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
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showingAssistant = true
                    } label: {
                        Label("Assistant", systemImage: "sparkles")
                    }
                    .accessibilityIdentifier("assistantButton")
                }
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
            .sheet(isPresented: $showingAssistant) {
                AssistantView()
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
    @AppStorage(AppAppearance.styleKey) private var style = AppAppearance.defaultStyle

    var body: some View {
        let isToday = day == now.day
        let agenda = timetable.agenda(on: day, todos: todos)
        let nowAndNext = timetable.currentAndNext(at: now)
        List {
            Section {
                DayHeader(day: day, isToday: isToday, status: timetable.semester.status(on: day).summary, pickDay: pickDay, today: now.day,
                          glass: style == .glass)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 0, leading: 8, bottom: 0, trailing: 8))
            }

            if isToday && !timetable.courses.isEmpty {
                Section {
                    NowNextCard(now: now, current: nowAndNext.current, next: nowAndNext.next,
                                openTodos: todos.filter { $0.day == now.day && !$0.isDone }.count, style: style)
                }
            }

            if !agenda.anytime.isEmpty {
                Section("Any time") {
                    ForEach(agenda.anytime) { todo in
                        TodoRow(todo: todo, timetable: timetable, edit: edit)
                    }
                    .glassRows(style)
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
                                .glassButtonStyle(style)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .glassRows(style)
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
                    .glassRows(style)
                }
            }

        }
        .tabBackground(style)
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
    /// The arrows and Today on glass. (Borderless buttons: a list row with several buttons of another style would
    /// tap them all.)
    var glass = false

    var body: some View {
        HStack(spacing: 12) {
            Button {
                pickDay(day.adding(days: -1))
            } label: {
                arrow("chevron.left")
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
                Button {
                    pickDay(today)
                } label: {
                    Text("Today")
                        .font(.subheadline)
                        .padding(.horizontal, glass ? 12 : 0)
                        .frame(minHeight: glass ? 36 : nil)
                        .modifier(GlassCapsuleIf(glass))
                }
            }
            Button {
                pickDay(day.adding(days: 1))
            } label: {
                arrow("chevron.right")
            }
            .accessibilityLabel(Text("Next Day"))
        }
        .buttonStyle(.borderless)
        .padding(.vertical, 4)
    }

    private func arrow(_ symbol: String) -> some View {
        Image(systemName: symbol)
            .fontWeight(glass ? .semibold : .regular)
            .frame(width: glass ? 36 : nil, height: glass ? 36 : nil)
            .modifier(GlassCapsuleIf(glass))
    }
}

/// `glassCapsule(interactive:)` only when `isOn`.
struct GlassCapsuleIf: ViewModifier {
    let isOn: Bool
    var tint: Color?

    init(_ isOn: Bool, tint: Color? = nil) {
        self.isOn = isOn
        self.tint = tint
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        if isOn {
            content.glassCapsule(tint: tint, interactive: true)
        } else {
            content
        }
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
                    .symbolEffect(.bounce, value: todo.todo.isDone)
            }
            .buttonStyle(.borderless)
            .sensoryFeedback(trigger: todo.todo.isDone) { _, isDone in isDone ? .success : nil }
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

/// Today's first card: the class in progress (time left, progress) or the next one (when, where), tinted in that
/// course's soft colour, and the to-dos still open today. It draws its own background, since a List row background
/// isn't redrawn when the class changes.
private struct NowNextCard: View {
    let now: Moment
    let current: ClassSession?
    let next: ClassSession?
    let openTodos: Int
    let style: AppStyle

    var body: some View {
        let session = current ?? next
        let tint = session.map { CoursePalette.block($0.course.colorIndex, style: .soft) }
        let ink = tint?.text ?? Color.primary
        VStack(alignment: .leading, spacing: 6) {
            if let session {
                HStack(spacing: 6) {
                    Circle()
                        .fill(session.course.color)
                        .frame(width: 8, height: 8)
                    Text(verbatim: headline(for: session))
                        .font(.caption.weight(.bold))
                        .textCase(.uppercase)
                }
                Text(verbatim: session.course.name)
                    .font(.title2.weight(.bold))
                    .lineLimit(2)
                Text(verbatim: ([session.timeRange] + (session.details.isEmpty ? [] : [session.details])).joined(separator: " · "))
                    .font(.subheadline)
                    .opacity(0.85)
                if current != nil {
                    ProgressView(value: progress(of: session))
                        .tint(session.course.color)
                        .padding(.top, 4)
                }
            } else {
                Label("No more classes today", systemImage: "moon.stars")
                    .font(.headline)
            }
            if openTodos > 0 {
                // Not a Label: in a list it keeps a wide column for the icon.
                HStack(spacing: 6) {
                    Image(systemName: "checklist")
                    Text("\(openTodos) to-dos left today")
                }
                .font(.footnote.weight(.medium))
                .opacity(0.85)
                .padding(.top, 2)
            }
        }
        .foregroundStyle(ink)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .surface(style, tint: tint?.fill)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.clear)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("nowNextCard")
    }

    /// "Now · 25 min left", "Next · in 1 hr, 5 min", "Next · Tomorrow", "Next · Monday".
    private func headline(for session: ClassSession) -> String {
        if session == current {
            let left = max(session.end.minutes - now.time.minutes, 0)
            return String(localized: "Now · \(focusDuration(left * 60)) left")
        }
        if session.day == now.day {
            let until = max(session.start.minutes - now.time.minutes, 0)
            return String(localized: "Next · in \(focusDuration(until * 60))")
        }
        if session.day == now.day.adding(days: 1) {
            return String(localized: "Next · Tomorrow")
        }
        return String(localized: "Next · \(session.course.weekday.fullName)")
    }

    private func progress(of session: ClassSession) -> Double {
        let length = Double(max(session.end.minutes - session.start.minutes, 1))
        return min(max(Double(now.time.minutes - session.start.minutes) / length, 0), 1)
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
