@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.CourseBlockStyle
import io.github.hilbert0619.coursekit.FocusWallpaper
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.appearance.AppearanceSettings
import io.github.hilbert0619.coursetable.data.DemoData
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.ChoiceRow
import io.github.hilbert0619.coursetable.ui.components.LocalAppearance
import io.github.hilbert0619.coursetable.ui.components.LocalAppearanceActions
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.TimetableBackdrop
import io.github.hilbert0619.coursetable.ui.focus.LocalWallpaperActions
import io.github.hilbert0619.coursetable.ui.theme.color
import io.github.hilbert0619.coursetable.ui.timetable.CourseBlock

/** Settings → Timetable Appearance (iOS 0.7.0 `TimetableAppearanceView`): how course blocks look, and a photo behind the timetable. */
@Composable
fun TimetableAppearanceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val appearance = LocalAppearance.current
    val actions = LocalAppearanceActions.current
    val wallpaperActions = LocalWallpaperActions.current
    val settings = appearance.settings
    val photo = appearance.photo
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(actions::setPhoto) }
    val wallpapers = remember { wallpaperActions.downloaded() }
    val thumbnails = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(wallpapers) {
        for (wallpaper in wallpapers) {
            if (wallpaper.id !in thumbnails) wallpaperActions.thumbnail(wallpaper, 192, 330)?.let { thumbnails[wallpaper.id] = it }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timetable_appearance)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(title = stringResource(R.string.course_blocks_header), footer = stringResource(R.string.course_blocks_footer)) {
                ChoiceRow(
                    listOf(CourseBlockStyle.SOFT, CourseBlockStyle.SOLID), settings.blockStyle,
                    label = { stringResource(if (it == CourseBlockStyle.SOFT) R.string.block_style_soft else R.string.block_style_solid) },
                ) { style -> actions.update { it.copy(blockStyle = style) } }
                BlockPreview(settings.blockStyle, if (photo == null) 1f else settings.blockOpacity)
            }

            Section(
                title = stringResource(R.string.background),
                footer = stringResource(if (wallpapers.isEmpty()) R.string.background_footer_own else R.string.background_footer_wallpapers),
            ) {
                if (photo != null) {
                    Box(Modifier.fillMaxWidth().height(160.dp).clipToBounds()) {
                        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = settings.fade)))
                    }
                    SliderRow(stringResource(R.string.fade), settings.fade, AppearanceSettings.FADE_RANGE) { value -> actions.update { it.copy(fade = value) } }
                    RowDivider()
                    SliderRow(stringResource(R.string.block_opacity), settings.blockOpacity, AppearanceSettings.BLOCK_OPACITY_RANGE) { value ->
                        actions.update { it.copy(blockOpacity = value) }
                    }
                    RowDivider()
                    ActionRow(R.drawable.ic_delete, stringResource(R.string.remove_photo), color = MaterialTheme.colorScheme.error) { actions.removePhoto() }
                    RowDivider()
                }
                ActionRow(R.drawable.ic_photo_library, stringResource(if (photo == null) R.string.choose_a_photo else R.string.choose_another_photo)) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (wallpapers.isNotEmpty()) {
                    LazyRow(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(wallpapers, key = { it.id }) { wallpaper ->
                            WallpaperThumbnail(wallpaper, thumbnails[wallpaper.id]) { actions.setPhoto(wallpaper) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(132.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).semantics { contentDescription = label })
    }
}

@Composable
private fun WallpaperThumbnail(wallpaper: FocusWallpaper, thumbnail: ImageBitmap?, onPick: () -> Unit) {
    val description = stringResource(R.string.photo_by, wallpaper.author)
    Box(
        Modifier.size(64.dp, 110.dp).clip(RoundedCornerShape(10.dp)).background(wallpaper.rgb?.color ?: Color.Gray)
            .clickable(onClick = onPick).semantics { contentDescription = description },
    ) {
        if (thumbnail != null) Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Three sample blocks in the chosen style, on the paper (and the photo, if there is one). */
@Composable
private fun BlockPreview(style: CourseBlockStyle, fillOpacity: Float) {
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val today = LocalNowSource.current.today()
    val samples = remember(chinese, today) { DemoData.courses(today, chinese).take(3) }
    Box(Modifier.fillMaxWidth().height(120.dp).clipToBounds()) {
        TimetableBackdrop()
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            samples.forEach { course ->
                CourseBlock(course, Modifier.weight(1f).height(96.dp), style = style, fillOpacity = fillOpacity)
            }
        }
    }
}
