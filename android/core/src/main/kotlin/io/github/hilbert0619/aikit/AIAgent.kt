package io.github.hilbert0619.aikit

import io.github.hilbert0619.vocabkit.trimmingWhitespacesAndNewlines

/**
 * A tool call from the latest answer that passed the checks, waiting for the app: a reading tool to run, or a change
 * for the user to confirm.
 */
data class AIPendingCall(val call: AIToolCall, val tool: AITool, val arguments: AIToolArguments) {
    val id: String get() = call.id
    val needsConfirmation: Boolean get() = tool.effect == AITool.Effect.CHANGES
}

/**
 * An Assistant conversation with tools (docs/ai-agent-spec.md §4), as plain state the app drives:
 *
 * 1. [send] the user's message, then ask the provider with [request].
 * 2. Hand the streamed answer to [receive]. Calls of unknown tools or with bad arguments get an error result right away
 *    (so the model can fix them); the rest come back as pending calls.
 * 3. Run the reading calls, show the changes as proposals, and [resolve] each call with its result, e.g. "added" or
 *    "the user cancelled".
 * 4. Once every call has a result ([isWaitingForModel]), ask again; repeat until the answer has no calls.
 *
 * After [MAX_ROUNDS] rounds of tool calls in one turn, further calls are answered with an error and the turn stops
 * ([stoppedAtRoundLimit]).
 *
 * Continues [messages] (e.g. a saved chat): calls at the end that never got results are closed as unanswered, and
 * thinking blocks are dropped, so the history can be sent as it is.
 */
class AIAgentSession(messages: List<AIMessage> = emptyList(), val tools: List<AITool>, system: String = "") {
    private val _messages = messages.map { it.copy(thinking = emptyList()) }.toMutableList()
    val messages: List<AIMessage> get() = _messages

    /** The system prompt of the current turn; it stays the same for every request of a turn. */
    var system: String = system
        private set

    /** Rounds of tool calls since the user's message. */
    var rounds: Int = 0
        private set
    var stoppedAtRoundLimit: Boolean = false
        private set

    /** The latest answer's calls still waiting for a result, in order. */
    val pending: List<AIPendingCall> get() = _pending
    private val _pending = mutableListOf<AIPendingCall>()

    /** Results of the latest answer's calls, by call ID, until all are in. */
    private val results = mutableMapOf<String, AIToolResult>()

    /** The latest answer's call IDs, in order. */
    private val callOrder = mutableListOf<String>()

    init {
        val last = _messages.lastOrNull()
        if (last != null && last.role == AIMessage.Role.ASSISTANT && last.toolCalls.isNotEmpty()) {
            _messages += AIMessage(AIMessage.Role.USER, "", toolResults = last.toolCalls.map { AIToolResult(it.id, CLOSED_RESULT) })
        }
    }

    /**
     * Adds the user's message with this turn's system prompt. Calls still waiting are closed as unanswered first, in the
     * same message. Thinking blocks of earlier turns are dropped: they belong to the turn that made them, and a new
     * turn's system prompt differs.
     */
    fun send(text: String, images: List<AIImage> = emptyList(), system: String) {
        var toolResults = emptyList<AIToolResult>()
        if (callOrder.isNotEmpty()) {
            for (id in callOrder) if (results[id] == null) results[id] = AIToolResult(id, INTERRUPTED_RESULT)
            toolResults = callOrder.mapNotNull { results[it] }
            clearCalls()
        }
        for (index in _messages.indices) _messages[index] = _messages[index].copy(thinking = emptyList())
        _messages += AIMessage(AIMessage.Role.USER, text, images = images, toolResults = toolResults)
        this.system = system
        rounds = 0
        stoppedAtRoundLimit = false
    }

    /** The request for the next answer. */
    fun request(maxCharacters: Int = Int.MAX_VALUE): AIRequest =
        AIRequest(system, messages.toList(), tools = tools).trimmed(maxCharacters)

