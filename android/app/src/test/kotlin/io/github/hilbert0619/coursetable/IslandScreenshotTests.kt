package io.github.hilbert0619.coursetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.hilbert0619.coursetable.island.Bounds
import io.github.hilbert0619.coursetable.island.IslandAction
import io.github.hilbert0619.coursetable.island.IslandCallbacks
import io.github.hilbert0619.coursetable.island.IslandContent
import io.github.hilbert0619.coursetable.island.IslandEntry
import io.github.hilbert0619.coursetable.island.IslandGeometry
import io.github.hilbert0619.coursetable.island.IslandItem
import io.github.hilbert0619.coursetable.island.IslandKind
import io.github.hilbert0619.coursetable.island.IslandUi
import io.github.hilbert0619.coursetable.island.PILL_TAG
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.settings.ReliableRemindersScreen
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette
import io.github.hilbert0619.coursetable.ui.theme.StudyHourTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild

/**
 * The Countdown Island over the top of a Home Screen with a centered camera hole (drawn on top, as the real one is),
 * and its section in Reliable Reminders. `gradlew :app:recordRoborazziDebug` writes them to app/build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class IslandScreenshotTests {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-09-28T00:15:30Z")

    private fun calculus(title: String = "Calculus", text: String = "Until 09:40 · A101 · Li") = IslandEntry(
        IslandItem("1:class:a", IslandKind.CLASS, title, text, now.plusSeconds(24 * 60 + 30), started = true, color = CoursePalette.color(3).toArgb()),
        actions = listOf(IslandAction(if (title == "Calculus") "Close" else "关闭", null)),
    )

    private fun focus(title: String = "Focus · Block 2 of 5", text: String = "Calculus review · ends at 11:30", paused: Boolean = false) = IslandEntry(
        IslandItem("10:", IslandKind.FOCUS, title, text, if (paused) null else now.plusSeconds(18 * 60), color = 0xFF3A4DCD.toInt()),
        actions = if (title.startsWith("Focus")) listOf(IslandAction("Pause", null), IslandAction("Stop", null)) else listOf(IslandAction("继续", null), IslandAction("停止", null)),
    )

    /**
     * The island as [IslandService] places it, over a status bar with a 30 dp camera hole 9 dp from the top, centered.
     */
    private fun showIsland(entries: List<IslandEntry>, expanded: Boolean, callbacks: IslandCallbacks = IslandCallbacks.None) {
        compose.setContent {
            StudyHourTheme {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(260.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF41618F), Color(0xFFC99A7A)))),
                ) {
                    val density = LocalDensity.current
                    val width = with(density) { maxWidth.roundToPx() }
                    val hole = with(density) { 30.dp.roundToPx() }
                    val holeTop = with(density) { 9.dp.roundToPx() }
                    val geometry = IslandGeometry.of(
                        width, Bounds(width / 2 - hole / 2, holeTop, width / 2 + hole / 2, holeTop + hole),
                        statusBarHeight = with(density) { 48.dp.roundToPx() }, density = density.density,
                    )
                    Text("9:15", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 24.dp, top = 14.dp))
                    Text("88%", color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 14.dp))
                    IslandContent(
                        IslandUi(entries, now, geometry, expanded), callbacks,
                        Modifier.align(Alignment.TopCenter).offset {
                            IntOffset(if (expanded) 0 else geometry.centerX - geometry.screenWidth / 2, geometry.top)
                        },
                    )
                    // The camera, on top of the island as on the phone.
                    Box(
                        Modifier.align(Alignment.TopCenter).offset { IntOffset(0, holeTop) }
                            .size(with(density) { hole.toDp() }).clip(CircleShape).background(Color(0xFF15171C)),
                    )
                }
            }
        }
    }

    private fun capture() = compose.onRoot().captureRoboImage()

    @Test
    fun pillForAClass() {
        showIsland(listOf(calculus()), expanded = false)
        compose.onNodeWithText("25 min").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun pillForAPausedSessionChinese() {
        showIsland(listOf(focus("专注 · 第 2 段，共 5 段", "高数复习 · 已暂停 · 12:00", paused = true)), expanded = false)
        compose.onNodeWithText("已暂停").assertExists()
        capture()
    }

    @Test
    fun cardWithFocusAndAClass() {
        showIsland(listOf(focus(), calculus()), expanded = true)
        compose.onNodeWithText("18:00").assertExists()
        compose.onNodeWithText("24:30").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun cardChinese() {
        showIsland(listOf(calculus("高等数学", "09:40 下课 · A101 · 李老师")), expanded = true)
        compose.onNodeWithText("24:30").assertExists()
        capture()
    }

    @Test
    fun aTapOpensTheCardAndASwipeUpHidesThePill() {
        var expanded = 0
        var hidden = 0
        showIsland(
            listOf(calculus()), expanded = false,
            callbacks = object : IslandCallbacks by IslandCallbacks.None {
                override fun expand() {
                    expanded++
                }

                override fun hide() {
                    hidden++
                }
            },
        )
        compose.onNodeWithTag(PILL_TAG).performClick()
        compose.onNodeWithTag(PILL_TAG).performTouchInput { swipe(center, Offset(center.x, center.y - 300f), 200) }
        compose.waitForIdle()
        assertEquals(1, expanded)
        assertEquals(1, hidden)
    }

    // Settings → Reliable Reminders

    @Test
    fun reliableRemindersIslandSection() {
        compose.setContent { StudyHourTheme { ReliableRemindersScreen(AppActions.None, onBack = {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Try It: a 2-Minute Countdown"))
        compose.onNodeWithText("Try It: a 2-Minute Countdown").assertExists()
        capture()
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun islandDialogOnVivoChinese() {
        ShadowBuild.setManufacturer("vivo")
        compose.setContent { StudyHourTheme { ReliableRemindersScreen(AppActions.None, onBack = {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("未开启"))
        compose.onNodeWithText("未开启").performClick()
        compose.onNodeWithText("开启倒计时岛？").assertExists()
        // The dialog is a window of its own: the last root.
        compose.onAllNodes(isRoot()).onLast().captureRoboImage()
    }
}
