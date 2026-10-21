package io.github.hilbert0619.coursekit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

/** The shared cases in `spec/fixtures/course-colors.json`: the values both platforms use, and their contrast. */
class CourseColorTests {
    @Serializable
    class Fixture(val minimumContrast: Minimum, val colors: List<Entry>, val contrastCases: List<ContrastCase>) {
        @Serializable class Minimum(val solidWithWhite: Double, val soft: Double, val dark: Double)
        @Serializable
        class Entry(
            val name: String, val solid: List<Double>, val softFill: List<Double>, val softText: List<Double>,
            val darkFill: List<Double>, val darkText: List<Double>,
        )
        @Serializable class ContrastCase(val a: List<Double>, val b: List<Double>, val ratio: Double)
    }

    private val fixture = SharedFixtures.decode<Fixture>("course-colors.json")

    private fun rgb(values: List<Double>) = RGB(values[0], values[1], values[2])

    private fun close(a: RGB, b: RGB) =
        abs(a.red - b.red) < 0.0005 && abs(a.green - b.green) < 0.0005 && abs(a.blue - b.blue) < 0.0005

    @Test
    fun valuesMatchTheSharedCases() {
        assertEquals(fixture.colors.map { it.name }, CourseColors.all.map { it.name })
        for ((color, entry) in CourseColors.all.zip(fixture.colors)) {
            assertTrue(close(color.solid, rgb(entry.solid)), "${entry.name} solid")
            assertTrue(close(color.softFill, rgb(entry.softFill)), "${entry.name} soft fill")
            assertTrue(close(color.softText, rgb(entry.softText)), "${entry.name} soft text")
            assertTrue(close(color.darkFill, rgb(entry.darkFill)), "${entry.name} dark fill")
            assertTrue(close(color.darkText, rgb(entry.darkText)), "${entry.name} dark text")
        }
    }

    @Test
    fun everyBlockIsReadable() {
        val minimum = fixture.minimumContrast
        for (color in CourseColors.all) {
            val solid = color.block(CourseBlockStyle.SOLID, dark = false)
            assertTrue(solid.text.contrast(solid.fill) >= minimum.solidWithWhite, "${color.name} solid")
            assertEquals(solid, color.block(CourseBlockStyle.SOLID, dark = true), "solid is the same in dark mode")
            val soft = color.block(CourseBlockStyle.SOFT, dark = false)
            assertTrue(soft.text.contrast(soft.fill) >= minimum.soft, "${color.name} soft")
            val dark = color.block(CourseBlockStyle.SOFT, dark = true)
            assertTrue(dark.text.contrast(dark.fill) >= minimum.dark, "${color.name} soft in dark mode")
        }
    }

    @Test
    fun contrastRatios() {
        for (test in fixture.contrastCases) {
            val ratio = rgb(test.a).contrast(rgb(test.b))
            assertTrue(abs(ratio - test.ratio) < 0.01, "${test.a} on ${test.b}: $ratio")
            assertTrue(abs(rgb(test.b).contrast(rgb(test.a)) - ratio) < 1e-12, "symmetric")
        }
    }

    @Test
    fun indicesWrapAround() {
        assertEquals("coral", CourseColors.color(0).name)
        assertEquals("coral", CourseColors.color(10).name)
        assertEquals("slate", CourseColors.color(-1).name)
        assertEquals(CourseColors.color(3).name, CourseColors.color(23).name)
    }

    @Test
    fun coloursStayDistinct() {
        // No two soft fills (what the timetable shows most) look alike: at least 0.05 apart on some channel. Blue and
        // indigo needed a nudge in hue for this (docs/design-0.7.md).
        for ((i, a) in CourseColors.all.withIndex()) {
            for (b in CourseColors.all.drop(i + 1)) {
                val gap = maxOf(
                    abs(a.softFill.red - b.softFill.red), abs(a.softFill.green - b.softFill.green),
                    abs(a.softFill.blue - b.softFill.blue),
                )
                assertTrue(gap >= 0.05, "${a.name} and ${b.name} soft fills are too close ($gap)")
            }
        }
    }

    @Test
    fun blockStylesAreStoredByName() {
        assertEquals(CourseBlockStyle.SOFT, CourseBlockStyle.of("soft"))
        assertEquals(CourseBlockStyle.SOLID, CourseBlockStyle.of("solid"))
        assertEquals(null, CourseBlockStyle.of("Solid"))
    }
}
