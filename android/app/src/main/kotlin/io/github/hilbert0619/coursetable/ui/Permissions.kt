package io.github.hilbert0619.coursetable.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.hilbert0619.coursetable.alerts.AlertNotifications
import kotlinx.coroutines.delay

/**
 * Asks for notifications (Android 13+; the system shows its prompt at most twice) when a feature that needs them is
 * turned on: class reminders, the live class notification, a timed to-do, a focus session. Returns the function to
 * call; [onResult] gets whether notifications are allowed afterwards.
 */
@Composable
fun rememberNotificationRequest(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted && AlertNotifications.canNotify(context))
    }
    return remember(context, launcher) {
        {
            val asked = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (asked) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onResult(AlertNotifications.canNotify(context))
        }
    }
}

/**
 * A system state read again whenever the screen comes back (e.g. from the system settings), and a second after
 * [refresh] changes (the system shows a posted notification a moment later).
 */
@Composable
fun <T> rememberSystemState(refresh: Int = 0, read: () -> T): T {
    var value by remember { mutableStateOf(read()) }
    val currentRead by rememberUpdatedState(read)
    LifecycleResumeEffect(Unit) {
        value = currentRead()
        onPauseOrDispose { }
    }
    LaunchedEffect(refresh) {
        if (refresh != 0) {
            delay(1_000)
            value = currentRead()
        }
    }
    return value
}
