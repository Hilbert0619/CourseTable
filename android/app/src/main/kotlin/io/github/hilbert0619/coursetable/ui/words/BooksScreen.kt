package io.github.hilbert0619.coursetable.ui.words

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.EditorDialog
import io.github.hilbert0619.coursetable.ui.components.EditorScaffold
import io.github.hilbert0619.coursetable.ui.components.RowDivider
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.ValueRow
import io.github.hilbert0619.coursetable.ui.components.WarningRow
import io.github.hilbert0619.coursetable.ui.timetable.FileText
import io.github.hilbert0619.coursetable.ui.timetable.readText
import io.github.hilbert0619.vocabkit.WordListImport
import io.github.hilbert0619.vocabkit.WordListPreview
import io.github.hilbert0619.vocabkit.store.BookRecord

/** The system word lists and the learner's own (iOS `BooksView`): choose the one to study, import, rename or delete. */
@Composable
fun BooksScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val controller = LocalWordsController.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    val current = state.currentBook?.id
    var importing by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<BookRecord?>(null) }

    WordsScaffold(stringResource(R.string.books), onBack, modifier) { content ->
        Column(content.verticalScroll(rememberScrollState())) {
            Section(title = stringResource(R.string.word_lists_section), footer = stringResource(R.string.word_lists_footer)) {
                state.books.filter { it.isSystem }.forEachIndexed { index, book ->
                    if (index > 0) RowDivider()
                    BookRow(book, book.id == current)
                }
            }
            Section(title = stringResource(R.string.my_lists), footer = stringResource(R.string.my_lists_footer)) {
                for (book in state.books.filter { !it.isSystem }) {
                    BookRow(book, book.id == current, onRename = { renaming = book }, onDelete = {
                        controller.launch {
                            model.deleteBook(book)
                            syncNow()
                        }
                    })
                    RowDivider()
                }
                ActionRow(R.drawable.ic_import, stringResource(R.string.import_word_list)) { importing = true }
            }
        }
    }

    renaming?.let { book -> RenameDialog(book, onDismiss = { renaming = null }) }
    if (importing) {
        EditorDialog(onDismiss = { importing = false }) { WordListImportScreen(onDismiss = { importing = false }) }
    }
}

@Composable
private fun BookRow(book: BookRecord, isCurrent: Boolean, onRename: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    val controller = LocalWordsController.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable {
                controller.launch {
                    model.updateSettings { it.copy(bookId = book.id) }
                    syncNow()
                }
            }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.name, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.word_count, book.wordCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isCurrent) {
            AppIcon(R.drawable.ic_check, stringResource(R.string.studying), Modifier.padding(horizontal = 8.dp), tint = MaterialTheme.colorScheme.primary)
        }
        if (onRename != null && onDelete != null) {
            Box {
                IconButton(onClick = { menu = true }) { AppIcon(R.drawable.ic_more, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { AppIcon(R.drawable.ic_edit, null) }, onClick = {
                        menu = false
                        onRename()
                    })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { AppIcon(R.drawable.ic_delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(book: BookRecord, onDismiss: () -> Unit) {
    val controller = LocalWordsController.current
    var name by remember { mutableStateOf(book.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_list)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                controller.launch {
                    model.renameBook(book, name)
                    syncNow()
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Paste or open a word list, check the preview, and save it as a new book (iOS `WordListImportView`). */
@Composable
fun WordListImportScreen(onDismiss: () -> Unit, initialText: String = "") {
    val controller = LocalWordsController.current
    val context = LocalContext.current
    val state by controller.vocab.model.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(initialText) }
    var name by rememberSaveable { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    val tooLarge = stringResource(R.string.word_file_unreadable)
    val example = stringResource(R.string.word_list_example)
    val defaultName = stringResource(R.string.my_words)
    val preview = if (text.isBlank()) null else WordListImport.preview(text) { it in state.words }
    val account = controller.vocab.account
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileError = null
        when (val file = readText(context, uri)) {
            is FileText.Text -> {
                text = file.text
                if (name.isEmpty()) name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty()
            }
            FileText.TooLarge, FileText.Unreadable -> fileError = tooLarge
        }
    }

    EditorScaffold(
        title = stringResource(R.string.import_word_list_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.import_action),
        confirmEnabled = !preview?.words.isNullOrEmpty() && account != null,
        onConfirm = {
            val words = preview?.words ?: return@EditorScaffold
            val owner = account?.id ?: return@EditorScaffold
            val listName = name.trim().ifEmpty { defaultName }
            controller.launch {
                val book = model.importBook(listName, words, owner)
                model.updateSettings { it.copy(bookId = book.id) }
                syncNow()
            }
            onDismiss()
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.list_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Section(title = stringResource(R.string.words_section), footer = stringResource(R.string.word_list_footer)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            ActionRow(R.drawable.ic_file_open, stringResource(R.string.open_a_file)) { chooseFile.launch(arrayOf("text/*")) }
            RowDivider()
            ActionRow(R.drawable.ic_add, stringResource(R.string.insert_example)) {
                text = example
                if (name.isEmpty()) name = defaultName
            }
        }
        fileError?.let { Section { WarningRow(it, blocking = true) } }
        preview?.let { PreviewSection(it) }
    }
}

@Composable
private fun PreviewSection(preview: WordListPreview) {
    Section(title = stringResource(R.string.preview)) {
        ValueRow(stringResource(R.string.words_to_import), "${preview.words.size}")
        RowDivider()
        ValueRow(stringResource(R.string.meaning_from_dictionary), "${preview.matchedCount}")
        RowDivider()
        ValueRow(stringResource(R.string.your_own_meaning), "${preview.customCount}")
        if (preview.duplicates.isNotEmpty()) {
            RowDivider()
            ValueRow(stringResource(R.string.repeated_skipped), "${preview.duplicates.size}")
        }
        if (preview.unknown.isNotEmpty()) {
            RowDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.not_in_dictionary_skipped))
                Text(preview.unknown.take(20).joinToString(", ") { it.display }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (preview.unreadable.isNotEmpty()) {
            RowDivider()
            Text(
                stringResource(R.string.lines_not_words, preview.unreadable.take(20).joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
