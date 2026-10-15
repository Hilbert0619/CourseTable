package io.github.hilbert0619.aikit

import io.github.hilbert0619.vocabkit.trimmingWhitespaces
import io.github.hilbert0619.vocabkit.trimmingWhitespacesAndNewlines
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** One Server-Sent Event: an optional `event:` name and its `data:` lines joined by newlines. */
data class SSEEvent(val name: String? = null, val data: String)

/**
 * Splits a byte stream into Server-Sent Events. Works on bytes, so a UTF-8 character split across network chunks stays
 * whole, and on blank lines, which a line reader would skip.
 */
class SSEParser {
    private val line = ByteArrayOutputStream()
    private var name: String? = null
    private val data = mutableListOf<String>()

    fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): List<SSEEvent> {
        val events = mutableListOf<SSEEvent>()
        for (index in offset until offset + length) {
            val byte = bytes[index]
            if (byte == NEWLINE) {
                var text = line.toByteArray()
                if (text.isNotEmpty() && text.last() == RETURN) text = text.copyOf(text.size - 1)
                take(String(text, Charsets.UTF_8))?.let { events += it }
                line.reset()
            } else {
                line.write(byte.toInt())
            }
        }
        return events
    }

    /** The last event when the stream ends without a blank line. */
    fun finish(): List<SSEEvent> {
        val events = mutableListOf<SSEEvent>()
        if (line.size() > 0) {
            take(String(line.toByteArray(), Charsets.UTF_8))?.let { events += it }
            line.reset()
        }
        take("")?.let { events += it }
        return events
    }

    private fun take(text: String): SSEEvent? {
        if (text.isEmpty()) {
            val event = if (data.isEmpty()) null else SSEEvent(name, data.joinToString("\n"))
            name = null
            data.clear()
            return event
        }
        if (text.startsWith(":")) return null // a comment, e.g. ": keep-alive"
        val colon = text.indexOf(':')
        val field = if (colon >= 0) text.substring(0, colon) else text
        var value = if (colon >= 0) text.substring(colon + 1) else ""
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "data" -> data += value
            "event" -> name = value
        }
        return null
    }

    private companion object {
        const val NEWLINE = '\n'.code.toByte()
        const val RETURN = '\r'.code.toByte()
    }
}

sealed interface AIStopReason {
    data object Complete : AIStopReason

    /** The provider's length limit cut the answer off. */
    data object CutOff : AIStopReason

    /** The provider's safety filter declined. */
    data object Refused : AIStopReason

    /** The model wants tools run before it goes on. */
    data object ToolUse : AIStopReason

    data class Other(val reason: String) : AIStopReason
}

/** What a streamed answer reports, in either wire format. */
sealed interface AIStreamEvent {
    data class Text(val text: String) : AIStreamEvent

    /** A reasoning model is thinking (its reasoning isn't shown). */
    data object Thinking : AIStreamEvent

    /** OpenAI-compatible `reasoning_content`, kept to be sent back (DeepSeek needs it once tools are on). */
    data class Reasoning(val text: String) : AIStreamEvent

    /** Anthropic thinking block [index] started; a redacted block brings its data. */
    data class ThinkingBlock(val index: Int, val redactedData: String?) : AIStreamEvent
    data class ThinkingText(val index: Int, val text: String) : AIStreamEvent
    data class ThinkingSignature(val index: Int, val signature: String) : AIStreamEvent

    /** A tool call starts, or (OpenAI-compatible) its ID or name arrives. [index] is `null` when the provider leaves it out. */
    data class ToolCall(val index: Int?, val id: String?, val name: String?) : AIStreamEvent

    /** A piece of a tool call's JSON arguments. */
    data class ToolArguments(val index: Int?, val text: String) : AIStreamEvent
    data class Usage(val input: Int?, val output: Int?) : AIStreamEvent
    data class Stop(val reason: AIStopReason) : AIStreamEvent

    /** The stream is over (`[DONE]` or `message_stop`). */
    data object End : AIStreamEvent
}

/** Turns Server-Sent Events into [AIStreamEvent]s. Throws [AIError] for an error event or an unreadable one. */
class AIStreamDecoder(val format: AIWireFormat) {
    fun decode(event: SSEEvent): List<AIStreamEvent> = when (format) {
        AIWireFormat.OPEN_AI -> decodeOpenAI(event)
        AIWireFormat.ANTHROPIC -> decodeAnthropic(event)
    }

