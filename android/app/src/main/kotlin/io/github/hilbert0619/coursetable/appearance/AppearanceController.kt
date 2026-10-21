package io.github.hilbert0619.coursetable.appearance

import android.content.Context
import android.net.Uri
import io.github.hilbert0619.coursekit.FocusWallpaper
import io.github.hilbert0619.coursetable.focus.WallpaperActions
import io.github.hilbert0619.coursetable.ui.components.AppearanceActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Settings → Timetable Appearance's changes: the settings, and the photo behind the timetable. */
class AppearanceController(
    private val context: Context,
    val preferences: AppearancePreferences,
    val background: TimetableBackground,
    private val wallpapers: WallpaperActions,
    private val scope: CoroutineScope,
) : AppearanceActions {
    override fun update(transform: (AppearanceSettings) -> AppearanceSettings) = preferences.update(transform)

    override fun setPhoto(uri: Uri) {
        scope.launch { background.set(context.contentResolver, uri) }
    }

    override fun setPhoto(wallpaper: FocusWallpaper) {
        val file = wallpapers.file(wallpaper) ?: return
        scope.launch { background.set(file) }
    }

    override fun removePhoto() = background.clear()

    override fun photoBytes(): Long = background.bytes
}
