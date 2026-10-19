import SwiftUI
import CourseKit
import AIKit

/// Adds or edits a to-do: what, which day, and when (any time, at a time, or after one of that day's classes).
struct TodoEditorView: View {
    @Environment(PlannerModel.self) private var planner
    @Environment(AIStore.self) private var ai
    @Environment(\.dismiss) private var dismiss

    let timetable: Timetable
    let isNew: Bool
    @State private var draft: Todo
    @State private var kind: Kind
    @State private var time: TimeOfDay
    @State private var courseID: UUID?
    @FocusState private var titleFocused: Bool
    /// Quick add: a note the AI turns into the fields below.
    @State private var note = ""
    @State private var noteRun = AIRun()
    @State private var noteProblem: NoteProblem?

    enum Kind: Hashable {
        case anytime, at, afterClass
    }

    enum NoteProblem: Hashable {
        case unknownCourse(String)
        case noClassThatDay(String)
        case unreadable
    }

    init(timetable: Timetable, target: TodoEditorTarget) {
        self.timetable = timetable
        let todo: Todo
        switch target {
        case .new(let day):
            isNew = true
            todo = Todo(title: "", day: day)
        case .edit(let existing):
            isNew = false
            todo = existing
        }
        _draft = State(initialValue: todo)
        switch todo.timing {
        case .anytime:
            _kind = State(initialValue: .anytime)
            _time = State(initialValue: Self.suggestedTime())
            _courseID = State(initialValue: nil)
        case .at(let time):
            _kind = State(initialValue: .at)
            _time = State(initialValue: time)
            _courseID = State(initialValue: nil)
        case .afterClass(let id):
            _kind = State(initialValue: .afterClass)
            _time = State(initialValue: Self.suggestedTime())
            _courseID = State(initialValue: id)
        }
    }

    /// The next full hour.
    private static func suggestedTime(now: Date = .now) -> TimeOfDay {
        TimeOfDay(hour: min(TimeOfDay(now).hour + 1, 23), minute: 0)
    }

    private var sessions: [ClassSession] { timetable.sessions(on: draft.day) }

    private var timing: TodoTiming? {
        switch kind {
        case .anytime:
            return TodoTiming.anytime
        case .at:
            return TodoTiming.at(time)
        case .afterClass:
            guard let courseID, sessions.contains(where: { $0.course.id == courseID }) else { return nil }
            return TodoTiming.afterClass(courseID: courseID)
        }
    }

    private var canSave: Bool {
        !draft.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && timing != nil
    }

    var body: some View {
        NavigationStack {
            Form {
                if isNew && ai.isSetUp {
                    quickAddSection
                }
                Section {
                    TextField("What to do", text: $draft.title, axis: .vertical)
                        .focused($titleFocused)
                        .accessibilityIdentifier("todoTitle")
                }
                Section {
                    DatePicker("Date", selection: dayBinding, displayedComponents: .date)
                    Picker("When", selection: $kind) {
                        Text("Any time").tag(Kind.anytime)
                        Text("At a time").tag(Kind.at)
                        Text("After a class").tag(Kind.afterClass)
                    }
                    .accessibilityIdentifier("todoWhen")
                    switch kind {
                    case .anytime:
                        EmptyView()
                    case .at:
                        DatePicker("Time", selection: timeBinding, displayedComponents: .hourAndMinute)
                    case .afterClass:
                        if sessions.isEmpty {
                            Text("No classes on this day")
                                .foregroundStyle(.secondary)
                        } else {
                            Picker("Class", selection: $courseID) {
                                ForEach(sessions) { session in
                                    Text(verbatim: "\(session.course.name) · \(session.timeRange)").tag(Optional(session.course.id))
                                }
                            }
                        }
                    }
                } footer: {
                    if kind != .anytime {
                        Text("You get a notification at that time, or when the class ends. A class's to-dos also show in its Live Activity.")
                    }
                }
                if !isNew {
                    Section {
                        Button("Delete To-Do", role: .destructive) {
                            planner.delete(draft.id)
                            dismiss()
                        }
                    }
                }
            }
            .paperBackground()
            .navigationTitle(isNew ? Text("New To-Do") : Text("Edit To-Do"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .disabled(!canSave)
                        .accessibilityIdentifier("todoSave")
                }
            }
            .onAppear {
                if isNew { titleFocused = true }
                if kind == .afterClass, courseID == nil { courseID = sessions.first?.course.id }
            }
            .onChange(of: kind) { _, newKind in
                if newKind == .afterClass, courseID == nil || !sessions.contains(where: { $0.course.id == courseID }) {
                    courseID = sessions.first?.course.id
                }
            }
            .onChange(of: draft.day) { _, _ in
                if kind == .afterClass, !sessions.contains(where: { $0.course.id == courseID }) {
                    courseID = sessions.first?.course.id
                }
            }
        }
    }

