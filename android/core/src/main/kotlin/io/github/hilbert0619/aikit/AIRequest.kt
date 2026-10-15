package io.github.hilbert0619.aikit

import io.github.hilbert0619.vocabkit.trimmingWhitespaces
import io.github.hilbert0619.vocabkit.trimmingWhitespacesAndNewlines
import java.net.URI
import java.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A picture sent with a message (JPEG). A saved chat keeps only that there was one ([data] empty). */
@Serializable
class AIImage(
    @Serializable(with = Base64Serializer::class) val data: ByteArray,
    val mediaType: String = "image/jpeg",
) {
    val isAvailable: Boolean get() = data.isNotEmpty()

    override fun equals(other: Any?): Boolean = other is AIImage && other.mediaType == mediaType && other.data.contentEquals(data)
    override fun hashCode(): Int = 31 * data.contentHashCode() + mediaType.hashCode()
    override fun toString(): String = "AIImage(${data.size} bytes, $mediaType)"

    companion object {
        /** Stands in for a picture that is no longer kept. */
        internal const val MISSING_NOTE = "(A photo was sent here earlier; it isn't available any more.)"
    }
}

/** A tool the model asked to run. */
@Serializable
data class AIToolCall(
    /** The provider's ID, which the result must name. */
    val id: String,
    val name: String,
    /** The arguments as the model wrote them (JSON text, possibly invalid). */
    val arguments: String,
)

/** What came of a tool call, for the model. */
@Serializable
data class AIToolResult(
    @SerialName("callID") val callId: String,
    val content: String,
    /** The call was wrong (unknown tool, bad arguments) or failed; the model may try again. */
    val isError: Boolean = false,
)

/** An Anthropic thinking block, sent back unchanged while the tools of the same turn run. */
@Serializable
data class AIThinkingBlock(
    val text: String = "",
    val signature: String? = null,
    /** A `redacted_thinking` block's data. */
    val redactedData: String? = null,
)

/** One turn of a conversation. */
@Serializable(with = AIMessageSerializer::class)
data class AIMessage(
    val role: Role,
    val text: String,
    /** User: pictures sent with the text. */
    val images: List<AIImage> = emptyList(),
    /** Assistant: the tools it asked to run, in order. */
    val toolCalls: List<AIToolCall> = emptyList(),
    /** User: the results of the previous answer's tool calls (sent before [text]). */
    val toolResults: List<AIToolResult> = emptyList(),
    /** Assistant, OpenAI-compatible: the model's `reasoning_content`. DeepSeek needs every earlier one back once a request has tools. */
    val reasoning: String? = null,
    /** Assistant, Anthropic: thinking blocks, needed back unchanged while that turn's tools run. */
    val thinking: List<AIThinkingBlock> = emptyList(),
) {
    @Serializable
    enum class Role {
        @SerialName("user") USER,
        @SerialName("assistant") ASSISTANT,
    }

    /** A message the user typed (not only tool results): where a turn starts. */
    val startsTurn: Boolean get() = role == Role.USER && toolResults.isEmpty()

    /** What the message costs in the conversation's length budget. */
    internal val size: Int
        get() = text.characterCount + images.size * 1500 + (reasoning?.characterCount ?: 0) +
            toolCalls.sumOf { it.name.characterCount + it.arguments.characterCount } +
            toolResults.sumOf { it.content.characterCount }

    /**
     * The message as a saved chat keeps it: without the pictures' bytes and without thinking blocks (which only matter
     * while a turn's tools run).
     */
    val forStorage: AIMessage
        get() = copy(images = images.map { AIImage(ByteArray(0), it.mediaType) }, thinking = emptyList())
}

/** Chats saved by 0.4 have only role and text; empty lists are left out. */
object AIMessageSerializer : KSerializer<AIMessage> {
    @Serializable
    private class Surrogate(
        val role: AIMessage.Role,
        val text: String = "",
        val images: List<AIImage>? = null,
        val toolCalls: List<AIToolCall>? = null,
        val toolResults: List<AIToolResult>? = null,
        val reasoning: String? = null,
        val thinking: List<AIThinkingBlock>? = null,
    )

