package io.github.hilbert0619.coursetable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Moment
import java.time.Clock
import kotlinx.coroutines.delay

/** Where "now" comes from: the system clock, or a fixed time in screenshots ([ticks] off). */
class NowSource(val clock: Clock, val ticks: Boolean = true) {
    fun moment(): Moment = Moment.from(clock.instant(), clock.zone)
    fun today(): LocalDate = moment().day
}

val LocalNowSource = staticCompositionLocalOf { NowSource(Clock.systemDefaultZone()) }

/** The current moment, updated at the start of every minute (the iOS TimelineView(.everyMinute)). */
@Composable
fun currentMoment(): Moment {
    val source = LocalNowSource.current
    var now by remember(source) { mutableStateOf(source.moment()) }
    if (source.ticks) {
        LaunchedEffect(source) {
            while (true) {
                delay(60_000 - source.clock.millis() % 60_000 + 20)
                now = source.moment()
            }
        }
    }
    return now
}
