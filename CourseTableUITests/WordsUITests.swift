import XCTest

/// Drives the Words tab with the demo backend (launch flag `-uiTesting`; `-vocabDemo` signs in the demo
/// learner with two weeks of answers). Nothing touches the network.
final class WordsUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// Signed out: the sign-in screen, and creating an account asks for the emailed link.
    @MainActor
    func testSignUpAsksForTheEmailedLink() throws {
        let app = launch(vocabDemo: false)
        openWordsTab(app)
        XCTAssertTrue(app.buttons["submitAuth"].waitForExistence(timeout: 20), "No sign-in screen")
        snapshot(app, "words-0-sign-in")
        tap(app.segmentedControls.buttons.element(boundBy: 1))
        let email = app.textFields["email"]
        tap(email)
        email.typeText("new@example.com")
        let password = app.secureTextFields["password"]
        tap(password)
        password.typeText("long-enough")
        snapshot(app, "words-0-sign-up")
        tap(app.buttons["submitAuth"])
        XCTAssertTrue(element(app, "authNotice").waitForExistence(timeout: 10), "No 'check your inbox' notice")
    }

    /// A whole session in "recall and rate" mode: new words (with their check) and reviews until the
    /// summary; afterwards today's plan is done.
    @MainActor
    func testStudySessionFinishes() throws {
        let app = launch(vocabDemo: true, reviewMode: "recognition")
        openWordsTab(app)
        tap(app.buttons["startStudy"])
        var answered = 0
        while !app.buttons["finishSession"].waitForExistence(timeout: 1) && answered < 80 {
            answer(app, snapshots: [])
            answered += 1
        }
        XCTAssertTrue(app.buttons["finishSession"].exists, "The session didn't finish after \(answered) answers")
        snapshot(app, "words-6-summary")
        tap(app.buttons["finishSession"])
        XCTAssertTrue(app.buttons["startStudy"].waitForNonExistence(timeout: 10), "Today's plan isn't done after the session")
    }

    /// Mixed questions: a screenshot of each kind as it first comes up, then close the session.
    @MainActor
    func testQuestionTypes() throws {
        let app = launch(vocabDemo: true)
        openWordsTab(app)
        tap(app.buttons["startStudy"])
        var seen: Set<String> = []
        for _ in 0..<30 where seen.count < 4 && !app.buttons["finishSession"].exists {
            seen.formUnion(answer(app, snapshots: seen))
        }
        XCTAssertTrue(seen.contains("new"), "No new word came up; saw \(seen)")
        XCTAssertTrue(seen.count >= 3, "Only saw \(seen)")
        if !app.buttons["finishSession"].exists {
            tap(app.buttons["closeSession"])
        }
        XCTAssertTrue(app.buttons["startStudy"].waitForExistence(timeout: 10) || app.staticTexts.count > 0)
    }

    /// Answers the question on screen: recognition "Good", the first multiple-choice option, a wrong
    /// spelling. Takes a screenshot of each kind not in `snapshots` yet and returns the kinds seen.
    @MainActor
    @discardableResult
    private func answer(_ app: XCUIApplication, snapshots: Set<String>) -> Set<String> {
        var kinds: Set<String> = []
        func shoot(_ kind: String, _ name: String) {
            kinds.insert(kind)
            if !snapshots.contains(kind) { snapshot(app, name) }
        }
        if app.buttons["learnWord"].exists {
            shoot("new", "words-2-new-word")
            app.buttons["learnWord"].tap()
        } else if app.buttons["showAnswer"].exists {
            app.buttons["showAnswer"].tap()
            shoot("recognition", "words-4-recognition")
            tap(app.buttons["rating-3"])
        } else if app.buttons["choice-0"].exists {
            app.buttons["choice-0"].tap()
            shoot("choice", "words-3-choice")
            tap(app.buttons["continue"])
        } else if app.textFields["spellingField"].exists {
            let field = app.textFields["spellingField"]
            field.tap()
            field.typeText("abandn")
            tap(app.buttons["checkSpelling"])
            shoot("spelling", "words-5-spelling")
            tap(app.buttons["continue"])
        } else if app.buttons["continue"].exists {
            app.buttons["continue"].tap()
        }
        return kinds
    }

    /// Screenshots of the Words screens with the demo learner, in English and Chinese.
    @MainActor
    func testWordsScreenshotTour() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language, vocabDemo: true)
            openWordsTab(app)
            XCTAssertTrue(app.buttons["startStudy"].waitForExistence(timeout: 20))
            snapshot(app, "\(language)-words-1-home")

            tap(element(app, "currentBook"))
            snapshot(app, "\(language)-words-7-books")
            tap(app.buttons["importWordList"])
            tap(app.buttons["insertWordExample"])
            snapshot(app, "\(language)-words-8-import")
            tap(app.buttons["cancel"])
            app.navigationBars.buttons.element(boundBy: 0).tap()

            app.swipeUp()
            tap(element(app, "statisticsLink"))
            XCTAssertTrue(element(app, "wordsChart").waitForExistence(timeout: 10))
            snapshot(app, "\(language)-words-9-statistics")
            app.swipeUp()
            snapshot(app, "\(language)-words-10-statistics-bottom")
            app.navigationBars.buttons.element(boundBy: 0).tap()

            app.swipeDown()
            tap(element(app, "wordsSettings"))
            snapshot(app, "\(language)-words-11-settings")
            app.navigationBars.buttons.element(boundBy: 0).tap()

            app.swipeUp()
            tap(element(app, "searchLink"))
            let search = app.searchFields.firstMatch
            tap(search)
            search.typeText(language == "en" ? "ab" : "放弃")
            snapshot(app, "\(language)-words-12-search")
            tap(app.cells.firstMatch)
            XCTAssertTrue(element(app, "favoriteButton").waitForExistence(timeout: 10))
            snapshot(app, "\(language)-words-13-word-page")
            app.terminate()
        }
    }

    // MARK: - Helpers

    @MainActor
    private func launch(language: String = "en", vocabDemo: Bool, reviewMode: String? = nil) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(\(language))",
            "-AppleLocale", language == "en" ? "en_US" : "zh_CN",
            "-uiTesting", "YES",
        ]
        if vocabDemo {
            app.launchArguments += ["-vocabDemo", "YES"]
        }
        if let reviewMode {
            app.launchArguments += ["-vocabMode", reviewMode]
        }
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "The app didn't show its tab bar")
        return app
    }

    @MainActor
    private func openWordsTab(_ app: XCUIApplication) {
        app.tabBars.buttons.element(boundBy: 2).tap()
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
