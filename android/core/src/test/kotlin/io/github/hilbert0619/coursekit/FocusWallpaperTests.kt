package io.github.hilbert0619.coursekit

import java.io.File
import java.time.Instant
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** The shared cases in `spec/fixtures/focus-wallpapers.json`, plus the ones of the Swift tests that are their own. */
class FocusWallpaperTests {
    @Serializable
    class Fixture(
        val usable: List<String>,
        val usableDetails: List<Detail>,
        val candidates: List<CandidateCase>,
        val ahead: List<AheadCase>,
        val remember: List<RememberCase>,
        val layout: List<LayoutCase>,
        val evictions: List<EvictionCase>,
    ) {
        @Serializable
        class Detail(
            val id: String, val color: String, val focusY: Double, val author: String,
            val authorURL: String? = null, val sourceURL: String? = null,
        )
        @Serializable
        class CandidateCase(
            val about: String, val catalog: List<String>, val downloaded: List<String>, val recent: List<String>,
            val canDownload: Boolean, val expected: List<String>,
        )
        @Serializable
        class AheadCase(
            val about: String, val catalog: List<String>, val downloaded: List<String>, val recent: List<String>,
            val ready: List<String>, val prefetch: List<String>,
        )
        @Serializable class RememberCase(val id: String, val recent: List<String>, val catalogCount: Int, val expected: List<String>)
        @Serializable class LayoutCase(val image: List<Double>, val container: List<Double>, val focusY: Double, val expected: List<Double>)
        @Serializable
        class EvictionCase(
            val about: String, val entries: List<Entry>, val catalog: List<String>? = null, val keeping: List<String>,
            val maxFiles: Int, val maxBytes: Long, val expected: List<String>,
        ) {
            @Serializable class Entry(val id: String, val bytes: Long, val lastUsed: Double)
        }
    }

    private val fixture = SharedFixtures.decode<Fixture>("focus-wallpapers.json")
    private val rows = CourseKitJson.parseToJsonElement(SharedFixtures.text("focus-wallpapers.json")).jsonObject["rows"].toString()

    private fun wallpaper(id: String) = FocusWallpaper(id, "https://example.com/$id.jpg", 1290, 2796)

    @Test
    fun rowsTheAppCanUse() {
        val catalog = FocusWallpaperCatalog.decode(rows)
        assertEquals(fixture.usable, catalog.map { it.id })
        assertEquals(fixture.usableDetails.size, catalog.size)
        for ((wallpaper, detail) in catalog.zip(fixture.usableDetails)) {
            assertEquals(detail.id, wallpaper.id)
            assertEquals(detail.color, wallpaper.color)
            assertEquals(detail.focusY, wallpaper.focusY)
            assertEquals(detail.author, wallpaper.author)
            assertEquals(detail.authorURL, wallpaper.authorUrl)
            assertEquals(detail.sourceURL, wallpaper.sourceUrl)
        }
        assertEquals("lake.jpg", catalog.first().fileName)
        assertEquals("Unsplash License", catalog.first().licence)
    }

    @Test
    fun candidates() {
        for (test in fixture.candidates) {
            val found = FocusWallpaperPicker.candidates(
                test.catalog.map(::wallpaper), test.downloaded.toSet(), test.recent, test.canDownload,
            )
            assertEquals(test.expected, found.map { it.id }, test.about)
        }
    }

    @Test
    fun pickingStaysAmongTheCandidates() {
        val random = Random(7)
        val catalog = listOf("a", "b", "c", "d").map(::wallpaper)
        val seen = mutableSetOf<String>()
        repeat(200) {
            seen += FocusWallpaperPicker.pick(catalog, emptySet(), listOf("a"), canDownload = true, random = random)!!.id
        }
        assertEquals(setOf("b", "c", "d"), seen)
        assertNull(FocusWallpaperPicker.pick(catalog, emptySet(), emptyList(), canDownload = false, random = random))
    }

