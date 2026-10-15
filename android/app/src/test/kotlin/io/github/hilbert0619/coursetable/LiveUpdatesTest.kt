package io.github.hilbert0619.coursetable

import android.app.Application
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hilbert0619.coursetable.alerts.Channels
import io.github.hilbert0619.coursetable.alerts.LiveUpdates
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The live class and focus notifications as Live Updates (Robolectric, Android 16+). */
@RunWith(AndroidJUnit4::class)
class LiveUpdatesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    /** Like the live class notification (big text) or the focus one (no style). */
    private fun builder(bigText: Boolean): NotificationCompat.Builder = NotificationCompat.Builder(context, Channels.CLASSES)
        .setSmallIcon(R.drawable.ic_notification_class)
        .setColor(0xFF3A4DCD.toInt())
        .setContentTitle("Calculus")
        .setContentText("Until 09:40 · A101 · Li")
        .apply { if (bigText) setStyle(NotificationCompat.BigTextStyle().bigText("Until 09:40 · A101 · Li\nAfter class: Hand in the paper")) }
        .setOngoing(true)
        .setRequestPromotedOngoing(true)

    @Test
    fun theNotificationIsPromotableUnderThePlatformsRules() {
        // Whichever of Android 16's two rule sets this platform uses (colorized or not), the notification qualifies.
        for (bigText in listOf(true, false)) {
            assertTrue("bigText=$bigText", LiveUpdates.build(builder(bigText)).hasPromotableCharacteristics())
        }
    }

    @Test
    fun theIslandShowsWholeMinutesLeft() {
        val target = Instant.parse("2026-09-28T01:40:00Z")
        assertEquals("25 min", LiveUpdates.minutesText(context, target, target.minusSeconds(24 * 60 + 10)))
        assertEquals("1 min", LiveUpdates.minutesText(context, target, target.minusSeconds(5)))
        assertNull(LiveUpdates.minutesText(context, target, target))
    }
}
