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
