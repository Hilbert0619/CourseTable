@file:OptIn(ExperimentalHazeApi::class)

package io.github.hilbert0619.coursetable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import io.github.hilbert0619.coursetable.ui.theme.isDarkTheme

/*
 * The glass look (docs/design-0.7.md §5, iOS `Views/Glass.swift`). The owner's recipe: (1) blur what's behind and raise
 * its saturation, (2) a 10 % white layer, (3) a 1-pixel bright edge, (4) refraction at the edges, (5) large soft shapes
 * behind. Haze's Glass (`hazeGlass`) does (1) and (4) on Android 13+ (blur, saturation, refraction, specular light),
 * with a tint and a soft rim before; this file adds (2) and (3). The wallpaper (5) is drawn once behind the main tabs
 * by `RootScreen` and captured with `hazeSource`.
 */

/** The wallpaper behind the current tab, captured for the glass, when `RootScreen` draws one. */
val LocalTabBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** The glass the current screen draws with; null keeps the paper (the classic look, pushed screens, dialogs). */
val LocalGlass = staticCompositionLocalOf<HazeState?> { null }

/** A main tab's own content in glass, when the glass look is on and a wallpaper is behind it. */
@Composable
fun GlassScope(content: @Composable () -> Unit) {
    val backdrop = LocalTabBackdrop.current
    val glass = if (LocalAppearance.current.settings.isGlass) backdrop else null
    CompositionLocalProvider(LocalGlass provides glass, content = content)
}

/** Content that keeps the paper, inside a glass screen (dialogs, pushed screens). */
@Composable
fun PaperScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlass provides null, LocalTabBackdrop provides null, content = content)
}

/** A screen's own background: transparent over the tab's wallpaper, else the paper. */
@Composable
fun screenBackground(): Color = if (LocalTabBackdrop.current != null) Color.Transparent else MaterialTheme.colorScheme.background

/** One physical pixel. */
@Composable
private fun hairline(): Dp = with(LocalDensity.current) { 1f.toDp() }

/** Bright at the top left, where the light falls, fainter round the rest (step 3). */
@Composable
private fun edgeBrush(): Brush {
    val strength = if (isDarkTheme()) 0.55f else 1f
    return Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.8f * strength), Color.White.copy(alpha = 0.15f * strength), Color.White.copy(alpha = 0.4f * strength)),
    )
}

/**
 * Glass behind this element: the wallpaper blurred and bent at the edges (Haze's Glass), [tint] mixed in, the 10 %
 * white layer and the bright edge. Without glass (the classic look): [classic] as a plain fill, if given.
 */
@Composable
fun Modifier.glass(shape: RoundedCornerShape, tint: Color? = null, classic: Color? = null): Modifier {
    val haze = LocalGlass.current
    if (haze == null) return if (classic != null) clip(shape).background(classic) else this
    val glassShape = shape
    val glassTint = tint ?: Color.Transparent
    val style = remember(glassShape, glassTint) {
        GlassStyle.regular.then {
            shape(glassShape)
            tint(glassTint)
        }
    }
    return hazeGlass(input = HazeInput.Sources(haze), style = style)
        .background(Color.White.copy(alpha = 0.10f), shape)
        .border(hairline(), edgeBrush(), shape)
}

/** A card of glass ([tint]: a colour mixed in), or the row colour (or [tint]) on the paper. */
@Composable
fun Modifier.glassCard(tint: Color? = null, shape: RoundedCornerShape = CardShape): Modifier =
    glass(shape, tint?.copy(alpha = tint.alpha * 0.75f), classic = tint ?: MaterialTheme.colorScheme.surfaceContainerLow)

/** The rounded corners of cards and sections. */
val CardShape = RoundedCornerShape(16.dp)

/**
 * Frosted glass behind list rows (iOS `FrostedFill`): the wallpaper blurred with a veil of the paper and the 10 % white
 * layer; [thick] for small text on a picture (the timetable's days and times). Nothing without glass.
 */
@Composable
fun Modifier.frosted(shape: Shape, thick: Boolean = false): Modifier {
    val haze = LocalGlass.current ?: return this
    val veil = MaterialTheme.colorScheme.background.copy(alpha = if (thick) 0.62f else 0.42f)
    val style = remember(veil, thick) {
        HazeBlurStyle {
            blurRadius(if (thick) 30.dp else 22.dp)
            colorEffects(listOf(HazeColorEffect.tint(veil)))
            fallbackColorEffect(HazeColorEffect.tint(veil.copy(alpha = 0.88f)))
        }
    }
    return clip(shape).hazeBlur(input = HazeInput.Sources(haze), style = style).background(Color.White.copy(alpha = 0.10f))
}

/**
 * A button: of glass in the glass look ([prominent]: the screen's main action, tinted in the accent colour), a filled
 * ([prominent]) or outlined Material button on the paper. [color] colours an outlined button's content (Stop in red).
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    color: Color? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalGlass.current == null) {
        if (prominent) {
            Button(onClick, modifier, enabled = enabled, contentPadding = contentPadding, content = content)
        } else {
            OutlinedButton(
                onClick, modifier, enabled = enabled, contentPadding = contentPadding,
                colors = if (color != null) ButtonDefaults.outlinedButtonColors(contentColor = color) else ButtonDefaults.outlinedButtonColors(),
                content = content,
            )
        }
        return
    }
    val shape = RoundedCornerShape(50)
    val tint = if (prominent) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else null
    val contentColor = when {
        prominent -> Color.White
        color != null -> color
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier.defaultMinSize(minWidth = 58.dp, minHeight = 40.dp)
            .glass(shape, tint)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else 0.38f)) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        }
    }
}

/** An icon button: a circle of glass in the glass look (the day arrows), a plain icon button on the paper. */
@Composable
fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalGlass.current == null) {
        IconButton(onClick = onClick, modifier = modifier, content = content)
        return
    }
    Box(
        modifier.padding(4.dp).size(40.dp).glass(CircleShape).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}