    private var quickAddSection: some View {
        Section {
            TextField("e.g. hand in the paper after Calculus tomorrow", text: $note, axis: .vertical)
                .accessibilityIdentifier("todoQuickAdd")
            Button {
                fillIn()
            } label: {
                HStack {
                    Label("Fill In with AI", systemImage: "sparkles")
                    if noteRun.isRunning {
                        Spacer()
                        ProgressView()
                    }
                }
            }
            .disabled(note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || noteRun.isRunning)
            .accessibilityIdentifier("todoFillIn")
            if let error = noteRun.error {
                AIErrorView(error: error)
            }
            if let noteProblem {
                Group {
                    switch noteProblem {
                    case .unknownCourse(let name):
                        Text("No class called “\(name)” in your timetable, so the time was left open.")
                    case .noClassThatDay(let name):
                        Text("\(name) has no class on that day. Check the date.")
                    case .unreadable:
                        Text("The AI's answer couldn't be read. Try describing it differently.")
                    }
                }
                .font(.footnote)
                .foregroundStyle(.orange)
            }
        } header: {
            Text("Quick Add")
        } footer: {
            Text("Describe the to-do in your own words; AI fills in the fields below for you to check.")
        }
    }

    private func fillIn() {
        titleFocused = false
        noteProblem = nil
        let today = LocalDate(.now)
        let courses = timetable.courses
        let names = Array(Set(courses.map(\.name))).sorted()
        let classes = AIPrompts.upcomingClasses(timetable, from: today)
        let request = AIPrompts.todoDraft(note, today: today, now: TimeOfDay(.now), courses: names, classes: classes)
        noteRun.start(request, store: ai) { answer in
            guard let parsed = try? TodoDraft.parse(answer.text, today: today, courses: courses) else {
                noteProblem = .unreadable
                return
            }
            apply(parsed)
        }
    }

    /// Fills in the fields from the AI's draft. The course is set before the day and the kind, so the checks
    /// that follow a change of day keep it.
    private func apply(_ parsed: TodoDraft) {
        draft.title = parsed.title
        switch parsed.timing {
        case .anytime:
            draft.day = parsed.day
            kind = .anytime
        case .at(let clock):
            time = clock
            draft.day = parsed.day
            kind = .at
        case .afterClass(let id):
            if timetable.sessions(on: parsed.day).contains(where: { $0.course.id == id }) {
                courseID = id
                draft.day = parsed.day
                kind = .afterClass
            } else {
                draft.day = parsed.day
                kind = .anytime
                noteProblem = .noClassThatDay(timetable.courses.first { $0.id == id }?.name ?? "")
            }
        }
        if let unknown = parsed.unknownCourse { noteProblem = .unknownCourse(unknown) }
    }

    private var dayBinding: Binding<Date> {
        Binding(get: { draft.day.date() }, set: { draft.day = LocalDate($0) })
    }

    private var timeBinding: Binding<Date> {
        Binding(get: { draft.day.date(at: time) }, set: { time = TimeOfDay($0) })
    }

    private func save() {
        guard let timing else { return }
        draft.timing = timing
        planner.save(draft)
        if timing != .anytime {
            // Asks the first time; the to-do's notification needs it.
            Task { _ = await ClassReminders.requestAuthorization() }
        }
        dismiss()
    }
}

#Preview {
    let today = LocalDate(.now)
    let courses = SampleCourses.all(today: today)
    let timetable = Timetable(semester: Semester(startingOn: today.adding(days: -28)), courses: courses)
    return TodoEditorView(timetable: timetable, target: .new(day: today))
        .environment(PlannerModel.preview(courses: courses, today: today))
        .environment(AIStore.makeForLaunch())
}