    private fun decodeOpenAI(event: SSEEvent): List<AIStreamEvent> {
        val data = event.data.trimmingWhitespaces()
        if (data == "[DONE]") return listOf(AIStreamEvent.End)
        val chunk = parseJson(data) as? JsonObject ?: throw AIError.Unreadable
        val read = Reader()
        val error = read.obj(chunk, "error")
        val choices = read.array(chunk, "choices")
        val usage = read.obj(chunk, "usage")
        val events = mutableListOf<AIStreamEvent>()
        val parsedChoices = choices.orEmpty().map { choice ->
            val item = choice as? JsonObject ?: throw AIError.Unreadable
            val delta = read.obj(item, "delta")
            val calls = delta?.let { read.array(it, "tool_calls") }.orEmpty().map { call ->
                val fields = call as? JsonObject ?: throw AIError.Unreadable
                val function = read.obj(fields, "function")
                ParsedCall(read.int(fields, "index"), read.string(fields, "id"), function?.let { read.string(it, "name") }, function?.let { read.string(it, "arguments") })
            }
            ParsedChoice(
                content = delta?.let { read.string(it, "content") },
                reasoningContent = delta?.let { read.string(it, "reasoning_content") },
                reasoning = delta?.let { read.string(it, "reasoning") },
                calls = calls,
                finishReason = read.string(item, "finish_reason"),
            )
        }
        val promptTokens = usage?.let { read.int(it, "prompt_tokens") }
        val completionTokens = usage?.let { read.int(it, "completion_tokens") }
        if (error != null) throw AIError.from(null, errorBody(error))
        for (choice in parsedChoices) {
            if (!choice.content.isNullOrEmpty()) events += AIStreamEvent.Text(choice.content)
            if (!choice.reasoningContent.isNullOrEmpty()) {
                events += AIStreamEvent.Thinking
                events += AIStreamEvent.Reasoning(choice.reasoningContent)
            } else if (!choice.reasoning.isNullOrEmpty()) {
                events += AIStreamEvent.Thinking
            }
            for (call in choice.calls) {
                val id = call.id?.ifEmpty { null }
                val name = call.name?.ifEmpty { null }
                if (id != null || name != null) events += AIStreamEvent.ToolCall(call.index, id, name)
                if (!call.arguments.isNullOrEmpty()) events += AIStreamEvent.ToolArguments(call.index, call.arguments)
            }
            if (choice.finishReason != null) events += AIStreamEvent.Stop(openAIStop(choice.finishReason))
        }
        if (usage != null && (promptTokens != null || completionTokens != null)) {
            events += AIStreamEvent.Usage(promptTokens, completionTokens)
        }
        return events
    }

    private class ParsedCall(val index: Int?, val id: String?, val name: String?, val arguments: String?)
    private class ParsedChoice(
        val content: String?, val reasoningContent: String?, val reasoning: String?, val calls: List<ParsedCall>, val finishReason: String?,
    )

