import XCTest

/// Screenshots of the widgets in the Home Screen's widget gallery (sample content, drawn by the widget extension),
/// then of the To-Dos and Focus widgets placed on the Home Screen. CI builds are unsigned, so there's no App Group:
/// placed widgets show "Open Course Table…". Gestures on the system UI are best effort: the screenshots show what
/// happened.
final class WidgetUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = true
    }

    @MainActor
    func testWidgetGalleryScreenshots() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-uiTesting", "YES", "-demoData", "YES", "-widgetDemo", "YES"]
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "The app didn't show its tab bar")
        Thread.sleep(forTimeInterval: 2)
        goHome()

        // The app's page: one horizontal page per widget size (Class Schedule 2, To-Dos 3, Focus 1).
        if openGallery(index: 0) {
            for page in 0..<6 {
                snapshot("widget-1-page-\(page)")
                springboard.swipeLeft()
                Thread.sleep(forTimeInterval: 1)
            }
            dismissGallery()
        }
        // The Clear look (Edit → Customize → Clear): iOS draws widgets on glass, in their accented rendering.
        if customize(choosing: "Clear"), openGallery(index: 2) {
            for page in 0..<6 {
                snapshot("widget-2-clear-page-\(page)")
                // Page 3 is the medium To-Dos: put it on the Home Screen, on glass.
                if page == 3, springboard.buttons["Add Widget"].firstMatch.exists {
                    springboard.buttons["Add Widget"].firstMatch.tap()
                    Thread.sleep(forTimeInterval: 2)
                    snapshot("widget-3-clear-added")
                    break
                }
                springboard.swipeLeft()
                Thread.sleep(forTimeInterval: 1)
            }
            dismissGallery()
        }
        goHome()
        snapshot("widget-9-home")
        app.terminate()
    }

    @MainActor
    private var springboard: XCUIApplication {
        XCUIApplication(bundleIdentifier: "com.apple.springboard")
    }

    /// Edit Home Screen → Add Widget → search "Course Table" → the app's widgets.
    @MainActor
    private func openGallery(index: Int) -> Bool {
        // A long press on the wallpaper starts editing.
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.62)).press(forDuration: 1.6)
        Thread.sleep(forTimeInterval: 1.5)
        snapshot("widget-\(index)-a-editing")
        let edit = springboard.buttons["Edit"].firstMatch
        if edit.waitForExistence(timeout: 3) {
            edit.tap()
            Thread.sleep(forTimeInterval: 1)
        }
        let add = springboard.buttons["Add Widget"].firstMatch
        if add.waitForExistence(timeout: 3) {
            add.tap()
        } else {
            let plus = springboard.buttons["Add"].firstMatch
            guard plus.waitForExistence(timeout: 2) else {
                snapshot("widget-\(index)-b-no-add-button")
                return false
            }
            plus.tap()
        }
        Thread.sleep(forTimeInterval: 2)
        let search = springboard.searchFields.firstMatch
        if search.waitForExistence(timeout: 5) {
            search.tap()
            search.typeText("Course Table")
            Thread.sleep(forTimeInterval: 2)
        }
        snapshot("widget-\(index)-c-search")
        let cell = springboard.cells.containing(.staticText, identifier: "Course Table").firstMatch
        let text = springboard.staticTexts["Course Table"].firstMatch
        if cell.exists {
            cell.tap()
        } else if text.exists {
            text.tap()
        } else {
            return false
        }
        Thread.sleep(forTimeInterval: 2)
        return true
    }

    /// Edit Home Screen → Customize → `style` (Default, Dark, Clear, Tinted), then back to the Home Screen.
    @MainActor
    private func customize(choosing style: String) -> Bool {
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.62)).press(forDuration: 1.6)
        Thread.sleep(forTimeInterval: 1.5)
        let edit = springboard.buttons["Edit"].firstMatch
        if edit.waitForExistence(timeout: 3) {
            edit.tap()
            Thread.sleep(forTimeInterval: 1)
        }
        let customize = springboard.buttons["Customize"].firstMatch
        guard customize.waitForExistence(timeout: 3) else {
            snapshot("widget-2-a-no-customize")
            return false
        }
        customize.tap()
        Thread.sleep(forTimeInterval: 2)
        snapshot("widget-2-a-customize")
        let option = springboard.buttons[style].firstMatch
        guard option.waitForExistence(timeout: 3) else {
            snapshot("widget-2-b-no-\(style)")
            return false
        }
        option.tap()
        Thread.sleep(forTimeInterval: 2)
        snapshot("widget-2-b-\(style)")
        dismissGallery()
        return true
    }

    @MainActor
    private func dismissGallery() {
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.05)).tap()
        Thread.sleep(forTimeInterval: 1)
        goHome()
        goHome()
    }

    @MainActor
    private func goHome() {
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 1.5)
    }

    @MainActor
    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
