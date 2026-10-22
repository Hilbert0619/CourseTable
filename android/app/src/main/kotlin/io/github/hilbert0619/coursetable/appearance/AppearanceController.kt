package io.github.hilbert0619.coursetable.appearance

import android.content.Context
import android.net.Uri
import io.github.hilbert0619.coursekit.BackgroundChoice
import io.github.hilbert0619.coursekit.FocusWallpaper
import io.github.hilbert0619.coursetable.focus.WallpaperActions
import io.github.hilbert0619.coursetable.ui.components.AppearanceActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Settings → Appearance's changes: the settings, and the background photo. */
class AppearanceController(
    private val context: Context,
    val preferences: AppearancePreferences,
    val background: TimetableBackground,
    private val wallpapers: WallpaperActions,
    private val scope: CoroutineScope,
) : AppearanceActions {
    override fun update(transform: (AppearanceSettings) -> AppearanceSettings) = preferences.update(transform)

    override fun setPhoto(uri: Uri) {
        scope.launch { if (background.set(context.contentResolver, uri)) usePhoto() }
    }

    override fun setPhoto(wallpaper: FocusWallpaper) {
        val file = wallpapers.file(wallpaper) ?: return
        scope.launch { if (background.set(file)) usePhoto() }
    }

    /** A new photo becomes the background, as on iOS. */
    private fun usePhoto() = preferences.update { it.copy(background = BackgroundChoice.Photo.stored) }

    /** Without the photo, the background goes back to the default (a wallpaper in glass, the paper in classic). */
    override fun removePhoto() {
        background.clear()
        preferences.update { it.copy(background = "") }
    }

    override fun photoBytes(): Long = background.bytes
}