    private fun decodeAnthropic(event: SSEEvent): List<AIStreamEvent> {
        val payload = parseJson(event.data) as? JsonObject ?: throw AIError.Unreadable
        val read = Reader()
        val type = read.string(payload, "type") ?: throw AIError.Unreadable
        val index = read.int(payload, "index") ?: 0
        val message = read.obj(payload, "message")
        val block = read.obj(payload, "content_block")
        val delta = read.obj(payload, "delta")
        val usage = read.obj(payload, "usage")
        val error = read.obj(payload, "error")
        val messageUsage = message?.let { read.obj(it, "usage") }
        val inputTokens = messageUsage?.let { read.int(it, "input_tokens") }
        messageUsage?.let { read.int(it, "output_tokens") }
        val outputTokens = usage?.let { read.int(it, "output_tokens") }
        usage?.let { read.int(it, "input_tokens") }
        val blockFields = block?.let { BlockFields(read, it) }
        val deltaType = delta?.let { read.string(it, "type") }
        val deltaText = delta?.let { read.string(it, "text") }
        val deltaThinking = delta?.let { read.string(it, "thinking") }
        val deltaSignature = delta?.let { read.string(it, "signature") }
        val deltaJSON = delta?.let { read.string(it, "partial_json") }
        val stopReason = delta?.let { read.string(it, "stop_reason") }
        return when (type) {
            "message_start" -> if (inputTokens == null) emptyList() else listOf(AIStreamEvent.Usage(inputTokens, null))
            "content_block_start" -> {
                val fields = blockFields ?: return emptyList()
                when (fields.type) {
                    "tool_use" -> listOf(AIStreamEvent.ToolCall(index, fields.id, fields.name))
                    "thinking" -> buildList {
                        add(AIStreamEvent.ThinkingBlock(index, null))
                        if (!fields.thinking.isNullOrEmpty()) add(AIStreamEvent.ThinkingText(index, fields.thinking))
                        if (!fields.signature.isNullOrEmpty()) add(AIStreamEvent.ThinkingSignature(index, fields.signature))
                    }
                    "redacted_thinking" -> listOf(AIStreamEvent.ThinkingBlock(index, fields.data ?: ""))
                    "text" -> if (fields.text.isNullOrEmpty()) emptyList() else listOf(AIStreamEvent.Text(fields.text))
                    else -> emptyList()
                }
            }
            "content_block_delta" -> when (deltaType) {
                "text_delta" -> if (deltaText.isNullOrEmpty()) emptyList() else listOf(AIStreamEvent.Text(deltaText))
                "thinking_delta" -> {
                    val text = deltaThinking ?: ""
                    if (text.isEmpty()) listOf(AIStreamEvent.Thinking) else listOf(AIStreamEvent.Thinking, AIStreamEvent.ThinkingText(index, text))
                }
                "signature_delta" -> if (deltaSignature == null) emptyList() else listOf(AIStreamEvent.ThinkingSignature(index, deltaSignature))
                "input_json_delta" -> if (deltaJSON.isNullOrEmpty()) emptyList() else listOf(AIStreamEvent.ToolArguments(index, deltaJSON))
                else -> emptyList()
            }
            "message_delta" -> buildList {
                if (stopReason != null) add(AIStreamEvent.Stop(anthropicStop(stopReason)))
                if (outputTokens != null) add(AIStreamEvent.Usage(null, outputTokens))
            }
            "message_stop" -> listOf(AIStreamEvent.End)
            "error" -> throw AIError.from(null, error?.let(::errorBody) ?: AIErrorDetails())
            else -> emptyList() // ping, content_block_stop
        }
    }

    private class BlockFields(read: Reader, block: JsonObject) {
        val type: String = read.string(block, "type") ?: throw AIError.Unreadable
        val id = read.string(block, "id")
        val name = read.string(block, "name")
        val text = read.string(block, "text")
        val thinking = read.string(block, "thinking")
        val signature = read.string(block, "signature")
        val data = read.string(block, "data")
    }

    /**
     * Reads fields as Swift's `Decodable` would: a missing or `null` field is `null`, a field of the wrong type makes
     * the whole event unreadable.
     */
    internal class Reader {
        private fun present(fields: JsonObject, key: String): JsonElement? = fields[key]?.takeIf { it !is JsonNull }

        fun string(fields: JsonObject, key: String): String? {
            val value = present(fields, key) ?: return null
            return value.stringValue ?: throw AIError.Unreadable
        }

        fun int(fields: JsonObject, key: String): Int? {
            val value = present(fields, key) ?: return null
            return value.intValue ?: throw AIError.Unreadable
        }

        fun obj(fields: JsonObject, key: String): JsonObject? {
            val value = present(fields, key) ?: return null
            return value as? JsonObject ?: throw AIError.Unreadable
        }

        fun array(fields: JsonObject, key: String): JsonArray? {
            val value = present(fields, key) ?: return null
            return value as? JsonArray ?: throw AIError.Unreadable
        }
    }