    /** The last message is the user's or the tools' results: the model's turn. */
    val isWaitingForModel: Boolean
        get() = _pending.isEmpty() && callOrder.isEmpty() && _messages.lastOrNull()?.role == AIMessage.Role.USER && !stoppedAtRoundLimit

    /**
     * Takes a finished answer. Returns the calls the app must handle; empty when the answer is final (or every call was
     * answered with an error, and [isWaitingForModel] says to ask again).
     */
    fun receive(answer: AIAnswer): List<AIPendingCall> {
        var calls = answer.toolCalls.toMutableList()
        val used = _messages.flatMap { it.toolCalls }.map { it.id }.toSet()
        for (index in calls.indices) {
            val id = calls[index].id
            if (id.isEmpty() || id in used || calls.subList(0, index).any { it.id == id }) {
                calls[index] = calls[index].copy(id = "call_${rounds + 1}_${index + 1}")
            }
        }
        // A cut-off or refused answer's calls are incomplete: keep the text only.
        if (answer.stop == AIStopReason.CutOff || answer.stop == AIStopReason.Refused) {
            calls = calls.filter { parseJson(it.arguments) != null }.toMutableList()
        }
        _messages += AIMessage(
            AIMessage.Role.ASSISTANT, answer.text.trimmingWhitespacesAndNewlines(), toolCalls = calls,
            reasoning = answer.reasoning.ifEmpty { null }, thinking = answer.thinkingBlocks.toList(),
        )
        if (calls.isEmpty()) return emptyList()
        rounds += 1
        callOrder.clear()
        callOrder += calls.map { it.id }
        if (rounds > MAX_ROUNDS) {
            stoppedAtRoundLimit = true
            for (call in calls) results[call.id] = AIToolResult(call.id, ROUND_LIMIT_RESULT, isError = true)
            finishRoundIfComplete()
            return emptyList()
        }
        for (call in calls) {
            val tool = tools.firstOrNull { it.name == call.name }
            if (tool == null) {
                val names = tools.joinToString(", ") { it.name }
                results[call.id] = AIToolResult(call.id, "There is no tool named \"${call.name}\". Tools: $names.", isError = true)
                continue
            }
            tool.validate(call.arguments).fold(
                onSuccess = { _pending += AIPendingCall(call, tool, it) },
                onFailure = { results[call.id] = AIToolResult(call.id, "Invalid arguments for ${tool.name}: ${it.message}", isError = true) },
            )
        }
        val waiting = _pending.toList()
        finishRoundIfComplete()
        return waiting
    }

    /** Keeps only the text of an answer the user stopped while it streamed (its calls may be incomplete). */
    fun receiveStopped(answer: AIAnswer) {
        val text = answer.text.trimmingWhitespacesAndNewlines()
        if (text.isEmpty()) return
        _messages += AIMessage(AIMessage.Role.ASSISTANT, text, reasoning = answer.reasoning.ifEmpty { null })
    }

    /** Records the result of a pending call. */
    fun resolve(callId: String, content: String, isError: Boolean = false) {
        if (_pending.none { it.id == callId }) return
        _pending.removeAll { it.id == callId }
        results[callId] = AIToolResult(callId, content, isError)
        finishRoundIfComplete()
    }

    /** Once every call of the latest answer has a result, they go into one message for the model. */
    private fun finishRoundIfComplete() {
        if (callOrder.isEmpty() || _pending.isNotEmpty() || !callOrder.all { results[it] != null }) return
        _messages += AIMessage(AIMessage.Role.USER, "", toolResults = callOrder.mapNotNull { results[it] })
        clearCalls()
    }

    private fun clearCalls() {
        _pending.clear()
        results.clear()
        callOrder.clear()
    }

    companion object {
        const val MAX_ROUNDS = 8

        const val CANCELLED_RESULT = "The user cancelled; nothing changed."
        const val INTERRUPTED_RESULT = "The user didn't answer this; they wrote a new message instead. Nothing changed."
        const val CLOSED_RESULT = "The chat was closed before the user answered. Nothing changed."
        const val ROUND_LIMIT_RESULT = "Not run: too many steps in one answer. Stop calling tools and answer the user."
    }
}
