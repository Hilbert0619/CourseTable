import SwiftUI
import SwiftData
import CourseKit
import VocabKit

struct RootView: View {
    enum Tab: Hashable {
        case today
        case timetable
        case words
        case settings
    }

    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @Environment(VocabApp.self) private var vocab
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
        let reminderRequest = ClassReminders.SyncRequest(
            timetable: timetable, enabled: remindersEnabled, leadMinutes: reminderLeadMinutes, isActive: scenePhase == .active
        )
        let activityRequest = ClassLiveActivities.SyncRequest(
            timetable: timetable, enabled: liveActivitiesEnabled, leadMinutes: reminderLeadMinutes, isActive: scenePhase == .active
        )
        TabView(selection: $tab) {
            TodayView(timetable: timetable)
                .tabItem { Label("Today", systemImage: "sun.max") }
                .tag(Tab.today)
            WeekView(timetable: timetable)
                .tabItem { Label("Timetable", systemImage: "calendar") }
                .tag(Tab.timetable)
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
        // Reschedule class reminders when the timetable or reminder settings change, and each time
        // the app comes to the foreground, which moves the window of scheduled reminders forward.
        .task(id: reminderRequest) {
            await ClassReminders.sync(reminderRequest)
        }
        // The same for the class Live Activities: end finished ones, start or schedule the next.
        .task(id: activityRequest) {
            await ClassLiveActivities.sync(activityRequest)
        }
        .task {
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
}
