package io.github.hilbert0619.coursekit

import java.net.URI
import java.time.Instant
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** A photo behind the Focus countdown (docs/todos-focus-spec.md §7): one row of the backend's `focus_wallpapers`. */
data class FocusWallpaper(
    val id: String,
    /** The picture, on https. */
    val url: String,
    val width: Int,
    val height: Int,
    /** The picture's average colour, `#rrggbb`, shown while it loads. */
    val color: String = "#808080",
    /** How far down the picture its subject is, 0 (top) to 1 (bottom). */
    val focusY: Double = 0.5,
    val author: String = "",
    val authorUrl: String? = null,
    val sourceUrl: String? = null,
    val licence: String = "",
) {
    /** The file a downloaded copy is kept in: the id, which the backend limits to `a-z`, `0-9` and dashes. */
    val fileName: String get() = "$id.jpg"

    /** The average colour; null if [color] isn't `#rrggbb`. */
    val rgb: RGB?
        get() {
            if (color.length != 7 || color[0] != '#') return null
            val value = color.substring(1).toIntOrNull(16) ?: return null
            return RGB.hex(value)
        }

    /** Whether the app can use the row: an https picture with a size and an id that is safe as a file name. */
    val isUsable: Boolean
        get() = scheme(url) == "https" && width > 0 && height > 0 && id.isNotEmpty() && id.length <= 60 &&
            id.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }

    companion object {
        /** Reads a row leniently: optional fields may be missing or empty, `focus_y` is brought into 0…1. */
        fun of(row: JsonObject): FocusWallpaper? {
            fun string(key: String) = (row[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun number(key: String) = (row[key] as? JsonPrimitive)?.takeIf { !it.isString }
            val id = string("id") ?: return null
            val url = string("url")?.takeIf { scheme(it) != null } ?: return null
            val width = number("width")?.intOrNull ?: return null
            val height = number("height")?.intOrNull ?: return null
            return FocusWallpaper(
                id = id,
                url = url,
                width = width,
                height = height,
                color = string("color") ?: "#808080",
                focusY = (number("focus_y")?.doubleOrNull ?: 0.5).coerceIn(0.0, 1.0),
                author = string("author") ?: "",
                authorUrl = link(string("author_url")),
                sourceUrl = link(string("source_url")),
                licence = string("licence") ?: "",
            )
        }

        private fun link(text: String?): String? = text?.takeIf { it.startsWith("https://") && scheme(it) != null }

        /** The URL's scheme; null if it isn't a URL. */
        private fun scheme(text: String): String? = runCatching { URI(text) }.getOrNull()?.scheme
    }
}

/** The list of wallpapers: where the app asks for it and how it reads the answer. */
object FocusWallpaperCatalog {
    /** How long a downloaded list is used before the app asks again, in seconds. */
    const val REFRESH_INTERVAL: Double = 12 * 60 * 60.0

    /** The REST API request for the active rows (`apikey` header: the publishable key; no account needed). */
    fun requestUrl(base: String): String =
        base.trimEnd('/') + "/rest/v1/focus_wallpapers" +
            "?select=id,url,width,height,color,focus_y,author,author_url,source_url,licence&active=is.true&order=id"

    /** The usable wallpapers in an answer (rows the app can't use are left out; a repeated id counts once). */
    fun decode(text: String): List<FocusWallpaper> {
        val rows = Json.parseToJsonElement(text) as? JsonArray ?: throw IllegalArgumentException("Expected a list of rows")
        val seen = mutableSetOf<String>()
        return rows.mapNotNull { (it as? JsonObject)?.let(FocusWallpaper::of) }.filter { it.isUsable && seen.add(it.id) }
    }

    /** Whether a list downloaded at [fetchedAt] should be asked for again at [now]. */
    fun isStale(fetchedAt: Instant?, now: Instant): Boolean {
        if (fetchedAt == null) return true
        return secondsBetween(fetchedAt, now) >= REFRESH_INTERVAL || now < fetchedAt
    }
}

/** Which wallpaper a session shows: a random one that wasn't shown recently. */
object FocusWallpaperPicker {
    /** The longest list of recently shown wallpapers that is kept. */
    const val MAX_RECENT = 10

    /**
     * The wallpapers the next session may show. Recently shown ones are skipped while there are others; without a
     * connection only downloaded ones can be shown.
     */
    fun candidates(
        catalog: List<FocusWallpaper>, downloaded: Set<String>, recent: List<String>, canDownload: Boolean,
    ): List<FocusWallpaper> {
        val reachable = if (canDownload) catalog else catalog.filter { it.id in downloaded }
        val fresh = reachable.filter { it.id !in recent }
        return fresh.ifEmpty { reachable }
    }

    fun pick(
        catalog: List<FocusWallpaper>, downloaded: Set<String>, recent: List<String>, canDownload: Boolean,
        random: Random = Random.Default,
    ): FocusWallpaper? = candidates(catalog, downloaded, recent, canDownload).randomOrNull(random)

    /** The wallpapers a session can show at once: downloaded and not shown recently. */
    fun ready(catalog: List<FocusWallpaper>, downloaded: Set<String>, recent: List<String>): List<FocusWallpaper> =
        catalog.filter { it.id in downloaded && it.id !in recent }

    /**
     * The wallpapers worth downloading ahead of the next session: not downloaded and not shown recently — but none
     * while a downloaded one is already waiting, so at most one picture is fetched ahead.
     */
    fun prefetchCandidates(catalog: List<FocusWallpaper>, downloaded: Set<String>, recent: List<String>): List<FocusWallpaper> {
        if (ready(catalog, downloaded, recent).isNotEmpty()) return emptyList()
        return catalog.filter { it.id !in downloaded && it.id !in recent }
    }

    /**
     * The recent list after [id] is shown: newest first, no repeats, at most half the catalog (so there's always a
     * choice left) and at most [MAX_RECENT].
     */
    fun remember(id: String, recent: List<String>, catalogCount: Int): List<String> {
        val limit = minOf(MAX_RECENT, maxOf(catalogCount / 2, 1))
        return (listOf(id) + recent.filter { it != id }).take(limit)
    }
}

/** A rectangle, in the container's units. */
data class Frame(val x: Double, val y: Double, val width: Double, val height: Double)

/**
 * Where a wallpaper goes in a frame of another shape: scaled to fill it, centred across, and moved up or down so its
 * subject (`focusY`) is as close to the middle as the picture allows.
 */
object FocusWallpaperLayout {
    fun frame(imageWidth: Double, imageHeight: Double, containerWidth: Double, containerHeight: Double, focusY: Double): Frame {
        if (imageWidth <= 0 || imageHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
            return Frame(0.0, 0.0, containerWidth, containerHeight)
        }
        val scale = maxOf(containerWidth / imageWidth, containerHeight / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        val top = minOf(maxOf(focusY * height - containerHeight / 2, 0.0), height - containerHeight)
        return Frame((containerWidth - width) / 2, -top, width, height)
    }
}

/** Keeps the downloaded wallpapers within bounds. */
object FocusWallpaperCache {
    /** A downloaded picture: its wallpaper's id, size and when it was last shown. */
    data class Entry(val id: String, val bytes: Long, val lastUsed: Instant)

    const val MAX_FILES = 40
    const val MAX_BYTES = 60_000_000L

    /**
     * The ids of the pictures to delete: those no longer in the catalog (when it's known), then the least recently
     * used until at most [maxFiles] pictures and [maxBytes] remain. Those in [keeping] (the one on screen) stay.
     */
    fun evictions(
        entries: List<Entry>, catalog: Set<String>?, keeping: Set<String> = emptySet(),
        maxFiles: Int = MAX_FILES, maxBytes: Long = MAX_BYTES,
    ): List<String> {
        val removed = mutableListOf<String>()
        val kept = mutableListOf<Entry>()
        for (entry in entries) {
            if (catalog != null && entry.id !in catalog && entry.id !in keeping) removed += entry.id else kept += entry
        }
        var count = kept.size
        var bytes = kept.sumOf { it.bytes }
        for (entry in kept.sortedWith(compareBy<Entry>({ it.lastUsed }, { it.id }))) {
            if (count <= maxFiles && bytes <= maxBytes) continue
            if (entry.id in keeping) continue
            removed += entry.id
            count -= 1
            bytes -= entry.bytes
        }
        return removed
    }
}
