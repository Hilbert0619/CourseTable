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
            XCTAssertTrue(element(app, "aiAnswer").waitForExistence(timeout: 15), "No answer")
            Thread.sleep(forTimeInterval: 2)
            app.swipeUp()
            snapshot(app, "\(language)-ai-1-word-help")
            // "Ask Again" is in the row below the answer; off-screen rows aren't in the tree, so scroll to it.
            let again = element(app, "aiAskAgain")
            for _ in 0..<4 where !(again.exists && again.isHittable) { app.swipeUp() }
            XCTAssertTrue(again.exists, "The answer didn't finish")
            snapshot(app, "\(language)-ai-1-word-help-bottom")

            app.tabBars.buttons.element(boundBy: 4).tap()
            let link = element(app, "aiSettingsLink")
            for _ in 0..<6 where !(link.exists && link.isHittable) { app.swipeUp() }
            tap(link)
            XCTAssertTrue(element(app, "aiAddConnection").waitForExistence(timeout: 10), "Settings → AI didn't open")
            snapshot(app, "\(language)-ai-2-settings")

            tap(element(app, "aiConnection"))
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
        // Answer whatever comes first (new words, recall) until a spelling question is on screen.
        let field = app.textFields["spellingField"]
        for _ in 0..<20 where !field.waitForExistence(timeout: 3) {
            if app.buttons["learnWord"].exists {
                app.buttons["learnWord"].tap()
            } else if app.buttons["showAnswer"].exists {
                app.buttons["showAnswer"].tap()
                tap(app.buttons["rating-3"])
            } else if app.buttons["choice-0"].exists {
                app.buttons["choice-0"].tap()
                tap(app.buttons["continue"])
            } else if app.buttons["continue"].exists {
                app.buttons["continue"].tap()
            }
        }
        tap(field)
        field.typeText("qqqq\n")
        tap(element(app, "aiWhy"))
        XCTAssertTrue(element(app, "aiAnswer").waitForExistence(timeout: 15), "No explanation")
        Thread.sleep(forTimeInterval: 1.5)
        // Each word once (two overlapping requests once interleaved their text).
        let start = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "You left out letters in the middle.")).firstMatch
        XCTAssertTrue(start.exists, "The explanation isn't shown as sent")
        snapshot(app, "en-ai-4-why")
    }

    /// Long sentences and writing feedback in the Words tab, and quick add in the to-do editor.
    @MainActor
    func testStudyToolsAndQuickAdd() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language, demoTimetable: true)
            app.tabBars.buttons.element(boundBy: 3).tap()

            open(app, "sentencesLink")
            let sentence = field(app, "sentenceField")
            tap(sentence)
            sentence.typeText("The study suggests that people who sleep less are more likely to abandon their goals.")
            tap(element(app, "analyseSentence"))
            XCTAssertTrue(element(app, "aiAnswer").waitForExistence(timeout: 15), "No analysis")
            Thread.sleep(forTimeInterval: 2)
            snapshot(app, "\(language)-ai-5-sentence")
            app.swipeUp()
            app.swipeUp()
            snapshot(app, "\(language)-ai-5-sentence-bottom")
            app.navigationBars.buttons.element(boundBy: 0).tap()

            open(app, "essayLink")
            let essay = field(app, "essayField")
            tap(essay)
            essay.typeText("As is shown in the picture, more and more people is busy with their phones. In my opinion, this phenomenon deserves our attention, because it affects our health and our families.")
            tap(element(app, "markEssay"))
            XCTAssertTrue(element(app, "aiAnswer").waitForExistence(timeout: 15), "No feedback")
            Thread.sleep(forTimeInterval: 2)
            app.swipeUp()
            snapshot(app, "\(language)-ai-6-essay")
            app.navigationBars.buttons.element(boundBy: 0).tap()

            app.tabBars.buttons.element(boundBy: 0).tap()
            tap(app.buttons["addTodo"])
            let note = field(app, "todoQuickAdd")
            tap(note)
            note.typeText(language == "en" ? "hand in the paper after class tomorrow" : "明天下课后交论文")
            tap(element(app, "todoFillIn"))
            let title = field(app, "todoTitle")
            XCTAssertTrue(title.waitForValue("交论文", timeout: 15), "The fields weren't filled in")
            snapshot(app, "\(language)-ai-7-quick-add")
            app.terminate()
        }
    }

    /// The Assistant from the Today tab: an empty chat with its study tools, then a question and its answer.
    @MainActor
    func testAssistant() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language, demoTimetable: true)
            tap(element(app, "assistantButton"))
            XCTAssertTrue(element(app, "assistantInput").waitForExistence(timeout: 10), "The Assistant didn't open")
            snapshot(app, "\(language)-ai-8-assistant-empty")
            let input = field(app, "assistantInput")
            tap(input)
            input.typeText(language == "en" ? "How should I plan this week?" : "这周怎么安排复习？")
            tap(element(app, "assistantSend"))
            XCTAssertTrue(element(app, "assistantReply").waitForExistence(timeout: 15), "No reply")
            snapshot(app, "\(language)-ai-8-assistant")
            app.terminate()
        }
    }

    // MARK: - Helpers

    /// Opens a link on the Words home screen, scrolling until it's there.
    @MainActor
    private func open(_ app: XCUIApplication, _ identifier: String) {
        let link = element(app, identifier)
        _ = link.waitForExistence(timeout: 2)
        for _ in 0..<5 where !(link.exists && link.isHittable) { app.swipeUp() }
        tap(link)
    }

    /// A one-line or multi-line text field.
    @MainActor
    private func field(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        let single = app.textFields[identifier]
        return single.waitForExistence(timeout: 5) ? single : app.textViews[identifier]
    }

    @MainActor
    private func launch(language: String, reviewMode: String? = nil, demoTimetable: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(\(language))",
            "-AppleLocale", language == "en" ? "en_US" : "zh_CN",
            "-uiTesting", "YES",
            "-vocabDemo", "YES",
            "-aiDemo", "YES",
        ]
        if let reviewMode { app.launchArguments += ["-vocabMode", reviewMode] }
        if demoTimetable { app.launchArguments += ["-demoData", "YES"] }
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

private extension XCUIElement {
    /// Waits until the element's value is `expected`.
    @MainActor
    func waitForValue(_ expected: String, timeout: TimeInterval) -> Bool {
        let deadline = Date.now.addingTimeInterval(timeout)
        while Date.now < deadline {
            if (value as? String) == expected { return true }
            Thread.sleep(forTimeInterval: 0.3)
        }
        return false
    }
}
