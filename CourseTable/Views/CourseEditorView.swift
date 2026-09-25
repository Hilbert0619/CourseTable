import SwiftUI
import SwiftData
import CourseKit

struct CourseEditorView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let timetable: Timetable
    let isNew: Bool
    @State private var draft: Course
    @State private var confirmingDelete = false

    init(timetable: Timetable, target: EditorTarget) {
        self.timetable = timetable
        switch target {
        case .new(let weekday, let period):
            isNew = true
            let periodsLeft = timetable.periods.count - period + 1
            _draft = State(initialValue: Course(
                name: "",
                weekday: weekday,
                startPeriod: period,
                periodCount: min(2, max(periodsLeft, 1)),
                weeks: CourseWeeks(first: 1, last: timetable.semester.totalWeeks),
                colorIndex: Course.suggestedColorIndex(avoiding: timetable.courses)
            ))
        case .edit(let course):
            isNew = false
            _draft = State(initialValue: course)
        }
    }

    private var periodsPerDay: Int { max(timetable.periods.count, 1) }
    private var issues: [CourseIssue] { timetable.issues(for: draft) }
    private var clashes: [Course] { timetable.clashes(for: draft) }
    private var canSave: Bool { !issues.contains(where: \.isBlocking) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Course name", text: $draft.name)
                    TextField("Teacher", text: $draft.teacher)
                    TextField("Room", text: $draft.room)
                }

                Section("Time") {
                    Picker("Day", selection: $draft.weekday) {
                        ForEach(Weekday.allCases) { day in
                            Text(verbatim: day.fullName).tag(day)
                        }
                    }
                    Picker("Starts at", selection: $draft.startPeriod) {
                        ForEach(1...periodsPerDay, id: \.self) { number in
                            Text("Period \(number)").tag(number)
                        }
                    }
                    Stepper(value: $draft.periodCount, in: 1...periodsPerDay) {
                        Text("Length: \(draft.periodCount) periods")
                    }
                    if let span = timetable.periods.timeSpan(of: draft.periods) {
                        LabeledContent("Class time", value: "\(span.start)–\(span.end)")
                    }
                }

                Section("Weeks") {
                    Stepper(value: $draft.weeks.first, in: 1...Semester.maxWeeks) {
                        Text("From week \(draft.weeks.first)")
                    }
                    Stepper(value: $draft.weeks.last, in: 1...Semester.maxWeeks) {
                        Text("To week \(draft.weeks.last)")
                    }
                    Picker("Repeats", selection: $draft.weeks.parity) {
                        ForEach(WeekParity.allCases, id: \.self) { parity in
                            Text(verbatim: parity.displayName).tag(parity)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("Color") {
                    ColorPickerRow(selection: $draft.colorIndex)
                }

                if !issues.isEmpty || !clashes.isEmpty {
                    Section {
                        ForEach(issues, id: \.self) { issue in
                            Label(issue.message, systemImage: issue.isBlocking ? "exclamationmark.circle.fill" : "exclamationmark.triangle.fill")
                                .foregroundStyle(issue.isBlocking ? Color.red : Color.orange)
                        }
                        ForEach(clashes) { other in
                            Label(other.clashMessage, systemImage: "exclamationmark.triangle.fill")
                                .foregroundStyle(.orange)
                        }
                    }
                }

                if !isNew {
                    Section {
                        Button("Delete Course", role: .destructive) { confirmingDelete = true }
                    }
                }
            }
            .navigationTitle(isNew ? Text("New Course") : Text("Edit Course"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }.disabled(!canSave)
                }
            }
            .confirmationDialog("Delete this course?", isPresented: $confirmingDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive) { delete() }
            }
            .onChange(of: draft.startPeriod) { _, start in
                draft.periodCount = min(draft.periodCount, max(periodsPerDay - start + 1, 1))
            }
        }
    }

    private func save() {
        var course = draft
        course.name = course.name.trimmingCharacters(in: .whitespacesAndNewlines)
        course.teacher = course.teacher.trimmingCharacters(in: .whitespacesAndNewlines)
        course.room = course.room.trimmingCharacters(in: .whitespacesAndNewlines)
        if isNew {
            context.insert(CourseRecord(course))
        } else {
            record(for: course.id)?.update(from: course)
        }
        try? context.save()
        dismiss()
    }

    private func delete() {
        if let record = record(for: draft.id) {
            context.delete(record)
            try? context.save()
        }
        dismiss()
    }

    private func record(for id: UUID) -> CourseRecord? {
        var descriptor = FetchDescriptor<CourseRecord>(predicate: #Predicate<CourseRecord> { record in record.id == id })
        descriptor.fetchLimit = 1
        return try? context.fetch(descriptor).first
    }
}

private struct ColorPickerRow: View {
    @Binding var selection: Int

    var body: some View {
        HStack(spacing: 0) {
            ForEach(CoursePalette.colors.indices, id: \.self) { index in
                Button {
                    selection = index
                } label: {
                    Circle()
                        .fill(CoursePalette.colors[index])
                        .frame(width: 26, height: 26)
                        .overlay {
                            if index == selection {
                                Image(systemName: "checkmark")
                                    .font(.caption.bold())
                                    .foregroundStyle(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Color \(index + 1)"))
                .accessibilityAddTraits(index == selection ? .isSelected : [])
            }
        }
        .padding(.vertical, 4)
    }
}

extension CourseIssue {
    var message: String {
        switch self {
        case .missingName:
            String(localized: "Enter a course name.")
        case .periodsOutOfRange(let periodsPerDay):
            String(localized: "The class must fit within periods 1–\(periodsPerDay).")
        case .invalidWeekRange:
            String(localized: "The last week can't be before the first week.")
        case .noMatchingWeeks:
            String(localized: "No week in this range matches the odd/even setting.")
        case .weeksBeyondSemester(let totalWeeks):
            String(localized: "Runs past the end of term (week \(totalWeeks)).")
        }
    }
}

#Preview {
    let today = LocalDate(.now)
    let timetable = Timetable(semester: Semester(startingOn: today), courses: SampleCourses.all(today: today))
    return CourseEditorView(timetable: timetable, target: .new(weekday: today.weekday, period: 3))
        .modelContainer(Persistence.previewContainer())
}
