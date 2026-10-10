package io.github.hilbert0619.coursetable

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hilbert0619.coursetable.ui.RootScreen
import io.github.hilbert0619.coursetable.ui.Tab
import io.github.hilbert0619.coursetable.ui.theme.StudyHourTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the root screen on the JVM. `gradlew :app:recordRoborazziDebug` writes the images to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class RootScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(tab: Tab = Tab.TODAY) {
        compose.setContent { StudyHourTheme { RootScreen(initialTab = tab) } }
    }

    @Test
    fun todayEnglish() {
        show()
        compose.onNodeWithText("The day's classes and to-dos will be here.").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun todayChinese() {
        show()
        compose.onNodeWithText("这里将显示当天的课程和待办。").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+night")
    fun todayDark() {
        show()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun tappingATabSwitchesToIt() {
        show()
        compose.onNodeWithText("Words").performClick()
        compose.onNodeWithText("Vocabulary study and sync will be here.").assertExists()
        compose.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "+zh-rCN-night")
    fun settingsChineseDark() {
        show(Tab.SETTINGS)
        compose.onNodeWithText("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").assertExists()
        compose.onRoot().captureRoboImage()
    }
}
