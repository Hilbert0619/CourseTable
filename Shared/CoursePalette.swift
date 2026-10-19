import SwiftUI
import UIKit
import CourseKit

/// Course colours in SwiftUI. The values, their styles and contrast checks are CourseKit's `CourseColors`
/// (docs/design-0.7.md).
enum CoursePalette {
    /// Each course's own colour, for stripes, dots and solid blocks (white text reads on it).
    static let colors: [Color] = CourseColors.all.map { Color(rgb: $0.solid) }

    static func color(_ index: Int) -> Color {
        Color(rgb: CourseColors.color(index).solid)
    }

    /// A timetable block's fill and text in `style`; they follow light and dark appearance.
    static func block(_ index: Int, style: CourseBlockStyle) -> (fill: Color, text: Color) {
        let color = CourseColors.color(index)
        let light = color.block(style, dark: false)
        let dark = color.block(style, dark: true)
        return (Color(light: light.fill, dark: dark.fill), Color(light: light.text, dark: dark.text))
    }
}

extension Color {
    init(rgb: RGB) {
        self.init(red: rgb.red, green: rgb.green, blue: rgb.blue)
    }

    /// A colour that changes with the appearance.
    init(light: RGB, dark: RGB) {
        self.init(uiColor: UIColor { traits in
            let rgb = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: rgb.red, green: rgb.green, blue: rgb.blue, alpha: 1)
        })
    }
}

extension Course {
    var color: Color { CoursePalette.color(colorIndex) }
}

extension CourseTemplate {
    var color: Color { CoursePalette.color(colorIndex) }
}
