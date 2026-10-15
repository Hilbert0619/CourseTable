import XCTest

/// The Assistant with the app's tools (`-aiDemo`: scripted tool calls, nothing touches the network): a reminder
/// proposed and confirmed, a focus session started, vocabulary progress analysed, and courses read from a
/// timetable photo (`-aiDemoPhoto` offers a drawn sample, read on the device).
final class AgentUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// "Remind me…" looks at tomorrow's classes and proposes a to-do; Confirm adds it (Undo is offered). Then a
    /// focus session is proposed, confirmed, and runs on the Focus tab.
    @MainActor
    func testReminderAndFocus() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language)
            tap(element(app, "assistantButton"))
            XCTAssertTrue(element(app, "assistantInput").waitForExistence(timeout: 10), "The Assistant didn't open")
            snapshot(app, "\(language)-agent-1-welcome")

            tap(element(app, "suggestReminder"))
            XCTAssertTrue(element(app, "agentConfirm").waitForExistence(timeout: 20), "No to-do was proposed")
            XCTAssertTrue(element(app, "agentLookup").exists, "The schedule lookup isn't shown")
            snapshot(app, "\(language)-agent-2-reminder-card")
            tap(element(app, "agentConfirm"))
            XCTAssertTrue(element(app, "agentUndo").waitForExistence(timeout: 10), "The to-do wasn't added")
            XCTAssertTrue(element(app, "assistantReply").waitForExistence(timeout: 15), "No reply after the change")
            snapshot(app, "\(language)-agent-3-reminder-done")

            let input = field(app, "assistantInput")
            tap(input)
            input.typeText(language == "en" ? "Start a 25-minute focus session" : "开始 25 分钟专注")
            tap(element(app, "assistantSend"))
            let confirm = element(app, "agentConfirm")
            XCTAssertTrue(confirm.waitForExistence(timeout: 20), "No focus session was proposed")
            snapshot(app, "\(language)-agent-4-focus-card")
            tap(confirm)
            XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "assistantReply").element(boundBy: 1)
                .waitForExistence(timeout: 15), "No reply after starting focus")
            app.navigationBars.buttons.element(boundBy: 0).tap()  // Done
            app.tabBars.buttons.element(boundBy: 2).tap()
            snapshot(app, "\(language)-agent-5-focus-running")
            app.terminate()
        }
    }

    /// Words → Statistics → Analyse My Progress: two lookups, then the analysis.
    @MainActor
    func testProgressAnalysis() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language)
            app.tabBars.buttons.element(boundBy: 3).tap()
            let statistics = element(app, "statisticsLink")
            _ = statistics.waitForExistence(timeout: 10)
            for _ in 0..<5 where !(statistics.exists && statistics.isHittable) { app.swipeUp() }
            tap(statistics)
            tap(element(app, "analyseProgress"))
            XCTAssertTrue(element(app, "assistantReply").waitForExistence(timeout: 20), "No analysis")
            XCTAssertGreaterThanOrEqual(app.descendants(matching: .any).matching(identifier: "agentLookup").count, 2,
                                        "Expected two lookups")
            Thread.sleep(forTimeInterval: 1.5)
            snapshot(app, "\(language)-agent-6-progress")
            app.terminate()
        }
    }

    /// A timetable photo in the Assistant becomes an "Add Courses" card; Review opens the import preview.
    /// The import screen reads a photo by itself too, with the photo's period times offered.
    @MainActor
    func testTimetablePhoto() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language, photo: true)
            tap(element(app, "assistantButton"))
            tap(element(app, "assistantAttach"))
            XCTAssertTrue(element(app, "assistantAttachment").waitForExistence(timeout: 30), "The photo wasn't read")
            let input = field(app, "assistantInput")
            tap(input)
            input.typeText(language == "en" ? "Add these courses" : "把这些课加进课程表")
            tap(element(app, "assistantSend"))
            XCTAssertTrue(element(app, "agentConfirm").waitForExistence(timeout: 20), "No courses were proposed")
            snapshot(app, "\(language)-agent-7-photo-card")
            tap(element(app, "agentConfirm"))
            XCTAssertTrue(element(app, "importButton").waitForExistence(timeout: 10), "The import preview didn't open")
            snapshot(app, "\(language)-agent-8-photo-preview")
            tap(element(app, "importButton"))
            XCTAssertTrue(element(app, "agentUndo").waitForExistence(timeout: 10), "The courses weren't imported")
            XCTAssertTrue(element(app, "assistantReply").waitForExistence(timeout: 15), "No reply after the import")
            snapshot(app, "\(language)-agent-9-photo-done")
            app.navigationBars.buttons.element(boundBy: 0).tap()  // Done

            app.tabBars.buttons.element(boundBy: 1).tap()
            tap(element(app, "addMenu"))
            tap(element(app, "importCourses"))
            tap(element(app, "samplePhoto"))
            XCTAssertTrue(element(app, "photoDone").waitForExistence(timeout: 30), "The photo wasn't read into courses")
            snapshot(app, "\(language)-agent-10-import-photo")
            let periods = element(app, "usePhotoPeriods")
            for _ in 0..<4 where !(periods.exists && periods.isHittable) { app.swipeUp() }
            XCTAssertTrue(periods.exists, "The photo's period times aren't offered")
            snapshot(app, "\(language)-agent-10-import-photo-periods")
            app.terminate()
        }
    }

    // MARK: - Helpers

    /// A one-line or multi-line text field.
    @MainActor
    private func field(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        let single = app.textFields[identifier]
        return single.waitForExistence(timeout: 5) ? single : app.textViews[identifier]
    }

    @MainActor
    private func launch(language: String, photo: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(\(language))",
            "-AppleLocale", language == "en" ? "en_US" : "zh_CN",
            "-uiTesting", "YES",
            "-vocabDemo", "YES",
            "-aiDemo", "YES",
            "-demoData", "YES",
        ]
        if photo { app.launchArguments += ["-aiDemoPhoto", "YES"] }
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
