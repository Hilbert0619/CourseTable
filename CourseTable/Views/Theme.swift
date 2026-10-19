import SwiftUI
import CourseKit

/// The app's own colours, taken from its icon (docs/design-0.7.md): the icon's warm paper white behind lists in
/// light mode (the system's black in dark mode) and the graphite ink of its Z. Cards keep the system's grouped-row
/// colour; the accent stays the asset catalog's AccentColor (4.5:1 on white already).
enum Theme {
    static let paper = Color(light: RGB(0.957, 0.949, 0.933), dark: RGB(0, 0, 0))
    static let ink = Color(light: RGB(0.169, 0.180, 0.200), dark: RGB(0.925, 0.925, 0.937))
    /// A row or card on the paper.
    static let card = Color(uiColor: .secondarySystemGroupedBackground)

    /// About the system's grouped-row corners (larger since iOS 26), for cards that draw their own background so
    /// they match the rows around them. (A List row background isn't redrawn when what it shows changes.)
    static var cardRadius: CGFloat {
        if #available(iOS 26, *) {
            return 26
        }
        return 10
    }
}

/// Timetable Appearance settings (Settings → Timetable Appearance).
enum TimetableAppearance {
    static let blockStyleKey = "timetable.blockStyle"
    static let defaultBlockStyle = CourseBlockStyle.soft
}

extension View {
    /// The warm paper behind a List or Form, instead of the system's cool grey.
    func paperBackground() -> some View {
        scrollContentBackground(.hidden)
            .background { Theme.paper.ignoresSafeArea() }
    }
}
