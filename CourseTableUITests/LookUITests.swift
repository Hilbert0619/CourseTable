import XCTest

/// Each tab in the looks the app must hold up in: English light and dark, Chinese dark, and English with large text
/// (all in glass, the default look), and the classic look in light and dark; plus a running Focus session in dark.
/// Run it before and after visual changes and compare screen by screen (screenshots `look-<variant>-<n>-<screen>`).
final class LookUITests: XCTestCase {
    struct Variant {
        var name: String
        var language: String
        var dark: Bool
        var largeText: Bool = false
        /// `-appStyle`: nil for the default (glass).
        var style: String?
    }

    static let variants = [
        Variant(name: "en-light", language: "en", dark: false),
        Variant(name: "en-dark", language: "en", dark: true),
        Variant(name: "zh-dark", language: "zh-Hans", dark: true),
        Variant(name: "en-large", language: "en", dark: false, largeText: true),
        Variant(name: "en-classic-light", language: "en", dark: false, style: "classic"),
        Variant(name: "en-classic-dark", language: "en", dark: true, style: "classic"),
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
        for variant in Self.variants where variant.dark && variant.style == nil {
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

    /// Settings → Appearance, light and dark: a photo behind the glass (the drawn demo picture), then a built-in
    /// wallpaper with solid blocks, then the classic look on that wallpaper.
    @MainActor
    func testAppearance() throws {
        for variant in [Self.variants[0], Self.variants[1]] {
            let app = launch(variant, extra: ["-timetableBackgroundDemo", "YES"])
            app.tabBars.buttons.element(boundBy: 1).tap()
            XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "courseBlock").firstMatch.waitForExistence(timeout: 10))
            snapshot(app, variant, "8-timetable-photo")

            app.tabBars.buttons.element(boundBy: 4).tap()
            let link = app.buttons["appearanceLink"]
            for _ in 0..<4 where !(link.exists && link.isHittable) { app.swipeUp() }
            link.tap()
            let style = app.segmentedControls["appStylePicker"]
            XCTAssertTrue(style.waitForExistence(timeout: 5))
            snapshot(app, variant, "9-appearance-photo")

            let lagoon = app.buttons["background-lagoon"]
            XCTAssertTrue(lagoon.waitForExistence(timeout: 5))
            lagoon.tap()
            let blocks = app.segmentedControls["blockStylePicker"]
            for _ in 0..<3 where !(blocks.exists && blocks.isHittable) { app.swipeUp() }
            blocks.buttons.element(boundBy: 1).tap()
            snapshot(app, variant, "9-appearance-lagoon-solid")
            app.tabBars.buttons.element(boundBy: 1).tap()
            snapshot(app, variant, "8-timetable-lagoon-solid")
            app.tabBars.buttons.element(boundBy: 0).tap()
            snapshot(app, variant, "8-today-lagoon")

            app.tabBars.buttons.element(boundBy: 4).tap()
            for _ in 0..<3 where !(style.exists && style.isHittable) { app.swipeDown() }
            style.buttons.element(boundBy: 1).tap()
            snapshot(app, variant, "9-appearance-classic")
            app.tabBars.buttons.element(boundBy: 1).tap()
            snapshot(app, variant, "8-timetable-classic-lagoon")
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
            // The device appearance above didn't take on CI: the app applies this itself.
            "-uiColorScheme", variant.dark ? "dark" : "light",
        ] + (variant.style.map { ["-appStyle", $0] } ?? []) + extra
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
