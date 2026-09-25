import SwiftUI
import SwiftData
import CourseKit

/// Type courses as "name room teacher" lines to add them to the course list, and manage the list.
struct CourseListView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \CourseTemplateRecord.createdAt) private var records: [CourseTemplateRecord]

    let timetable: Timetable
    /// Called with the courses just added, before the sheet closes.
    let onAdd: ([CourseTemplate]) -> Void
    @State private var text = ""

    var body: some View {
        let saved = records.map(\.template)
        let typed = CourseTemplate.parseList(text).filter { new in !saved.contains { $0.hasSameDetails(as: new) } }
        let additions = timetable.assigningColors(to: typed, saved: saved)
        NavigationStack {
            Form {
                Section {
                    TextEditor(text: $text)
                        .frame(minHeight: 140)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                } header: {
                    Text("One course per line")
                } footer: {
                    Text("Name, room and teacher, separated by spaces, commas or tabs, e.g. “高等数学 A101 李老师”. Room and teacher are optional. Use commas if a name contains spaces.")
                }

                if !additions.isEmpty {
                    Section {
                        ForEach(additions) { template in
                            TemplateRow(template: template)
                        }
                    } header: {
                        Text("\(additions.count) new courses")
                    }
                }

                if !saved.isEmpty {
                    Section {
                        ForEach(saved) { template in
                            TemplateRow(template: template)
                        }
                        .onDelete { offsets in
                            remove(Set(offsets.map { saved[$0].id }))
                        }
                    } header: {
                        Text("Your course list")
                    } footer: {
                        Text("Swipe left to remove a course from the list. Courses already placed on the timetable stay.")
                    }
                }
            }
            .navigationTitle("Course List")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .accessibilityIdentifier("cancel")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") { add(additions) }
                        .disabled(additions.isEmpty)
                }
            }
        }
    }

    private func add(_ additions: [CourseTemplate]) {
        let now = Date.now
        for (index, template) in additions.enumerated() {
            // Distinct timestamps keep the typed order in the list.
            context.insert(CourseTemplateRecord(template, createdAt: now.addingTimeInterval(Double(index) / 1000)))
        }
        try? context.save()
        onAdd(additions)
        dismiss()
    }

    private func remove(_ ids: Set<UUID>) {
        for record in records where ids.contains(record.id) {
            context.delete(record)
        }
        try? context.save()
    }
}

private struct TemplateRow: View {
    let template: CourseTemplate

    var body: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 3)
                .fill(template.color)
                .frame(width: 6, height: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: template.name)
                    .font(.headline)
                let details = [template.room, template.teacher].filter { !$0.isEmpty }.joined(separator: " · ")
                if !details.isEmpty {
                    Text(verbatim: details)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

#Preview {
    let today = LocalDate(.now)
    let timetable = Timetable(semester: Semester(startingOn: today), courses: SampleCourses.all(today: today))
    return CourseListView(timetable: timetable) { _ in }
        .modelContainer(Persistence.previewContainer())
}
