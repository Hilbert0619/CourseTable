package io.github.hilbert0619.coursetable.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.aikit.TimetablePhoto
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ai.TimetablePhotoReader
import io.github.hilbert0619.coursetable.ai.aiAnswerLanguage
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.Section

private val WARNING = Color(0xFFE08A00)

/**
 * Import → From a Photo (AI) (iOS `ImportView.photoSection`): a photo or screenshot of the timetable becomes CSV in the
 * import field below, through the connection's picture model or the phone's text recognition ([TimetablePhotoReader]).
 */
@Composable
fun ImportPhotoSection(store: AIStore, timetable: Timetable, onReading: (TimetablePhoto.Reading) -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val language = aiAnswerLanguage(LocalConfiguration.current)
    val scope = rememberCoroutineScope()
    val reader = remember { TimetablePhotoReader(scope) }
    DisposableEffect(reader) { onDispose { reader.run.cancel() } }
    var settingUp by remember { mutableStateOf(false) }
    val connection = state.defaultConnection
    val footer = when {
        connection == null -> stringResource(R.string.ai_photo_import_footer)
        connection.pictureConnection != null -> stringResource(R.string.ai_photo_import_picture, connection.name)
        else -> stringResource(R.string.ai_photo_import_text, connection.name)
    }

    Section(title = stringResource(R.string.ai_from_photo), footer = footer) {
        if (!state.isSetUp) {
            ActionRow(R.drawable.ic_sparkles, stringResource(R.string.ai_set_up)) { settingUp = true }
            return@Section
        }
        PhotoSourceButtons(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), enabled = !reader.isWorking) { photo ->
            reader.read(context, photo, store, timetable, language, onReading)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            val error = reader.run.state.error
            when (reader.phase) {
                TimetablePhotoReader.Phase.SCANNING -> Progress(stringResource(R.string.ai_reading_photo))
                TimetablePhotoReader.Phase.ASKING -> if (error != null) {
                    AIErrorView(error, onRetry = { reader.retry(context, store, timetable, language, onReading) })
                } else {
                    Progress(stringResource(R.string.ai_asking, reader.run.state.model))
                }
                TimetablePhotoReader.Phase.NO_TEXT -> Warning(stringResource(R.string.ai_no_text_in_photo))
                TimetablePhotoReader.Phase.NO_SCANNER -> Warning(stringResource(R.string.ai_scan_unavailable))
                TimetablePhotoReader.Phase.NO_TIMETABLE -> Warning(stringResource(R.string.ai_no_timetable))
                TimetablePhotoReader.Phase.DONE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppIcon(R.drawable.ic_sparkles, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.ai_photo_done, reader.run.state.model),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TimetablePhotoReader.Phase.IDLE -> Unit
            }
        }
    }
    if (settingUp) AISettingsDialog(store, onDismiss = { settingUp = false })
}

@Composable
private fun Progress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Warning(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AppIcon(R.drawable.ic_warning, null, Modifier.size(18.dp), tint = WARNING)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = WARNING)
    }
}
