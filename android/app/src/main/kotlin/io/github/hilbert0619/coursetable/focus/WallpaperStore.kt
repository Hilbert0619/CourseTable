package io.github.hilbert0619.coursetable.focus

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.hilbert0619.coursekit.FocusWallpaper
import io.github.hilbert0619.coursekit.FocusWallpaperCache
import io.github.hilbert0619.coursekit.FocusWallpaperCatalog
import io.github.hilbert0619.coursekit.FocusWallpaperPicker
import io.github.hilbert0619.coursetable.words.BackendConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The wallpaper on the countdown. [image] is null while it downloads: its average colour shows meanwhile. */
data class ShownWallpaper(val wallpaper: FocusWallpaper, val image: ImageBitmap?)

/** What the Focus tab shows of the wallpapers. */
data class WallpaperState(
    /** Focus setup → Wallpaper. */
    val enabled: Boolean = true,
    val shown: ShownWallpaper? = null,
    /** A new wallpaper is being found or downloaded. */
    val isLoading: Boolean = false,
)

/** The kept pictures, for Settings → Storage. */
data class WallpaperCacheUsage(val count: Int, val bytes: Long)

/** What the screens can ask of the wallpapers. [WallpaperStore] does it; screenshots pass [None]. */
interface WallpaperActions {
    fun setEnabled(enabled: Boolean)

    /** Another wallpaper for the session in progress (the shuffle button). */
    fun showAnother()

    /** The countdown appeared (also after a relaunch): show the session's wallpaper, or find one. */
    fun restore()

    /** Downloads one picture for the next session, unless one is already waiting. */
    fun prefetch()

    /** The downloaded wallpapers that are in the list, for Timetable Appearance. */
    fun downloaded(): List<FocusWallpaper> = emptyList()

    /** A downloaded wallpaper's picture, as a thumbnail about [width] × [height] pixels or larger. */
    suspend fun thumbnail(wallpaper: FocusWallpaper, width: Int, height: Int): ImageBitmap? = null

    /** A downloaded wallpaper's file, to put it behind the timetable. */
    fun file(wallpaper: FocusWallpaper): File? = null

    fun cacheUsage(): WallpaperCacheUsage = WallpaperCacheUsage(0, 0)

    /** Settings → Storage: deletes the kept pictures and the list (the one on screen stays until the session ends). */
    fun clearCache() = Unit

    object None : WallpaperActions {
        override fun setEnabled(enabled: Boolean) = Unit
        override fun showAnother() = Unit
        override fun restore() = Unit
        override fun prefetch() = Unit
    }
}

/**
 * The photo behind the Focus countdown (docs/todos-focus-spec.md §7, iOS `WallpaperStore`). Each session gets a random
 * one from the backend's list (`focus_wallpapers`, readable without an account) that wasn't shown recently. A picture
 * is downloaded once and kept in the cache folder `Wallpapers`; without a connection a kept one is shown, and with
 * none kept the countdown keeps its plain look. One picture is downloaded ahead (when the Focus tab opens and after
 * each shown wallpaper; not with Data Saver on a metered network), so a session usually starts with its photo at once.
 * Main thread only; files and the network on the IO dispatcher.
 */
