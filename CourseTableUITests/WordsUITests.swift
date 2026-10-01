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
        let chinese = launch(language: "zh-Hans", vocabDemo: false)
        openWordsTab(chinese)
        XCTAssertTrue(chinese.buttons["submitAuth"].waitForExistence(timeout: 20), "No sign-in screen")
        snapshot(chinese, "zh-Hans-words-0-sign-in")
        chinese.terminate()

        let app = launch(vocabDemo: false)
        openWordsTab(app)
        XCTAssertTrue(app.buttons["submitAuth"].waitForExistence(timeout: 20), "No sign-in screen")
        snapshot(app, "en-words-0-sign-in")
        tap(app.segmentedControls.buttons.element(boundBy: 1))
        let email = app.textFields["email"]
        tap(email)
        email.typeText("new@example.com")
        let password = app.secureTextFields["password"]
        tap(password)
        // Return ends editing, so the keyboard doesn't cover the button.
        password.typeText("long-enough\n")
        snapshot(app, "en-words-0-sign-up")
        tap(app.buttons["submitAuth"])
        XCTAssertTrue(element(app, "authNotice").waitForExistence(timeout: 10), "No 'check your inbox' notice")
        app.swipeUp()
        snapshot(app, "en-words-0-sign-up-notice")
    }

    /// A whole session in "recall and rate" mode: new words (with their check) and reviews until the
    /// summary; afterwards today's plan is done.
    @MainActor
    func testStudySessionFinishes() throws {
        let app = launch(vocabDemo: true, reviewMode: "recognition")
        openWordsTab(app)
        tap(app.buttons["startStudy"])
        var answered = 0
        while !app.buttons["finishSession"].exists && answered < 80 {
            answer(app)
            answered += 1
        }
        XCTAssertTrue(app.buttons["finishSession"].exists, "The session didn't finish after \(answered) answers")
        snapshot(app, "en-words-6-summary")
        tap(app.buttons["finishSession"])
        XCTAssertTrue(app.buttons["startStudy"].waitForNonExistence(timeout: 10), "Today's plan isn't done after the session")
        snapshot(app, "en-words-6-done-for-today")
    }

    /// Mixed questions, in English and Chinese: a screenshot of each kind as it first comes up, then
    /// close the session.
    @MainActor
    func testQuestionTypes() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language, vocabDemo: true)
            openWordsTab(app)
            tap(app.buttons["startStudy"])
            var seen: Set<String> = []
            for _ in 0..<30 where seen.count < 4 && !app.buttons["finishSession"].exists {
                seen.formUnion(answer(app, screenshots: language, skipping: seen))
            }
            XCTAssertTrue(seen.contains("new"), "No new word came up; saw \(seen)")
            XCTAssertTrue(seen.count >= 3, "Only saw \(seen)")
            tap(app.buttons["finishSession"].exists ? app.buttons["finishSession"] : app.buttons["closeSession"])
            XCTAssertTrue(element(app, "wordsSettings").waitForExistence(timeout: 10), "Not back on the Words home screen")
            app.terminate()
        }
    }

    /// Screenshots of the Words screens with the demo learner, in English and Chinese.
    @MainActor
    func testWordsScreenshotTour() throws {
        for language in ["en", "zh-Hans"] {
            let app = launch(language: language, vocabDemo: true)
            openWordsTab(app)
            XCTAssertTrue(app.buttons["startStudy"].waitForExistence(timeout: 20))
            snapshot(app, "\(language)-words-1-home")
            app.swipeUp()
            snapshot(app, "\(language)-words-1-home-bottom")
            app.swipeDown()

            open(app, "currentBook")
            snapshot(app, "\(language)-words-7-books")
            tap(app.buttons["importWordList"])
            tap(app.buttons["insertWordExample"])
            snapshot(app, "\(language)-words-8-import")
            tap(app.buttons["cancel"])
            XCTAssertTrue(app.buttons["insertWordExample"].waitForNonExistence(timeout: 5), "The import sheet didn't close")
            goBack(app)

            open(app, "statisticsLink")
            _ = element(app, "wordsChart").waitForExistence(timeout: 5)
            snapshot(app, "\(language)-words-9-statistics")
            app.swipeUp()
            snapshot(app, "\(language)-words-10-statistics-bottom")
            goBack(app)

            open(app, "favoritesLink")
            snapshot(app, "\(language)-words-11-favourites")
            goBack(app)
            open(app, "hardWordsLink")
            snapshot(app, "\(language)-words-11-hard-words")
            goBack(app)

            open(app, "wordsSettings")
            snapshot(app, "\(language)-words-12-settings")
            app.swipeUp()
            snapshot(app, "\(language)-words-12-settings-bottom")
            goBack(app)

            open(app, "searchLink")
            let search = app.searchFields.firstMatch
            tap(search)
            search.typeText(language == "en" ? "ab" : "放弃")
            XCTAssertTrue(app.cells.firstMatch.waitForExistence(timeout: 10), "No search results")
            snapshot(app, "\(language)-words-13-search")
            tap(app.cells.firstMatch)
            XCTAssertTrue(element(app, "favoriteButton").waitForExistence(timeout: 10))
            snapshot(app, "\(language)-words-14-word-page")
            app.swipeUp()
            snapshot(app, "\(language)-words-14-word-page-bottom")
            app.terminate()
        }
    }

    // MARK: - Helpers

    /// Answers the question on screen: recognition "Good", the first multiple-choice option, a wrong
    /// spelling. With a language, takes a screenshot of each kind not in `skipping`. Returns the kinds seen.
    @MainActor
    @discardableResult
    private func answer(_ app: XCUIApplication, screenshots language: String? = nil, skipping: Set<String> = []) -> Set<String> {
        var kinds: Set<String> = []
        func shoot(_ kind: String, _ name: String) {
            kinds.insert(kind)
            if let language, !skipping.contains(kind) { snapshot(app, "\(language)-words-\(name)") }
        }
        let onScreen = ["learnWord", "showAnswer", "choice-0", "spellingField", "continue", "finishSession"]
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "identifier IN %@", argumentArray: [onScreen]))
            .firstMatch.waitForExistence(timeout: 10), "No question on screen")
        if app.buttons["learnWord"].exists {
            shoot("new", "2-new-word")
            app.buttons["learnWord"].tap()
        } else if app.buttons["showAnswer"].exists {
            if let language, !skipping.contains("recognition") { snapshot(app, "\(language)-words-4-recall") }
            app.buttons["showAnswer"].tap()
            shoot("recognition", "4-recall-answer")
            tap(app.buttons["rating-3"])
        } else if app.buttons["choice-0"].exists {
            if let language, !skipping.contains("choice") { snapshot(app, "\(language)-words-3-choice") }
            app.buttons["choice-0"].tap()
            shoot("choice", "3-choice-answer")
            tap(app.buttons["continue"])
        } else if app.textFields["spellingField"].exists {
            if let language, !skipping.contains("spelling") { snapshot(app, "\(language)-words-5-spelling") }
            let field = app.textFields["spellingField"]
            field.tap()
            // Return checks the spelling.
            field.typeText("abandn\n")
            shoot("spelling", "5-spelling-answer")
            tap(app.buttons["continue"])
        } else if app.buttons["continue"].exists {
            app.buttons["continue"].tap()
        }
        return kinds
    }

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

    /// Opens a screen from the Words home screen and waits until it's there. The home screen's settings
    /// button (in the navigation bar, so never scrolled away) tells the two apart.
    @MainActor
    private func open(_ app: XCUIApplication, _ identifier: String, file: StaticString = #filePath, line: UInt = #line) {
        tap(element(app, identifier), file: file, line: line)
        XCTAssertTrue(element(app, "wordsSettings").waitForNonExistence(timeout: 10), "\(identifier) didn't open", file: file, line: line)
    }

    /// Back to the Words home screen.
    @MainActor
    private func goBack(_ app: XCUIApplication, file: StaticString = #filePath, line: UInt = #line) {
        tap(app.navigationBars.buttons.element(boundBy: 0), file: file, line: line)
        XCTAssertTrue(element(app, "wordsSettings").waitForExistence(timeout: 10), "Not back on the Words home screen", file: file, line: line)
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
