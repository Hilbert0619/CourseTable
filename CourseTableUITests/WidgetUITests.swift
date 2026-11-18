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

        // Each widget in turn: the gallery's pages are its sizes.
        for (index, name) in ["Class Schedule", "To-Dos", "Focus"].enumerated() {
            guard openGallery(index: index) else { break }
            // The app's page lists its widgets; pick this one if the gallery shows a list.
            if springboard.staticTexts[name].firstMatch.waitForExistence(timeout: 2) {
                snapshot("widget-\(index)-\(name)-list")
            }
            for page in 0..<3 {
                snapshot("widget-\(index)-\(name)-size-\(page)")
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
        // Further widgets of the app are further pages of kinds, or rows to tap.
        for _ in 0..<index {
            springboard.swipeUp()
        }
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
