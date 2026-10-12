import XCTest

/// AI help with canned answers (`-aiDemo`: one DeepSeek connection answered by the app itself; nothing touches the
/// network): word help, "Why?" after a wrong spelling, and Settings → AI with a connection test.
final class AIUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testWordHelpAndSettings() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language)
            app.tabBars.buttons.element(boundBy: 3).tap()
            tap(element(app, "searchLink"))
            let search = app.searchFields.firstMatch
            tap(search)
            search.typeText("abandon")
            tap(app.cells.firstMatch)
            XCTAssertTrue(element(app, "favoriteButton").waitForExistence(timeout: 10), "The word page didn't open")

            let explain = element(app, "aiHelp-explain")
            for _ in 0..<4 where !(explain.exists && explain.isHittable) { app.swipeUp() }
            tap(explain)
            XCTAssertTrue(element(app, "aiAskAgain").waitForExistence(timeout: 15), "The answer didn't finish")
            XCTAssertTrue(element(app, "aiAnswer").exists)
            snapshot(app, "\(language)-ai-1-word-help")
            app.swipeUp()
            snapshot(app, "\(language)-ai-1-word-help-bottom")

            app.tabBars.buttons.element(boundBy: 4).tap()
            let link = element(app, "aiSettingsLink")
            for _ in 0..<6 where !(link.exists && link.isHittable) { app.swipeUp() }
            tap(link)
            XCTAssertTrue(element(app, "aiAddConnection").waitForExistence(timeout: 10), "Settings → AI didn't open")
            snapshot(app, "\(language)-ai-2-settings")

            tap(app.cells.firstMatch)
            XCTAssertTrue(element(app, "aiSave").waitForExistence(timeout: 10), "The connection didn't open")
            let test = element(app, "aiTest")
            for _ in 0..<4 where !(test.exists && test.isHittable) { app.swipeUp() }
            tap(test)
            XCTAssertTrue(element(app, "aiTestOK").waitForExistence(timeout: 15), "The connection test didn't pass")
            snapshot(app, "\(language)-ai-3-connection")
            app.terminate()
        }
    }

    /// A wrong spelling offers "Why?", which explains the mistake in a sheet.
    @MainActor
    func testWhyAfterAWrongAnswer() throws {
        let app = launch(language: "en", reviewMode: "spelling")
        app.tabBars.buttons.element(boundBy: 3).tap()
        tap(app.buttons["startStudy"])
        for _ in 0..<10 where !app.textFields["spellingField"].exists {
            if app.buttons["learnWord"].waitForExistence(timeout: 5) { app.buttons["learnWord"].tap() }
        }
        let field = app.textFields["spellingField"]
        tap(field)
        field.typeText("qqqq\n")
        tap(element(app, "aiWhy"))
        XCTAssertTrue(element(app, "aiAnswer").waitForExistence(timeout: 15), "No explanation")
        Thread.sleep(forTimeInterval: 1.5)
        snapshot(app, "en-ai-4-why")
    }

    // MARK: - Helpers

    @MainActor
    private func launch(language: String, reviewMode: String? = nil) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(\(language))",
            "-AppleLocale", language == "en" ? "en_US" : "zh_CN",
            "-uiTesting", "YES",
            "-vocabDemo", "YES",
            "-aiDemo", "YES",
        ]
        if let reviewMode { app.launchArguments += ["-vocabMode", reviewMode] }
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "The app didn't show its tab bar")
        return app
    }

    @MainActor
    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    @MainActor
    private func tap(_ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Not found: \(element)", file: file, line: line)
        element.tap()
    }

    @MainActor
    private func snapshot(_ app: XCUIApplication, _ name: String) {
        Thread.sleep(forTimeInterval: 0.8)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
