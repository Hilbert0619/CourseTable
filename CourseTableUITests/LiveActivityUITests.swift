import XCTest

/// Screenshots of the class Live Activity: the app starts a demo one (`-liveActivityDemo before|during|switch`),
/// then the test goes to the Home Screen to see the Dynamic Island (compact and expanded) and pulls down
/// Notification Center, which shows the Lock Screen layout. `switch` starts 45 seconds after launch, to
/// see the countdown turn into the class in progress. Gestures on the system UI are best effort: the
/// screenshots show what happened.
///
/// The activity is drawn by the widget extension in the system's language (English in the Simulator),
/// so only the course details follow `-AppleLanguages` here.
final class LiveActivityUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testClassLiveActivityScreenshots() throws {
        for (language, mode) in [("en", "before"), ("en", "during"), ("zh-Hans", "before")] {
            let app = launch(language: language, mode: mode)
            if mode == "before" {
                showLiveActivitySetting(in: app)
                snapshot(app.screenshot(), "\(language)-live-0-settings")
            }
            // The activity starts with the app; give the system a moment to show it.
            Thread.sleep(forTimeInterval: 3)
            goHome()
            snapshot(XCUIScreen.main.screenshot(), "\(language)-live-1-\(mode)-island")
            expandIsland()
            snapshot(XCUIScreen.main.screenshot(), "\(language)-live-2-\(mode)-expanded")
            collapseIsland()
            openNotificationCenter()
            snapshot(XCUIScreen.main.screenshot(), "\(language)-live-3-\(mode)-lock-screen")
            goHome()
            app.terminate()
        }
    }

    /// The layout switches when the class starts, with no update from the app (the stale date).
    @MainActor
    func testLiveActivitySwitchesAtTheStart() throws {
        let app = launch(language: "en", mode: "switch")
        Thread.sleep(forTimeInterval: 3)
        goHome()
        snapshot(XCUIScreen.main.screenshot(), "en-live-4-switch-before-island")
        // Answer iOS's first-time question about Live Activities, so it doesn't hold anything back.
        openNotificationCenter()
        if springboard.buttons["Allow"].exists {
            springboard.buttons["Allow"].tap()
        }
        goHome()
        // The demo class starts 45 seconds after launch. With the app suspended, iOS redraws a stale
        // activity up to about two minutes late, so look well after that.
        Thread.sleep(forTimeInterval: 160)
        snapshot(XCUIScreen.main.screenshot(), "en-live-5-switch-after-island")
        expandIsland()
        snapshot(XCUIScreen.main.screenshot(), "en-live-6-switch-after-expanded")
        collapseIsland()
        openNotificationCenter()
        snapshot(XCUIScreen.main.screenshot(), "en-live-7-switch-after-lock-screen")
        goHome()
        app.terminate()
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
        // If the demo activity couldn't start, the app shows why: keep it with the results.
        let error = app.staticTexts["liveActivityDemoError"]
        if error.waitForExistence(timeout: 2) {
            let attachment = XCTAttachment(string: error.label)
            attachment.name = "\(language)-live-\(mode)-start-error"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        return app
    }

    /// Settings, scrolled until the Live Activity switch and the footer below it clear the tab bar.
    @MainActor
    private func showLiveActivitySetting(in app: XCUIApplication) {
        app.tabBars.buttons.element(boundBy: 3).tap()
        let toggle = app.descendants(matching: .any)["liveActivityToggle"].firstMatch
        let tabBarTop = app.tabBars.firstMatch.frame.minY
        for _ in 0..<6 {
            if toggle.exists && toggle.frame.maxY < tabBarTop - 220 { break }
            app.swipeUp(velocity: .slow)
        }
        Thread.sleep(forTimeInterval: 0.8)
    }

    @MainActor
    private var springboard: XCUIApplication {
        XCUIApplication(bundleIdentifier: "com.apple.springboard")
    }

    @MainActor
    private func goHome() {
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 2)
    }

    /// A long press on the island expands it.
    @MainActor
    private func expandIsland() {
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.025)).press(forDuration: 1.2)
        Thread.sleep(forTimeInterval: 1.5)
    }

    @MainActor
    private func collapseIsland() {
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7)).tap()
        Thread.sleep(forTimeInterval: 1)
    }

    /// Notification Center lists Live Activities in their Lock Screen layout.
    @MainActor
    private func openNotificationCenter() {
        let top = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.005))
        top.press(forDuration: 0.1, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.6)))
        Thread.sleep(forTimeInterval: 1.5)
    }

    @MainActor
    private func snapshot(_ screenshot: XCUIScreenshot, _ name: String) {
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
