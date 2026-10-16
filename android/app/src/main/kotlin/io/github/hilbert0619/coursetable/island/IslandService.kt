package io.github.hilbert0619.coursetable.island

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.time.Duration
import java.time.Instant

/**
 * Draws StudyHour's island ([Island]) while the person has it turned on in the accessibility settings. It mirrors the
 * countdown notifications ([IslandSource]): read again when StudyHour posts or removes one ([Island.changed]), at each
 * new minute, at least once a minute while it shows (a notification can time out), and every second while the card
 * is open. Hidden while StudyHour is on screen, in landscape, and after a swipe up until the countdowns change.
 *
 * It asks for no accessibility events and can't read the screen (`res/xml/island_service.xml`).
 */
class IslandService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var window: IslandWindow? = null
    private var entries: List<IslandEntry> = emptyList()
    private var expanded = false

    /** The countdowns' [IslandPlanner.phase] when the person swiped the island away. */
    private var hiddenPhase: String? = null

    private val refresh = Runnable { update() }

    /** A second look: the system may list a notification a moment after it was posted. */
    private val settle = Runnable { update() }
    private val tick = Runnable { update() }
    private val collapseLater = Runnable { setExpanded(false) }

    private val callbacks = object : IslandCallbacks {
        override fun expand() = setExpanded(true)
        override fun collapse() = setExpanded(false)

        override fun hide() {
            hiddenPhase = IslandPlanner.phase(entries.map { it.item })
            setExpanded(false)
        }

        override fun open(entry: IslandEntry) {
            send(entry.open)
            setExpanded(false)
        }

        override fun act(action: IslandAction) {
            send(action.intent)
            setExpanded(false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        window = IslandWindow(this, callbacks)
        Island.attach {
            handler.removeCallbacks(refresh)
            handler.post(refresh)
            handler.removeCallbacks(settle)
            handler.postDelayed(settle, SETTLE_MILLIS)
        }
        update()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        update()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        Island.detach()
        handler.removeCallbacksAndMessages(null)
        window?.destroy()
        window = null
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        handler.removeCallbacks(collapseLater)
        if (value) handler.postDelayed(collapseLater, COLLAPSE_MILLIS)
        update()
    }

    private fun update() {
        handler.removeCallbacks(tick)
        val window = window ?: return
        val now = Instant.now()
        val read = IslandSource.read(this)
        val order = IslandPlanner.order(read.map { it.item })
        entries = order.mapNotNull { item -> read.firstOrNull { it.item.key == item.key } }
        if (hiddenPhase != null && hiddenPhase != IslandPlanner.phase(order)) hiddenPhase = null
        val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val visible = entries.isNotEmpty() && !Island.appVisible && hiddenPhase == null && portrait
        if (!visible) expanded = false
        try {
            if (visible) window.show(IslandUi(entries, now, window.geometry(), expanded)) else window.hide()
        } catch (error: RuntimeException) {
            // The system refused the window (it can while the service is being turned off).
            Log.w(TAG, "Island window failed", error)
        }
        if (entries.isEmpty()) return
        val next = IslandPlanner.nextChange(order, now)
        var delay = next?.let { Duration.between(now, it).toMillis() + 50 } ?: MINUTE_MILLIS
        delay = delay.coerceIn(200, MINUTE_MILLIS)
        if (visible && expanded) delay = minOf(delay, 1000 - now.toEpochMilli() % 1000 + 10)
        handler.postDelayed(tick, delay)
    }

    /**
     * Sends a notification's intent: opening StudyHour (allowed from the background: a system-bound accessibility
     * service may start activities) or a button's broadcast.
     */
    private fun send(intent: PendingIntent?) {
        intent ?: return
        val options = if (Build.VERSION.SDK_INT >= 34) {
            val mode = if (Build.VERSION.SDK_INT >= 36) {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            } else {
                @Suppress("DEPRECATION")
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode).toBundle()
        } else {
            null
        }
        try {
            intent.send(this, 0, null, null, null, null, options)
        } catch (error: PendingIntent.CanceledException) {
            Log.w(TAG, "The notification's intent was cancelled", error)
        }
        Island.changed()
    }

    private companion object {
        const val TAG = "Island"
        const val SETTLE_MILLIS = 700L
        const val COLLAPSE_MILLIS = 8_000L
        const val MINUTE_MILLIS = 60_000L
    }
}
