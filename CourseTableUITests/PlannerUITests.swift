import XCTest

/// To-dos on the Today tab and the Focus tab, in English and Chinese, plus the focus Live Activity.
/// `-demoData` adds a timetable and today's demo to-dos; `-focusDemo` starts a session that began 7 minutes
/// ago (no sound). Screenshots are kept as attachments, like the other UI tests.
final class PlannerUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testTodosAndFocusSetup() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language)
            // Today: classes with the demo to-dos (one after the first class, one at noon, one any time, one done).
            XCTAssertTrue(app.buttons["addTodo"].waitForExistence(timeout: 10))
            snapshot(app, "\(language)-todo-1-today")

            app.buttons["addTodo"].tap()
            let title = app.textFields["todoTitle"].exists ? app.textFields["todoTitle"] : app.textViews["todoTitle"]
            XCTAssertTrue(title.waitForExistence(timeout: 5))
            title.tap()
            title.typeText(language == "en" ? "Return the lab coat" : "还实验服")
            snapshot(app, "\(language)-todo-2-editor")
            app.buttons["todoSave"].tap()
            XCTAssertTrue(app.buttons["addTodo"].waitForExistence(timeout: 5))
            snapshot(app, "\(language)-todo-3-added")

            // Another day: the arrows move through the week.
            app.buttons.matching(NSPredicate(format: "label IN %@", ["Next Day", "后一天"])).firstMatch.tap()
            Thread.sleep(forTimeInterval: 0.5)
            snapshot(app, "\(language)-todo-4-tomorrow")

            app.tabBars.buttons.element(boundBy: 2).tap()
            XCTAssertTrue(app.buttons["startFocus"].waitForExistence(timeout: 5) || app.textFields["focusGoal"].exists)
            snapshot(app, "\(language)-focus-1-setup")
            app.swipeUp()
            snapshot(app, "\(language)-focus-2-setup-sound")
            app.terminate()
        }
    }

    @MainActor
    func testFocusRunning() throws {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language, extra: ["-focusDemo", "YES"])
            XCTAssertTrue(app.buttons["pauseFocus"].waitForExistence(timeout: 10))
            snapshot(app, "\(language)-focus-3-running")

            Thread.sleep(forTimeInterval: 2)
            XCUIDevice.shared.press(.home)
            Thread.sleep(forTimeInterval: 2)
            attach(XCUIScreen.main.screenshot(), "\(language)-focus-4-island")
            springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.025)).press(forDuration: 1.2)
            Thread.sleep(forTimeInterval: 1.5)
            attach(XCUIScreen.main.screenshot(), "\(language)-focus-5-expanded")
            springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7)).tap()
            Thread.sleep(forTimeInterval: 1)
            let top = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.005))
            top.press(forDuration: 0.1, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.25, dy: 0.6)))
            Thread.sleep(forTimeInterval: 1.5)
            attach(XCUIScreen.main.screenshot(), "\(language)-focus-6-lock-screen")
            XCUIDevice.shared.press(.home)
            Thread.sleep(forTimeInterval: 1)

            // Back in the app: pause shows on the timer.
            app.activate()
            XCTAssertTrue(app.buttons["pauseFocus"].waitForExistence(timeout: 10))
            app.buttons["pauseFocus"].tap()
            Thread.sleep(forTimeInterval: 1)
            snapshot(app, "\(language)-focus-7-paused")
            app.terminate()
        }
    }

    /// The running session's wallpaper (the drawn demo picture in UI tests), then the history it leaves (open all
    /// sessions, swipe one away) and Settings → Storage clearing the done to-do.
    @MainActor
    func testWallpaperHistoryAndStorage() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language, extra: ["-focusDemo", "YES"])
            XCTAssertTrue(app.buttons["anotherWallpaper"].waitForExistence(timeout: 10), "No wallpaper on the countdown")
            snapshot(app, "\(language)-wallpaper-1-running")

            app.buttons["stopFocus"].tap()
            let stop = app.buttons.matching(NSPredicate(format: "label IN %@", ["Stop Session", "结束专注"])).firstMatch
            XCTAssertTrue(stop.waitForExistence(timeout: 5))
            stop.tap()
            XCTAssertTrue(app.buttons["startFocus"].waitForExistence(timeout: 5))
            let allSessions = app.buttons["focusHistoryLink"]
            for _ in 0..<5 where !(allSessions.exists && allSessions.isHittable) { app.swipeUp() }
            snapshot(app, "\(language)-wallpaper-2-history")

            allSessions.tap()
            XCTAssertTrue(app.buttons["clearFocusHistory"].waitForExistence(timeout: 5))
            snapshot(app, "\(language)-wallpaper-3-all-sessions")
            app.cells.firstMatch.swipeLeft()
            let delete = app.buttons.matching(NSPredicate(format: "label IN %@", ["Delete", "删除"])).firstMatch
            XCTAssertTrue(delete.waitForExistence(timeout: 5))
            delete.tap()
            XCTAssertTrue(app.buttons["clearFocusHistory"].waitForNonExistence(timeout: 5), "The session wasn't deleted")
            snapshot(app, "\(language)-wallpaper-4-history-empty")

            app.tabBars.buttons.element(boundBy: 4).tap()
            let storage = app.buttons["storageLink"]
            for _ in 0..<5 where !(storage.exists && storage.isHittable) { app.swipeUp() }
            storage.tap()
            let todos = app.buttons["storage-completedTodos"]
            XCTAssertTrue(todos.waitForExistence(timeout: 5))
            snapshot(app, "\(language)-storage-1")
            todos.tap()
            app.buttons["clearSelectedStorage"].tap()
            let clear = app.buttons.matching(NSPredicate(format: "label IN %@", ["Clear", "清除"])).firstMatch
            XCTAssertTrue(clear.waitForExistence(timeout: 5))
            snapshot(app, "\(language)-storage-2-confirm")
            clear.tap()
            Thread.sleep(forTimeInterval: 0.5)
            XCTAssertFalse(todos.isEnabled, "The done to-do is still counted")
            snapshot(app, "\(language)-storage-3-cleared")
            app.terminate()
        }
    }

    @MainActor
    private func launch(language: String, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(\(language))",
            "-AppleLocale", language == "en" ? "en_US" : "zh_CN",
            "-uiTesting", "YES",
            "-demoData", "YES",
        ] + extra
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "The app didn't show its tab bar")
        return app
    }

    @MainActor
    private func snapshot(_ app: XCUIApplication, _ name: String) {
        attach(app.screenshot(), name)
    }

    @MainActor
    private func attach(_ screenshot: XCUIScreenshot, _ name: String) {
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
