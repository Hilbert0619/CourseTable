@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.hilbert0619.coursekit.BackgroundChoice
import io.github.hilbert0619.coursekit.CourseBlockStyle
import io.github.hilbert0619.coursekit.FocusWallpaper
import io.github.hilbert0619.coursekit.SoftWallpaper
import io.github.hilbert0619.coursetable.BuildConfig
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.appearance.AppStyle
import io.github.hilbert0619.coursetable.appearance.AppearanceSettings
import io.github.hilbert0619.coursetable.data.DemoData
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppBackdrop
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.ChoiceRow
import io.github.hilbert0619.coursetable.ui.components.GlassButton
import io.github.hilbert0619.coursetable.ui.components.LocalAppearance
import io.github.hilbert0619.coursetable.ui.components.LocalAppearanceActions
import io.github.hilbert0619.coursetable.ui.components.LocalGlass
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.SoftWallpaperView
import io.github.hilbert0619.coursetable.ui.components.glassCard
import io.github.hilbert0619.coursetable.ui.components.title
import io.github.hilbert0619.coursetable.ui.focus.LocalWallpaperActions
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette
import io.github.hilbert0619.coursetable.ui.theme.color
import io.github.hilbert0619.coursetable.ui.timetable.CourseBlock

/**
 * Settings → Appearance (iOS `AppearanceView`, docs/design-0.7.md §5): glass or classic, what's behind it (a built-in
 * wallpaper, your photo or a Focus wallpaper you've seen, or the paper), and how course blocks look.
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val appearance = LocalAppearance.current
    val actions = LocalAppearanceActions.current
    val wallpaperActions = LocalWallpaperActions.current
    val settings = appearance.settings
    val choice = appearance.choice
    val photo = appearance.photo
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(actions::setPhoto) }
    val wallpapers = remember { wallpaperActions.downloaded() }
    val thumbnails = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(wallpapers) {
        for (wallpaper in wallpapers) {
            if (wallpaper.id !in thumbnails) wallpaperActions.thumbnail(wallpaper, 192, 330)?.let { thumbnails[wallpaper.id] = it }
        }
    }
    fun choose(background: BackgroundChoice) = actions.update { it.copy(background = background.stored) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(
                title = stringResource(R.string.style),
                footer = stringResource(
                    when {
                        !settings.isGlass -> R.string.style_footer_classic
                        BuildConfig.WORDS -> R.string.style_footer_glass
                        else -> R.string.style_footer_glass_no_words
                    },
                ),
            ) {
                ChoiceRow(
                    listOf(AppStyle.GLASS, AppStyle.CLASSIC), settings.style,
                    label = { stringResource(if (it == AppStyle.GLASS) R.string.style_glass else R.string.style_classic) },
                ) { style -> actions.update { it.copy(style = style) } }
                StylePreview(settings.style)
            }

            Section(
                title = stringResource(R.string.background),
                footer = stringResource(if (wallpapers.isEmpty()) R.string.background_footer_own else R.string.background_footer_wallpapers),
            ) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    item("paper") {
                        BackgroundTile(stringResource(R.string.background_paper), choice == BackgroundChoice.Paper, { choose(BackgroundChoice.Paper) }) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        }
                    }
                    // Your photo first: it's the one most likely chosen, and stays in view.
                    if (photo != null) {
                        item("photo") {
                            BackgroundTile(stringResource(R.string.background_photo), choice == BackgroundChoice.Photo, { choose(BackgroundChoice.Photo) }) {
                                Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    items(SoftWallpaper.entries, key = { it.id }) { wallpaper ->
                        val option = BackgroundChoice.Wallpaper(wallpaper)
                        BackgroundTile(wallpaper.title(), choice == option, { choose(option) }) { SoftWallpaperView(wallpaper) }
                    }
                }
                RowDivider()
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
                if (choice == BackgroundChoice.Photo) {
                    RowDivider()
                    SliderRow(stringResource(R.string.fade), settings.photoFade, AppearanceSettings.FADE_RANGE) { value ->
                        actions.update { if (it.isGlass) it.copy(glassFade = value) else it.copy(fade = value) }
                    }
                    RowDivider()
                    ActionRow(R.drawable.ic_delete, stringResource(R.string.remove_photo), color = MaterialTheme.colorScheme.error) { actions.removePhoto() }
                }
            }

            Section(title = stringResource(R.string.course_blocks_header), footer = stringResource(R.string.course_blocks_footer)) {
                ChoiceRow(
                    listOf(CourseBlockStyle.SOFT, CourseBlockStyle.SOLID), settings.blockStyle,
                    label = { stringResource(if (it == CourseBlockStyle.SOFT) R.string.block_style_soft else R.string.block_style_solid) },
                ) { style -> actions.update { it.copy(blockStyle = style) } }
                BlockPreview(settings.blockStyle, settings.isGlass, if (choice == BackgroundChoice.Paper) 1f else settings.blockOpacity)
                if (choice != BackgroundChoice.Paper) {
                    SliderRow(stringResource(R.string.block_opacity), settings.blockOpacity, AppearanceSettings.BLOCK_OPACITY_RANGE) { value ->
                        actions.update { it.copy(blockOpacity = value) }
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

/** One background to pick: a phone-shaped picture with its name, ringed when chosen. */
@Composable
private fun BackgroundTile(title: String, isSelected: Boolean, onPick: () -> Unit, picture: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.width(64.dp).clickable(role = Role.RadioButton, onClick = onPick).semantics { selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(64.dp, 110.dp).clip(shape)
                .border(if (isSelected) 3.dp else 0.5.dp, if (isSelected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape),
        ) {
            picture()
            if (isSelected) {
                AppIcon(R.drawable.ic_check_circle, null, Modifier.align(Alignment.BottomEnd).padding(5.dp).size(20.dp), tint = accent)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

/** The demo timetable's courses, in the app's language. */
@Composable
private fun sampleCourses() = run {
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val today = LocalNowSource.current.today()
    remember(chinese, today) { DemoData.courses(today, chinese) }
}

/** On the background in effect, in [style]: glass needs the background captured right here. */
@Composable
private fun OnBackground(style: AppStyle, height: Int, content: @Composable () -> Unit) {
    val haze = rememberHazeState()
    val glass = style == AppStyle.GLASS
    Box(Modifier.fillMaxWidth().height(height.dp).clipToBounds()) {
        if (glass) {
            AppBackdrop(Modifier.hazeSource(haze))
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        CompositionLocalProvider(LocalGlass provides if (glass) haze else null, content = content)
    }
}

/** The look at a glance: a card and a button on the background, in glass or on the paper. */
@Composable
private fun StylePreview(style: AppStyle) {
    val course = sampleCourses()[1]
    val tint = CoursePalette.block(course.colorIndex, CourseBlockStyle.SOFT)
    OnBackground(style, 130) {
        Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).glassCard(tint.fill).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tint.text)
                Text("${course.room} · 10:00", style = MaterialTheme.typography.bodyMedium, color = tint.text.copy(alpha = 0.85f))
            }
            GlassButton(onClick = {}, prominent = true, modifier = Modifier.height(48.dp)) {
                AppIcon(R.drawable.ic_play, null, Modifier.size(20.dp))
            }
        }
    }
}

/** Three sample blocks in the chosen style, on the background as the timetable shows it. */
@Composable
private fun BlockPreview(style: CourseBlockStyle, glass: Boolean, fillOpacity: Float) {
    val samples = sampleCourses().take(3)
    val haze = rememberHazeState()
    Box(Modifier.fillMaxWidth().height(120.dp).clipToBounds()) {
        AppBackdrop(Modifier.hazeSource(haze))
        CompositionLocalProvider(LocalGlass provides if (glass) haze else null) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                samples.forEach { course ->
                    CourseBlock(course, Modifier.weight(1f).height(96.dp), style = style, fillOpacity = fillOpacity)
                }
            }
        }
    }
}
