package io.github.hilbert0619.coursetable.ui.ai

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.aikit.AIError
import io.github.hilbert0619.aikit.AIStopReason
import io.github.hilbert0619.aikit.MarkdownBlock
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ai.AIRun
import io.github.hilbert0619.coursetable.ai.AIRunState
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ui.components.AppIcon

/** The app's AI store; `null` where AI help isn't offered (e.g. screenshots of other screens). */
val LocalAI = staticCompositionLocalOf<AIStore?> { null }

/** A run in this screen's scope, stopped when the screen goes away. */
@Composable
fun rememberAIRun(): AIRun {
    val scope = rememberCoroutineScope()
    val run = remember { AIRun(scope) }
    DisposableEffect(run) { onDispose { run.cancel() } }
    return run
}

private val WARNING = Color(0xFFE08A00)

/** An AI answer as it streams in: the text, a "thinking" or "asking" line before it, what went wrong, and the label. */
@Composable
fun AIAnswerView(state: AIRunState, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.isRunning && state.text.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    if (state.isThinking) stringResource(R.string.ai_thinking) else stringResource(R.string.ai_asking, state.model),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.text.isNotEmpty()) MarkdownText(state.text)
        val error = state.error
        when {
            error != null -> AIErrorView(error, onRetry = onRetry)
            state.stop == AIStopReason.CutOff -> NoteLine(R.drawable.ic_warning, stringResource(R.string.ai_cut_off))
            state.stop == AIStopReason.Refused -> NoteLine(R.drawable.ic_warning, stringResource(R.string.ai_refused))
        }
        if (state.text.isNotEmpty() && !state.isRunning) {
            val clipboard = LocalClipboard.current
            val scope = rememberCoroutineScope()
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_sparkles, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.ai_generated, state.model),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", state.text))) } }) {
                    AppIcon(R.drawable.ic_copy, stringResource(R.string.copy), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun NoteLine(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AppIcon(icon, null, Modifier.size(18.dp), tint = WARNING)
        Text(text, style = MaterialTheme.typography.bodySmall, color = WARNING)
    }
}

/** What went wrong, the provider's own words, and Try Again. */
@Composable
fun AIErrorView(error: AIError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(R.drawable.ic_warning, null, Modifier.size(20.dp), tint = WARNING)
            Text(stringResource(aiErrorText(error)), style = MaterialTheme.typography.bodyMedium)
        }
        error.providerMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
        }
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}

@StringRes
fun aiErrorText(error: AIError): Int = when (error) {
    is AIError.WrongKey -> R.string.ai_error_wrong_key
    is AIError.NoCredit -> R.string.ai_error_no_credit
    is AIError.NotFound -> R.string.ai_error_not_found
    is AIError.RateLimited -> R.string.ai_error_rate_limited
    is AIError.Busy -> R.string.ai_error_busy
    is AIError.Rejected -> R.string.ai_error_rejected
    is AIError.Network -> R.string.ai_error_network
    AIError.BadAddress -> R.string.ai_error_bad_address
    AIError.NoModel -> R.string.ai_error_no_model
    AIError.Unreadable -> R.string.ai_error_unreadable
    AIError.Empty -> R.string.ai_error_empty
}

/** Markdown from an AI answer: blocks from [MarkdownBlock], inline styling from [InlineMarkdown]. Selectable. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownBlock.parse(text) }
    SelectionContainer(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (block in blocks) MarkdownBlockView(block)
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock) {
    val body = MaterialTheme.typography.bodyLarge
    when (block) {
        is MarkdownBlock.Heading -> Text(
            inline(block.text),
            style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        is MarkdownBlock.Bullet -> ListItem(if (block.depth == 0) "•" else "◦", block.depth, block.text)
        is MarkdownBlock.Numbered -> ListItem("${block.number}.", block.depth, block.text)
        is MarkdownBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            Text(inline(block.text), style = body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
        }
        is MarkdownBlock.Code -> Text(
            block.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                .padding(8.dp),
        )
        MarkdownBlock.Rule -> HorizontalDivider()
        is MarkdownBlock.Paragraph -> Text(inline(block.text), style = body)
    }
}

@Composable
private fun ListItem(mark: String, depth: Int, text: String) {
    Row(Modifier.padding(start = (depth * 16).dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(mark, style = MaterialTheme.typography.bodyLarge)
        Text(inline(text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun inline(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, codeBackground, linkColor) {
        buildAnnotatedString {
            for (span in InlineMarkdown.parse(text)) {
                val style = SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    background = if (span.code) codeBackground else Color.Unspecified,
                )
                val link = span.link
                if (link != null) {
                    withLink(LinkAnnotation.Url(link, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                        withStyle(style) { append(span.text) }
                    }
                } else {
                    withStyle(style) { append(span.text) }
                }
            }
        }
    }
}
