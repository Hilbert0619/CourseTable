package io.github.hilbert0619.coursetable.appearance

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.hilbert0619.coursekit.CourseBlockStyle
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app's look (docs/design-0.7.md §5): glass on a wallpaper, the default since 0.7, or the classic warm paper. */
enum class AppStyle(val rawValue: String) {
    GLASS("glass"),
    CLASSIC("classic");

    companion object {
        fun of(rawValue: String?): AppStyle? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

/** Settings → Appearance (iOS `AppAppearance`, `AppBackground`, `BackgroundPhoto`, `TimetableAppearance`). Per device. */
data class AppearanceSettings(
    val style: AppStyle = AppStyle.GLASS,
    /** [io.github.hilbert0619.coursekit.BackgroundChoice.stored]; empty until something is chosen. */
    val background: String = "",
    val blockStyle: CourseBlockStyle = CourseBlockStyle.SOFT,
    /** How much of the paper is drawn over a background photo in the classic look. */
    val fade: Float = DEFAULT_FADE,
    /** The same in the glass look: less, as the glass needs the picture's colour. */
    val glassFade: Float = DEFAULT_GLASS_FADE,
    /** Course blocks' fill over a picture (the text and stripe stay solid). */
    val blockOpacity: Float = DEFAULT_BLOCK_OPACITY,
) {
    val isGlass: Boolean get() = style == AppStyle.GLASS

    /** The fade over a photo in the look in use. */
    val photoFade: Float get() = if (isGlass) glassFade else fade

    companion object {
        const val DEFAULT_FADE = 0.45f
        const val DEFAULT_GLASS_FADE = 0.2f
        const val DEFAULT_BLOCK_OPACITY = 0.9f
        val FADE_RANGE = 0f..0.8f
        val BLOCK_OPACITY_RANGE = 0.5f..1f
    }
}

/** The appearance settings, kept in `SharedPreferences` (`appearance`). */
class AppearancePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        val settings = transform(_settings.value).let {
            it.copy(
                fade = it.fade.coerceIn(AppearanceSettings.FADE_RANGE),
                glassFade = it.glassFade.coerceIn(AppearanceSettings.FADE_RANGE),
                blockOpacity = it.blockOpacity.coerceIn(AppearanceSettings.BLOCK_OPACITY_RANGE),
            )
        }
        prefs.edit()
            .putString(STYLE, settings.style.rawValue)
            .putString(BACKGROUND, settings.background)
            .putString(BLOCK_STYLE, settings.blockStyle.rawValue)
            .putFloat(FADE, settings.fade)
            .putFloat(GLASS_FADE, settings.glassFade)
            .putFloat(BLOCK_OPACITY, settings.blockOpacity)
            .apply()
        _settings.value = settings
    }

    private fun read() = AppearanceSettings(
        style = AppStyle.of(prefs.getString(STYLE, null)) ?: AppStyle.GLASS,
        background = prefs.getString(BACKGROUND, null).orEmpty(),
        glassFade = prefs.getFloat(GLASS_FADE, AppearanceSettings.DEFAULT_GLASS_FADE),
        blockStyle = CourseBlockStyle.of(prefs.getString(BLOCK_STYLE, null).orEmpty()) ?: CourseBlockStyle.SOFT,
        fade = prefs.getFloat(FADE, AppearanceSettings.DEFAULT_FADE),
        blockOpacity = prefs.getFloat(BLOCK_OPACITY, AppearanceSettings.DEFAULT_BLOCK_OPACITY),
    )

    private companion object {
        // The iOS keys.
        const val STYLE = "appearance.style"
        const val BACKGROUND = "appearance.background"
        const val GLASS_FADE = "appearance.glassFade"
        const val BLOCK_STYLE = "timetable.blockStyle"
        const val FADE = "timetable.backgroundFade"
        const val BLOCK_OPACITY = "timetable.blockOpacity"
    }
}

/**
 * The background photo (Settings → Appearance): one of the downloaded Focus wallpapers or your own, downscaled and
 * kept in the app's files (it isn't a cache: clearing it is up to you, in Storage). The file keeps its 0.6.0 name,
 * when it was the timetable's alone.
 */
class TimetableBackground(private val file: File, scope: CoroutineScope) {
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    val image: StateFlow<ImageBitmap?> = _image.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            if (file.exists()) _image.value = BitmapFactory.decodeFile(file.path)?.asImageBitmap()
        }
    }

    /** Uses the picture at [uri] (from the photo picker) behind the timetable. False if it couldn't be read. */
    suspend fun set(resolver: ContentResolver, uri: Uri): Boolean = save { decode(resolver, uri) }

    /** Uses a picture file (a downloaded Focus wallpaper) behind the timetable. */
    suspend fun set(picture: File): Boolean = save { decode(picture) }

    /** No photo behind the timetable. */
    fun clear() {
        file.delete()
        _image.value = null
    }

    /** Bytes on disk, for Settings → Storage. */
    val bytes: Long get() = if (file.exists()) file.length() else 0

    private suspend fun save(decode: () -> Bitmap?): Boolean = withContext(Dispatchers.IO) {
        val bitmap = runCatching(decode).onFailure { Log.w(TAG, "Reading the picture failed", it) }.getOrNull() ?: return@withContext false
        val resized = downscaled(bitmap)
        try {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.outputStream().use { resized.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Saving the timetable background failed", error)
        }
        _image.value = resized.asImageBitmap()
        true
    }

    /** Decodes [uri] the right way up (a camera photo's rotation is applied on Android 9+). */
    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                fit(info.size.width, info.size.height)?.let { (width, height) -> decoder.setTargetSize(width, height) }
            }
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }

    private fun decode(picture: File): Bitmap? = BitmapFactory.decodeFile(picture.path)

    companion object {
        private const val TAG = "TimetableBackground"

        /** The longest side kept: enough for any phone screen. */
        const val MAX_PIXELS = 2400

        fun create(context: Context, scope: CoroutineScope) = TimetableBackground(File(context.filesDir, "TimetableBackground.jpg"), scope)

        /** The size that fits [MAX_PIXELS], or null if the picture already does. */
        private fun fit(width: Int, height: Int): Pair<Int, Int>? {
            val longest = max(width, height)
            if (longest <= MAX_PIXELS || longest <= 0) return null
            val scale = MAX_PIXELS.toFloat() / longest
            return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
        }

        private fun downscaled(bitmap: Bitmap): Bitmap {
            val (width, height) = fit(bitmap.width, bitmap.height) ?: return bitmap
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
    }
}