class WallpaperStore(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) : WallpaperActions {
    private val prefs = context.getSharedPreferences("wallpapers", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(WallpaperState(enabled = prefs.getBoolean(ENABLED, true)))
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    /** Where the pictures and the list are kept. */
    val folder = File(context.cacheDir, "Wallpapers")
    private var catalog: List<FocusWallpaper> = emptyList()
    private var fetchedAt: Instant? = null
    private var catalogRead: Job? = null
    private var loading: Job? = null
    private var prefetching: Job? = null

    init {
        catalogRead = scope.launch(Dispatchers.Main.immediate) {
            val saved = withContext(Dispatchers.IO) { File(folder, CATALOG_FILE).takeIf { it.exists() }?.readText() }
            if (saved != null && catalog.isEmpty()) {
                catalog = runCatching { FocusWallpaperCatalog.decode(saved) }.getOrDefault(emptyList())
                fetchedAt = prefs.getLong(FETCHED_AT, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            }
        }
    }

    /** Follows the focus sessions: a new one gets a new wallpaper, an ended one lets it go. */
    fun follow(runs: Flow<UUID?>) {
        scope.launch(Dispatchers.Main.immediate) {
            var previous: UUID? = null
            var first = true
            runs.distinctUntilChanged().collect { id ->
                // The first value is the session already running when the app started: restored by the countdown.
                if (!first) {
                    if (id == null) sessionEnded() else if (id != previous) sessionStarted()
                }
                first = false
                previous = id
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(ENABLED, enabled).apply()
        if (!enabled) {
            loading?.cancel()
            loading = null
            _state.value = WallpaperState(enabled = false)
        } else {
            _state.update { it.copy(enabled = true) }
        }
    }

    // Sessions

    private fun sessionStarted() {
        prefs.edit().remove(SESSION).apply()
        _state.update { it.copy(shown = null) }
        showAnother()
    }

    private fun sessionEnded() {
        loading?.cancel()
        loading = null
        prefs.edit().remove(SESSION).apply()
        _state.update { it.copy(shown = null, isLoading = false) }
    }

    override fun restore() {
        if (!_state.value.enabled || _state.value.shown != null || loading != null) return
        loading = scope.launch(Dispatchers.Main.immediate) {
            catalogRead?.join()
            val id = prefs.getString(SESSION, null)
            val wallpaper = catalog.firstOrNull { it.id == id }
            if (wallpaper != null && pictureFile(wallpaper).exists()) {
                _state.update { it.copy(shown = ShownWallpaper(wallpaper, null)) }
                loadImage(wallpaper, download = false)?.let { image -> _state.update { it.copy(shown = ShownWallpaper(wallpaper, image)) } }
            } else {
                findNext()
            }
            loading = null
        }
    }

    override fun showAnother() {
        if (!_state.value.enabled) return
        loading?.cancel()
        loading = scope.launch(Dispatchers.Main.immediate) {
            catalogRead?.join()
            findNext()
            if (isActive) loading = null
        }
    }

    private suspend fun findNext() {
        _state.update { it.copy(isLoading = true) }
        try {
            // A picture downloaded ahead shows at once; an old list is refreshed afterwards, by the prefetch.
            val ready = FocusWallpaperPicker.ready(catalog, downloadedIds(), recentIds()).randomOrNull()
            if (ready != null) {
                val image = loadImage(ready, download = false)
                if (image != null) return show(ready, image, catalogIsFresh = false)
            }
            var online = true
            if (catalog.isEmpty() || FocusWallpaperCatalog.isStale(fetchedAt, clock.instant())) online = refreshCatalog()
            val recent = recentIds()
            // Without a connection (or if the download fails) only kept pictures can be shown.
            for (canDownload in if (online) listOf(true, false) else listOf(false)) {
                val wallpaper = FocusWallpaperPicker.pick(catalog, downloadedIds(), recent, canDownload) ?: continue
                if (_state.value.shown == null) _state.update { it.copy(shown = ShownWallpaper(wallpaper, null)) }
                val image = loadImage(wallpaper, download = canDownload) ?: continue
                return show(wallpaper, image, catalogIsFresh = online)
            }
            // Nothing to show: keep the one on screen, if any; drop a placeholder that never loaded.
            if (_state.value.shown?.image == null) _state.update { it.copy(shown = null) }
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun show(wallpaper: FocusWallpaper, image: ImageBitmap, catalogIsFresh: Boolean) {
        _state.update { it.copy(shown = ShownWallpaper(wallpaper, image)) }
        val remembered = FocusWallpaperPicker.remember(wallpaper.id, savedRecent(), catalog.size)
        prefs.edit().putString(SESSION, wallpaper.id).putString(RECENT, remembered.joinToString(",")).apply()
        scope.launch(Dispatchers.IO) { trimCache(setOf(wallpaper.id), catalogIsFresh) }
        prefetch()
    }

    private fun savedRecent(): List<String> = prefs.getString(RECENT, null)?.split(",")?.filter { it.isNotEmpty() }.orEmpty()

    /** The recently shown wallpapers, the one on screen first. */
    private fun recentIds(): List<String> = listOfNotNull(_state.value.shown?.wallpaper?.id) + savedRecent()

    // Ahead of the next session

    override fun prefetch() {
        if (!_state.value.enabled || prefetching != null) return
        prefetching = scope.launch(Dispatchers.Main.immediate) {
            try {
                catalogRead?.join()
                if (dataSaverOn()) return@launch
                var fresh = catalog.isNotEmpty() && !FocusWallpaperCatalog.isStale(fetchedAt, clock.instant())
                if (!fresh) fresh = refreshCatalog()
                if (!fresh) return@launch
                val next = FocusWallpaperPicker.prefetchCandidates(catalog, downloadedIds(), recentIds()).randomOrNull() ?: return@launch
                if (!download(next)) return@launch
                val keeping = setOfNotNull(next.id, _state.value.shown?.wallpaper?.id)
                withContext(Dispatchers.IO) { trimCache(keeping, catalogIsFresh = true) }
            } finally {
                prefetching = null
            }
        }
    }

    /** Data Saver on a metered network (the iOS Low Data Mode): nothing is downloaded ahead. */
    private fun dataSaverOn(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return connectivity.isActiveNetworkMetered &&
            connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }

    // Network

    /** Downloads the list of wallpapers. False if it couldn't (no connection, server error). */
    private suspend fun refreshCatalog(): Boolean {
        val text = withContext(Dispatchers.IO) {
            try {
                val connection = open(FocusWallpaperCatalog.requestUrl(BackendConfig.URL))
                connection.setRequestProperty("apikey", BackendConfig.PUBLISHABLE_KEY)
                connection.setRequestProperty("Accept", "application/json")
                try {
                    if (connection.responseCode != 200) {
                        Log.w(TAG, "The wallpaper list answered ${connection.responseCode}")
                        return@withContext null
                    }
                    connection.inputStream.use { it.readBytes().decodeToString() }
                } finally {
                    connection.disconnect()
                }
            } catch (error: IOException) {
                Log.i(TAG, "The wallpaper list couldn't be loaded: $error")
                null
            }
        } ?: return false
        val decoded = runCatching { FocusWallpaperCatalog.decode(text) }.getOrNull() ?: return false
        catalog = decoded
        val now = clock.instant()
        fetchedAt = now
        prefs.edit().putLong(FETCHED_AT, now.toEpochMilli()).apply()
        withContext(Dispatchers.IO) {
            folder.mkdirs()
            File(folder, CATALOG_FILE).writeText(text)
        }
        return true
    }

    /** Downloads the picture into the cache (if it isn't there yet). False if it couldn't. */
    private suspend fun download(wallpaper: FocusWallpaper): Boolean = withContext(Dispatchers.IO) {
        val file = pictureFile(wallpaper)
        if (file.exists()) return@withContext true
        val temporary = File(folder, "${wallpaper.id}.part")
        try {
            folder.mkdirs()
            val connection = open(wallpaper.url)
            try {
                if (connection.responseCode != 200) return@withContext false
                connection.inputStream.use { input -> temporary.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            if (!file.exists() && !temporary.renameTo(file)) return@withContext false
            true
        } catch (error: IOException) {
            Log.i(TAG, "Wallpaper ${wallpaper.id} couldn't be downloaded: $error")
            false
        } finally {
            temporary.delete()
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        useCaches = false
    }

    /** The picture, from the cache or (if [download]) downloaded into it, ready to draw. */
    private suspend fun loadImage(wallpaper: FocusWallpaper, download: Boolean): ImageBitmap? {
        val file = pictureFile(wallpaper)
        if (!withContext(Dispatchers.IO) { file.exists() }) {
            if (!download || !download(wallpaper)) return null
        }
        return withContext(Dispatchers.IO) {
            file.setLastModified(clock.millis())
            val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
            if (bitmap == null) file.delete()
            bitmap?.apply { prepareToDraw() }?.asImageBitmap()
        }
    }

    // Cache

    override fun file(wallpaper: FocusWallpaper): File? = pictureFile(wallpaper).takeIf { it.exists() }

    private fun pictureFile(wallpaper: FocusWallpaper) = File(folder, wallpaper.fileName)

    private fun pictures(): List<FocusWallpaperCache.Entry> =
        folder.listFiles { file -> file.name.endsWith(".jpg") }.orEmpty().map { file ->
            FocusWallpaperCache.Entry(file.nameWithoutExtension, file.length(), Instant.ofEpochMilli(file.lastModified()))
        }

    private fun downloadedIds(): Set<String> = pictures().map { it.id }.toSet()

    private fun trimCache(keeping: Set<String>, catalogIsFresh: Boolean) {
        val catalogIds = if (catalogIsFresh) catalog.map { it.id }.toSet() else null
        for (id in FocusWallpaperCache.evictions(pictures(), catalogIds, keeping)) File(folder, "$id.jpg").delete()
    }

    override fun downloaded(): List<FocusWallpaper> {
        val ids = downloadedIds()
        return catalog.filter { it.id in ids }
    }

    override suspend fun thumbnail(wallpaper: FocusWallpaper, width: Int, height: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = pictureFile(wallpaper).takeIf { it.exists() } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }

    override fun cacheUsage(): WallpaperCacheUsage {
        val kept = pictures()
        return WallpaperCacheUsage(kept.size, kept.sumOf { it.bytes })
    }

    override fun clearCache() {
        folder.deleteRecursively()
        catalog = emptyList()
        fetchedAt = null
        prefs.edit().remove(FETCHED_AT).apply()
    }

    private companion object {
        const val TAG = "Wallpapers"
        const val ENABLED = "focus.wallpaper.enabled"
        const val RECENT = "focus.wallpaper.recent"
        /** The running session's wallpaper, so a relaunch shows the same one. */
        const val SESSION = "focus.wallpaper.session"
        const val FETCHED_AT = "focus.wallpaper.fetchedAt"
        const val CATALOG_FILE = "catalog.json"
        const val TIMEOUT_MS = 20_000
    }
}
