package io.github.hilbert0619.coursetable

import android.Manifest
import android.app.Application
import android.os.PowerManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hilbert0619.coursekit.BackgroundChoice
import io.github.hilbert0619.coursekit.CourseBlockStyle
import io.github.hilbert0619.coursekit.CourseCSV
import io.github.hilbert0619.coursekit.CourseTemplate
import io.github.hilbert0619.coursekit.FocusPlan
import io.github.hilbert0619.coursekit.FocusRun
import io.github.hilbert0619.coursekit.FocusSettings
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Moment
import io.github.hilbert0619.coursekit.Semester
import io.github.hilbert0619.coursekit.SoftWallpaper
import io.github.hilbert0619.coursekit.TimeOfDay
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursetable.alerts.ReminderSettings
import io.github.hilbert0619.coursetable.appearance.AppStyle
import io.github.hilbert0619.coursetable.appearance.AppearanceSettings
import io.github.hilbert0619.coursetable.data.DemoData
import io.github.hilbert0619.coursetable.data.FocusSessionRecord
import io.github.hilbert0619.coursetable.focus.FocusActions
import io.github.hilbert0619.coursetable.focus.FocusState
import io.github.hilbert0619.coursetable.focus.WallpaperState
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.AppData
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.NowSource
import io.github.hilbert0619.coursetable.ui.RootScreen
import io.github.hilbert0619.coursetable.ui.Tab
import io.github.hilbert0619.coursetable.ui.components.AppearanceState
import io.github.hilbert0619.coursetable.ui.components.LocalAppearance
import io.github.hilbert0619.coursetable.ui.focus.FocusHistoryScreen
import io.github.hilbert0619.coursetable.ui.focus.FullScreenFocusContent
import io.github.hilbert0619.coursetable.ui.focus.LocalWallpapers
import io.github.hilbert0619.coursetable.ui.settings.PeriodScheduleEditor
import io.github.hilbert0619.coursetable.ui.settings.ReliableRemindersScreen
import io.github.hilbert0619.coursetable.ui.settings.StorageItem
import io.github.hilbert0619.coursetable.ui.settings.StorageScreen
import io.github.hilbert0619.coursetable.ui.settings.AppearanceScreen
import io.github.hilbert0619.coursetable.ui.theme.StudyHourTheme
import io.github.hilbert0619.coursetable.ui.timetable.CourseEditorContent
import io.github.hilbert0619.coursetable.ui.timetable.CourseEditorTarget
import io.github.hilbert0619.coursetable.ui.timetable.CourseListContent
import io.github.hilbert0619.coursetable.ui.timetable.ImportContent
import io.github.hilbert0619.coursetable.ui.timetable.TimetableScreen
import io.github.hilbert0619.coursetable.ui.today.TodoEditorContent
import io.github.hilbert0619.coursetable.ui.today.TodoEditorTarget
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowBuild

