package io.github.hilbert0619.coursetable.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.hilbert0619.coursekit.FocusWallpaper
import io.github.hilbert0619.coursetable.appearance.AppearanceSettings

/** The look the screens read: the appearance settings and the photo behind the timetable, if there is one. */
data class AppearanceState(val settings: AppearanceSettings = AppearanceSettings(), val photo: ImageBitmap? = null)

/** What Settings → Timetable Appearance can change. The app does it; screenshots pass [None]. */
interface AppearanceActions {
    fun update(transform: (AppearanceSettings) -> AppearanceSettings)

    /** A picture from the photo picker behind the timetable. */
    fun setPhoto(uri: Uri)

    /** A downloaded Focus wallpaper behind the timetable. */
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

/** Behind the timetable: the paper, or the chosen photo faded towards it (iOS `TimetableBackdrop`). */
@Composable
fun TimetableBackdrop(modifier: Modifier = Modifier) {
    val appearance = LocalAppearance.current
    val paper = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(paper)) {
        val photo = appearance.photo ?: return@Box
        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(paper.copy(alpha = appearance.settings.fade)))
    }
}
