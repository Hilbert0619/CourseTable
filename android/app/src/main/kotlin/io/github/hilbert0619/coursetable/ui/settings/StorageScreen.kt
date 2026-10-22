@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.settings

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.coursekit.FolderUsage
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.AIStoreState
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.ai.LocalAI
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.ConfirmDialog
import io.github.hilbert0619.coursetable.ui.components.LocalAppearanceActions
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.focus.LocalWallpaperActions
import io.github.hilbert0619.coursetable.ui.rememberNames
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What Settings → Storage can clear. */
enum class StorageItem(@param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    FOCUS_HISTORY(R.string.focus_history, R.drawable.ic_timer),
    COMPLETED_TODOS(R.string.completed_todos, R.drawable.ic_checklist),
    ASSISTANT_CHATS(R.string.assistant_chats, R.drawable.ic_chat),
    SAVED_ANSWERS(R.string.saved_ai_answers, R.drawable.ic_sparkles),
    AI_USAGE(R.string.ai_usage_counts, R.drawable.ic_bar_chart),
    WALLPAPERS(R.string.focus_wallpapers, R.drawable.ic_photo_library),
    BACKGROUND_PHOTO(R.string.background_photo_title, R.drawable.ic_photo),
    OTHER_FILES(R.string.other_cached_files, R.drawable.ic_document);

    companion object {
        val RECORDS = listOf(FOCUS_HISTORY, COMPLETED_TODOS, ASSISTANT_CHATS, SAVED_ANSWERS, AI_USAGE)
        val FILES = listOf(WALLPAPERS, BACKGROUND_PHOTO, OTHER_FILES)
    }
}

/** The sizes of the files Settings → Storage lists, measured when it opens and after clearing. */
private data class FileSizes(val wallpaperCount: Int = 0, val wallpaperBytes: Long = 0, val backgroundBytes: Long = 0, val otherBytes: Long = 0)

/**
 * Settings → Storage (iOS `StorageView`, docs/todos-focus-spec.md §7): the records and downloaded files the app keeps
 * besides the timetable, and clearing the ones you pick. The timetable, unfinished to-dos, Words progress and AI
 * connections are never cleared here.
 */
