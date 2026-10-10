package io.github.hilbert0619.coursetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Material 3 "fidelity" schemes from the iOS accent colour #586BEA (Assets.xcassets/AccentColor), so the app looks
// the same on any phone (no dynamic colour).
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
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1A1B23),
    surface = Color(0xFFFBF8FF),
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
    surfaceBright = Color(0xFFFBF8FF),
    surfaceDim = Color(0xFFDAD9E4),
    surfaceContainer = Color(0xFFEEECF8),
    surfaceContainerHigh = Color(0xFFE9E7F2),
    surfaceContainerHighest = Color(0xFFE3E1EC),
    surfaceContainerLow = Color(0xFFF4F2FD),
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
    background = Color(0xFF12131A),
    onBackground = Color(0xFFE3E1EC),
    surface = Color(0xFF12131A),
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
    surfaceBright = Color(0xFF383841),
    surfaceDim = Color(0xFF12131A),
    surfaceContainer = Color(0xFF1E1F27),
    surfaceContainerHigh = Color(0xFF292931),
    surfaceContainerHighest = Color(0xFF34343C),
    surfaceContainerLow = Color(0xFF1A1B23),
    surfaceContainerLowest = Color(0xFF0D0E15),
)

@Composable
fun StudyHourTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