    override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: AIMessage) = encoder.encodeSerializableValue(
        Surrogate.serializer(),
        Surrogate(
            value.role, value.text, value.images.ifEmpty { null }, value.toolCalls.ifEmpty { null },
            value.toolResults.ifEmpty { null }, value.reasoning, value.thinking.ifEmpty { null },
        ),
    )

    override fun deserialize(decoder: Decoder): AIMessage {
        val surrogate = decoder.decodeSerializableValue(Surrogate.serializer())
        return AIMessage(
            surrogate.role, surrogate.text, surrogate.images.orEmpty(), surrogate.toolCalls.orEmpty(),
            surrogate.toolResults.orEmpty(), surrogate.reasoning, surrogate.thinking.orEmpty(),
        )
    }
}

/** What to ask, independent of the provider. */
data class AIRequest(
    val system: String,
    val messages: List<AIMessage>,
    /** The answer must be one JSON object (JSON mode where the provider has it; the prompt asks for it anyway). */
    val wantsJSON: Boolean = false,
    /** The tools the model may call (`tool_choice` is left at the providers' default, "auto"). */
    val tools: List<AITool> = emptyList(),
) {
    constructor(system: String, user: String, wantsJSON: Boolean = false) :
        this(system, listOf(AIMessage(AIMessage.Role.USER, user)), wantsJSON)

    /**
     * Drops the oldest turns until the text fits [maxCharacters]. The latest turn (from the last message the user
     * typed, with its tool calls and results) is always kept whole, so the first kept message is a user turn and no
     * tool call loses its result. Once anything is dropped, the thinking blocks are dropped too: Anthropic ties them
     * to everything before them.
     */
    fun trimmed(maxCharacters: Int): AIRequest {
        val starts = messages.indices.filter { messages[it].startsTurn }
        val last = starts.lastOrNull() ?: return this
        var first = last
        var used = system.characterCount + messages.subList(last, messages.size).sumOf { it.size }
        for (start in starts.dropLast(1).reversed()) {
            val turn = messages.subList(start, first).sumOf { it.size }
            if (used + turn > maxCharacters) break
            used += turn
            first = start
        }
        if (first <= 0) return this
        return copy(messages = messages.drop(first).map { it.copy(thinking = emptyList()) })
    }

    /** The same request without Anthropic thinking blocks: the retry when a provider rejects them. */
    val withoutThinking: AIRequest get() = copy(messages = messages.map { it.copy(thinking = emptyList()) })
}

/** An HTTP request ready for Ktor. */
class AIHTTPRequest(val url: String, val method: String, val headers: Map<String, String>, val body: ByteArray?)

/** Anthropic needs an output ceiling; this is the most an answer may use, not a target. */
const val ANTHROPIC_MAX_TOKENS = 16000
const val ANTHROPIC_VERSION = "2023-06-01"

/** The streaming chat request for [request]. Throws [AIError.BadAddress] or [AIError.NoModel]. */
fun AIConnection.chatRequest(request: AIRequest, key: String?): AIHTTPRequest {
    val base = normalizedBaseURL ?: throw AIError.BadAddress
    if (model.trimmingWhitespaces().isEmpty()) throw AIError.NoModel
    return when (format) {
        AIWireFormat.OPEN_AI -> {
            val body = linkedMapOf<String, Any?>(
                "model" to model,
                "messages" to openAIMessages(request),
                "stream" to true,
            )
            if (reportsStreamUsage) body["stream_options"] = mapOf("include_usage" to true)
            if (request.wantsJSON && supportsJSONMode) body["response_format"] = mapOf("type" to "json_object")
            if (request.tools.isNotEmpty()) {
                body["tools"] = request.tools.map { tool ->
                    mapOf("type" to "function", "function" to mapOf("name" to tool.name, "description" to tool.description, "parameters" to tool.schema))
                }
            }
            AIHTTPRequest("$base/chat/completions", "POST", headers(key), json(body).jsonText.toByteArray())
        }
        AIWireFormat.ANTHROPIC -> {
            val body = linkedMapOf<String, Any?>(
                "model" to model,
                "max_tokens" to ANTHROPIC_MAX_TOKENS,
                "messages" to anthropicMessages(request.messages),
                "stream" to true,
            )
            if (request.system.isNotEmpty()) body["system"] = request.system
            if (request.tools.isNotEmpty()) {
                body["tools"] = request.tools.map { tool ->
                    mapOf("name" to tool.name, "description" to tool.description, "input_schema" to tool.schema)
                }
            }
            AIHTTPRequest(anthropicURL(base, "messages"), "POST", headers(key), json(body).jsonText.toByteArray())
        }
    }
}

