package io.github.hilbert0619.coursetable.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The island's window: an accessibility overlay ([WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]), the one
 * kind of app window that may cover the status bar and the camera hole. It is only as big as the pill or the card,
 * so every touch elsewhere goes to the app below; a touch outside the open card closes it.
 */
internal class IslandWindow(private val context: Context, callbacks: IslandCallbacks) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private var ui by mutableStateOf<IslandUi?>(null)
    private var added = false

    private val root: FrameLayout = OutsideTouchFrame(context) { if (ui?.expanded == true) callbacks.collapse() }.apply {
        addView(ComposeView(context).apply { setContent { ui?.let { IslandContent(it, callbacks) } } })
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
    }

    init {
        owner.start()
    }

    /** Shows [next] (adding the window the first time). */
    fun show(next: IslandUi) {
        ui = next
        val params = params(next)
        if (added) {
            windowManager.updateViewLayout(root, params)
        } else {
            windowManager.addView(root, params)
            added = true
        }
    }

    fun hide() {
        if (!added) return
        windowManager.removeViewImmediate(root)
        added = false
        ui = null
    }

    fun destroy() {
        hide()
        owner.stop()
    }

    private fun params(ui: IslandUi) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        // The pill is centered on the camera hole; the card spans the width.
        x = if (ui.expanded) 0 else ui.geometry.centerX - ui.geometry.screenWidth / 2
        y = ui.geometry.top
        if (Build.VERSION.SDK_INT >= 30) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        title = "StudyHour island"
    }

    /** Where the island goes on this screen now (it changes with the rotation and the display size). */
    fun geometry(): IslandGeometry {
        val density = context.resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val metrics = windowManager.maximumWindowMetrics
                val insets = metrics.windowInsets
                val hole = insets.displayCutout?.boundingRectTop?.takeIf { !it.isEmpty }
                val statusBar = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
                return IslandGeometry.of(metrics.bounds.width(), hole?.let { Bounds(it.left, it.top, it.right, it.bottom) }, statusBar, density)
            }
        }
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val hole = if (Build.VERSION.SDK_INT >= 29) display.cutout?.boundingRectTop?.takeIf { !it.isEmpty } else null
        return IslandGeometry.of(context.resources.displayMetrics.widthPixels, hole?.let { Bounds(it.left, it.top, it.right, it.bottom) }, 0, density)
    }
}

/** Passes [MotionEvent.ACTION_OUTSIDE] (a touch anywhere else on the screen) to [onOutside]. */
@SuppressLint("ViewConstructor")
private class OutsideTouchFrame(context: Context, private val onOutside: () -> Unit) : FrameLayout(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside()
            return false
        }
        return super.dispatchTouchEvent(event)
    }
}

/** What Compose needs from a window outside an activity: a lifecycle and a place to save state. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
