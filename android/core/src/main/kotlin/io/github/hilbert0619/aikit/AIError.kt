package io.github.hilbert0619.aikit

import io.github.hilbert0619.vocabkit.trimmingWhitespacesAndNewlines
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What went wrong, in terms the app can explain. `providerMessage` is the provider's own text, when it sent one. */
sealed class AIError : Exception() {
    /** 401/403: the key is wrong, expired or not allowed to use this model. */
    data class WrongKey(val text: String?) : AIError()

    /** No credit or quota left (402, or any error that says so). */
    data class NoCredit(val text: String?) : AIError()

    /** 404: no such model, or the address is wrong. */
    data class NotFound(val text: String?) : AIError()

    /** 429: too many requests for now. */
    data class RateLimited(val text: String?) : AIError()

    /** 5xx / 529: the provider is overloaded or down. */
    data class Busy(val text: String?) : AIError()

    /** Any other rejected request. */
    data class Rejected(val status: Int?, val text: String?) : AIError()

    /** The connection failed (no network, timeout, TLS…). */
    data class Network(val text: String) : AIError()

    /** The base URL isn't a valid http(s) address. */
    data object BadAddress : AIError()

    /** No model name. */
    data object NoModel : AIError()

    /** The response wasn't in the expected format. */
    data object Unreadable : AIError()

    /** The answer ended without any text. */
    data object Empty : AIError()

    val providerMessage: String?
        get() = when (this) {
            is WrongKey -> text
            is NoCredit -> text
            is NotFound -> text
            is RateLimited -> text
            is Busy -> text
            is Rejected -> text
            is Network -> text
            BadAddress, NoModel, Unreadable, Empty -> null
        }

    override val message: String? get() = providerMessage ?: this::class.simpleName

    /** The provider rejected the request because of its tools: the model can't call them. */
    val rejectsTools: Boolean get() = rejectionMentions(listOf("tool", "function"))

    /** The provider rejected the request because of a picture: the model can't read them. */
    val rejectsImages: Boolean get() = rejectionMentions(listOf("image", "vision", "multimodal", "multi-modal"))

    private fun rejectionMentions(words: List<String>): Boolean {
        val text = when (this) {
            is Rejected -> if (status == null || status in 400 until 500) text else return false
            is NotFound -> text
            else -> return false
        }.orEmpty().lowercase()
        return words.any { text.contains(it) }
    }

    /** Worth trying again later without changing anything. */
    val isTransient: Boolean get() = this is RateLimited || this is Busy || this is Network

    companion object {
        /** An error response, from its HTTP status and body (both formats wrap the details in `"error"`). */
        fun from(status: Int, body: ByteArray): AIError {
            val details = envelopeDetails(body) ?: AIErrorDetails(message = rawText(body)?.trimmingWhitespacesAndNewlines()?.nullIfEmpty())
            return from(status, details)
        }

        fun from(status: Int?, details: AIErrorDetails): AIError {
            val message = details.message
            val words = listOfNotNull(details.type, details.code, message).joinToString(" ") { it.lowercase() }
            // "Quota" alone also shows up in per-minute rate limits (429), so it only counts for other statuses.
            val creditWords = listOf("insufficient", "balance", "credit", "arrearage", "billing")
            if (status == 402 || creditWords.any { words.contains(it) } || (status != 429 && words.contains("quota"))) {
                return NoCredit(message)
            }
            when {
                status == 401 || status == 403 -> return WrongKey(message)
                status == 404 -> return NotFound(message)
                status == 429 -> return RateLimited(message)
                status != null && status >= 500 -> return Busy(message)
            }
            // Errors inside a stream have no status; go by their type.
            if (words.contains("overloaded") || words.contains("api_error")) return Busy(message)
            if (words.contains("rate_limit")) return RateLimited(message)
            if (words.contains("authentication") || words.contains("permission")) return WrongKey(message)
            if (words.contains("not_found")) return NotFound(message)
            return Rejected(status, message)
        }

        /** `{"error": {…}}` or `{"error": "text"}` (e.g. Ollama); `null` when the body isn't such an object. */
        private fun envelopeDetails(body: ByteArray): AIErrorDetails? {
            val root = parseJson(String(body, Charsets.UTF_8)) as? JsonObject ?: return null
            return when (val error = root["error"]) {
                is JsonObject -> errorBody(error)
                is JsonPrimitive -> if (error.isString) AIErrorDetails(message = error.content) else null
                else -> null
            }
        }

        /** The first 300 bytes as text, or `null` when they aren't UTF-8 (as Swift's `String(data:encoding:)`). */
        private fun rawText(body: ByteArray): String? = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body, 0, minOf(body.size, 300)))
                .toString()
        }.getOrNull()
    }
}

data class AIErrorDetails(val message: String? = null, val type: String? = null, val code: String? = null)

/** `{"message": …, "type": …, "code": …}`; `code` is a string or a number depending on the provider. */
internal fun errorBody(error: JsonObject): AIErrorDetails {
    val code = error["code"]?.let { it.stringValue ?: it.intValue?.toString() }
    return AIErrorDetails(message = error["message"]?.stringValue, type = error["type"]?.stringValue, code = code)
}
