package io.github.hilbert0619.coursetable.ui.theme

import androidx.compose.ui.graphics.Color

/** Course block colours, as on iOS (Shared/CoursePalette.swift). Mid-tone so white text stays readable in light and dark. */
object CoursePalette {
    val colors: List<Color> = listOf(
        Color(0.89f, 0.35f, 0.33f), // coral
        Color(0.93f, 0.53f, 0.18f), // orange
        Color(0.76f, 0.58f, 0.05f), // mustard
        Color(0.28f, 0.64f, 0.36f), // green
        Color(0.13f, 0.60f, 0.58f), // teal
        Color(0.22f, 0.52f, 0.86f), // blue
        Color(0.38f, 0.40f, 0.84f), // indigo
        Color(0.60f, 0.38f, 0.83f), // purple
        Color(0.86f, 0.36f, 0.60f), // pink
        Color(0.47f, 0.52f, 0.58f), // slate
    )

    fun color(index: Int): Color = colors[index.mod(colors.size)]
}
