package io.github.hilbert0619.coursekit

import kotlin.math.pow

/** An sRGB colour, each channel 0–1. */
data class RGB(val red: Double, val green: Double, val blue: Double) {
    /** WCAG 2 relative luminance. */
    val luminance: Double
        get() {
            fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
        }

    /** WCAG 2 contrast ratio with [other], 1–21. */
    fun contrast(other: RGB): Double {
        val light = maxOf(luminance, other.luminance)
        val dark = minOf(luminance, other.luminance)
        return (light + 0.05) / (dark + 0.05)
    }

    /** This colour drawn at [alpha] over [background]. */
    fun over(background: RGB, alpha: Double): RGB = RGB(
        red * alpha + background.red * (1 - alpha),
        green * alpha + background.green * (1 - alpha),
        blue * alpha + background.blue * (1 - alpha),
    )

    companion object {
        val WHITE = RGB(1.0, 1.0, 1.0)

        /** From 0xRRGGBB. */
        fun hex(value: Int): RGB =
            RGB(((value shr 16) and 0xFF) / 255.0, ((value shr 8) and 0xFF) / 255.0, (value and 0xFF) / 255.0)
    }
}

/** How course blocks are drawn on the timetable (Settings → Appearance). */
enum class CourseBlockStyle(val rawValue: String) {
    /** A light tint with dark text in the same hue (a deep tint with light text in dark mode). The default. */
    SOFT("soft"),

    /** The full colour with white text, as before 0.7. */
    SOLID("solid");

    companion object {
        fun of(rawValue: String): CourseBlockStyle? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

/** A block's fill and text colour. */
data class BlockColors(val fill: RGB, val text: RGB)

/**
 * One of the ten course colours (docs/design-0.7.md), in every style. Derived from the original hues in OKLCH:
 * [solid] is the hue darkened until white text reaches 3.5:1 (it is also the course's stripe and dot colour); the
 * soft fills and texts reach at least 7:1.
 */
data class CourseColor(
    val name: String,
    val solid: RGB,
    val softFill: RGB,
    val softText: RGB,
    val darkFill: RGB,
    val darkText: RGB,
) {
    /** A block's fill and text colour in [style], for light or dark appearance. */
    fun block(style: CourseBlockStyle, dark: Boolean): BlockColors = when (style) {
        CourseBlockStyle.SOLID -> BlockColors(solid, RGB.WHITE)
        CourseBlockStyle.SOFT -> if (dark) BlockColors(darkFill, darkText) else BlockColors(softFill, softText)
    }
}

object CourseColors {
    /** In `colorIndex` order: courses keep their colour across versions. */
    val all: List<CourseColor> = listOf(
        CourseColor("coral", RGB(0.890, 0.350, 0.330), RGB(1.000, 0.867, 0.853),
            RGB(0.499, 0.126, 0.126), RGB(0.317, 0.136, 0.125), RGB(0.997, 0.786, 0.764)),
        CourseColor("orange", RGB(0.819, 0.434, 0.005), RGB(0.996, 0.877, 0.794),
            RGB(0.431, 0.215, 0.000), RGB(0.302, 0.157, 0.030), RGB(0.989, 0.802, 0.671)),
        CourseColor("mustard", RGB(0.675, 0.511, 0.002), RGB(0.973, 0.895, 0.726),
            RGB(0.357, 0.266, 0.005), RGB(0.257, 0.187, 0.000), RGB(0.926, 0.835, 0.640)),
        CourseColor("green", RGB(0.240, 0.603, 0.325), RGB(0.799, 0.949, 0.818),
            RGB(0.005, 0.343, 0.129), RGB(0.084, 0.236, 0.118), RGB(0.727, 0.897, 0.749)),
        CourseColor("teal", RGB(0.108, 0.588, 0.568), RGB(0.773, 0.943, 0.929),
            RGB(0.015, 0.326, 0.314), RGB(0.039, 0.231, 0.222), RGB(0.700, 0.889, 0.874)),
        CourseColor("blue", RGB(0.220, 0.520, 0.860), RGB(0.808, 0.922, 0.996),
            RGB(0.000, 0.277, 0.541), RGB(0.089, 0.203, 0.335), RGB(0.744, 0.856, 0.995)),
        CourseColor("indigo", RGB(0.380, 0.400, 0.840), RGB(0.868, 0.901, 0.995),
            RGB(0.225, 0.234, 0.547), RGB(0.167, 0.182, 0.337), RGB(0.806, 0.834, 0.997)),
        CourseColor("purple", RGB(0.600, 0.380, 0.830), RGB(0.927, 0.877, 0.999),
            RGB(0.344, 0.189, 0.498), RGB(0.231, 0.160, 0.312), RGB(0.884, 0.803, 0.995)),
        CourseColor("pink", RGB(0.846, 0.348, 0.588), RGB(0.996, 0.860, 0.912),
            RGB(0.472, 0.126, 0.305), RGB(0.303, 0.134, 0.211), RGB(0.994, 0.773, 0.861)),
        CourseColor("slate", RGB(0.470, 0.520, 0.580), RGB(0.881, 0.905, 0.935),
            RGB(0.245, 0.285, 0.334), RGB(0.180, 0.201, 0.227), RGB(0.821, 0.848, 0.881)),
    )

    /** The colour for a course's `colorIndex` (any integer: it wraps around). */
    fun color(index: Int): CourseColor = all[index.mod(all.size)]
}
