@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.alerts.SystemSettings
import io.github.hilbert0619.coursetable.ui.AppActions
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.rememberNotificationRequest
import io.github.hilbert0619.coursetable.ui.rememberSystemState

/**
 * Settings → Reminders → Reliable Reminders: what keeps alerts on time on Android (notifications, exact alarms,
 * battery optimisation, Live Updates) with a way to fix each, the steps for vivo/iQOO's background limits, and a test.
 * Each check is read again when coming back from the system settings.
 */
@Composable
fun ReliableRemindersScreen(actions: AppActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notifications = rememberSystemState { SystemSettings.notificationsAllowed(context) }
    val exactAlarms = rememberSystemState { SystemSettings.exactAlarmsAllowed(context) }
    val battery = rememberSystemState { SystemSettings.ignoringBatteryOptimizations(context) }
    val liveUpdates = rememberSystemState { SystemSettings.liveUpdatesAllowed(context) }
    val askForNotifications = rememberNotificationRequest { allowed -> if (!allowed) SystemSettings.openNotificationSettings(context) }
    var testSent by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reliable_reminders)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.reliable_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            Section(title = stringResource(R.string.permissions)) {
                CheckRow(
                    R.drawable.ic_notifications, stringResource(R.string.notifications_label), notifications,
                    stringResource(if (notifications) R.string.status_allowed else R.string.status_off),
                ) { askForNotifications() }
                RowDivider()
                CheckRow(
                    R.drawable.ic_alarm, stringResource(R.string.exact_alarms), exactAlarms,
                    stringResource(if (exactAlarms) R.string.status_allowed else R.string.status_not_allowed),
                ) { SystemSettings.openExactAlarmSettings(context) }
                RowDivider()
                CheckRow(
                    R.drawable.ic_battery, stringResource(R.string.battery_optimization), battery,
                    stringResource(if (battery) R.string.status_unrestricted else R.string.status_optimized),
                ) { SystemSettings.requestIgnoreBatteryOptimizations(context) }
                if (liveUpdates != null) {
                    RowDivider()
                    CheckRow(
                        R.drawable.ic_live_update, stringResource(R.string.live_updates), liveUpdates,
                        stringResource(if (liveUpdates) R.string.status_allowed else R.string.status_off),
                    ) { SystemSettings.openLiveUpdateSettings(context) }
                }
            }

            Section(title = stringResource(R.string.background_running), footer = stringResource(R.string.menu_names_differ)) {
                Text(
                    stringResource(if (SystemSettings.isVivo) R.string.vivo_steps else R.string.other_steps),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                RowDivider()
                ActionRow(R.drawable.ic_autostart, stringResource(R.string.open_autostart)) { SystemSettings.openAutoStartSettings(context) }
                RowDivider()
                ActionRow(R.drawable.ic_open, stringResource(R.string.open_app_info)) { SystemSettings.openAppInfo(context) }
            }

            Section(footer = stringResource(if (testSent) R.string.test_sent else R.string.test_footer)) {
                ActionRow(R.drawable.ic_send, stringResource(R.string.send_test_reminder)) {
                    askForNotifications()
                    actions.sendTestReminder()
                    testSent = true
                }
            }
        }
    }
}

/** One check: what it is, whether it's fine, and a tap to the system page that changes it. */
@Composable
private fun CheckRow(@DrawableRes icon: Int, title: String, ok: Boolean, status: String, onClick: () -> Unit) {
    val statusColor = if (ok) OK_GREEN else MaterialTheme.colorScheme.tertiary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
        }
        AppIcon(if (ok) R.drawable.ic_check_circle else R.drawable.ic_warning, null, Modifier.size(22.dp), tint = statusColor)
    }
}

private val OK_GREEN = Color(0xFF2E9E4F)
