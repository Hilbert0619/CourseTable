import XCTest

/// Screenshots of the class Live Activity: the app starts a demo one (`-liveActivityDemo before|during`),
/// then the test goes to the Home Screen to see the Dynamic Island (compact and expanded) and pulls down
/// Notification Center, which shows the Lock Screen layout. Gestures on the system UI are best effort:
/// the screenshots show what happened.
final class LiveActivityUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testClassLiveActivityScreenshots() throws {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for (language, mode) in [("en", "before"), ("en", "during"), ("zh-Hans", "during")] {
            let app = launch(language: language, mode: mode)
            if mode == "before" || language != "en" {
                app.tabBars.buttons.element(boundBy: 3).tap()
                let toggle = app.descendants(matching: .any)["liveActivityToggle"].firstMatch
                for _ in 0..<4 where !(toggle.exists && toggle.isHittable) { app.swipeUp() }
                Thread.sleep(forTimeInterval: 0.8)
                snapshot(app.screenshot(), "\(language)-live-0-settings")
            }
            // The activity starts with the app; give the system a moment to show it.
            Thread.sleep(forTimeInterval: 3)
            XCUIDevice.shared.press(.home)
            Thread.sleep(forTimeInterval: 2)
            snapshot(XCUIScreen.main.screenshot(), "\(language)-live-1-\(mode)-island")

            // A long press on the island expands it.
            springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.025)).press(forDuration: 1.2)
            Thread.sleep(forTimeInterval: 1.5)
            snapshot(XCUIScreen.main.screenshot(), "\(language)-live-2-\(mode)-expanded")
            springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7)).tap()
            Thread.sleep(forTimeInterval: 1)

            // Notification Center lists Live Activities in their Lock Screen layout.
            let top = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.005))
            top.press(forDuration: 0.1, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.6)))
            Thread.sleep(forTimeInterval: 1.5)
            snapshot(XCUIScreen.main.screenshot(), "\(language)-live-3-\(mode)-lock-screen")
            XCUIDevice.shared.press(.home)
            Thread.sleep(forTimeInterval: 1)
            app.terminate()
        }
    }

    @MainActor
    private func launch(language: String, mode: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(\(language))",
            "-AppleLocale", language == "en" ? "en_US" : "zh_CN",
            "-uiTesting", "YES",
            "-demoData", "YES",
            "-liveActivityDemo", mode,
        ]
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "The app didn't show its tab bar")
        return app
    }

    @MainActor
    private func snapshot(_ screenshot: XCUIScreenshot, _ name: String) {
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
