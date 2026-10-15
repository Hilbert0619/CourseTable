package io.github.hilbert0619.coursetable.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hilbert0619.aikit.AIAnswer
import io.github.hilbert0619.aikit.AIConnection
import io.github.hilbert0619.aikit.AIError
import io.github.hilbert0619.aikit.AIRequest
import io.github.hilbert0619.aikit.AIStopReason
import io.github.hilbert0619.aikit.AIStreamEvent
import io.github.hilbert0619.aikit.chatRequest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What a run shows: the answer so far, a thinking or waiting line before it, and what went wrong. */
data class AIRunState(
    val text: String = "",
    val isThinking: Boolean = false,
    val stop: AIStopReason? = null,
    val error: AIError? = null,
    val isRunning: Boolean = false,
    /** The model answering, for the "AI-generated" label. */
    val model: String = "",
) {
    val hasContent: Boolean get() = isRunning || error != null || text.isNotEmpty() || stop == AIStopReason.Refused
}

/**
 * One request to the AI and its answer as it streams in (iOS `AIRun`), as Compose state. Runs in [scope], usually the
 * screen's, so leaving the screen stops it. Main thread only.
 */
class AIRun(private val scope: CoroutineScope) {
    var state by mutableStateOf(AIRunState())
        private set

    /** The whole answer so far, with its tool calls and token counts. */
    var answer = AIAnswer()
        private set

    private var job: Job? = null

    /** Counts requests; events from an older one are ignored. */
    private var generation = 0

    /**
     * Asks [request] through the default connection (or [connection] with [key], e.g. a connection being edited).
     * [onComplete] gets the answer when it finished normally (not cut off, refused or failed); [onEnd] gets it whenever
     * the stream ends without being cancelled, whatever happened.
     */
    fun start(
        request: AIRequest,
        store: AIStore,
        connection: AIConnection? = null,
        key: String? = null,
        onComplete: ((AIAnswer) -> Unit)? = null,
        onEnd: ((AIAnswer) -> Unit)? = null,
    ) {
        cancel()
        answer = AIAnswer()
        state = AIRunState()
        val target = connection ?: store.defaultConnection ?: return
        val http = try {
            target.chatRequest(request.trimmed(MAX_CHARACTERS), key ?: store.key(target))
        } catch (error: AIError) {
            state = AIRunState(error = error, model = target.model)
            return
        }
        state = AIRunState(isRunning = true, model = target.model)
        val current = ++generation
        val answer = answer
        job = scope.launch {
            var failure: AIError? = null
            try {
                store.transport.stream(http, target.format).collect { event ->
                    // A replaced request may still deliver a few events; only the latest one's count.
                    if (generation == current) apply(answer, event)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AIError) {
                failure = error
            } catch (error: Exception) {
                failure = AIError.Network(error.message ?: error.javaClass.simpleName)
            }
            if (generation != current) return@launch
            store.record(answer, target)
            val error = failure ?: if (!answer.hasResult) AIError.Empty else null
            state = state.copy(isRunning = false, isThinking = false, error = error)
            if (error == null && answer.stop != AIStopReason.CutOff && answer.stop != AIStopReason.Refused) onComplete?.invoke(answer)
            onEnd?.invoke(answer)
        }
    }

    private fun apply(answer: AIAnswer, event: AIStreamEvent) {
        answer.apply(event)
        state = state.copy(text = answer.text, isThinking = answer.isThinking, stop = answer.stop)
    }

    /** Shows an answer saved earlier instead of asking again. */
    fun show(saved: CachedAIAnswer) {
        cancel()
        answer = AIAnswer().apply {
            apply(AIStreamEvent.Text(saved.text))
            apply(AIStreamEvent.Stop(AIStopReason.Complete))
            apply(AIStreamEvent.End)
        }
        state = AIRunState(text = saved.text, stop = AIStopReason.Complete, model = saved.model)
    }

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
        if (state.isRunning) state = state.copy(isRunning = false, isThinking = false)
    }

    fun reset() {
        cancel()
        answer = AIAnswer()
        state = AIRunState()
    }

    companion object {
        /** The longest conversation sent, in characters (older turns are dropped first). */
        const val MAX_CHARACTERS = 60_000
    }
}
