import SwiftUI
import SwiftData
import CoreTransferable
import UniformTypeIdentifiers
import CourseKit

/// Paste CSV or spreadsheet rows (or open a file), preview the courses, then add them.
struct ImportView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let timetable: Timetable
    @State private var text = ""
    @State private var replacesExisting = false
    @State private var choosingFile = false
    @State private var confirmingReplace = false
    @State private var fileError: String?

    var body: some View {
        let result = CourseImporter(for: timetable).parse(text)
        let plan = timetable.planningImport(of: result.courses, replacingExisting: replacesExisting)
        NavigationStack {
            Form {
                Section {
                    TextEditor(text: $text)
                        .font(.system(.footnote, design: .monospaced))
                        .frame(minHeight: 140)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    Button("Choose File…") { choosingFile = true }
                    Button("Insert Example") { text = example }
                } header: {
                    Text("Paste CSV or spreadsheet rows")
                } footer: {
                    Text("One course per line with the columns name, teacher, room, weekday, periods and weeks. Add a header row (English or Chinese) to use any column order. Rows copied from Excel or Numbers work too.")
                }

                if let fileError {
                    Section {
                        Label(fileError, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                    }
                }

                Section {
                    Toggle("Replace existing courses", isOn: $replacesExisting)
                } footer: {
                    Text("Deletes the courses already in your timetable before importing.")
                }

                if !text.allSatisfy(\.isWhitespace) {
                    ImportPreview(result: result, plan: plan)
                }
            }
            .navigationTitle("Import Courses")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .accessibilityIdentifier("cancel")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Import") {
                        if plan.replacesExisting && !timetable.courses.isEmpty {
                            confirmingReplace = true
                        } else {
                            apply(plan)
                        }
                    }
                    .disabled(plan.added.isEmpty)
                }
            }
            .confirmationDialog("Replace all current courses?", isPresented: $confirmingReplace, titleVisibility: .visible) {
                Button("Replace", role: .destructive) { apply(plan) }
            } message: {
                Text("This deletes the \(timetable.courses.count) courses in your timetable.")
            }
            .fileImporter(
                isPresented: $choosingFile,
                allowedContentTypes: [.commaSeparatedText, .tabSeparatedText, .plainText, .text]
            ) { outcome in
                load(outcome)
            }
        }
    }

    private var example: String {
        Bundle.main.preferredLocalizations.first?.hasPrefix("zh") == true ? CourseCSV.chineseExample : CourseCSV.englishExample
    }

    private func load(_ outcome: Result<URL, Error>) {
        fileError = nil
        do {
            let url = try outcome.get()
            let accessing = url.startAccessingSecurityScopedResource()
            defer { if accessing { url.stopAccessingSecurityScopedResource() } }
            if let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size > ImportedText.maxBytes {
                fileError = String(localized: "That file is too large to be a timetable.")
                return
            }
            let data = try Data(contentsOf: url)
            guard let decoded = ImportedText.decode(data) else {
                fileError = String(localized: "That file isn't readable text.")
                return
            }
            text = decoded
        } catch {
            fileError = error.localizedDescription
        }
    }

    private func apply(_ plan: ImportPlan) {
        if plan.replacesExisting {
            for record in (try? context.fetch(FetchDescriptor<CourseRecord>())) ?? [] {
                context.delete(record)
            }
        }
        for course in plan.added {
            context.insert(CourseRecord(course))
        }
        try? context.save()
        dismiss()
    }
}

private struct ImportPreview: View {
    let result: CourseImportResult
    let plan: ImportPlan

    var body: some View {
        Section {
            if plan.added.isEmpty {
                Text("No courses found yet.")
                    .foregroundStyle(.secondary)
            }
            ForEach(plan.added) { course in
                ImportedCourseRow(
                    course: course,
                    issues: plan.timetable.issues(for: course),
                    clashes: plan.timetable.clashes(for: course)
                )
            }
        } header: {
            Text("\(plan.added.count) courses to add")
        } footer: {
            if !plan.duplicates.isEmpty {
                Text("\(plan.duplicates.count) already in your timetable and will be skipped.")
            }
        }

        if !result.problems.isEmpty {
            Section("Lines that can't be imported") {
                ForEach(Array(result.problems.enumerated()), id: \.offset) { _, problem in
                    Label(problem.message, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                }
            }
        }
    }
}

private struct ImportedCourseRow: View {
    let course: Course
    let issues: [CourseIssue]
    let clashes: [Course]

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            RoundedRectangle(cornerRadius: 3)
                .fill(course.color)
                .frame(width: 6)
            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: course.name)
                    .font(.headline)
                Text(verbatim: "\(course.weekday.fullName) · \(course.periodsLabel) · \(course.weeks.summary)")
                    .font(.subheadline)
                let details = [course.room, course.teacher].filter { !$0.isEmpty }.joined(separator: " · ")
                if !details.isEmpty {
                    Text(verbatim: details)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                ForEach(issues, id: \.self) { issue in
                    Label(issue.message, systemImage: "exclamationmark.triangle.fill")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
                ForEach(clashes) { other in
                    Label(other.clashMessage, systemImage: "exclamationmark.triangle.fill")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
            }
        }
        .padding(.vertical, 2)
    }
}

/// The courses as a CSV file for the share sheet.
struct CourseListFile: Transferable {
    static let fileName = "CourseTable.csv"

    let courses: [Course]

    static var transferRepresentation: some TransferRepresentation {
        DataRepresentation(exportedContentType: .commaSeparatedText) { file in
            CourseCSV.exportData(file.courses)
        }
        .suggestedFileName(fileName)
    }
}

extension ImportProblem {
    var message: String {
        let detail = switch kind {
        case .missingColumns(let columns):
            String(localized: "The header row is missing \(columns.map(\.displayName).formatted(.list(type: .and))).")
        case .missingValue(let column):
            String(localized: "Missing \(column.displayName).")
        case .invalidValue(let column, let text):
            String(localized: "Can't read the \(column.displayName) “\(text)”.")
        case .periodsOutOfRange(let text, let periodsPerDay):
            String(localized: "Periods “\(text)” are outside periods 1–\(periodsPerDay).")
        }
        return String(localized: "Line \(line): \(detail)")
    }
}

extension ImportColumn {
    var displayName: String {
        switch self {
        case .name: String(localized: "course name")
        case .teacher: String(localized: "teacher")
        case .room: String(localized: "room")
        case .weekday: String(localized: "weekday")
        case .periods: String(localized: "periods")
        case .weeks: String(localized: "weeks")
        }
    }
}

#Preview {
    let today = LocalDate(.now)
    let timetable = Timetable(semester: Semester(startingOn: today), courses: SampleCourses.all(today: today))
    return ImportView(timetable: timetable)
        .modelContainer(Persistence.previewContainer())
}
