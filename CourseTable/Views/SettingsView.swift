import SwiftUI
import SwiftData
import CourseKit

struct SettingsView: View {
    @Query private var settingsRecords: [SettingsRecord]

    var body: some View {
        NavigationStack {
            Group {
                if let settings = settingsRecords.first {
                    SettingsForm(settings: settings)
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Settings")
        }
    }
}

private struct SettingsForm: View {
    @Bindable var settings: SettingsRecord

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "–"
    }

    var body: some View {
        Form {
            Section {
                DatePicker("First day of term", selection: $settings.semester.startDate.dateValue, displayedComponents: .date)
                Stepper(value: $settings.totalWeeks, in: 1...Semester.maxWeeks) {
                    LabeledContent("Weeks in term") {
                        Text(verbatim: "\(settings.totalWeeks)")
                    }
                }
                LabeledContent("Today") {
                    Text(verbatim: settings.semester.status(on: LocalDate(.now)).summary)
                }
            } header: {
                Text("Term")
            } footer: {
                Text("Week 1 is the Monday–Sunday week that contains the first day of term.")
            }

            Section("Class periods") {
                NavigationLink {
                    PeriodScheduleEditor(settings: settings)
                } label: {
                    LabeledContent("Period times") {
                        Text(verbatim: "\(settings.periods.count)")
                    }
                }
            }

            Section("About") {
                LabeledContent("Version") {
                    Text(verbatim: appVersion)
                }
            }
        }
    }
}

struct PeriodScheduleEditor: View {
    let settings: SettingsRecord
    @State private var schedule: PeriodSchedule
    @State private var confirmingReset = false

    init(settings: SettingsRecord) {
        self.settings = settings
        _schedule = State(initialValue: settings.periods)
    }

    var body: some View {
        List {
            let issues = schedule.issues
            if !issues.isEmpty {
                Section {
                    ForEach(issues, id: \.self) { issue in
                        Label(issue.message, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(Color.orange)
                    }
                }
            }

            Section {
                ForEach(schedule.periods.indices, id: \.self) { index in
                    PeriodRow(number: index + 1, period: $schedule[safe: index])
                }
                .onDelete { schedule.periods.remove(atOffsets: $0) }
            } footer: {
                Text("Swipe left on a period to delete it.")
            }

            Section {
                Button("Add Period") { schedule = schedule.appendingPeriod() }
                    .disabled(schedule.count >= PeriodSchedule.maxPeriods)
                Button("Reset to Default", role: .destructive) { confirmingReset = true }
            }
        }
        .navigationTitle("Period Times")
        .onChange(of: schedule) { _, newValue in
            settings.periods = newValue
        }
        .confirmationDialog("Reset all period times to the default 12-period day?", isPresented: $confirmingReset, titleVisibility: .visible) {
            Button("Reset", role: .destructive) { schedule = .standard }
        }
    }
}

private struct PeriodRow: View {
    let number: Int
    @Binding var period: ClassPeriod

    var body: some View {
        HStack {
            Text("Period \(number)")
            Spacer()
            DatePicker("Start", selection: $period.start.dateValue, displayedComponents: .hourAndMinute)
                .labelsHidden()
            Text(verbatim: "–")
            DatePicker("End", selection: $period.end.dateValue, displayedComponents: .hourAndMinute)
                .labelsHidden()
        }
    }
}

extension PeriodScheduleIssue {
    var message: String {
        switch self {
        case .empty:
            String(localized: "Add at least one period.")
        case .endsBeforeStart(let period):
            String(localized: "Period \(period) ends before it starts.")
        case .overlapsPrevious(let period):
            String(localized: "Period \(period) starts before the previous one ends.")
        }
    }
}

// Settable `Date` views of CourseKit values, so DatePicker can bind to them through key paths.

extension LocalDate {
    var dateValue: Date {
        get { date() }
        set { self = LocalDate(newValue) }
    }
}

extension TimeOfDay {
    var dateValue: Date {
        get { LocalDate(.now).date(at: self) }
        set { self = TimeOfDay(newValue) }
    }
}

extension PeriodSchedule {
    /// Bounds-checked element access: a row's binding can outlive the period it pointed at after a delete.
    subscript(safe index: Int) -> ClassPeriod {
        get { periods.indices.contains(index) ? periods[index] : ClassPeriod(start: .midnight, end: .midnight) }
        set { if periods.indices.contains(index) { periods[index] = newValue } }
    }
}

#Preview {
    SettingsView()
        .modelContainer(Persistence.previewContainer())
}
