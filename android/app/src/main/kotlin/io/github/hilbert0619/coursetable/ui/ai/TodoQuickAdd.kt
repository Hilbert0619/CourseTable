package io.github.hilbert0619.coursetable.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.aikit.AIPrompts
import io.github.hilbert0619.aikit.TodoDraft
import io.github.hilbert0619.coursekit.Timetable
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ui.LocalNowSource
import io.github.hilbert0619.coursetable.ui.components.ActionRow
import io.github.hilbert0619.coursetable.ui.components.Section

private val WARNING = Color(0xFFE08A00)

/**
 * Quick add in a new to-do (iOS `TodoEditorView.quickAddSection`): a note like "明天高数课后交论文" that the AI turns into
 * the editor's fields for the user to check. [apply] fills them in and returns a problem to show, if any.
 */
@Composable
fun TodoQuickAddSection(store: AIStore, timetable: Timetable, apply: (TodoDraft) -> String?) {
    val nowSource = LocalNowSource.current
    val focus = LocalFocusManager.current
    val run = rememberAIRun()
    var note by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val unreadable = stringResource(R.string.ai_todo_unreadable)

    fun fillIn() {
        focus.clearFocus()
        problem = null
        val moment = nowSource.moment()
        val today = moment.day
        val courses = timetable.courses
        val names = courses.map { it.name }.toSortedSet().toList()
        val request = AIPrompts.todoDraft(note, today, moment.time, names, AIPrompts.upcomingClasses(timetable, today))
        run.start(request, store, onComplete = { answer ->
            val draft = runCatching { TodoDraft.parse(answer.text, today, courses) }.getOrNull()
            problem = if (draft == null) unreadable else apply(draft)
        })
    }

    Section(title = stringResource(R.string.ai_quick_add), footer = stringResource(R.string.ai_quick_add_footer)) {
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text(stringResource(R.string.ai_quick_add_hint)) },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ActionRow(R.drawable.ic_sparkles, stringResource(R.string.ai_fill_in), enabled = note.isNotBlank() && !run.state.isRunning) { fillIn() }
            }
            if (run.state.isRunning) CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
        }
        run.state.error?.let { AIErrorView(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        problem?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = WARNING, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
        }
    }
}
