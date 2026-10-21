@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.ai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.aikit.AIConnection
import io.github.hilbert0619.aikit.AIError
import io.github.hilbert0619.aikit.AIImage
import io.github.hilbert0619.aikit.AIPrompts
import io.github.hilbert0619.aikit.AIProvider
import io.github.hilbert0619.aikit.AIWireFormat
import io.github.hilbert0619.aikit.modelIDs
import io.github.hilbert0619.aikit.modelsRequest
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.ConfirmDialog
import io.github.hilbert0619.coursetable.ui.components.EditorDialog
import io.github.hilbert0619.coursetable.ui.components.EditorScaffold
import io.github.hilbert0619.coursetable.ui.components.MenuRow
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.SwitchRow
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.launch

private val OK_GREEN = Color(0xFF2E9E4F)
private val WARNING = Color(0xFFE08A00)

/** One line of the privacy summary, with its icon. */
@Composable
private fun PrivacyLine(@DrawableRes icon: Int, text: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** AI Help → Privacy → What's Sent: the full account of what goes where. */
@Composable
private fun AIPrivacyDetails(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_privacy)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section {
                listOf(
                    R.string.ai_privacy_direct, R.string.ai_privacy_minimal, R.string.ai_privacy_tools,
                    R.string.ai_privacy_photos, R.string.ai_privacy_wrong,
                ).forEachIndexed { index, text ->
                    if (index > 0) RowDivider()
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

/**
 * Settings → AI Help (iOS `AISettingsView`): the user's connections (provider, key, model), which one AI help uses,
 * this month's usage, and what is sent where.
 */
@Composable
fun AISettingsScreen(store: AIStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by store.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AIConnection?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    var showingPrivacy by rememberSaveable { mutableStateOf(false) }
    val defaultId = state.defaultConnection?.id
    if (showingPrivacy) {
        BackHandler { showingPrivacy = false }
        AIPrivacyDetails(onBack = { showingPrivacy = false }, modifier = modifier)
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_help)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(
                title = stringResource(R.string.ai_connections),
                footer = if (state.connections.size > 1) stringResource(R.string.ai_default_footer) else null,
            ) {
                for (connection in state.connections) {
                    ConnectionRow(connection, isDefault = connection.id == defaultId, usage = store.usage(connection)) { editing = connection }
                    RowDivider()
                }
                ActionRow(R.drawable.ic_add, stringResource(R.string.ai_add_connection)) { adding = true }
            }

            Section(title = stringResource(R.string.ai_privacy)) {
                PrivacyLine(R.drawable.ic_arrow_outward, stringResource(R.string.ai_privacy_short_direct))
                RowDivider()
                PrivacyLine(R.drawable.ic_key, stringResource(R.string.ai_privacy_short_key))
                RowDivider()
                PrivacyLine(R.drawable.ic_touch, stringResource(R.string.ai_privacy_short_confirm))
                RowDivider()
                ActionRow(null, stringResource(R.string.ai_whats_sent)) { showingPrivacy = true }
            }

            if (state.savedAnswerCount > 0 || state.conversations.isNotEmpty()) {
                Section(footer = stringResource(R.string.ai_saved_footer)) {
                    ActionRow(R.drawable.ic_delete, stringResource(R.string.ai_delete_saved), color = MaterialTheme.colorScheme.error) {
                        confirmingClear = true
                    }
                }
            }
        }
    }

    if (adding) AIConnectionEditor(store, null, onDismiss = { adding = false })
    editing?.let { connection -> AIConnectionEditor(store, connection, onDismiss = { editing = null }) }
    if (confirmingClear) {
        ConfirmDialog(
            title = stringResource(R.string.ai_delete_saved_question), text = null, confirmLabel = stringResource(R.string.ai_delete_saved),
            onConfirm = store::clearSavedAnswers, onDismiss = { confirmingClear = false },
        )
    }
}

@Composable
private fun ConnectionRow(connection: AIConnection, isDefault: Boolean, usage: io.github.hilbert0619.coursetable.ai.AIUsageTotal, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(connection.name, style = MaterialTheme.typography.bodyLarge)
            Text(connection.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (usage.requests > 0) {
                Text(
                    stringResource(R.string.ai_month_requests, usage.requests) + " · " +
                        stringResource(R.string.ai_tokens, usage.inputTokens + usage.outputTokens),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isDefault) AppIcon(R.drawable.ic_check, stringResource(R.string.ai_used_for_help), tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Adds or edits a connection (iOS `AIConnectionEditor`): provider, key, model (typed or loaded from the provider),
 * address, the Assistant's switches, and a test. Full screen over the settings; [inDialog] `false` draws it in place
 * (screenshots).
 */
@Composable
fun AIConnectionEditor(store: AIStore, connection: AIConnection?, onDismiss: () -> Unit, inDialog: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialProvider = connection?.provider ?: AIProvider.preset("deepseek")
    val initial = connection ?: initialProvider?.let { AIConnection(it) }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var providerID by rememberSaveable { mutableStateOf(connection?.providerID ?: "deepseek") }
    var name by rememberSaveable { mutableStateOf(connection?.name ?: "") }
    var baseURL by rememberSaveable { mutableStateOf(initial?.baseURL ?: "") }
    var model by rememberSaveable { mutableStateOf(initial?.model ?: "") }
    var key by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(emptyList<String>()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<AIError?>(null) }
    var toolsOn by rememberSaveable { mutableStateOf(initial?.usesTools ?: true) }
    var imagesOn by rememberSaveable { mutableStateOf(initial?.readsImages ?: false) }
    var photoModel by rememberSaveable { mutableStateOf(initial?.photoModel ?: "") }
    val test = rememberAIRun()
    val toolTest = rememberAIRun()
    val imageTest = rememberAIRun()
    var testedOK by remember { mutableStateOf(false) }
    var toolsWork by remember { mutableStateOf<Boolean?>(null) }
    var toolsTurnedOff by remember { mutableStateOf(false) }
    var readsPhotos by remember { mutableStateOf<Boolean?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val provider = AIProvider.preset(providerID)
    val hasStoredKey = connection?.let(store::hasKey) ?: false

    /** The connection as filled in. The switches are kept only where they differ from the preset. */
    fun draft(): AIConnection {
        val plain = AIConnection(
            id = connection?.id ?: UUID.fromString(newId), providerID = providerID,
            name = name.trim().ifEmpty { provider?.name ?: "AI" }, format = provider?.format ?: AIWireFormat.OPEN_AI,
            baseURL = baseURL, model = model.trim(),
        )
        val photo = photoModel.trim()
        return plain.copy(
            toolsSwitch = if (toolsOn == plain.usesTools) null else toolsOn,
            imagesSwitch = if (imagesOn == plain.readsImages) null else imagesOn,
            photoModelChoice = if (photo == (plain.photoModel ?: "")) null else photo,
        )
    }

    /** The key to use: the one typed, or the stored one. */
    fun effectiveKey(): String? = key.trim().ifEmpty { null } ?: connection?.let(store::key)

    fun resetTests() {
        testedOK = false
        toolsWork = null
        toolsTurnedOff = false
        readsPhotos = null
        test.reset()
        toolTest.reset()
        imageTest.reset()
    }

    fun setSwitches(source: AIConnection) {
        toolsOn = source.usesTools
        imagesOn = source.readsImages
        photoModel = source.photoModel ?: ""
    }

    fun chooseProvider(id: String) {
        if (id == providerID) return
        providerID = id
        val chosen = AIProvider.preset(id) ?: return
        baseURL = chosen.baseURL
        model = chosen.defaultModel
        models = emptyList()
        modelsError = null
        resetTests()
        setSwitches(AIConnection(chosen))
    }

    /** A model the user changed gets the preset's answer for whether it reads photos. */
    fun chooseModel(value: String) {
        val old = model
        model = value
        if (old.isNotEmpty()) imagesOn = draft().copy(imagesSwitch = null).readsImages
    }

    val current = draft()
    val canSave = current.normalizedBaseURL != null && current.model.isNotEmpty() && (provider?.needsKey == false || effectiveKey() != null)

    fun testPhotos(tested: AIConnection, testKey: String?) {
        val pictures = tested.pictureConnection ?: return
        imageTest.start(AIPrompts.imageTest(AIImage(redSquare())), store, pictures, testKey, onEnd = { answer ->
            readsPhotos = imageTest.state.error == null && answer.text.lowercase().contains("red")
        })
    }

    fun testTools(tested: AIConnection, testKey: String?) {
        toolTest.start(AIPrompts.toolTest, store, tested, testKey, onEnd = { answer ->
            val error = toolTest.state.error
            if (error != null) {
                if (error.rejectsTools) {
                    toolsOn = false
                    toolsTurnedOff = true
                } else {
                    toolsWork = false
                }
            } else {
                toolsWork = answer.toolCalls.any { it.name == "ping" }
            }
            testPhotos(tested, testKey)
        })
    }

    /** A one-word answer; then, with tools on, a call of a test tool; then, when photos go somewhere, a red picture. */
    fun runTest() {
        resetTests()
        val tested = draft()
        val testKey = effectiveKey()
        test.start(AIPrompts.connectionTest, store, tested, testKey, onComplete = {
            testedOK = true
            if (toolsOn) testTools(tested, testKey) else testPhotos(tested, testKey)
        })
    }

    fun loadModels() {
        loadingModels = true
        modelsError = null
        val request = try {
            current.modelsRequest(effectiveKey())
        } catch (error: AIError) {
            modelsError = error
            loadingModels = false
            return
        }
        scope.launch {
            try {
                models = modelIDs(store.transport.data(request))
                if (model.isEmpty()) models.firstOrNull()?.let { model = it }
            } catch (error: AIError) {
                modelsError = error
            }
            loadingModels = false
        }
    }

    EditorFrame(inDialog, onDismiss) {
        EditorScaffold(
            title = connection?.name ?: stringResource(R.string.ai_new_connection),
            onDismiss = onDismiss,
            confirmLabel = stringResource(R.string.save),
            confirmEnabled = canSave,
            onConfirm = {
                store.save(draft(), key.trim().ifEmpty { null })
                onDismiss()
            },
        ) {
            Section {
                if (connection == null) {
                    MenuRow(stringResource(R.string.ai_provider), providerID, AIProvider.PRESETS.map { it.id }, { id -> providerLabel(id) }) {
                        chooseProvider(it)
                    }
                } else {
                    io.github.hilbert0619.coursetable.ui.components.ValueRow(stringResource(R.string.ai_provider), provider?.name ?: providerID)
                }
                RowDivider()
                Field(stringResource(R.string.ai_name), name, placeholder = provider?.name) { name = it }
                provider?.keyPage?.let { page ->
                    RowDivider()
                    ActionRow(R.drawable.ic_open, stringResource(R.string.ai_get_key, provider.name)) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page)))
                        } catch (error: ActivityNotFoundException) {
                            // No browser: nothing to open.
                        }
                    }
                }
            }

            if (provider?.needsKey != false) {
                Section(title = stringResource(R.string.ai_api_key_title), footer = stringResource(R.string.ai_key_footer)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        // An empty field shows its label, so a saved key is mentioned there.
                        label = { Text(stringResource(if (hasStoredKey) R.string.ai_key_saved else R.string.ai_api_key)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            val suggested = provider?.models.orEmpty()
            Section(
                title = stringResource(R.string.ai_model),
                footer = if (suggested.isNotEmpty()) stringResource(R.string.ai_suggested, suggested.joinToString(", ")) else null,
            ) {
                Field(stringResource(R.string.ai_model), model, keyboardType = KeyboardType.Uri) { chooseModel(it) }
                if (models.isNotEmpty()) {
                    RowDivider()
                    val options = if (model in models) models else models + model
                    MenuRow(stringResource(R.string.ai_available_models), model, options, { it }) { chooseModel(it) }
                }
                RowDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        ActionRow(R.drawable.ic_refresh, stringResource(R.string.ai_load_models), enabled = !loadingModels && current.normalizedBaseURL != null) {
                            loadModels()
                        }
                    }
                    if (loadingModels) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                }
                modelsError?.let { AIErrorView(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }

            val addressFooter = when {
                providerID == "local" -> stringResource(R.string.ai_address_local)
                current.isInsecure -> stringResource(R.string.ai_address_insecure)
                current.normalizedBaseURL == null -> stringResource(R.string.ai_address_hint)
                else -> null
            }
            Section(title = stringResource(R.string.ai_address), footer = addressFooter) {
                Field(stringResource(R.string.ai_address), baseURL, keyboardType = KeyboardType.Uri) { baseURL = it }
            }

            Section(title = stringResource(R.string.ai_assistant), footer = stringResource(R.string.ai_assistant_footer)) {
                SwitchRow(stringResource(R.string.ai_use_tools), toolsOn) { toolsOn = it }
                RowDivider()
                SwitchRow(stringResource(R.string.ai_model_reads_photos), imagesOn) { imagesOn = it }
                if (!imagesOn) {
                    RowDivider()
                    Field(stringResource(R.string.ai_photo_model), photoModel, placeholder = stringResource(R.string.ai_none), keyboardType = KeyboardType.Uri) {
                        photoModel = it
                    }
                }
            }

            Section {
                ActionRow(R.drawable.ic_bolt, stringResource(R.string.ai_test_connection), enabled = canSave && !test.state.isRunning) { runTest() }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val testError = test.state.error
                    when {
                        test.state.isRunning -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        testError != null -> AIErrorView(testError)
                        testedOK -> Result(true, stringResource(R.string.ai_test_ok))
                    }
                    if (toolTest.state.isRunning || imageTest.state.isRunning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    when {
                        toolsTurnedOff -> Result(false, stringResource(R.string.ai_tools_turned_off))
                        toolsWork == true -> Result(true, stringResource(R.string.ai_tools_ok))
                        toolsWork == false -> Result(false, stringResource(R.string.ai_tools_not_called))
                    }
                    when (readsPhotos) {
                        true -> Result(true, stringResource(R.string.ai_photos_ok))
                        false -> Result(false, stringResource(R.string.ai_photos_failed))
                        null -> Unit
                    }
                }
            }

            if (connection != null) {
                Section {
                    if (store.state.value.defaultConnection?.id != connection.id) {
                        ActionRow(R.drawable.ic_check_circle, stringResource(R.string.ai_use_for_help)) {
                            store.makeDefault(connection)
                            onDismiss()
                        }
                        RowDivider()
                    }
                    ActionRow(R.drawable.ic_delete, stringResource(R.string.ai_delete_connection), color = MaterialTheme.colorScheme.error) {
                        confirmingDelete = true
                    }
                }
            }
        }
    }

    if (confirmingDelete && connection != null) {
        ConfirmDialog(
            title = stringResource(R.string.ai_delete_connection), text = null, confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                store.delete(connection)
                onDismiss()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun EditorFrame(inDialog: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (inDialog) EditorDialog(onDismiss, content) else content()
}

/** "DeepSeek · No VPN needed in mainland China" in the provider menu, grouped as on iOS. */
@Composable
private fun providerLabel(id: String): String {
    val provider = AIProvider.preset(id) ?: return id
    val group = when (provider.region) {
        AIProvider.Region.CHINA -> stringResource(R.string.ai_region_china)
        AIProvider.Region.INTERNATIONAL -> stringResource(R.string.ai_region_international)
        AIProvider.Region.LOCAL, AIProvider.Region.CUSTOM -> stringResource(R.string.ai_region_other)
    }
    return "${provider.name} · $group"
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Result(ok: Boolean, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(if (ok) R.drawable.ic_check_circle else R.drawable.ic_warning, null, Modifier.size(20.dp), tint = if (ok) OK_GREEN else WARNING)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (ok) OK_GREEN else WARNING)
    }
}

/** A red 64-pixel square as JPEG: the picture check. */
private fun redSquare(): ByteArray {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(android.graphics.Color.RED)
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
    bitmap.recycle()
    return output.toByteArray()
}
