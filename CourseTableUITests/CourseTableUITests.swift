import XCTest

/// Drives the app in the Simulator (scheme CourseTableUITests; on CI the manual Screenshots workflow).
///
/// Every launch passes `-uiTesting`, so the app uses an in-memory store and touches nothing on the
/// device. Screenshots are kept as test attachments; the workflow exports them as PNG files.
final class CourseTableUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// Type a course list, place two courses by tapping the grid, then check which swipes change the week.
    @MainActor
    func testTypeCourseListAndPlaceCourses() throws {
        let app = launch()
        app.tabBars.buttons.element(boundBy: 1).tap()

        tap(app.buttons["Type Course List…"])
        let editor = app.textViews.firstMatch
        XCTAssertTrue(editor.waitForExistence(timeout: 10))
        editor.tap()
        editor.typeText("Calculus A101 Li\nPhysics, B204, Wang\n")
        snapshot(app, "flow-1-course-list")
        tap(app.buttons["Add"])

        XCTAssertTrue(app.staticTexts["Tap empty slots to place Calculus."].waitForExistence(timeout: 10))
        snapshot(app, "flow-2-placing")
        tapSlot(app, dayIndex: 0, period: 1, visibleDays: 7)
        XCTAssertTrue(courseBlock(app, named: "Calculus").waitForExistence(timeout: 5))

        tap(chip(app, named: "Physics"))
        XCTAssertTrue(app.staticTexts["Tap empty slots to place Physics."].waitForExistence(timeout: 5))
        tapSlot(app, dayIndex: 2, period: 3, visibleDays: 7)
        XCTAssertTrue(courseBlock(app, named: "Physics").waitForExistence(timeout: 5))
        snapshot(app, "flow-3-placed")

        // Scrolling the course tray must not change the week; swiping the grid does.
        let weekMenu = element(app, "weekMenu")
        let startWeek = weekMenu.label
        element(app, "courseChips").swipeLeft()
        XCTAssertEqual(weekMenu.label, startWeek, "Swiping the course tray changed the week")

        tap(app.buttons["Done"])
        XCTAssertTrue(element(app, "courseChips").waitForNonExistence(timeout: 5))
        XCTAssertEqual(courseBlocks(app).count, 2)

        element(app, "weekGrid").swipeLeft()
        XCTAssertNotEqual(weekMenu.label, startWeek, "Swiping the grid didn't change the week")
        snapshot(app, "flow-4-next-week")
    }

    /// Import the built-in example from Settings and find its courses on the timetable.
    @MainActor
    func testImportExampleFromSettings() throws {
        let app = launch()
        app.tabBars.buttons.element(boundBy: 2).tap()
        tap(app.buttons["Import Courses…"])
        tap(app.buttons["Insert Example"])
        snapshot(app, "import-1-example")
        tap(app.buttons["Import"])
        XCTAssertTrue(app.buttons["Import"].waitForNonExistence(timeout: 5))

        app.tabBars.buttons.element(boundBy: 1).tap()
        XCTAssertTrue(courseBlock(app, named: "Advanced Mathematics").waitForExistence(timeout: 10))
        // Week 1: Mathematics, English, Physics Lab (odd weeks) and the first part of Data Structures.
        XCTAssertEqual(courseBlocks(app).count, 4)
        snapshot(app, "import-2-timetable")
    }

    /// Screenshots of the main screens with demo data, in English and Chinese.
    @MainActor
    func testScreenshotTour() throws {
        for language in ["en", "zh-Hans"] {
            let chinese = language != "en"
            func text(_ english: String, _ simplifiedChinese: String) -> String {
                chinese ? simplifiedChinese : english
            }
            let app = launch(language: language, demoData: true)
            snapshot(app, "\(language)-1-today")

            app.tabBars.buttons.element(boundBy: 1).tap()
            XCTAssertTrue(courseBlocks(app).firstMatch.waitForExistence(timeout: 10))
            snapshot(app, "\(language)-2-timetable")

            tap(courseBlocks(app).firstMatch)
            XCTAssertTrue(app.buttons["cancel"].waitForExistence(timeout: 5))
            snapshot(app, "\(language)-3-course-editor")
            tap(app.buttons["cancel"])

            tap(element(app, "addMenu"))
            snapshot(app, "\(language)-4-add-menu")
            tap(app.buttons[text("Place Courses", "排课")])
            XCTAssertTrue(element(app, "courseChips").waitForExistence(timeout: 5))
            snapshot(app, "\(language)-5-placing")
            tap(app.buttons[text("Done", "完成")])

            app.tabBars.buttons.element(boundBy: 2).tap()
            snapshot(app, "\(language)-6-settings")
            tap(app.buttons[text("Import Courses…", "导入课程…")])
            tap(app.buttons[text("Insert Example", "填入示例")])
            snapshot(app, "\(language)-7-import")
            app.swipeUp()
            snapshot(app, "\(language)-8-import-preview")
            tap(app.buttons["cancel"])

            app.swipeUp()
            snapshot(app, "\(language)-9-settings-bottom")
            app.terminate()
        }
    }

    // MARK: - Helpers

    @MainActor
    private func launch(language: String = "en", demoData: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        // Flags carry a value so the user-defaults argument parser doesn't pair them with the next argument.
        app.launchArguments = [
            "-AppleLanguages", "(\(language))",
            "-AppleLocale", language == "en" ? "en_US" : "zh_CN",
            "-uiTesting", "YES",
        ]
        if demoData {
            app.launchArguments += ["-demoData", "YES"]
        }
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "The app didn't show its tab bar")
        return app
    }

    /// Any element with this accessibility identifier, whatever its type.
    @MainActor
    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    @MainActor
    private func courseBlocks(_ app: XCUIApplication) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(identifier: "courseBlock")
    }

    @MainActor
    private func courseBlock(_ app: XCUIApplication, named name: String) -> XCUIElement {
        courseBlocks(app).matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch
    }

    @MainActor
    private func chip(_ app: XCUIApplication, named name: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "courseChip")
            .matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch
    }

    /// Taps the grid cell for `dayIndex` (0 = Monday) and `period`, using WeekView's grid geometry:
    /// a 40-point time column, then equal day columns, 60 points per period.
    @MainActor
    private func tapSlot(_ app: XCUIApplication, dayIndex: Int, period: Int, visibleDays: Int) {
        let grid = element(app, "weekGrid")
        XCTAssertTrue(grid.waitForExistence(timeout: 5), "No week grid")
        let timeColumnWidth: CGFloat = 40
        let rowHeight: CGFloat = 60
        let columnWidth = (grid.frame.width - timeColumnWidth) / CGFloat(visibleDays)
        let offset = CGVector(
            dx: timeColumnWidth + columnWidth * (CGFloat(dayIndex) + 0.5),
            dy: rowHeight * (CGFloat(period) - 0.5)
        )
        grid.coordinate(withNormalizedOffset: .zero).withOffset(offset).tap()
    }

    @MainActor
    private func tap(_ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Not found: \(element)", file: file, line: line)
        element.tap()
    }

    /// Saves a screenshot as an attachment named `name`, after letting animations settle.
    @MainActor
    private func snapshot(_ app: XCUIApplication, _ name: String) {
        Thread.sleep(forTimeInterval: 0.8)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
