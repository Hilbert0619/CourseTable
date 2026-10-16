@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.ai

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.aikit.AIImage
import io.github.hilbert0619.aikit.AIMessage
import io.github.hilbert0619.aikit.AIPrompts
import io.github.hilbert0619.aikit.AppTools
import io.github.hilbert0619.aikit.TimetablePhoto
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.AIConversation
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ai.AgentCard
import io.github.hilbert0619.coursetable.ai.AgentContext
import io.github.hilbert0619.coursetable.ai.AgentRun
import io.github.hilbert0619.coursetable.ai.AgentTexts
import io.github.hilbert0619.coursetable.ai.PhotoPrep
import io.github.hilbert0619.coursetable.ai.TextReader
import io.github.hilbert0619.coursetable.ai.aiAnswerLanguage
import io.github.hilbert0619.coursetable.ai.typedPart
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.EditorDialog
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.timetable.ImportScreen
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** The app as the Assistant's tools see it, provided by the root screen; `null` where the Assistant isn't offered. */
val LocalAgentContext = compositionLocalOf<AgentContext?> { null }

private val OK_GREEN = Color(0xFF2E9E4F)

/** The ✨ button in the Today and Words toolbars: opens the Assistant. Nothing where it isn't offered. */
@Composable
fun AssistantButton(startMessage: String? = null) {
    LocalAI.current ?: return
    LocalAgentContext.current ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { AppIcon(R.drawable.ic_sparkles, stringResource(R.string.agent_assistant)) }
    if (open) AssistantDialog(startMessage, onDismiss = { open = false })
}

/** The Assistant over the current screen. */
@Composable
fun AssistantDialog(startMessage: String? = null, onDismiss: () -> Unit) {
    val store = LocalAI.current ?: return
    val context = LocalAgentContext.current ?: return
    EditorDialog(onDismiss) { AssistantScreen(store, context, startMessage, onClose = onDismiss) }
}

/** A picture for the model, or the text the phone read from one when the model can't read pictures. */
private sealed interface Attachment {
    class Image(val jpeg: ByteArray) : Attachment
    class PhotoText(val text: String) : Attachment
}

/**
 * The Assistant (iOS `AssistantView`): a chat with the user's AI connection, kept on the device. When the model can call
 * tools it looks at the user's to-dos, classes, focus sessions and vocabulary numbers by itself, and suggests changes as
 * cards the user confirms (docs/ai-agent-spec.md). The week's classes and the hard words can also be shared up front.
 */
