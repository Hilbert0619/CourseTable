import SwiftUI
import SwiftData
import CourseKit

struct RootView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @Query(sort: \CourseRecord.createdAt) private var courseRecords: [CourseRecord]
    @Query private var settingsRecords: [SettingsRecord]
    @AppStorage(ReminderSettings.enabledKey) private var remindersEnabled = false
    @AppStorage(ReminderSettings.leadMinutesKey) private var reminderLeadMinutes = ReminderSettings.defaultLeadMinutes

    var body: some View {
        let timetable = Persistence.timetable(settings: settingsRecords.first, courses: courseRecords)
        let reminderRequest = ClassReminders.SyncRequest(
            timetable: timetable, enabled: remindersEnabled, leadMinutes: reminderLeadMinutes, isActive: scenePhase == .active
        )
        TabView {
            TodayView(timetable: timetable)
                .tabItem { Label("Today", systemImage: "sun.max") }
            WeekView(timetable: timetable)
                .tabItem { Label("Timetable", systemImage: "calendar") }
            SettingsView(timetable: timetable)
                .tabItem { Label("Settings", systemImage: "gearshape") }
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
}