@Composable
fun StorageScreen(
    focusSessionCount: Int,
    completedTodoCount: Int,
    actions: AppActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialSelection: Set<StorageItem> = emptySet(),
) {
    val context = LocalContext.current
    val ai = LocalAI.current
    val aiState = ai?.state?.collectAsStateWithLifecycle()?.value ?: AIStoreState()
    val wallpapers = LocalWallpaperActions.current
    val appearance = LocalAppearanceActions.current
    val names = rememberNames()
    var selection by remember { mutableStateOf(initialSelection) }
    var confirming by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    var measured by remember { mutableIntStateOf(0) }
    var sizes by remember { mutableStateOf(FileSizes()) }
    val cache = context.cacheDir
    LaunchedEffect(measured) {
        val usage = wallpapers.cacheUsage()
        sizes = withContext(Dispatchers.IO) {
            FileSizes(usage.count, usage.bytes, appearance.photoBytes(), otherCacheFiles(cache).sumOf { it.length() })
        }
    }

    fun count(item: StorageItem): Long = when (item) {
        StorageItem.FOCUS_HISTORY -> focusSessionCount.toLong()
        StorageItem.COMPLETED_TODOS -> completedTodoCount.toLong()
        StorageItem.ASSISTANT_CHATS -> aiState.conversations.size.toLong()
        StorageItem.SAVED_ANSWERS -> aiState.savedAnswerCount.toLong()
        StorageItem.AI_USAGE -> aiState.countedRequests.toLong()
        StorageItem.WALLPAPERS -> sizes.wallpaperBytes
        StorageItem.BACKGROUND_PHOTO -> sizes.backgroundBytes
        StorageItem.OTHER_FILES -> sizes.otherBytes
    }
    val clearable = StorageItem.entries.filter { count(it) > 0 }.toSet()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.back)) } },
                actions = {
                    val all = selection == clearable && clearable.isNotEmpty()
                    TextButton(onClick = { selection = if (all) emptySet() else clearable }, enabled = clearable.isNotEmpty()) {
                        Text(stringResource(if (all) R.string.deselect_all else R.string.select_all))
                    }
                },
            )
        },
        bottomBar = {
            // Always in reach above the tab bar, like Start Focusing; the list fades out under it.
            val paper = MaterialTheme.colorScheme.background
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to paper.copy(alpha = 0f), 0.4f to paper))
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cleared) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppIcon(R.drawable.ic_check_circle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.cleared), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = { confirming = true },
                    enabled = selection.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.clear_selected), style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).verticalScroll(rememberScrollState())) {
            Section(title = stringResource(R.string.records), footer = stringResource(R.string.records_footer)) {
                StorageItem.RECORDS.forEachIndexed { index, item ->
                    if (index > 0) RowDivider()
                    StorageRow(item, detail(item, count(item), sizes), selected = item in selection, enabled = item in clearable) { on ->
                        selection = if (on) selection + item else selection - item
                        cleared = false
                    }
                }
            }
            Section(title = stringResource(R.string.downloaded_files), footer = stringResource(R.string.downloaded_files_footer)) {
                StorageItem.FILES.forEachIndexed { index, item ->
                    if (index > 0) RowDivider()
                    StorageRow(item, detail(item, count(item), sizes), selected = item in selection, enabled = item in clearable) { on ->
                        selection = if (on) selection + item else selection - item
                        cleared = false
                    }
                }
            }
            Spacer(Modifier.height(padding.calculateBottomPadding() + 8.dp))
        }
    }

    if (confirming) {
        val titles = StorageItem.entries.filter { it in selection }.map { stringResource(it.title) }
        ConfirmDialog(
            title = stringResource(R.string.clear_selected_question),
            text = stringResource(R.string.clear_selected_message, names.list(titles)),
            confirmLabel = stringResource(R.string.clear),
            onConfirm = {
                for (item in selection) {
                    when (item) {
                        StorageItem.FOCUS_HISTORY -> actions.deleteAllFocusSessions()
                        StorageItem.COMPLETED_TODOS -> actions.deleteCompletedTodos()
                        StorageItem.ASSISTANT_CHATS -> ai?.clearConversations()
                        StorageItem.SAVED_ANSWERS -> ai?.clearAnswers()
                        StorageItem.AI_USAGE -> ai?.clearUsage()
                        StorageItem.WALLPAPERS -> wallpapers.clearCache()
                        StorageItem.BACKGROUND_PHOTO -> appearance.removePhoto()
                        StorageItem.OTHER_FILES -> otherCacheFiles(cache).forEach { it.delete() }
                    }
                }
                selection = emptySet()
                cleared = true
                measured++
            },
            onDismiss = { confirming = false },
        )
    }
}

/** The cached files besides the Focus wallpapers (exports, pictures for the Assistant, other caches). */
private fun otherCacheFiles(cache: File): List<File> {
    val wallpapers = File(cache, "Wallpapers")
    return FolderUsage.files(cache).map { it.file }.filter { !it.path.startsWith(wallpapers.path + File.separator) }
}

/** "3 sessions", "2 photos · 1.1 MB", "320 kB" */
@Composable
private fun detail(item: StorageItem, count: Long, sizes: FileSizes): String {
    val context = LocalContext.current
    val n = count.toInt()
    return when (item) {
        StorageItem.FOCUS_HISTORY -> pluralStringResource(R.plurals.storage_sessions, n, n)
        StorageItem.COMPLETED_TODOS -> pluralStringResource(R.plurals.storage_todos, n, n)
        StorageItem.ASSISTANT_CHATS -> pluralStringResource(R.plurals.storage_chats, n, n)
        StorageItem.SAVED_ANSWERS -> pluralStringResource(R.plurals.storage_answers, n, n)
        StorageItem.AI_USAGE -> pluralStringResource(R.plurals.storage_requests, n, n)
        StorageItem.WALLPAPERS -> pluralStringResource(
            R.plurals.storage_photos, sizes.wallpaperCount, sizes.wallpaperCount, Formatter.formatShortFileSize(context, sizes.wallpaperBytes),
        )
        StorageItem.BACKGROUND_PHOTO, StorageItem.OTHER_FILES -> Formatter.formatShortFileSize(context, count)
    }
}

/** A row to pick: a check circle, the title and its count or size, the item's icon. */
@Composable
private fun StorageRow(item: StorageItem, detail: String, selected: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(
            if (selected) R.drawable.ic_todo_done else R.drawable.ic_todo_open, null, Modifier.size(24.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(item.title),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box { AppIcon(item.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