@Composable
fun AssistantScreen(store: AIStore, context: AgentContext, startMessage: String? = null, onClose: () -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    val android = LocalContext.current
    val language = aiAnswerLanguage(LocalConfiguration.current)
    val scope = rememberCoroutineScope()
    val texts = remember(android) { AgentTexts(android.resources) }
    val usesTools = state.defaultConnection?.usesTools ?: false
    var includeSchedule by rememberSaveable { mutableStateOf(false) }
    var includeHardWords by rememberSaveable { mutableStateOf(false) }

    fun newRun(conversation: AIConversation = AIConversation()) =
        AgentRun(scope, store, texts, conversation, usesTools).also { it.context = context }

    var run by remember { mutableStateOf(newRun()) }
    SideEffect { run.context = context }
    DisposableEffect(Unit) { onDispose { run.round.cancel() } }

    var input by rememberSaveable { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<Attachment>() }
    var readingPhoto by remember { mutableStateOf(false) }
    var photoProblem by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showingHistory by remember { mutableStateOf(false) }
    var settingUp by remember { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf<String?>(null) }

    fun systemPrompt(): String {
        val now = context.clock.instant()
        val world = context.world(now)
        val schedule = if (includeSchedule) AIPrompts.weekSchedule(context.timetable, world.today) else null
        val hardWords = if (includeHardWords) context.vocab?.model?.state?.value?.hardWords?.take(20)?.map { it.headword } else null
        return AIPrompts.assistant(language, world.today, world.now, schedule, hardWords, run.usesTools)
    }

    fun send(typed: String) {
        val photoTexts = attachments.filterIsInstance<Attachment.PhotoText>().map { AIPrompts.photoText(it.text) }
        val images = attachments.filterIsInstance<Attachment.Image>().map { AIImage(it.jpeg) }
        val text = (listOf(typed) + photoTexts).filter { it.isNotEmpty() }.joinToString("\n\n")
        if (text.isEmpty() && images.isEmpty()) return
        // A new connection (or a changed tools switch) applies from a fresh chat on.
        if (run.isEmpty && run.usesTools != usesTools) run = newRun()
        input = ""
        attachments.clear()
        photoProblem = null
        run.send(text, images, systemPrompt())
    }

    LaunchedEffect(Unit) { if (startMessage != null && state.isSetUp) send(startMessage) }

    /** A picture goes to a model that reads pictures; for any other, the phone reads its text first. */
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        photoProblem = null
        readingPhoto = true
        scope.launch {
            if (state.defaultConnection?.readsImages == true) {
                val jpeg = PhotoPrep.jpeg(android, uri)
                if (jpeg != null) attachments += Attachment.Image(jpeg) else photoProblem = R.string.ai_no_text_in_photo
            } else {
                try {
                    val boxes = TextReader.read(android, uri).boxes
                    if (boxes.isEmpty()) photoProblem = R.string.ai_no_text_in_photo else attachments += Attachment.PhotoText(TimetablePhoto.ocrText(boxes))
                } catch (error: TextReader.Unavailable) {
                    photoProblem = R.string.ai_scan_unavailable
                } catch (error: Exception) {
                    photoProblem = R.string.ai_no_text_in_photo
                }
            }
            readingPhoto = false
        }
    }

    // A study tool opened from an empty chat shows in place of it.
    when (tool) {
        "sentences" -> {
            SentenceAnalysisScreen(store, onOpenWord = {}, onBack = { tool = null })
            return
        }
        "essay" -> {
            EssayFeedbackScreen(store, onBack = { tool = null })
            return
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agent_assistant)) },
                navigationIcon = { IconButton(onClick = onClose) { AppIcon(R.drawable.ic_close, stringResource(R.string.done)) } },
                actions = {
                    if (state.isSetUp) {
                        IconButton(onClick = { run.round.cancel(); run = newRun(); input = ""; attachments.clear() }, enabled = !run.isEmpty) {
                            AppIcon(R.drawable.ic_edit_note, stringResource(R.string.agent_new_chat))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { AppIcon(R.drawable.ic_more, stringResource(R.string.agent_options)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.agent_share_classes)) },
                                    onClick = { includeSchedule = !includeSchedule },
                                    trailingIcon = { if (includeSchedule) AppIcon(R.drawable.ic_check, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.agent_share_hard_words)) },
                                    onClick = { includeHardWords = !includeHardWords },
                                    trailingIcon = { if (includeHardWords) AppIcon(R.drawable.ic_check, null) },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.agent_earlier_chats)) },
                                    onClick = { menu = false; showingHistory = true },
                                    enabled = state.conversations.isNotEmpty(),
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.isSetUp) {
                InputBar(
                    input = input, onInput = { input = it },
                    attachments = attachments, onRemove = { attachments.remove(it) },
                    readingPhoto = readingPhoto, photoProblem = photoProblem,
                    canAttach = attachments.size < MAX_ATTACHMENTS && !readingPhoto,
                    onAttach = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    isRunning = run.round.state.isRunning,
                    canSend = (input.isNotBlank() || attachments.isNotEmpty()) && input.trim().length <= MAX_INPUT && !readingPhoto,
                    onSend = { send(input.trim()) },
                    onStop = { run.stop() },
                )
            }
        },
    ) { padding ->
        if (!state.isSetUp) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Section(footer = stringResource(R.string.agent_not_set_up)) {
                    ActionRow(R.drawable.ic_sparkles, stringResource(R.string.ai_set_up)) { settingUp = true }
                }
            }
        } else {
            Chat(
                run, state.defaultConnection?.name.orEmpty(), includeSchedule, includeHardWords,
                studyTools = context.vocab != null, onTool = { tool = it }, onSuggestion = ::send,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    run.importRequest?.let { request ->
        ImportScreen(
            context.timetable, context.actions, onDismiss = { run.importClosed() }, initialText = request.csv,
            onImported = { added, skipped -> run.imported(request.id, added, skipped) },
        )
    }
    if (showingHistory) {
        ChatHistory(store, state.conversations, onOpen = { picked ->
            run.round.cancel()
            run = newRun(picked)
            showingHistory = false
        }, onDismiss = { showingHistory = false })
    }
    if (settingUp) AISettingsDialog(store, onDismiss = { settingUp = false })
}

private const val MAX_INPUT = 4000
private const val MAX_ATTACHMENTS = 3

/** One row of the chat: what the user wrote, the assistant's text, or a tool call. */
private sealed interface ChatRow {
    val key: String

    class User(index: Int, val message: AIMessage) : ChatRow {
        override val key = "user-$index"
    }

    class Reply(index: Int, val text: String) : ChatRow {
        override val key = "text-$index"
    }

    class Card(val card: AgentCard) : ChatRow {
        override val key = "card-${card.id}"
    }
}

private fun rows(run: AgentRun): List<ChatRow> = buildList {
    run.messages.forEachIndexed { index, message ->
        when (message.role) {
            AIMessage.Role.USER -> if (message.text.isNotEmpty() || message.images.isNotEmpty()) add(ChatRow.User(index, message))
            AIMessage.Role.ASSISTANT -> {
                if (message.text.isNotEmpty()) add(ChatRow.Reply(index, message.text))
                for (call in message.toolCalls) run.conversation.cards[call.id]?.let { add(ChatRow.Card(it)) }
            }
        }
    }
}

@Composable
private fun Chat(
    run: AgentRun,
    provider: String,
    includeSchedule: Boolean,
    includeHardWords: Boolean,
    studyTools: Boolean,
    onTool: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    modifier: Modifier,
) {
    val rows = rows(run)
    val round = run.round.state
    val list = rememberLazyListState()
    // A conversation follows its latest message.
    LaunchedEffect(rows.size, round.text.length, round.isRunning, run.notice) {
        val count = list.layoutInfo.totalItemsCount
        if (rows.isNotEmpty() && count > 0) list.animateScrollToItem(count - 1)
    }
    LazyColumn(modifier, state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (run.isEmpty) {
            item(key = "welcome") { Welcome(run.usesTools, provider, includeSchedule, includeHardWords, studyTools, onTool, onSuggestion) }
        }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ChatRow.User -> UserBubble(row.message)
                is ChatRow.Reply -> Bubble { MarkdownText(row.text) }
                is ChatRow.Card -> AgentCardView(
                    row.card, canUndo = run.canUndo(row.card.id),
                    confirm = { run.confirm(row.card.id) }, cancel = { run.cancel(row.card.id) }, undo = { run.undo(row.card.id) },
                )
            }
        }
        if (round.hasContent) item(key = "round") { Bubble { AIAnswerView(round, onRetry = { run.retry() }) } }
        run.notice?.let { notice -> item(key = "notice") { NoticeLine(notice) } }
    }
}