/**
 * The screens with sample data at a fixed moment (Monday 2026-09-28, 09:00, the first class in progress), rendered on
 * the JVM. `gradlew :app:recordRoborazziDebug` writes them to app/build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTests {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate(2026, 9, 28)

    private fun data(chinese: Boolean, templates: List<CourseTemplate> = emptyList()): AppData {
        val courses = DemoData.courses(today, chinese)
        return AppData(Timetable(Semester(today.adding(-28)), courses = courses), templates, DemoData.todos(today, courses, chinese))
    }

    private fun show(
        time: String = "09:00",
        appearance: AppearanceState = AppearanceState(),
        wallpapers: WallpaperState = WallpaperState(enabled = false),
        content: @Composable () -> Unit,
    ) {
        val now = Moment(today, TimeOfDay.parse(time)!!).toInstant(zone)
        compose.setContent {
            CompositionLocalProvider(
                LocalNowSource provides NowSource(Clock.fixed(now, zone), ticks = false),
                LocalAppearance provides appearance,
                LocalWallpapers provides wallpapers,
            ) {
                StudyHourTheme { content() }
            }
        }
    }

    private val onPhoto = AppearanceState(photo = DemoWallpaper.image)
    private val classic = AppearanceState(AppearanceSettings(style = AppStyle.CLASSIC))
    private fun on(wallpaper: SoftWallpaper) = AppearanceState(AppearanceSettings(background = BackgroundChoice.Wallpaper(wallpaper).stored))
    private val onWallpaper = WallpaperState(enabled = true, shown = DemoWallpaper.shown)

    private fun capture() = compose.onRoot().captureRoboImage()

    private fun at(time: String, day: LocalDate = today) = Moment(day, TimeOfDay.parse(time)!!).toInstant(zone)

    // Today

    @Test
    fun todayEnglish() {
        show { RootScreen(data(chinese = false), AppActions.None) }
        compose.onNodeWithText("Hand in the paper").assertExists()
        compose.onNodeWithText("Pick up the package").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun todayChinese() {
        show { RootScreen(data(chinese = true), AppActions.None) }
        compose.onNodeWithText("交论文").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+night")
    fun todayDark() {
        show("10:30") { RootScreen(data(chinese = false), AppActions.None) }
        capture()
    }

    @Test
    @Config(qualifiers = "+night")
    fun todayOnAPhotoDark() {
        show(appearance = onPhoto) { RootScreen(data(chinese = false), AppActions.None) }
        capture()
    }

    @Test
    fun todayClassic() {
        show(appearance = classic) { RootScreen(data(chinese = false), AppActions.None) }
        capture()
    }

    @Test
    @Config(qualifiers = "+night")
    fun todayOnLagoonDark() {
        show("10:30", appearance = on(SoftWallpaper.LAGOON)) { RootScreen(data(chinese = false), AppActions.None) }
        capture()
    }

    @Test
    fun todayEvening() {
        show("20:30") { RootScreen(data(chinese = false), AppActions.None) }
        compose.onNodeWithText("Next · Tomorrow", substring = true, ignoreCase = true).assertExists()
        capture()
    }

    @Test
    fun todayNextDay() {
        show { RootScreen(data(chinese = false), AppActions.None) }
        compose.onNodeWithContentDescription("Next Day").performClick()
        compose.onNodeWithText("Data Structures").assertExists()
        capture()
    }

    @Test
    fun todoEditorAfterAClass() {
        val data = data(chinese = false)
        show { TodoEditorContent(data.timetable, TodoEditorTarget.Edit(data.todos.first()), AppActions.None, onDismiss = {}) }
        compose.onNodeWithText("Edit To-Do").assertExists()
        capture()
    }

    // Timetable

    @Test
    fun timetableEnglish() {
        show { RootScreen(data(chinese = false), AppActions.None, initialTab = Tab.TIMETABLE) }
        compose.onNodeWithText("Week 5").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN-night")
    fun timetableChineseDark() {
        show { RootScreen(data(chinese = true), AppActions.None, initialTab = Tab.TIMETABLE) }
        compose.onNodeWithText("第 5 周").assertExists()
        capture()
    }

    @Test
    fun timetableOnAPhoto() {
        show(appearance = onPhoto) { RootScreen(data(chinese = false), AppActions.None, initialTab = Tab.TIMETABLE) }
        capture()
    }

    @Test
    @Config(qualifiers = "+night")
    fun timetableSolidOnAPhotoDark() {
        show(appearance = onPhoto.copy(settings = AppearanceSettings(blockStyle = CourseBlockStyle.SOLID))) {
            RootScreen(data(chinese = false), AppActions.None, initialTab = Tab.TIMETABLE)
        }
        capture()
    }

    @Test
    fun timetableClassicOnAPhoto() {
        show(appearance = onPhoto.copy(settings = AppearanceSettings(style = AppStyle.CLASSIC))) {
            RootScreen(data(chinese = false), AppActions.None, initialTab = Tab.TIMETABLE)
        }
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun timetableOnMeadowChinese() {
        show(appearance = on(SoftWallpaper.MEADOW)) { RootScreen(data(chinese = true), AppActions.None, initialTab = Tab.TIMETABLE) }
        capture()
    }

    @Test
    fun timetableSolid() {
        show(appearance = AppearanceState(AppearanceSettings(blockStyle = CourseBlockStyle.SOLID))) {
            RootScreen(data(chinese = false), AppActions.None, initialTab = Tab.TIMETABLE)
        }
        capture()
    }

    @Test
    fun placingCourses() {
        val templates = listOf(CourseTemplate(name = "Biology", room = "Lab 2", colorIndex = 2), CourseTemplate(name = "History", colorIndex = 8))
        val data = data(chinese = false, templates)
        show { TimetableScreen(data.timetable, data.templates, AppActions.None, startPlacing = true) }
        compose.onNodeWithText("Biology").performClick()
        compose.onNodeWithText("Tap empty slots to place Biology.").assertExists()
        capture()
    }

    @Test
    fun emptyTimetable() {
        show { RootScreen(AppData(Timetable(Semester(today)), emptyList(), emptyList()), AppActions.None, initialTab = Tab.TIMETABLE) }
        compose.onNodeWithText("Tap an empty slot or + to add a course").assertExists()
        capture()
    }

    @Test
    fun courseEditorWithAClash() {
        val data = data(chinese = false)
        show { CourseEditorContent(data.timetable, CourseEditorTarget.New(today.weekday, 2), AppActions.None, onDismiss = {}) }
        compose.onNodeWithText("New Course").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun courseEditorChinese() {
        val data = data(chinese = true)
        show { CourseEditorContent(data.timetable, CourseEditorTarget.Edit(data.timetable.courses[1]), AppActions.None, onDismiss = {}) }
        compose.onNodeWithText("编辑课程").assertExists()
        capture()
    }

    @Test
    fun courseList() {
        val data = data(chinese = false, listOf(CourseTemplate(name = "Biology", room = "Lab 2", colorIndex = 2)))
        show {
            CourseListContent(
                data.timetable, data.templates, AppActions.None, onAdded = {}, onDismiss = {},
                initialText = "高等数学 A101 李老师\nCollege Physics, C3, Zhao\nBiology, Lab 2",
            )
        }
        compose.onNodeWithText("2 new courses").assertExists()
        capture()
    }

    @Test
    fun importPreview() {
        val data = data(chinese = false)
        show { ImportContent(data.timetable, AppActions.None, onDismiss = {}, initialText = CourseCSV.ENGLISH_EXAMPLE + "\nOops,Funday,1-2,1-16") }
        compose.onNodeWithText("5 courses to add").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun importPreviewChinese() {
        val data = data(chinese = true)
        show { ImportContent(data.timetable, AppActions.None, onDismiss = {}, initialText = CourseCSV.CHINESE_EXAMPLE) }
        compose.onNodeWithText("将添加 5 门课程").assertExists()
        capture()
    }

    // Focus

    private val focusHistory = listOf(
        FocusSessionRecord(UUID.randomUUID(), "Calculus review", at("07:10"), at("07:55"), 40 * 60, 40 * 60, finished = true),
        FocusSessionRecord(UUID.randomUUID(), "", at("20:00", today.adding(-1)), at("20:50", today.adding(-1)), 45 * 60, 100 * 60, finished = false),
    )

    private fun focusRun(minutesAgo: Long) = FocusRun(
        goal = "Calculus review", plan = FocusPlan(FocusSettings(totalMinutes = 150)), startedAt = at("09:00").minus(Duration.ofMinutes(minutesAgo)),
    )

    @Test
    fun focusSetup() {
        val state = FocusState(settings = FocusSettings(totalMinutes = 150), goal = "Calculus review")
        show { RootScreen(data(chinese = false).copy(focusSessions = focusHistory), AppActions.None, initialTab = Tab.FOCUS, focus = state) }
        compose.onNodeWithText("Start Focusing").assertExists()
        // 4 × 25 min + 20 min, with 3 short breaks and 1 long one (FocusTests in :core).
        compose.onNodeWithText("5 focus blocks", substring = true).assertExists()
        compose.onNodeWithText("ends at 11:30", substring = true).assertExists()
        capture()
    }

    @Test
    fun focusRunning() {
        show { RootScreen(data(chinese = false), AppActions.None, initialTab = Tab.FOCUS, focus = FocusState(run = focusRun(7))) }
        compose.onNodeWithText("Focus · Block 1 of 5").assertExists()
        compose.onNodeWithText("18:00").assertExists()
        capture()
    }

    @Test
    fun focusSetupClassic() {
        val state = FocusState(settings = FocusSettings(totalMinutes = 150), goal = "Calculus review")
        show(appearance = classic) {
            RootScreen(data(chinese = false).copy(focusSessions = focusHistory), AppActions.None, initialTab = Tab.FOCUS, focus = state)
        }
        capture()
    }

    @Test
    @Config(qualifiers = "+night")
    fun focusSetupOnEmberDark() {
        val state = FocusState(settings = FocusSettings(totalMinutes = 50), goal = "Calculus review")
        show(appearance = on(SoftWallpaper.EMBER)) {
            RootScreen(data(chinese = false).copy(focusSessions = focusHistory), AppActions.None, initialTab = Tab.FOCUS, focus = state)
        }
        capture()
    }

    @Test
    fun focusOnAWallpaper() {
        show(wallpapers = onWallpaper) { RootScreen(data(chinese = false), AppActions.None, initialTab = Tab.FOCUS, focus = FocusState(run = focusRun(7))) }
        compose.onNodeWithText("Photo by Demo Photographer on Unsplash").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+night")
    fun focusFullScreen() {
        show(wallpapers = onWallpaper) { FullScreenFocusContent(focusRun(7), FocusActions.None, onClose = {}) }
        compose.onNodeWithText("18:00").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun focusFullScreenWithoutAPhotoChinese() {
        show { FullScreenFocusContent(focusRun(27), FocusActions.None, onClose = {}) }
        capture()
    }

    @Test
    fun focusHistory() {
        show { FocusHistoryScreen(focusHistory, onDelete = {}, onClear = {}, onBack = {}) }
        compose.onNodeWithText("Clear History").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN-night")
    fun focusBreakChineseDark() {
        show { RootScreen(data(chinese = true), AppActions.None, initialTab = Tab.FOCUS, focus = FocusState(run = focusRun(27), goal = "高数复习")) }
        compose.onNodeWithText("3:00").assertExists()
        capture()
    }

    // Settings

    /** Notifications and exact alarms allowed and battery optimisation off, as after following Reliable Reminders. */
    private fun allowEverything() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(application.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(application.packageName, true)
    }

    @Test
    fun settingsReminders() {
        allowEverything()
        show { RootScreen(data(chinese = false), AppActions.None, initialTab = Tab.SETTINGS, reminders = ReminderSettings(remindersEnabled = true)) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Reliable Reminders"))
        compose.onNodeWithText("Live class notification").assertExists()
        capture()
    }

    @Test
    fun reliableReminders() {
        allowEverything()
        show { ReliableRemindersScreen(AppActions.None, onBack = {}) }
        compose.onNodeWithText("Send a Test Reminder").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun reliableRemindersOnVivoChinese() {
        ShadowBuild.setManufacturer("vivo")
        show { ReliableRemindersScreen(AppActions.None, onBack = {}) }
        compose.onNodeWithText("发送测试提醒").assertExists()
        capture()
    }

    @Test
    fun storage() {
        show {
            StorageScreen(
                focusSessionCount = 2, completedTodoCount = 1, actions = AppActions.None, onBack = {},
                initialSelection = setOf(StorageItem.FOCUS_HISTORY, StorageItem.COMPLETED_TODOS),
            )
        }
        compose.onNodeWithText("Clear Selected").assertExists()
        capture()
    }

    @Test
    fun appearance() {
        show { AppearanceScreen(onBack = {}) }
        compose.onNodeWithText("Dawn").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN-night")
    fun appearanceClassicWithAPhotoChineseDark() {
        show(appearance = onPhoto.copy(settings = AppearanceSettings(style = AppStyle.CLASSIC, background = "photo"))) { AppearanceScreen(onBack = {}) }
        compose.onNodeWithText("移除照片").assertExists()
        capture()
    }

    @Test
    fun settings() {
        show { RootScreen(data(chinese = false), AppActions.None, initialTab = Tab.SETTINGS) }
        compose.onNodeWithText("First day of term").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN-night")
    fun periodTimesChineseDark() {
        val broken = PeriodTimesFixture.withOverlap()
        show { PeriodScheduleEditor(broken, AppActions.None, onBack = {}) }
        capture()
    }

    @Test
    fun launcherIcon() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val icon = context.getDrawable(R.mipmap.ic_launcher)!!.toBitmap(432, 432).asImageBitmap()
        val layer = context.getDrawable(R.drawable.ic_launcher_foreground)!!.toBitmap(432, 432).asImageBitmap()
        val glyph = context.getDrawable(R.drawable.ic_launcher_monochrome)!!.toBitmap(432, 432).asImageBitmap()
        show {
            Column(Modifier.fillMaxSize().background(Color(0xFF282C34)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Image(icon, contentDescription = null, modifier = Modifier.size(160.dp))
                Image(layer, contentDescription = null, modifier = Modifier.size(160.dp))
                Box(Modifier.size(160.dp).background(Color(0xFF3B4A6B), CircleShape)) {
                    Image(glyph, contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(Color(0xFFD7E2FF)))
                }
            }
        }
        capture()
    }

    @Test
    fun tappingATabSwitchesToIt() {
        show { RootScreen(data(chinese = false), AppActions.None) }
        compose.onNodeWithText("Words").performClick()
        compose.onNodeWithText("Vocabulary study and sync will be here.").assertExists()
    }

    private object PeriodTimesFixture {
        /** The standard day with period 5 starting inside period 4. */
        fun withOverlap() = io.github.hilbert0619.coursekit.PeriodSchedule.STANDARD.let { standard ->
            standard.copy(periods = standard.periods.toMutableList().also { it[4] = it[4].copy(start = TimeOfDay(11, 0)) })
        }
    }
}
