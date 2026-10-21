package io.github.hilbert0619.coursetable.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.hilbert0619.coursekit.CourseBlockStyle
import io.github.hilbert0619.coursekit.CourseColors
import io.github.hilbert0619.coursekit.RGB

/**
 * Course colours in Compose, as on iOS (Shared/CoursePalette.swift). The values, their styles and contrast checks are
 * the core's [CourseColors] (docs/design-0.7.md §2).
 */
object CoursePalette {
    /** Each course's own colour, for stripes, dots and solid blocks (white text reads on it). */
    val colors: List<Color> = CourseColors.all.map { it.solid.color }

    fun color(index: Int): Color = CourseColors.color(index).solid.color

    /** A timetable block's fill and text in [style], for light or [dark] appearance. */
    fun block(index: Int, style: CourseBlockStyle, dark: Boolean): BlockColor {
        val colors = CourseColors.color(index).block(style, dark)
        return BlockColor(colors.fill.color, colors.text.color)
    }

    /** [block] in the current appearance. */
    @Composable
    fun block(index: Int, style: CourseBlockStyle): BlockColor = block(index, style, isDarkTheme())
}

data class BlockColor(val fill: Color, val text: Color)

val RGB.color: Color get() = Color(red.toFloat(), green.toFloat(), blue.toFloat())
