package io.github.hilbert0619.coursetable.ui.ai

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.TextReader
import java.io.File
import kotlinx.coroutines.launch

/**
 * "Photo" and "Camera" buttons that read text from a picture on the phone ([TextReader]) and hand it over for the user
 * to check before anything is sent (iOS `TextScanButtons`).
 */
@Composable
fun TextScanButtons(modifier: Modifier = Modifier, onText: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<Int?>(null) }

    fun read(picture: Uri) {
        reading = true
        note = null
        scope.launch {
            val text = try {
                TextReader.read(context, picture).text
            } catch (error: TextReader.Unavailable) {
                note = R.string.ai_scan_unavailable
                null
            } catch (error: Exception) {
                ""
            }
            reading = false
            when {
                text == null -> Unit
                text.isBlank() -> note = R.string.ai_no_text_found
                else -> onText(text)
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::read) }
    var cameraFile by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraFile
        if (taken && uri != null) read(uri)
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !reading) {
            Text(stringResource(R.string.ai_photo))
        }
        OutlinedButton(
            onClick = {
                val uri = scanFile(context)
                cameraFile = uri
                try {
                    takePhoto.launch(uri)
                } catch (error: ActivityNotFoundException) {
                    note = R.string.ai_no_camera
                }
            },
            enabled = !reading,
        ) { Text(stringResource(R.string.ai_camera)) }
        if (reading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            note?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

/** Where the camera saves its photo: `cache/scans/`, shared with the camera app through the FileProvider. */
fun scanFile(context: Context): Uri {
    val folder = File(context.cacheDir, "scans").apply { mkdirs() }
    val file = File(folder, "photo.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}