/**
 * Chat Completions messages: the system prompt, then per message the tool results as `tool` messages, the text (with
 * pictures as content parts) and the assistant's tool calls.
 */
internal fun openAIMessages(request: AIRequest): List<JsonElement> {
    val messages = mutableListOf<JsonElement>()
    if (request.system.isNotEmpty()) messages += obj("role" to "system", "content" to request.system)
    for (message in request.messages) {
        when (message.role) {
            AIMessage.Role.USER -> {
                for (result in message.toolResults) {
                    messages += obj(
                        "role" to "tool", "tool_call_id" to result.callId,
                        "content" to if (result.isError) "Error: ${result.content}" else result.content,
                    )
                }
                if (message.text.isNotEmpty() || message.images.isNotEmpty()) {
                    messages += obj("role" to "user", "content" to openAIContent(message))
                }
            }
            AIMessage.Role.ASSISTANT -> {
                if (message.text.isEmpty() && message.toolCalls.isEmpty()) continue
                val fields = linkedMapOf<String, Any?>("role" to "assistant", "content" to message.text)
                if (message.toolCalls.isNotEmpty()) {
                    fields["tool_calls"] = message.toolCalls.map { call ->
                        mapOf(
                            "id" to call.id, "type" to "function",
                            "function" to mapOf("name" to call.name, "arguments" to normalizedArguments(call.arguments)),
                        )
                    }
                }
                if (!message.reasoning.isNullOrEmpty()) fields["reasoning_content"] = message.reasoning
                messages += json(fields)
            }
        }
    }
    return messages
}

private fun openAIContent(message: AIMessage): JsonElement {
    val available = message.images.filter { it.isAvailable }
    var text = message.text
    if (available.size < message.images.size) text = listOf(AIImage.MISSING_NOTE, text).filter { it.isNotEmpty() }.joinToString("\n")
    if (available.isEmpty()) return JsonPrimitive(text)
    val parts = available.map { image ->
        obj("type" to "image_url", "image_url" to obj("url" to "data:${image.mediaType};base64,${base64(image.data)}"))
    }.toMutableList<JsonElement>()
    if (text.isNotEmpty()) parts += obj("type" to "text", "text" to text)
    return JsonArray(parts)
}

/**
 * Messages API messages: content blocks (thinking, text, tool use; tool results, pictures, text), with neighbouring
 * messages of the same role joined, as the API wants roles to alternate. A message with only text stays a plain string.
 */