    @Test
    fun readyAndAhead() {
        for (test in fixture.ahead) {
            val catalog = test.catalog.map(::wallpaper)
            val downloaded = test.downloaded.toSet()
            assertEquals(test.ready, FocusWallpaperPicker.ready(catalog, downloaded, test.recent).map { it.id }, test.about)
            assertEquals(test.prefetch, FocusWallpaperPicker.prefetchCandidates(catalog, downloaded, test.recent).map { it.id }, test.about)
        }
    }

    @Test
    fun remember() {
        for (test in fixture.remember) {
            assertEquals(test.expected, FocusWallpaperPicker.remember(test.id, test.recent, test.catalogCount))
        }
    }

    @Test
    fun layout() {
        for (test in fixture.layout) {
            val frame = FocusWallpaperLayout.frame(test.image[0], test.image[1], test.container[0], test.container[1], test.focusY)
            val values = listOf(frame.x, frame.y, frame.width, frame.height)
            assertTrue(values.zip(test.expected).all { (a, b) -> abs(a - b) < 0.01 }, "${test.image} in ${test.container} → $values")
        }
    }

    @Test
    fun evictions() {
        for (test in fixture.evictions) {
            val entries = test.entries.map { FocusWallpaperCache.Entry(it.id, it.bytes, Instant.EPOCH.plusSeconds(it.lastUsed)) }
            val removed = FocusWallpaperCache.evictions(
                entries, test.catalog?.toSet(), test.keeping.toSet(), test.maxFiles, test.maxBytes,
            )
            assertEquals(test.expected, removed, test.about)
        }
    }

    @Test
    fun theRequestAsksForTheActiveRows() {
        assertEquals(
            "https://abc.supabase.co/rest/v1/focus_wallpapers?select=id,url,width,height,color,focus_y,author,author_url,source_url,licence&active=is.true&order=id",
            FocusWallpaperCatalog.requestUrl("https://abc.supabase.co"),
        )
        assertEquals(
            FocusWallpaperCatalog.requestUrl("https://abc.supabase.co"), FocusWallpaperCatalog.requestUrl("https://abc.supabase.co/"),
        )
    }

    @Test
    fun theListIsAskedForAgainAfterHalfADay() {
        val now = Instant.ofEpochSecond(1_800_000_000)
        assertTrue(FocusWallpaperCatalog.isStale(null, now))
        assertFalse(FocusWallpaperCatalog.isStale(now.minusSeconds(3600), now))
        assertTrue(FocusWallpaperCatalog.isStale(now.minusSeconds(12 * 3600), now))
        // A clock set back: ask again rather than trust a list "from the future".
        assertTrue(FocusWallpaperCatalog.isStale(now.plusSeconds(60), now))
    }

    @Test
    fun averageColour() {
        val rgb = assertNotNull(wallpaper("a").copy(color = "#ff8000").rgb)
        assertEquals(1.0, rgb.red)
        assertTrue(abs(rgb.green - 128.0 / 255) < 0.0001)
        assertEquals(0.0, rgb.blue)
        assertNull(wallpaper("a").copy(color = "orange").rgb)
    }

    @Test
    fun aRowThatIsNotAnObjectIsLeftOut() {
        val catalog = FocusWallpaperCatalog.decode("""[1, "x", null, {"id": "a", "url": "https://e.com/a.jpg", "width": 2, "height": 3}]""")
        assertEquals(listOf("a"), catalog.map { it.id })
        assertTrue(FocusWallpaper.of(JsonObject(emptyMap())) == null)
    }

    @Test
    fun theMigrationListsTheBundledWallpapers() {
        // supabase/migrations/*_focus_wallpapers_unsplash.sql names a file in supabase/wallpapers/ for every row.
        val repository = SharedFixtures.directory.parentFile.parentFile
        val migrations = File(repository, "supabase/migrations")
        val sql = assertNotNull(migrations.listFiles()?.firstOrNull { it.name.endsWith("_focus_wallpapers_unsplash.sql") }).readText()
        val files = sql.split("/storage/v1/object/public/wallpapers/").drop(1).map { it.takeWhile { c -> c != '\'' } }
        val onDisk = File(repository, "supabase/wallpapers").list()!!.toSet()
        assertEquals(30, files.size)
        assertEquals(onDisk, files.toSet())
    }
}