    /**
     * Reads a response that came back whole instead of streamed (some proxies ignore `stream: true`): the answer's
     * events, or the error it carries. `null` when the body is neither.
     */
    fun decodeComplete(data: ByteArray): List<AIStreamEvent>? {
        val response = parseJson(String(data, Charsets.UTF_8)) as? JsonObject ?: return null
        val read = Reader()
        val parsed = runCatching { CompleteResponse(read, response) }.getOrElse { return null }
        parsed.error?.let { throw AIError.from(null, errorBody(it)) }
        val events = mutableListOf<AIStreamEvent>()
        when (format) {
            AIWireFormat.OPEN_AI -> {
                val choice = parsed.choices?.firstOrNull() ?: return null
                if (!choice.reasoningContent.isNullOrEmpty()) events += AIStreamEvent.Reasoning(choice.reasoningContent)
                if (!choice.content.isNullOrEmpty()) events += AIStreamEvent.Text(choice.content)
                choice.toolCalls.forEachIndexed { index, call ->
                    events += AIStreamEvent.ToolCall(index, call.first, call.second)
                    call.third?.let { events += AIStreamEvent.ToolArguments(index, it) }
                }
                if (choice.finishReason != null) events += AIStreamEvent.Stop(openAIStop(choice.finishReason))
                parsed.usage?.let { usage ->
                    events += AIStreamEvent.Usage(usage.promptTokens ?: usage.inputTokens, usage.completionTokens ?: usage.outputTokens)
                }
            }
            AIWireFormat.ANTHROPIC -> {
                val content = parsed.content ?: return null
                content.forEachIndexed { index, block ->
                    when (block.type) {
                        "text" -> if (!block.text.isNullOrEmpty()) events += AIStreamEvent.Text(block.text)
                        "thinking" -> {
                            events += AIStreamEvent.ThinkingBlock(index, null)
                            if (!block.thinking.isNullOrEmpty()) events += AIStreamEvent.ThinkingText(index, block.thinking)
                            if (block.signature != null) events += AIStreamEvent.ThinkingSignature(index, block.signature)
                        }
                        "redacted_thinking" -> events += AIStreamEvent.ThinkingBlock(index, block.data ?: "")
                        "tool_use" -> {
                            events += AIStreamEvent.ToolCall(index, block.id, block.name)
                            events += AIStreamEvent.ToolArguments(index, (block.input ?: JsonObject(emptyMap())).jsonText)
                        }
                    }
                }
                if (parsed.stopReason != null) events += AIStreamEvent.Stop(anthropicStop(parsed.stopReason))
                parsed.usage?.let { events += AIStreamEvent.Usage(it.inputTokens, it.outputTokens) }
            }
        }
        events += AIStreamEvent.End
        return events
    }

    private class CompleteResponse(read: Reader, response: JsonObject) {
        class Choice(val content: String?, val reasoningContent: String?, val toolCalls: List<Triple<String?, String?, String?>>, val finishReason: String?)
        class Block(
            val type: String, val text: String?, val thinking: String?, val signature: String?, val data: String?,
            val id: String?, val name: String?, val input: JsonElement?,
        )
        class Usage(val promptTokens: Int?, val completionTokens: Int?, val inputTokens: Int?, val outputTokens: Int?)

        val choices: List<Choice>? = read.array(response, "choices")?.map { item ->
            val choice = item as? JsonObject ?: throw AIError.Unreadable
            val message = read.obj(choice, "message")
            Choice(
                content = message?.let { read.string(it, "content") },
                reasoningContent = message?.let { read.string(it, "reasoning_content") },
                toolCalls = message?.let { read.array(it, "tool_calls") }.orEmpty().map { call ->
                    val fields = call as? JsonObject ?: throw AIError.Unreadable
                    val function = read.obj(fields, "function")
                    Triple(read.string(fields, "id"), function?.let { read.string(it, "name") }, function?.let { read.string(it, "arguments") })
                },
                finishReason = read.string(choice, "finish_reason"),
            )
        }
        val content: List<Block>? = read.array(response, "content")?.map { item ->
            val block = item as? JsonObject ?: throw AIError.Unreadable
            Block(
                type = read.string(block, "type") ?: throw AIError.Unreadable,
                text = read.string(block, "text"), thinking = read.string(block, "thinking"),
                signature = read.string(block, "signature"), data = read.string(block, "data"),
                id = read.string(block, "id"), name = read.string(block, "name"), input = block["input"]?.takeIf { it !is JsonNull },
            )
        }
        val stopReason: String? = read.string(response, "stop_reason")
        val usage: Usage? = read.obj(response, "usage")?.let {
            Usage(read.int(it, "prompt_tokens"), read.int(it, "completion_tokens"), read.int(it, "input_tokens"), read.int(it, "output_tokens"))
        }
        val error: JsonObject? = response["error"]?.takeIf { it !is JsonNull }?.let { it as? JsonObject ?: throw AIError.Unreadable }
    }