internal fun anthropicMessages(messages: List<AIMessage>): List<JsonElement> {
    val result = mutableListOf<Pair<String, MutableList<JsonElement>>>()
    for (message in messages) {
        val blocks = mutableListOf<JsonElement>()
        when (message.role) {
            AIMessage.Role.USER -> {
                for (item in message.toolResults) {
                    blocks += obj(
                        "type" to "tool_result", "tool_use_id" to item.callId, "content" to item.content,
                        "is_error" to if (item.isError) true else null,
                    )
                }
                var text = message.text
                for (image in message.images) {
                    if (image.isAvailable) {
                        blocks += obj(
                            "type" to "image",
                            "source" to obj("type" to "base64", "media_type" to image.mediaType, "data" to base64(image.data)),
                        )
                    } else {
                        text = listOf(AIImage.MISSING_NOTE, text).filter { it.isNotEmpty() }.joinToString("\n")
                    }
                }
                if (text.isNotEmpty()) blocks += obj("type" to "text", "text" to text)
            }
            AIMessage.Role.ASSISTANT -> {
                for (block in message.thinking) {
                    if (block.redactedData != null) {
                        blocks += obj("type" to "redacted_thinking", "data" to block.redactedData)
                    } else if (block.signature != null) {
                        blocks += obj("type" to "thinking", "thinking" to block.text, "signature" to block.signature)
                    }
                }
                if (message.text.isNotEmpty()) blocks += obj("type" to "text", "text" to message.text)
                for (call in message.toolCalls) {
                    val input = parseJson(normalizedArguments(call.arguments)) as? JsonObject
                    blocks += obj("type" to "tool_use", "id" to call.id, "name" to call.name, "input" to (input ?: JsonObject(emptyMap())))
                }
            }
        }
        // An answer stopped before any text has nothing to send.
        if (blocks.isEmpty()) continue
        val role = if (message.role == AIMessage.Role.USER) "user" else "assistant"
        val last = result.lastOrNull()
        if (last != null && last.first == role) last.second += blocks else result += role to blocks
    }
    return result.map { (role, blocks) ->
        val only = blocks.singleOrNull() as? JsonObject
        if (only != null && only["type"]?.stringValue == "text" && only["text"] != null) {
            obj("role" to role, "content" to only["text"])
        } else {
            obj("role" to role, "content" to JsonArray(blocks))
        }
    }
}

/** Empty arguments (a tool without parameters) as `{}`. */
private fun normalizedArguments(text: String): String = if (text.trimmingWhitespacesAndNewlines().isEmpty()) "{}" else text

private fun base64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

/** The request that lists the provider's models. */
fun AIConnection.modelsRequest(key: String?): AIHTTPRequest {
    val base = normalizedBaseURL ?: throw AIError.BadAddress
    val headers = headers(key).toMutableMap()
    headers.remove("Content-Type")
    headers["Accept"] = "application/json"
    return when (format) {
        AIWireFormat.OPEN_AI -> AIHTTPRequest("$base/models", "GET", headers, null)
        AIWireFormat.ANTHROPIC -> AIHTTPRequest("${anthropicURL(base, "models")}?limit=100", "GET", headers, null)
    }
}

/** The model IDs in a models-list response (`{"data": [{"id": …}]}` in both formats), sorted. */
fun modelIDs(data: ByteArray): List<String> {
    val list = (parseJson(String(data, Charsets.UTF_8))?.get("data") as? JsonArray) ?: throw AIError.Unreadable
    val ids = list.map { (it["id"]?.stringValue) ?: throw AIError.Unreadable }
    return ids.toSortedSet().toList()
}

private fun AIConnection.headers(key: String?): Map<String, String> {
    val headers = linkedMapOf("Content-Type" to "application/json", "Accept" to "text/event-stream")
    val trimmed = key?.trimmingWhitespacesAndNewlines().orEmpty()
    when (format) {
        AIWireFormat.OPEN_AI -> if (trimmed.isNotEmpty()) headers["Authorization"] = "Bearer $trimmed"
        AIWireFormat.ANTHROPIC -> {
            headers["x-api-key"] = trimmed
            headers["anthropic-version"] = ANTHROPIC_VERSION
        }
    }
    return headers
}

/** `https://api.anthropic.com` and `https://api.anthropic.com/v1` both work as the base. */
private fun anthropicURL(base: String, endpoint: String): String {
    val path = runCatching { URI(base).path }.getOrNull().orEmpty()
    return if (path.substringAfterLast('/') == "v1") "$base/$endpoint" else "$base/v1/$endpoint"
}
