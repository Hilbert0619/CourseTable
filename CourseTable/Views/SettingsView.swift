import SwiftUI
import SwiftData
import UIKit
import ActivityKit
import CourseKit

struct SettingsView: View {
    /// The current timetable, for importing and exporting courses.
    let timetable: Timetable
    @Query private var settingsRecords: [SettingsRecord]

    var body: some View {
        NavigationStack {
            Group {
                if let settings = settingsRecords.first {
                    SettingsForm(settings: settings, timetable: timetable)
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
    let timetable: Timetable
    @State private var importing = false

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

            Section("Courses") {
                Button {
                    importing = true
                } label: {
                    Label("Import Courses…", systemImage: "square.and.arrow.down")
                }
                ShareLink(
                    item: CourseListFile(courses: timetable.courses),
                    preview: SharePreview(CourseListFile.fileName)
                ) {
                    Label("Export as CSV", systemImage: "square.and.arrow.up")
                }
                .disabled(timetable.courses.isEmpty)
            }

            ReminderSection()

            AISettingsSection()

            Section {
                NavigationLink {
                    StorageView()
                } label: {
                    Label("Storage", systemImage: "internaldrive")
                }
                .accessibilityIdentifier("storageLink")
            } footer: {
                Text("Clear focus history, completed to-dos, AI chats and downloaded files.")
            }

            Section {
                LabeledContent("Version") {
                    Text(verbatim: appVersion)
                }
                LabeledContent("Widget") {
                    SnapshotStore.defaultURL == nil ? Text("Unavailable") : Text("Connected")
                }
            } header: {
                Text("About")
            } footer: {
                if SnapshotStore.defaultURL == nil {
                    Text("The widget can't read your timetable because this copy of the app has no App Group. Install it with Xcode, TestFlight, iloader, SideStore or AltStore to use the widget.")
                }
            }
        }
        .sheet(isPresented: $importing) {
            ImportView(timetable: timetable)
        }
    }
}

/// Settings → AI, with the connection in use.
private struct AISettingsSection: View {
    @Environment(AIStore.self) private var ai

    var body: some View {
        Section {
            NavigationLink {
                AISettingsView()
            } label: {
                LabeledContent {
                    if let connection = ai.defaultConnection {
                        Text(verbatim: connection.name)
                    } else {
                        Text("Off")
                    }
                } label: {
                    Label("AI Help", systemImage: "sparkles")
                }
            }
            .accessibilityIdentifier("aiSettingsLink")
        } footer: {
            Text("Word explanations and more from an AI provider you choose, with your own key.")
        }
    }
}

private struct ReminderSection: View {
    @AppStorage(ReminderSettings.enabledKey) private var enabled = false
    @AppStorage(ReminderSettings.leadMinutesKey) private var leadMinutes = ReminderSettings.defaultLeadMinutes
    @AppStorage(LiveActivitySettings.enabledKey) private var liveActivities = LiveActivitySettings.defaultEnabled
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @State private var notificationsDenied = false
    @State private var activitiesDisabled = false

    var body: some View {
        Section {
            Toggle("Class reminders", isOn: $enabled)
            Toggle("Live Activity", isOn: $liveActivities)
                .accessibilityIdentifier("liveActivityToggle")
            if enabled || liveActivities {
                Picker("Remind me", selection: $leadMinutes) {
                    ForEach(ReminderSettings.leadChoices, id: \.self) { minutes in
                        Text(verbatim: ReminderSettings.label(forLeadMinutes: minutes)).tag(minutes)
                    }
                }
            }
            if notificationsDenied {
                Label("Notifications for Course Table are turned off.", systemImage: "bell.slash")
                    .foregroundStyle(.orange)
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                        openURL(url)
                    }
                }
            }
            if liveActivities && activitiesDisabled {
                Label("Live Activities for Course Table are turned off in Settings.", systemImage: "rectangle.slash")
                    .foregroundStyle(.orange)
            }
        } header: {
            Text("Reminders")
        } footer: {
            VStack(alignment: .leading, spacing: 6) {
                Text("Class reminders notify you of classes that have no Live Activity, up to your next \(Timetable.maxScheduledReminders) classes, and are renewed whenever you open the app.")
                Text("Live Activity shows each class in the Dynamic Island and on the Lock Screen from the reminder time until it ends. Close it there if you don't need it.")
                if !ClassLiveActivities.canSchedule {
                    Text("On this version of iOS, a Live Activity can only start while Course Table is open, for example when you tap the class reminder.")
                }
            }
        }
        .onChange(of: enabled) { _, isOn in
            guard isOn else { return }
            Task {
                // Prompts the first time; afterwards returns the choice made in Settings.
                let granted = await ClassReminders.requestAuthorization()
                if !granted {
                    enabled = false
                }
                notificationsDenied = await ClassReminders.isDenied()
            }
        }
        // Check again when coming back from the Settings app.
        .task(id: scenePhase) {
            notificationsDenied = await ClassReminders.isDenied()
            activitiesDisabled = !ActivityAuthorizationInfo().areActivitiesEnabled
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
    let today = LocalDate(.now)
    let timetable = Timetable(semester: Semester(startingOn: today), courses: SampleCourses.all(today: today))
    return SettingsView(timetable: timetable)
        .modelContainer(Persistence.previewContainer())
}
