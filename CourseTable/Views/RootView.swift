import SwiftUI
import SwiftData
import CourseKit

struct RootView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \CourseRecord.createdAt) private var courseRecords: [CourseRecord]
    @Query private var settingsRecords: [SettingsRecord]

    var body: some View {
        let timetable = Persistence.timetable(settings: settingsRecords.first, courses: courseRecords)
        TabView {
            TodayView(timetable: timetable)
                .tabItem { Label("Today", systemImage: "sun.max") }
            WeekView(timetable: timetable)
                .tabItem { Label("Timetable", systemImage: "calendar") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
        .onAppear { Persistence.settings(in: context) }
        // Any change to courses or settings refreshes the widget's copy.
        .onChange(of: timetable, initial: true) { _, newValue in
            WidgetSync.publish(newValue)
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
