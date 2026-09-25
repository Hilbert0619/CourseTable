import SwiftUI
import SwiftData

@main
struct CourseTableApp: App {
    private let container: ModelContainer

    init() {
        do {
            container = try Persistence.makeContainer(inMemory: UITestLaunch.isActive)
        } catch {
            fatalError("Could not open the course database: \(error)")
        }
        if UITestLaunch.isActive && UITestLaunch.wantsDemoData {
            Persistence.insertDemoData(into: container.mainContext)
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(container)
    }
}