@Composable
private fun Bubble(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun Welcome(
    usesTools: Boolean, provider: String, includeSchedule: Boolean, includeHardWords: Boolean, studyTools: Boolean,
    onTool: (String) -> Unit, onSuggestion: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(R.drawable.ic_sparkles, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.agent_welcome), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (usesTools) {
            Text(stringResource(R.string.agent_tools_note, provider), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (suggestion in listOf(R.string.agent_suggest_reminder, R.string.agent_suggest_progress, R.string.agent_suggest_focus)) {
                val text = stringResource(suggestion)
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.clickable { onSuggestion(text) }) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        } else if (includeSchedule || includeHardWords) {
            val summary = when {
                includeSchedule && includeHardWords -> R.string.agent_sees_both
                includeSchedule -> R.string.agent_sees_classes
                else -> R.string.agent_sees_hard_words
            }
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (studyTools) {
            Section {
                ActionRow(R.drawable.ic_notes, stringResource(R.string.ai_long_sentences)) { onTool("sentences") }
                ActionRow(R.drawable.ic_edit_note, stringResource(R.string.ai_writing_feedback)) { onTool("essay") }
            }
        }
    }
}

@Composable
private fun NoticeLine(notice: AgentRun.Notice) {
    val text = when (notice) {
        AgentRun.Notice.TOOLS_UNAVAILABLE -> R.string.agent_tools_unavailable
        AgentRun.Notice.ROUND_LIMIT -> R.string.agent_round_limit
        AgentRun.Notice.CUT_OFF -> R.string.ai_cut_off
        AgentRun.Notice.REFUSED -> R.string.ai_refused
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AppIcon(R.drawable.ic_warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UserBubble(message: AIMessage) {
    val (typed, hasPhotoText) = message.typedPart()
    Column(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (message.images.isNotEmpty() || hasPhotoText) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (image in message.images) {
                    val bitmap = remember(image) { image.data.takeIf { it.isNotEmpty() }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), stringResource(R.string.ai_photo), Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Text(stringResource(R.string.ai_photo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasPhotoText) Text(stringResource(R.string.agent_photo_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (typed.isNotEmpty()) {
            SelectionContainer {
                Text(
                    typed,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** A tool call in the chat: a line for a lookup, a card with Confirm / Cancel for a change (iOS `AgentCardView`). */
@Composable
fun AgentCardView(card: AgentCard, canUndo: Boolean, confirm: () -> Unit, cancel: () -> Unit, undo: () -> Unit) {
    when (card.state) {
        AgentCard.State.LOOKED -> Line(R.drawable.ic_check_circle, card.title)
        AgentCard.State.FAILED -> Line(R.drawable.ic_wrong, stringResource(R.string.agent_not_possible, card.title))
        else -> Surface(
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(cardIcon(card.tool), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                }
                card.lines.forEachIndexed { index, line ->
                    Text(
                        line,
                        style = if (index == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (card.state) {
                    AgentCard.State.WAITING -> Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = confirm) {
                            Text(stringResource(if (card.tool == AppTools.addCourses.name) R.string.agent_review else R.string.agent_confirm))
                        }
                    }
                    AgentCard.State.CONFIRMED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(R.drawable.ic_check_circle, null, Modifier.size(18.dp), tint = OK_GREEN)
                        Text(stringResource(R.string.done), color = OK_GREEN, modifier = Modifier.padding(start = 6.dp).weight(1f))
                        if (canUndo) TextButton(onClick = undo) { Text(stringResource(R.string.agent_undo)) }
                    }
                    AgentCard.State.CANCELLED -> Text(stringResource(R.string.agent_cancelled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AgentCard.State.UNDONE -> Text(stringResource(R.string.agent_undone), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AgentCard.State.LOOKED, AgentCard.State.FAILED -> Unit
                }
            }
        }
    }
}

@Composable
private fun Line(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AppIcon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun cardIcon(tool: String): Int = when (tool) {
    AppTools.addTodo.name -> R.drawable.ic_add
    AppTools.updateTodo.name -> R.drawable.ic_edit
    AppTools.deleteTodo.name -> R.drawable.ic_delete
    AppTools.addCourses.name -> R.drawable.ic_new_course
    AppTools.startFocus.name -> R.drawable.ic_tab_focus
    else -> R.drawable.ic_sparkles
}

@Composable
private fun InputBar(
    input: String, onInput: (String) -> Unit,
    attachments: List<Attachment>, onRemove: (Attachment) -> Unit,
    readingPhoto: Boolean, photoProblem: Int?, canAttach: Boolean, onAttach: () -> Unit,
    isRunning: Boolean, canSend: Boolean, onSend: () -> Unit, onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (attachments.isNotEmpty() || readingPhoto || photoProblem != null) {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (attachment in attachments) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(if (attachment is Attachment.Image) R.string.ai_photo else R.string.agent_photo_text),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                IconButton(onClick = { onRemove(attachment) }, modifier = Modifier.size(32.dp)) {
                                    AppIcon(R.drawable.ic_close, stringResource(R.string.agent_remove), Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    if (readingPhoto) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.ai_reading_photo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (photoProblem != null) {
                        Text(stringResource(photoProblem), style = MaterialTheme.typography.bodySmall, color = Color(0xFFE08A00))
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onAttach, enabled = canAttach) { AppIcon(R.drawable.ic_add_photo, stringResource(R.string.agent_add_photo)) }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    placeholder = { Text(stringResource(R.string.agent_message)) },
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f),
                )
                if (isRunning) {
                    IconButton(onClick = onStop) { AppIcon(R.drawable.ic_stop_circle, stringResource(R.string.stop), tint = MaterialTheme.colorScheme.primary) }
                } else {
                    IconButton(onClick = onSend, enabled = canSend) { AppIcon(R.drawable.ic_send, stringResource(R.string.agent_send)) }
                }
            }
        }
    }
}

/** Earlier Assistant chats, newest first (iOS `ChatHistoryView`). */
@Composable
private fun ChatHistory(store: AIStore, conversations: List<AIConversation>, onOpen: (AIConversation) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.agent_earlier_chats), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(conversations, key = { it.id.toString() }) { conversation ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(conversation) }.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(conversation.title, maxLines = 1)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(conversation.updated)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { store.deleteConversation(conversation.id) }) { AppIcon(R.drawable.ic_delete, stringResource(R.string.delete)) }
                }
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}