    companion object {
        fun openAIStop(reason: String): AIStopReason = when (reason) {
            "stop" -> AIStopReason.Complete
            "length" -> AIStopReason.CutOff
            "content_filter" -> AIStopReason.Refused
            "tool_calls", "function_call" -> AIStopReason.ToolUse
            else -> AIStopReason.Other(reason)
        }

        fun anthropicStop(reason: String): AIStopReason = when (reason) {
            "end_turn", "stop_sequence" -> AIStopReason.Complete
            "max_tokens" -> AIStopReason.CutOff
            "refusal" -> AIStopReason.Refused
            "tool_use" -> AIStopReason.ToolUse
            else -> AIStopReason.Other(reason)
        }
    }
}

/** A streamed answer so far. */
class AIAnswer {
    var text: String = ""
    var isThinking: Boolean = false
    var stop: AIStopReason? = null
    var inputTokens: Int? = null
    var outputTokens: Int? = null
    var hasEnded: Boolean = false

    /** OpenAI-compatible reasoning text. */
    var reasoning: String = ""

    /** Anthropic thinking blocks, in order. */
    val thinkingBlocks: List<AIThinkingBlock> get() = _thinkingBlocks
    private val _thinkingBlocks = mutableListOf<AIThinkingBlock>()

    /** The tools the model asked for, in order; arguments are complete once the stream has ended. */
    val toolCalls: List<AIToolCall> get() = _toolCalls
    private val _toolCalls = mutableListOf<AIToolCall>()
    private val thinkingPositions = mutableMapOf<Int, Int>()
    private val callPositions = mutableMapOf<Int, Int>()

    fun apply(event: AIStreamEvent) {
        when (event) {
            is AIStreamEvent.Text -> {
                text += event.text
                isThinking = false
            }
            AIStreamEvent.Thinking -> if (text.isEmpty() && _toolCalls.isEmpty()) isThinking = true
            is AIStreamEvent.Reasoning -> reasoning += event.text
            is AIStreamEvent.ThinkingBlock -> {
                thinkingPositions[event.index] = _thinkingBlocks.size
                _thinkingBlocks += AIThinkingBlock(redactedData = event.redactedData)
            }
            is AIStreamEvent.ThinkingText -> {
                val position = thinkingPosition(event.index)
                _thinkingBlocks[position] = _thinkingBlocks[position].let { it.copy(text = it.text + event.text) }
            }
            is AIStreamEvent.ThinkingSignature -> {
                val position = thinkingPosition(event.index)
                _thinkingBlocks[position] = _thinkingBlocks[position].let { it.copy(signature = (it.signature ?: "") + event.signature) }
            }
            is AIStreamEvent.ToolCall -> {
                val index = event.index
                val known = index?.let { callPositions[it] }
                val last = _toolCalls.indices.lastOrNull()
                val position = when {
                    known != null -> known
                    index == null && last != null && (event.id == null || event.id == _toolCalls[last].id || _toolCalls[last].id.isEmpty()) -> last
                    else -> {
                        _toolCalls += AIToolCall("", "", "")
                        if (index != null) callPositions[index] = _toolCalls.size - 1
                        _toolCalls.size - 1
                    }
                }
                var call = _toolCalls[position]
                if (event.id != null && call.id.isEmpty()) call = call.copy(id = event.id)
                if (event.name != null && call.name.isEmpty()) call = call.copy(name = event.name)
                _toolCalls[position] = call
                isThinking = false
            }
            is AIStreamEvent.ToolArguments -> {
                val position = if (event.index != null) callPositions[event.index] else _toolCalls.indices.lastOrNull()
                if (position != null) _toolCalls[position] = _toolCalls[position].let { it.copy(arguments = it.arguments + event.text) }
            }
            is AIStreamEvent.Usage -> {
                event.input?.let { inputTokens = it }
                event.output?.let { outputTokens = it }
            }
            is AIStreamEvent.Stop -> {
                stop = event.reason
                isThinking = false
            }
            AIStreamEvent.End -> {
                hasEnded = true
                isThinking = false
            }
        }
    }

    /** Thinking deltas without a block start (some proxies) open one. */
    private fun thinkingPosition(index: Int): Int = thinkingPositions.getOrPut(index) {
        _thinkingBlocks += AIThinkingBlock()
        _thinkingBlocks.size - 1
    }

    /** Something came back: text, tool calls, or a refusal. */
    val hasResult: Boolean
        get() = text.trimmingWhitespacesAndNewlines().isNotEmpty() || _toolCalls.isNotEmpty() || stop == AIStopReason.Refused
}
