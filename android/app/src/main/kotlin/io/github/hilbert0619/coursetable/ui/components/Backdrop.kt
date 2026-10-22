package io.github.hilbert0619.coursetable.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import io.github.hilbert0619.coursekit.BackgroundChoice
import io.github.hilbert0619.coursekit.FocusWallpaper
import io.github.hilbert0619.coursekit.SoftWallpaper
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.appearance.AppearanceSettings
import io.github.hilbert0619.coursetable.ui.theme.color
import io.github.hilbert0619.coursetable.ui.theme.isDarkTheme

/** The look the screens read: the appearance settings and the background photo, if there is one. */
data class AppearanceState(val settings: AppearanceSettings = AppearanceSettings(), val photo: ImageBitmap? = null) {
    /** The background in effect (a cleared photo can't be shown). */
    val choice: BackgroundChoice get() = BackgroundChoice.resolve(settings.background, settings.isGlass, hasPhoto = photo != null)
}

/** What Settings → Appearance can change. The app does it; screenshots pass [None]. */
interface AppearanceActions {
    fun update(transform: (AppearanceSettings) -> AppearanceSettings)

    /** A picture from the photo picker as the background photo (and the background). */
    fun setPhoto(uri: Uri)

    /** A downloaded Focus wallpaper as the background photo (and the background). */
    fun setPhoto(wallpaper: FocusWallpaper)

    fun removePhoto()

    /** The photo's bytes on disk, for Settings → Storage. */
    fun photoBytes(): Long = 0

    object None : AppearanceActions {
        override fun update(transform: (AppearanceSettings) -> AppearanceSettings) = Unit
        override fun setPhoto(uri: Uri) = Unit
        override fun setPhoto(wallpaper: FocusWallpaper) = Unit
        override fun removePhoto() = Unit
    }
}

val LocalAppearance = compositionLocalOf { AppearanceState() }
val LocalAppearanceActions = staticCompositionLocalOf<AppearanceActions> { AppearanceActions.None }

/**
 * Behind the main tabs in the glass look, and behind the timetable in either look (iOS `AppBackdrop`): the chosen
 * wallpaper, the photo with some paper over it (Fade), or the paper alone. [choice] overrides the one in effect (the
 * previews in Settings → Appearance).
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier, choice: BackgroundChoice? = null) {
    val appearance = LocalAppearance.current
    val paper = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(paper)) {
        when (val shown = choice ?: appearance.choice) {
            BackgroundChoice.Paper -> Unit
            is BackgroundChoice.Wallpaper -> SoftWallpaperView(shown.wallpaper)
            BackgroundChoice.Photo -> {
                val photo = appearance.photo ?: return@Box
                Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(paper.copy(alpha = appearance.settings.photoFade)))
            }
        }
    }
}

/** How far a shape's colour reaches beyond its own size (iOS `SoftWallpaperView.spread`). */
private const val SPREAD = 1.56f

/** A shape's opacity from its centre (0) to the gradient's edge (1): close to a Gaussian blur's falloff. */
private val FALLOFF = listOf(0f to 1f, 0.28f to 1f, 0.46f to 0.84f, 0.64f to 0.5f, 0.82f to 0.16f, 1f to 0f)

/**
 * A built-in wallpaper (iOS `SoftWallpaperView`): large shapes of colour on the base, melting into each other. Each
 * shape is an elliptical gradient that fades the way the shape blurred by 16 % of the width would: no blur pass, so
 * it's cheap and looks the same on every phone. Pale in light mode, deep in dark mode.
 */
@Composable
fun SoftWallpaperView(wallpaper: SoftWallpaper, modifier: Modifier = Modifier) {
    val palette = wallpaper.palette(dark = isDarkTheme())
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        drawRect(palette.base.color)
        for (shape in palette.shapes) {
            val color = shape.color.color
            val width = size.width * shape.width.toFloat() * SPREAD
            val height = size.width * shape.height.toFloat() * SPREAD
            val center = Offset(size.width * shape.x.toFloat(), size.height * shape.y.toFloat())
            val radius = height / 2
            val stops = FALLOFF.map { (location, alpha) -> location to color.copy(alpha = alpha) }.toTypedArray()
            val brush = Brush.radialGradient(*stops, center = center, radius = radius)
            // A circle stretched sideways into the shape's ellipse.
            withTransform({ scale(scaleX = width / height, scaleY = 1f, pivot = center) }) {
                drawCircle(brush, radius = radius, center = center)
            }
        }
    }
}

/** "Dawn", "Lagoon", … */
@Composable
fun SoftWallpaper.title(): String = stringResource(
    when (this) {
        SoftWallpaper.DAWN -> R.string.wallpaper_dawn
        SoftWallpaper.LAGOON -> R.string.wallpaper_lagoon
        SoftWallpaper.MEADOW -> R.string.wallpaper_meadow
        SoftWallpaper.LAVENDER -> R.string.wallpaper_lavender
        SoftWallpaper.SKY -> R.string.wallpaper_sky
        SoftWallpaper.EMBER -> R.string.wallpaper_ember
    },
)
