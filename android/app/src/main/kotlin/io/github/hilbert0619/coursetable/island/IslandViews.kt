package io.github.hilbert0619.coursetable.island

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hilbert0619.coursetable.R
import java.time.Instant

/** What the island draws at one moment. */
data class IslandUi(
    /** In [IslandPlanner.order]: the first one is in the pill. */
    val entries: List<IslandEntry>,
    val now: Instant,
    val geometry: IslandGeometry,
    /** The card with every countdown instead of the pill. */
    val expanded: Boolean,
)

/** What the person can do with the island. */
interface IslandCallbacks {
    fun expand()
    fun collapse()

    /** Swiped up: hidden until the countdowns change ([IslandPlanner.phase]). */
    fun hide()
    fun open(entry: IslandEntry)
    fun act(action: IslandAction)

    object None : IslandCallbacks {
        override fun expand() {}
        override fun collapse() {}
        override fun hide() {}
        override fun open(entry: IslandEntry) {}
        override fun act(action: IslandAction) {}
    }
}

/**
 * The island: a black pill around the camera hole with the first countdown's icon on the left and its minutes on the
 * right ("25 min", "Paused"), or, after a tap, a card with every countdown, its time to the second, and the
 * notification's buttons. Sizes ignore the font scale: the island has to fit the status bar.
 */
@Composable
fun IslandContent(ui: IslandUi, callbacks: IslandCallbacks, modifier: Modifier = Modifier) {
    val first = ui.entries.firstOrNull() ?: return
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Box(modifier.animateContentSize()) {
            if (ui.expanded) {
                IslandCard(ui.entries, ui.now, ui.geometry, callbacks)
            } else {
                IslandPill(first.item, ui.now, ui.geometry, callbacks)
            }
        }
    }
}

@Composable
fun IslandPill(item: IslandItem, now: Instant, geometry: IslandGeometry, callbacks: IslandCallbacks, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val text = IslandPlanner.compactText(
        item, now,
        minutes = { resources.getString(R.string.minutes_short, it) },
        paused = resources.getString(R.string.paused),
    )
    val density = LocalDensity.current
    val height = with(density) { geometry.height.toDp() }
    val hideDistance = with(density) { 16.dp.toPx() }
    val accent = accent(item)
    Layout(
        content = {
            Icon(painterResource(icon(item.kind)), null, Modifier.size(18.dp), tint = accent)
            Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        },
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(Color.Black)
            .testTag(PILL_TAG)
            .pointerInput(Unit) { detectTapGestures(onTap = { callbacks.expand() }) }
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -hideDistance) callbacks.hide() },
                ) { change, amount ->
                    dragged += amount
                    change.consume()
                }
            }
            .padding(horizontal = 12.dp),
    ) { measurables, constraints ->
        // Both sides as wide as the wider one, so the camera hole stays in the middle.
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val left = measurables[0].measure(loose)
        val right = measurables[1].measure(loose)
        val side = maxOf(left.width, right.width)
        val width = side * 2 + geometry.cameraWidth
        val pillHeight = constraints.minHeight.coerceAtLeast(maxOf(left.height, right.height))
        layout(width, pillHeight) {
            left.place(0, (pillHeight - left.height) / 2)
            right.place(width - right.width, (pillHeight - right.height) / 2)
        }
    }
}

@Composable
fun IslandCard(entries: List<IslandEntry>, now: Instant, geometry: IslandGeometry, callbacks: IslandCallbacks, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // The card starts at the pill's top; its content starts below the camera.
    val cameraSpace = with(density) { (geometry.cameraBottom - geometry.top).coerceAtLeast(0).toDp() } + 10.dp
    Column(
        modifier
            .padding(horizontal = 10.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Color.Black)
            .padding(start = 16.dp, end = 16.dp, top = cameraSpace, bottom = 12.dp),
    ) {
        entries.take(MAX_CARD_ROWS).forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFF2C2C2E))
            CardRow(entry, now, callbacks)
        }
    }
}

@Composable
private fun CardRow(entry: IslandEntry, now: Instant, callbacks: IslandCallbacks) {
    val item = entry.item
    val accent = accent(item)
    val resources = LocalContext.current.resources
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { callbacks.open(entry) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon(item.kind)), null, Modifier.size(20.dp), tint = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.text.isNotEmpty()) {
                    Text(item.text, color = Color(0xFFAEAEB2), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            val target = item.target
            Text(
                if (target != null) IslandPlanner.clock(target, now) else resources.getString(R.string.paused),
                color = if (target != null) accent else Color(0xFFAEAEB2),
                fontSize = if (target != null) 24.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                // Digits of one width, so the clock doesn't wobble.
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        if (entry.actions.isNotEmpty()) {
            Row(Modifier.padding(start = 50.dp, top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (action in entry.actions) {
                    Text(
                        action.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E))
                            .clickable { callbacks.act(action) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/** The pill, for tests. */
const val PILL_TAG = "island-pill"

/** The card shows at most this many countdowns (there are rarely more than two). */
private const val MAX_CARD_ROWS = 3

@DrawableRes
private fun icon(kind: IslandKind): Int = when (kind) {
    IslandKind.FOCUS -> R.drawable.ic_tab_focus
    IslandKind.CLASS, IslandKind.TEST -> R.drawable.ic_notification_class
}

/** The notification's color, lightened to read on black. */
private fun accent(item: IslandItem): Color =
    if (item.color == 0) Color.White else lerp(Color(item.color), Color.White, 0.35f).copy(alpha = 1f)
