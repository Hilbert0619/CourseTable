package io.github.hilbert0619.coursetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Material 3 "fidelity" schemes from the iOS accent colour #586BEA (Assets.xcassets/AccentColor), so the app looks
// the same on any phone (no dynamic colour). Since 0.6 (iOS 0.7, docs/design-0.7.md §1) the background and surfaces
// are the icon's warm paper #F4F2EE in light mode and black in dark mode, with cards (`surfaceContainerLow`, the
// sections) in the system's row colours: white, and #1C1C1E in dark mode. The containers above them are warm greys.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF3A4DCD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5568E7),
    onPrimaryContainer = Color(0xFFFFFBFF),
    inversePrimary = Color(0xFFBBC3FF),
    secondary = Color(0xFF545B8C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBFC6FE),
    onSecondaryContainer = Color(0xFF4A5181),
    tertiary = Color(0xFF904900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB55D00),
    onTertiaryContainer = Color(0xFFFFFBFF),
    background = Color(0xFFF4F2EE),
    onBackground = Color(0xFF1A1B23),
    surface = Color(0xFFF4F2EE),
    onSurface = Color(0xFF1A1B23),
    surfaceVariant = Color(0xFFE1E1F3),
    onSurfaceVariant = Color(0xFF454654),
    surfaceTint = Color(0xFF3D50CF),
    inverseSurface = Color(0xFF2F3038),
    inverseOnSurface = Color(0xFFF1EFFB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF757685),
    outlineVariant = Color(0xFFC5C5D6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4F2EE),
    surfaceDim = Color(0xFFDBD8D3),
    surfaceContainer = Color(0xFFEDEAE5),
    surfaceContainerHigh = Color(0xFFE7E4DF),
    surfaceContainerHighest = Color(0xFFE1DED9),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    onPrimary = Color(0xFF001B97),
    primaryContainer = Color(0xFF7586FF),
    onPrimaryContainer = Color(0xFF000225),
    inversePrimary = Color(0xFF3D50CF),
    secondary = Color(0xFFBCC3FB),
    onSecondary = Color(0xFF262C5B),
    secondaryContainer = Color(0xFF3C4373),
    onSecondaryContainer = Color(0xFFABB2E9),
    tertiary = Color(0xFFFFB783),
    onTertiary = Color(0xFF4F2500),
    tertiaryContainer = Color(0xFFD97721),
    onTertiaryContainer = Color(0xFF0F0400),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE3E1EC),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE3E1EC),
    surfaceVariant = Color(0xFF454654),
    onSurfaceVariant = Color(0xFFC5C5D6),
    surfaceTint = Color(0xFFBBC3FF),
    inverseSurface = Color(0xFFE3E1EC),
    inverseOnSurface = Color(0xFF2F3038),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8F8FA0),
    outlineVariant = Color(0xFF454654),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2C2C2E),
    surfaceDim = Color(0xFF000000),
    surfaceContainer = Color(0xFF161618),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainerLowest = Color(0xFF000000),
)

/** The graphite ink of the icon's Z (`Theme.ink` on iOS), for hero titles. */
val InkLight = Color(0xFF2B2E33)
val InkDark = Color(0xFFECECEF)

/** Whether the theme in effect is the dark one (also inside a part drawn dark on a photo). */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun StudyHourTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
