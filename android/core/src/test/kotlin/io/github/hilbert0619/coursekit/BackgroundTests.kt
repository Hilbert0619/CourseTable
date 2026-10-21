package io.github.hilbert0619.coursekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class BackgroundTests {
    companion object {
        /** The system's secondary text (#3C3C43 at 60 % in light mode, #EBEBF5 at 60 % in dark mode) and the accent. */
        val SECONDARY_LIGHT = RGB.hex(0x3C3C43)
        val SECONDARY_DARK = RGB.hex(0xEBEBF5)
        val ACCENT_LIGHT = RGB(0.345, 0.420, 0.918)
        val ACCENT_DARK = RGB(0.510, 0.580, 1.000)
    }

    /**
     * Step 5 of the glass recipe: large soft shapes, with text readable anywhere on them (titles, section headers and
     * buttons sit right on the wallpaper).
     */
    @ParameterizedTest
    @EnumSource(SoftWallpaper::class)
    fun readability(wallpaper: SoftWallpaper) {
        for (color in wallpaper.palette(dark = false).colors) {
            assertTrue(RGB(0.0, 0.0, 0.0).contrast(color) >= 10)
            assertTrue(SECONDARY_LIGHT.over(color, alpha = 0.6).contrast(color) >= 3)
            assertTrue(ACCENT_LIGHT.contrast(color) >= 3)
        }
        for (color in wallpaper.palette(dark = true).colors) {
            assertTrue(RGB.WHITE.contrast(color) >= 10)
            assertTrue(SECONDARY_DARK.over(color, alpha = 0.6).contrast(color) >= 3)
            assertTrue(ACCENT_DARK.contrast(color) >= 3)
        }
    }

    @ParameterizedTest
    @EnumSource(SoftWallpaper::class)
    fun fourLargeShapes(wallpaper: SoftWallpaper) {
        for (dark in listOf(false, true)) {
            val palette = wallpaper.palette(dark)
            assertEquals(4, palette.shapes.size)
            assertTrue(palette.shapes.all { it.width >= 1 && it.height >= 0.8 })
            assertEquals(5, palette.colors.toSet().size)
        }
    }

    @Test
    fun wallpapersDiffer() {
        val palettes = SoftWallpaper.entries.map { it.palette(dark = false) }
        assertEquals(SoftWallpaper.entries.size, palettes.toSet().size)
        assertEquals(RGB.hex(0xF7F1EA), SoftWallpaper.DAWN.palette(dark = false).base)
    }

    @Test
    fun storedChoices() {
        val choices = listOf(BackgroundChoice.Paper, BackgroundChoice.Photo) + SoftWallpaper.entries.map { BackgroundChoice.Wallpaper(it) }
        for (choice in choices) assertEquals(choice, BackgroundChoice.of(choice.stored))
        assertNull(BackgroundChoice.of(""))
        assertNull(BackgroundChoice.of("wallpaper.unknown"))
        assertEquals("wallpaper.sky", BackgroundChoice.Wallpaper(SoftWallpaper.SKY).stored)
    }

    @Test
    fun resolvedChoice() {
        val dawn = BackgroundChoice.Wallpaper(SoftWallpaper.DAWN)
        // Nothing chosen: 0.7.0's timetable photo, else the default wallpaper (glass) or the paper (classic).
        assertEquals(dawn, BackgroundChoice.resolve("", glass = true, hasPhoto = false))
        assertEquals(BackgroundChoice.Paper, BackgroundChoice.resolve("", glass = false, hasPhoto = false))
        assertEquals(BackgroundChoice.Photo, BackgroundChoice.resolve("", glass = true, hasPhoto = true))
        assertEquals(BackgroundChoice.Photo, BackgroundChoice.resolve("", glass = false, hasPhoto = true))
        // A choice holds in both looks.
        assertEquals(BackgroundChoice.Wallpaper(SoftWallpaper.EMBER), BackgroundChoice.resolve("wallpaper.ember", glass = false, hasPhoto = true))
        assertEquals(BackgroundChoice.Paper, BackgroundChoice.resolve("paper", glass = true, hasPhoto = true))
        // The photo was cleared in Storage.
        assertEquals(dawn, BackgroundChoice.resolve("photo", glass = true, hasPhoto = false))
        assertEquals(BackgroundChoice.Paper, BackgroundChoice.resolve("photo", glass = false, hasPhoto = false))
    }

    // The shared cases in spec/fixtures/backgrounds.json, which the Swift tests read too.

    @Serializable
    class Fixture(
        val defaultWallpaper: String,
        val wallpapers: List<Wallpaper>,
        val readability: Floors,
        val stored: List<String>,
        val invalid: List<String>,
        val resolve: List<ResolveCase>,
    ) {
        @Serializable class Shape(val color: String, val x: Double, val y: Double, val width: Double, val height: Double)
        @Serializable class Palette(val base: String, val shapes: List<Shape>)
        @Serializable class Wallpaper(val id: String, val light: Palette, val dark: Palette)
        @Serializable
        class Floors(
            val text: Double, val secondary: Double, val accent: Double, val secondaryAlpha: Double,
            val light: Colors, val dark: Colors,
        ) {
            @Serializable class Colors(val text: List<Double>, val secondary: String, val accent: List<Double>)
        }
        @Serializable
        class ResolveCase(val about: String, val stored: String, val glass: Boolean, val hasPhoto: Boolean, val expected: String)
    }

    private fun hex(text: String) = RGB.hex(text.removePrefix("#").toInt(16))

    private fun rgb(values: List<Double>) = RGB(values[0], values[1], values[2])

    @Test
    fun sharedCases() {
        val fixture = SharedFixtures.decode<Fixture>("backgrounds.json")
        assertEquals(SoftWallpaper.entries.map { it.id }, fixture.wallpapers.map { it.id })
        assertEquals(BackgroundChoice.DEFAULT_WALLPAPER.id, fixture.defaultWallpaper)
        for (entry in fixture.wallpapers) {
            val wallpaper = assertNotNull(SoftWallpaper.of(entry.id))
            for ((dark, expected) in listOf(false to entry.light, true to entry.dark)) {
                val palette = wallpaper.palette(dark)
                assertEquals(hex(expected.base), palette.base, "${entry.id} base, dark $dark")
                assertEquals(
                    expected.shapes.map { SoftWallpaper.Shape(hex(it.color), it.x, it.y, it.width, it.height) },
                    palette.shapes, "${entry.id} shapes, dark $dark",
                )
            }
        }
        val floors = fixture.readability
        assertEquals(SECONDARY_LIGHT, hex(floors.light.secondary))
        assertEquals(SECONDARY_DARK, hex(floors.dark.secondary))
        assertEquals(ACCENT_LIGHT, rgb(floors.light.accent))
        assertEquals(ACCENT_DARK, rgb(floors.dark.accent))
        for (wallpaper in SoftWallpaper.entries) {
            for ((dark, colors) in listOf(false to floors.light, true to floors.dark)) {
                for (color in wallpaper.palette(dark).colors) {
                    assertTrue(rgb(colors.text).contrast(color) >= floors.text, "${wallpaper.id} text")
                    assertTrue(hex(colors.secondary).over(color, floors.secondaryAlpha).contrast(color) >= floors.secondary)
                    assertTrue(rgb(colors.accent).contrast(color) >= floors.accent, "${wallpaper.id} accent")
                }
            }
        }
        for (text in fixture.stored) assertEquals(text, BackgroundChoice.of(text)?.stored)
        assertEquals(SoftWallpaper.entries.size + 2, fixture.stored.size)
        for (text in fixture.invalid) assertNull(BackgroundChoice.of(text), "\"$text\"")
        for (test in fixture.resolve) {
            assertEquals(test.expected, BackgroundChoice.resolve(test.stored, test.glass, test.hasPhoto).stored, test.about)
        }
    }
}
