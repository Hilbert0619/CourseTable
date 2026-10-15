package io.github.hilbert0619.coursetable.alerts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.hilbert0619.coursetable.R
import java.time.Instant

/**
 * Android 16 Live Updates: the live class and focus notifications in the status bar chip, or in the brand's island
 * (vivo/iQOO 原子岛, OPPO 流体云, Xiaomi 超级岛), which draw the same promoted notifications in their own way.
 *
 * - **Two sets of rules.** Android 16 promotes a notification by one of two rule sets, picked by a platform flag
 *   (`ui_rich_ongoing`): the Live Update rules (it asks for promotion and is *not* colorized; Pixel from Android 16
 *   QPR1) or the rules of the first Android 16 release (it *is* colorized, with no style or a promotable one). Brands
 *   ship either, so a notification that suits one gets nothing on the other: no island at all. The app can't read the
 *   flag, but `Notification.hasPromotableCharacteristics()` runs the phone's own check, so [build] posts the
 *   notification as it is, or colorized when only that is promotable on this phone.
 * - **The island's text.** The vivo, OPPO and Xiaomi islands don't run the countdown chronometer; they show the app
 *   name or the title in its place (measured by tigerduck-app/android-dynamic-island-test on OriginOS 6, ColorOS 16
 *   and HyperOS 3). On those phones the notification also carries the minutes left as its short critical text
 *   ("25 min"), posted again each time the number changes ([IslandCountdown]); elsewhere the chip runs the
 *   chronometer by itself, to the second.
 */
object LiveUpdates {
    /** `Notification.FLAG_PROMOTED_ONGOING`: the system shows the notification as a Live Update. */
    private const val FLAG_PROMOTED_ONGOING = 0x00040000

    /** `Notification.EXTRA_SHORT_CRITICAL_TEXT` (hidden): the chip's or island's text. */
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"

    /** The islands that show a text in place of the countdown chronometer. */
    private val TEXT_ISLAND_BRANDS = setOf("vivo", "iqoo", "oppo", "oneplus", "realme", "xiaomi", "redmi", "poco")

    /** The notification [builder] makes, colorized if only that makes it a Live Update on this phone. */
    fun build(builder: NotificationCompat.Builder): Notification {
        val plain = builder.build()
        if (Build.VERSION.SDK_INT < 36 || promotable(plain) != false) return plain
        val colorized = builder.setColorized(true).build()
        return if (promotable(colorized) == true) colorized else plain
    }

    private fun promotable(notification: Notification): Boolean? =
        if (Build.VERSION.SDK_INT >= 36) runCatching { notification.hasPromotableCharacteristics() }.getOrNull() else null

    /** Whether the countdown also goes into the short critical text ("25 min") on this phone. */
    fun islandNeedsText(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 36 && hasTextIsland && SystemSettings.liveUpdatesAllowed(context) == true

    private val hasTextIsland: Boolean
        get() = listOf(Build.MANUFACTURER, Build.BRAND).any { it.orEmpty().lowercase() in TEXT_ISLAND_BRANDS }

    /**
     * Counts down to [target]: the chronometer in the notification and the chip, and with [islandText] the minutes
     * left in the island.
     */
    fun NotificationCompat.Builder.countdown(context: Context, target: Instant, now: Instant, islandText: Boolean): NotificationCompat.Builder {
        setWhen(target.toEpochMilli()).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        if (islandText) minutesText(context, target, now)?.let { setShortCriticalText(it) }
        return this
    }

    /** "25 min" until [target], or `null` once it has passed. */
    fun minutesText(context: Context, target: Instant, now: Instant): String? =
        IslandCountdown.minutesLeft(target, now).takeIf { it > 0 }?.let { context.getString(R.string.minutes_short, it.toInt()) }

    /** The short critical text a posted notification carries. */
    fun islandText(notification: Notification): String? = notification.extras.getString(EXTRA_SHORT_CRITICAL_TEXT)

    /** How the system shows a posted notification: as a Live Update or not, and colorized or not. */
    data class Shown(val promoted: Boolean, val colorized: Boolean) {
        /** "promoted · colorized · BP2A.250605.031 · PD2338_A_16.0.12.4": for reporting what a phone does. */
        val details: String
            get() = listOf(
                if (promoted) "promoted" else "not promoted",
                if (colorized) "colorized" else "standard",
                Build.ID,
                Build.DISPLAY,
            ).filter { !it.isNullOrEmpty() }.joinToString(" · ")
    }

    /** How the system shows the notification with [id] (and [tag]), or `null` when it isn't showing. */
    fun shown(context: Context, id: Int, tag: String? = null): Shown? {
        val active = runCatching { context.getSystemService(NotificationManager::class.java).activeNotifications }.getOrNull() ?: return null
        val notification = active.firstOrNull { it.id == id && it.tag == tag }?.notification ?: return null
        return Shown(
            promoted = notification.flags and FLAG_PROMOTED_ONGOING != 0,
            colorized = notification.extras.getBoolean(NotificationCompat.EXTRA_COLORIZED),
        )
    }
}
