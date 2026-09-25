import SwiftUI
import CourseKit

/// Course block colours. Mid-tone so white text stays readable in light and dark mode.
enum CoursePalette {
    static let colors: [Color] = [
        Color(red: 0.89, green: 0.35, blue: 0.33),  // coral
        Color(red: 0.93, green: 0.53, blue: 0.18),  // orange
        Color(red: 0.76, green: 0.58, blue: 0.05),  // mustard
        Color(red: 0.28, green: 0.64, blue: 0.36),  // green
        Color(red: 0.13, green: 0.60, blue: 0.58),  // teal
        Color(red: 0.22, green: 0.52, blue: 0.86),  // blue
        Color(red: 0.38, green: 0.40, blue: 0.84),  // indigo
        Color(red: 0.60, green: 0.38, blue: 0.83),  // purple
        Color(red: 0.86, green: 0.36, blue: 0.60),  // pink
        Color(red: 0.47, green: 0.52, blue: 0.58),  // slate
    ]

    static func color(_ index: Int) -> Color {
        colors[((index % colors.count) + colors.count) % colors.count]
    }
}

extension Course {
    var color: Color { CoursePalette.color(colorIndex) }
}

extension CourseTemplate {
    var color: Color { CoursePalette.color(colorIndex) }
}
