package io.github.hilbert0619.coursetable.ui.focus

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.FocusWallpaperLayout
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.focus.ShownWallpaper
import io.github.hilbert0619.coursetable.focus.WallpaperActions
import io.github.hilbert0619.coursetable.focus.WallpaperState
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.theme.color
import java.net.URI

/** The Focus wallpapers' state; off (nothing shown) in screenshots unless a test provides one. */
val LocalWallpapers = compositionLocalOf { WallpaperState(enabled = false) }
val LocalWallpaperActions = staticCompositionLocalOf<WallpaperActions> { WallpaperActions.None }

/**
 * A wallpaper filling its box: its average colour until it loads, placed so its subject stays in view, and darkened
 * towards the bottom so white text reads on any photo (iOS `WallpaperBackground`).
 */
@Composable
fun WallpaperBackground(shown: ShownWallpaper, modifier: Modifier = Modifier) {
    val average = shown.wallpaper.rgb?.color ?: Color.Gray
    BoxWithConstraints(modifier.fillMaxSize().background(average).clipToBounds()) {
        Crossfade(shown.image, label = "wallpaper") { image ->
            if (image == null) return@Crossfade
            val width = constraints.maxWidth.toDouble()
            val height = constraints.maxHeight.toDouble()
            val frame = FocusWallpaperLayout.frame(image.width.toDouble(), image.height.toDouble(), width, height, shown.wallpaper.focusY)
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image,
                    dstOffset = IntOffset(frame.x.toInt(), frame.y.toInt()),
                    dstSize = IntSize(frame.width.toInt(), frame.height.toInt()),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.38f), Color.Black.copy(alpha = 0.55f)))))
    }
}

/** "Photo by … on Unsplash" (opens the photo's page) and a button for another wallpaper (iOS `WallpaperCredit`). */
@Composable
fun WallpaperCredit(shown: ShownWallpaper, isLoading: Boolean, onAnother: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wallpaper = shown.wallpaper
    val tint = Color.White.copy(alpha = 0.85f)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (wallpaper.author.isNotEmpty()) {
            val page = wallpaper.sourceUrl
            val onUnsplash = page?.let { runCatching { URI(it).host }.getOrNull() }?.endsWith("unsplash.com") == true
            TextButton(
                onClick = { page?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                enabled = page != null,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(
                    if (onUnsplash) stringResource(R.string.photo_by_on_unsplash, wallpaper.author) else stringResource(R.string.photo_by, wallpaper.author),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onAnother, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
            } else {
                AppIcon(R.drawable.ic_shuffle, stringResource(R.string.another_wallpaper), Modifier.size(20.dp), tint = tint)
            }
        }
    }
}
