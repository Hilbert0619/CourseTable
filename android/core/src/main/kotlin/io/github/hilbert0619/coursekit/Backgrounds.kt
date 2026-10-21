package io.github.hilbert0619.coursekit

/**
 * A built-in wallpaper for the glass look (docs/design-0.7.md §5): four large, soft shapes of colour, blurred into
 * each other. Pale in light mode and deep in dark mode, so text on them stays readable: black and white text reach
 * 10:1 on every colour, the system's secondary text and the accent colour 3:1 (`readability` in the tests).
 */
enum class SoftWallpaper(val id: String, private val layout: Int, private val light: IntArray, private val dark: IntArray) {
    DAWN("dawn", 0, intArrayOf(0xF7F1EA, 0xFFD2B8, 0xF9CFDD, 0xE0D8F8, 0xFFE6B3), intArrayOf(0x15111A, 0x4A1F2C, 0x3B1D3D, 0x232456, 0x47321A)),
    LAGOON("lagoon", 1, intArrayOf(0xEDF6F6, 0xAEE8E2, 0xC9E0F9, 0xCFF1DC, 0xE8F5CB), intArrayOf(0x071518, 0x0B4049, 0x0E2F57, 0x0B4436, 0x16351F)),
    MEADOW("meadow", 2, intArrayOf(0xF3F7EC, 0xD0EDBC, 0xF5E8AE, 0xBDE6D3, 0xEAF2C2), intArrayOf(0x0C130B, 0x21431F, 0x3E3A12, 0x123E36, 0x2A3A15)),
    LAVENDER("lavender", 0, intArrayOf(0xF5F1FA, 0xE3D7F8, 0xF7D0E7, 0xD0DAF8, 0xEEE0F8), intArrayOf(0x110D19, 0x35215A, 0x4A1D44, 0x1C2A59, 0x2E1D48)),
    SKY("sky", 1, intArrayOf(0xEFF5FB, 0xC6DCF9, 0xDAD8F8, 0xC4ECF5, 0xE1EBFB), intArrayOf(0x0A0F1C, 0x172E5C, 0x2B245C, 0x0C3B4E, 0x182448)),
    EMBER("ember", 2, intArrayOf(0xFBF3EC, 0xFFD0B2, 0xFFE3AC, 0xFBD2CD, 0xFFEAD3), intArrayOf(0x150B08, 0x56250F, 0x4E340C, 0x4C1822, 0x3A210E));

    /** One shape: its centre as fractions of the screen's width and height, its size as fractions of the width. */
    data class Shape(val color: RGB, val x: Double, val y: Double, val width: Double, val height: Double)

    data class Palette(val base: RGB, val shapes: List<Shape>) {
        /** The base and every shape's colour. */
        val colors: List<RGB> get() = listOf(base) + shapes.map { it.color }
    }

    fun palette(dark: Boolean): Palette {
        val colors = (if (dark) this.dark else light).map(RGB::hex)
        val shapes = LAYOUTS[layout].zip(colors.drop(1)) { place, color -> Shape(color, place[0], place[1], place[2], place[3]) }
        return Palette(colors[0], shapes)
    }

    companion object {
        /** Three arrangements, so the wallpapers differ in more than colour: (x, y, width, height). */
        private val LAYOUTS: List<List<DoubleArray>> = listOf(
            listOf(doubleArrayOf(0.10, 0.10, 1.15, 0.95), doubleArrayOf(0.95, 0.38, 1.05, 0.95), doubleArrayOf(0.15, 0.68, 1.15, 1.0), doubleArrayOf(0.90, 0.95, 1.15, 0.9)),
            listOf(doubleArrayOf(0.85, 0.08, 1.2, 0.9), doubleArrayOf(0.05, 0.36, 1.1, 1.0), doubleArrayOf(0.90, 0.66, 1.1, 0.95), doubleArrayOf(0.20, 0.97, 1.2, 0.9)),
            listOf(doubleArrayOf(0.50, 0.04, 1.4, 0.8), doubleArrayOf(0.00, 0.50, 1.0, 1.1), doubleArrayOf(1.00, 0.55, 1.0, 1.1), doubleArrayOf(0.50, 1.00, 1.4, 0.8)),
        )

        fun of(id: String): SoftWallpaper? = entries.firstOrNull { it.id == id }
    }
}

/** What's behind the glass and the timetable (Settings → Appearance), kept as a string in the app's settings. */
sealed interface BackgroundChoice {
    /** The warm paper, as in 0.7.0. */
    data object Paper : BackgroundChoice

    data class Wallpaper(val wallpaper: SoftWallpaper) : BackgroundChoice

    /** Your photo, or a Focus wallpaper you picked (one file on the phone). */
    data object Photo : BackgroundChoice

    /** "paper", "wallpaper.dawn", "photo". */
    val stored: String
        get() = when (this) {
            Paper -> "paper"
            is Wallpaper -> "wallpaper.${wallpaper.id}"
            Photo -> "photo"
        }

    companion object {
        val DEFAULT_WALLPAPER = SoftWallpaper.DAWN

        /** The choice kept as [stored]; null for anything else (nothing chosen yet). */
        fun of(stored: String): BackgroundChoice? = when (stored) {
            "paper" -> Paper
            "photo" -> Photo
            else -> if (stored.startsWith("wallpaper.")) SoftWallpaper.of(stored.removePrefix("wallpaper."))?.let(::Wallpaper) else null
        }

        /**
         * The background in effect. What you chose, as long as it can be shown (a photo you cleared in Storage can't).
         * Before any choice: a photo you put behind the timetable in 0.7.0, else the default wallpaper in the glass
         * look and the paper in the classic one.
         */
        fun resolve(stored: String, glass: Boolean, hasPhoto: Boolean): BackgroundChoice {
            val choice = of(stored)
            if (choice != null && (choice != Photo || hasPhoto)) return choice
            if (hasPhoto) return Photo
            return if (glass) Wallpaper(DEFAULT_WALLPAPER) else Paper
        }
    }
}
