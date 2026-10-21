import SwiftUI
import SwiftData
import UserNotifications
import CourseKit

@main
struct CourseTableApp: App {
    private let container: ModelContainer
    /// The Words tab: its own store (Vocabulary.store) and the vocabulary backend.
    @State private var vocab = VocabApp.makeForLaunch()
    /// To-dos and focus history (Planner.store).
    @State private var planner: PlannerModel
    /// AI connections (keys in the Keychain), usage and saved answers.
    @State private var ai = AIStore.makeForLaunch()

    init() {
        do {
            container = try Persistence.makeContainer(inMemory: UITestLaunch.isActive)
        } catch {
            fatalError("Could not open the course database: \(error)")
        }
        if UITestLaunch.isActive {
            // @AppStorage values survive between UI-test launches: every test starts from the defaults.
            for key in [TimetableAppearance.blockStyleKey, BackgroundPhoto.fadeKey, BackgroundPhoto.blockOpacityKey, AppAppearance.styleKey,
                        AppBackground.key, AppBackground.glassFadeKey] {
                UserDefaults.standard.removeObject(forKey: key)
            }
            if let style = UITestLaunch.appStyle {
                UserDefaults.standard.set(style.rawValue, forKey: AppAppearance.styleKey)
            }
        }
        let planner = PlannerModel.makeForLaunch()
        if UITestLaunch.isActive && UITestLaunch.wantsDemoData {
            Persistence.insertDemoData(into: container.mainContext)
            let courses = (try? container.mainContext.fetch(FetchDescriptor<CourseRecord>()))?.map(\.course) ?? []
            planner.insertDemoData(courses: courses, today: LocalDate(.now))
        }
        _planner = State(initialValue: planner)
        FocusEngine.shared.planner = planner
        UNUserNotificationCenter.current().delegate = NotificationPresenter.shared
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(vocab)
                .environment(planner)
                .environment(ai)
                // UI tests only (`-uiColorScheme`); nil follows the system.
                .preferredColorScheme(UITestLaunch.colorScheme == "dark" ? .dark : UITestLaunch.colorScheme == "light" ? .light : nil)
        }
        .modelContainer(container)
        // Ends class Live Activities whose class is over, whenever iOS grants a refresh after one ends.
        .backgroundTask(.appRefresh(ClassLiveActivities.cleanupTaskID)) {
            await ClassLiveActivities.endFinished()
        }
    }
}

/// Shows the app's notifications (a focus switch, a to-do, a class) while the app is open too.
final class NotificationPresenter: NSObject, UNUserNotificationCenterDelegate, Sendable {
    static let shared = NotificationPresenter()

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}
