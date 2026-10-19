import XCTest

/// Each tab in the looks the app must hold up in: English light and dark, Chinese dark, and English with large text,
/// plus a running Focus session in dark. Run it before and after visual changes and compare screen by screen
/// (screenshots `look-<variant>-<n>-<screen>`).
final class LookUITests: XCTestCase {
    struct Variant {
        var name: String
        var language: String
        var dark: Bool
        var largeText: Bool = false
    }

    static let variants = [
        Variant(name: "en-light", language: "en", dark: false),
        Variant(name: "en-dark", language: "en", dark: true),
        Variant(name: "zh-dark", language: "zh-Hans", dark: true),
        Variant(name: "en-large", language: "en", dark: false, largeText: true),
    ]

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDown() {
        XCUIDevice.shared.appearance = .light
        super.tearDown()
    }

    @MainActor
    func testTabsInEveryLook() throws {
        for variant in Self.variants {
            let app = launch(variant)
            snapshot(app, variant, "1-today")
            app.tabBars.buttons.element(boundBy: 1).tap()
            XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "courseBlock").firstMatch.waitForExistence(timeout: 10))
            snapshot(app, variant, "2-timetable")
            app.tabBars.buttons.element(boundBy: 2).tap()
            snapshot(app, variant, "3-focus")
            app.tabBars.buttons.element(boundBy: 3).tap()
            snapshot(app, variant, "4-words")
            app.tabBars.buttons.element(boundBy: 4).tap()
            snapshot(app, variant, "5-settings")
            app.terminate()
        }
    }

    @MainActor
    func testRunningFocusInDark() throws {
        for variant in Self.variants where variant.dark {
            let app = launch(variant, extra: ["-focusDemo", "YES"])
            XCTAssertTrue(app.buttons["pauseFocus"].waitForExistence(timeout: 10))
            // Once the demo wallpaper is in.
            Thread.sleep(forTimeInterval: 3)
            snapshot(app, variant, "6-focus-running")
            if app.buttons["fullScreenFocus"].exists {
                app.buttons["fullScreenFocus"].tap()
                XCTAssertTrue(app.buttons["exitFullScreenFocus"].waitForExistence(timeout: 5))
                snapshot(app, variant, "7-focus-full-screen")
                app.buttons["exitFullScreenFocus"].tap()
                XCTAssertTrue(app.buttons["pauseFocus"].waitForExistence(timeout: 5))
            }
            app.terminate()
        }
    }

    /// Settings → Timetable Appearance with a photo behind the timetable (the drawn demo picture), in both block
    /// styles, light and dark.
    @MainActor
    func testTimetableAppearance() throws {
        for variant in [Self.variants[0], Self.variants[1]] {
            let app = launch(variant, extra: ["-timetableBackgroundDemo", "YES"])
            app.tabBars.buttons.element(boundBy: 1).tap()
            XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "courseBlock").firstMatch.waitForExistence(timeout: 10))
            snapshot(app, variant, "8-timetable-photo")

            app.tabBars.buttons.element(boundBy: 4).tap()
            let link = app.buttons["timetableAppearanceLink"]
            for _ in 0..<4 where !(link.exists && link.isHittable) { app.swipeUp() }
            link.tap()
            let picker = app.segmentedControls["blockStylePicker"]
            XCTAssertTrue(picker.waitForExistence(timeout: 5))
            snapshot(app, variant, "9-appearance-soft")
            picker.buttons.element(boundBy: 1).tap()
            snapshot(app, variant, "9-appearance-solid")

            app.tabBars.buttons.element(boundBy: 1).tap()
            snapshot(app, variant, "8-timetable-photo-solid")
            app.terminate()
        }
    }

    // MARK: - Helpers

    @MainActor
    private func launch(_ variant: Variant, extra: [String] = []) -> XCUIApplication {
        XCUIDevice.shared.appearance = variant.dark ? .dark : .light
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(\(variant.language))",
            "-AppleLocale", variant.language == "en" ? "en_US" : "zh_CN",
            "-uiTesting", "YES",
            "-demoData", "YES",
            "-vocabDemo", "YES",
        ] + extra
        if variant.largeText {
            app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        }
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "The app didn't show its tab bar")
        return app
    }

    @MainActor
    private func snapshot(_ app: XCUIApplication, _ variant: Variant, _ screen: String) {
        Thread.sleep(forTimeInterval: 1)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "look-\(variant.name)-\(screen)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
