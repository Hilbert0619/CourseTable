import SwiftUI
import SwiftData
import CourseKit
import VocabKit

struct RootView: View {
    enum Tab: Hashable {
        case today
        case timetable
        case focus
        case words
        case settings
    }

    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @Environment(VocabApp.self) private var vocab
    @Environment(PlannerModel.self) private var planner
    @State private var tab = Tab.today
    @Query(sort: \CourseRecord.createdAt) private var courseRecords: [CourseRecord]
    @Query private var settingsRecords: [SettingsRecord]
    @AppStorage(ReminderSettings.enabledKey) private var remindersEnabled = false
    @AppStorage(ReminderSettings.leadMinutesKey) private var reminderLeadMinutes = ReminderSettings.defaultLeadMinutes
    @AppStorage(LiveActivitySettings.enabledKey) private var liveActivitiesEnabled = LiveActivitySettings.defaultEnabled
    /// UI tests only: why the demo Live Activity couldn't start.
    @State private var liveActivityDemoError: String?

    var body: some View {
        let timetable = Persistence.timetable(settings: settingsRecords.first, courses: courseRecords)
        let alertRequest = Alerts.SyncRequest(
            timetable: timetable, remindersEnabled: remindersEnabled, liveActivitiesEnabled: liveActivitiesEnabled,
            leadMinutes: reminderLeadMinutes, todos: planner.todos, isActive: scenePhase == .active
        )
        TabView(selection: $tab) {
            TodayView(timetable: timetable)
                .tabItem { Label("Today", systemImage: "sun.max") }
                .tag(Tab.today)
            WeekView(timetable: timetable)
                .tabItem { Label("Timetable", systemImage: "calendar") }
                .tag(Tab.timetable)
            FocusView()
                .tabItem { Label("Focus", systemImage: "timer") }
                .tag(Tab.focus)
            WordsTab()
                .tabItem { Label("Words", systemImage: "character.book.closed") }
                .tag(Tab.words)
            SettingsView(timetable: timetable)
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
        // Sign-in email links (io.github.hilbert0619.coursetable://auth-callback#…) open the Words tab,
        // where the result (signed in, a new password to choose, or what went wrong) is shown.
        .onOpenURL { url in
            guard AuthLink(url) != .notAuthLink else { return }
            tab = .words
            Task { await vocab.open(url) }
        }
        .onAppear { Persistence.settings(in: context) }
        // Any change to courses or settings refreshes the widget's copy.
        .onChange(of: timetable, initial: true) { _, newValue in
            WidgetSync.publish(newValue)
        }
        // Class Live Activities, class reminders and to-do notifications, whenever the timetable, the
        // to-dos or the settings change, and each time the app comes to the foreground (which moves the
        // window of scheduled alerts forward).
        .task(id: alertRequest) {
            await Alerts.sync(alertRequest)
        }
        // A focus session may have switched phase or ended while the app wasn't running.
        .onChange(of: scenePhase, initial: true) { _, phase in
            if phase == .active { FocusEngine.shared.refresh() }
        }
        .task {
            if UITestLaunch.wantsFocusDemo {
                tab = .focus
                FocusEngine.shared.startDemo()
            }
            if let mode = UITestLaunch.liveActivityDemo {
                liveActivityDemoError = await ClassLiveActivities.startDemo(mode)
            }
        }
        .overlay(alignment: .center) {
            if let liveActivityDemoError {
                Text(verbatim: liveActivityDemoError)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .padding()
                    .background(.background)
                    .accessibilityIdentifier("liveActivityDemoError")
            }
        }
    }
}

/// What the course editor sheet is showing.
enum EditorTarget: Identifiable {
    case new(weekday: Weekday, period: Int)
    case edit(Course)

    var id: String {
        switch self {
        case .new(let weekday, let period): "new-\(weekday.rawValue)-\(period)"
        case .edit(let course): course.id.uuidString
        }
    }
}

#Preview {
    RootView()
        .modelContainer(Persistence.previewContainer())
        .environment(VocabApp.preview())
        .environment(PlannerModel.preview())
}
