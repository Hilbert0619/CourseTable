package io.github.hilbert0619.coursetable.ai

import io.github.hilbert0619.aikit.AIError
import io.github.hilbert0619.aikit.AIHTTPRequest
import io.github.hilbert0619.aikit.AIStopReason
import io.github.hilbert0619.aikit.AIStreamDecoder
import io.github.hilbert0619.aikit.AIStreamEvent
import io.github.hilbert0619.aikit.AIWireFormat
import io.github.hilbert0619.aikit.SSEEvent
import io.github.hilbert0619.aikit.SSEParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Sends AI requests. The real one uses Ktor; tests and screenshots use canned answers. */
interface AITransport {
    /** The streamed answer to a chat request. Fails with an [AIError]. */
    fun stream(request: AIHTTPRequest, format: AIWireFormat): Flow<AIStreamEvent>

    /** A plain request's body (the models list). Fails with an [AIError]. */
    suspend fun data(request: AIHTTPRequest): ByteArray
}

/**
 * Talks to the providers over Ktor (OkHttp), the client supabase-kt already brings. The stream is read as bytes and fed
 * to [SSEParser], which keeps the blank lines that end events and characters split across chunks.
 */
class KtorAITransport(engine: HttpClientEngine? = null) : AITransport {
    private val client: HttpClient = (if (engine != null) HttpClient(engine) { configure() } else HttpClient(OkHttp) { configure() })

    private fun io.ktor.client.HttpClientConfig<*>.configure() {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            // Reasoning models can think for a while before the first word.
            socketTimeoutMillis = 120_000
        }
    }

    override fun stream(request: AIHTTPRequest, format: AIWireFormat): Flow<AIStreamEvent> = channelFlow {
        failingAsAIError {
            client.prepareRequest { build(request) }.execute { response ->
                val channel = response.bodyAsChannel()
                if (!response.status.isSuccess()) throw AIError.from(response.status.value, readUpTo(channel, 64 * 1024))
                readEvents(channel, AIStreamDecoder(format), this@channelFlow)
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Decodes the events as they arrive; a body that wasn't streamed (some proxies) is read whole at the end. */
    private suspend fun readEvents(channel: ByteReadChannel, decoder: AIStreamDecoder, output: SendChannel<AIStreamEvent>) {
        val parser = SSEParser()
        val buffer = ByteArray(8192)
        // Kept until the first event, in case the provider answered in one piece instead.
        var whole: ByteArrayOutputStream? = ByteArrayOutputStream()
        suspend fun handle(events: List<SSEEvent>) {
            for (event in events) {
                whole = null
                for (item in decoder.decode(event)) output.send(item)
            }
        }
        while (true) {
            val count = channel.readAvailable(buffer, 0, buffer.size)
            if (count < 0) break
            if (count == 0) continue
            whole?.let { if (it.size() < 1024 * 1024) it.write(buffer, 0, count) }
            handle(parser.feed(buffer, 0, count))
        }
        handle(parser.finish())
        whole?.let { bytes -> decoder.decodeComplete(bytes.toByteArray())?.forEach { output.send(it) } }
    }

    override suspend fun data(request: AIHTTPRequest): ByteArray = failingAsAIError {
        client.prepareRequest { build(request) }.execute { response: HttpResponse ->
            val body = readUpTo(response.bodyAsChannel(), 8 * 1024 * 1024)
            if (!response.status.isSuccess()) throw AIError.from(response.status.value, body)
            body
        }
    }

    private fun HttpRequestBuilder.build(request: AIHTTPRequest) {
        url(request.url)
        method = HttpMethod.parse(request.method)
        for ((name, value) in request.headers) {
            // Ktor sets the content type from the body.
            if (!name.equals("Content-Type", ignoreCase = true)) header(name, value)
        }
        request.body?.let { setBody(ByteArrayContent(it, ContentType.Application.Json)) }
    }

    private suspend fun readUpTo(channel: ByteReadChannel, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val count = channel.readAvailable(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /** Any failure other than a cancellation becomes an [AIError] (a network failure unless it already is one). */
    private suspend fun <T> failingAsAIError(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: AIError) {
        throw error
    } catch (error: Exception) {
        throw AIError.Network(error.message ?: error.javaClass.simpleName)
    }
}

/**
 * Canned answers for screenshots and tests, streamed in small pieces, never touching the network: word help, "Why?",
 * sentence analysis, essay feedback, the quick-add to-do, the Assistant and the connection checks (iOS
 * `DemoAITransport`).
 */
class DemoAITransport(private val pieceDelayMillis: Long = 8) : AITransport {
    override fun stream(request: AIHTTPRequest, format: AIWireFormat): Flow<AIStreamEvent> = flow {
        val body = request.body?.let { String(it, Charsets.UTF_8) }.orEmpty()
        emit(AIStreamEvent.Usage(180, null))
        if (body.contains("\"tools\"") && body.contains("Call the ping tool")) {
            emit(AIStreamEvent.ToolCall(0, "call_demo_ping", "ping"))
            emit(AIStreamEvent.ToolArguments(0, "{}"))
            emit(AIStreamEvent.Stop(AIStopReason.ToolUse))
            emit(AIStreamEvent.Usage(null, 30))
        } else {
            val pieces = pieces(DemoAnswers.answer(body))
            for (piece in pieces) {
                if (pieceDelayMillis > 0) delay(pieceDelayMillis)
                emit(AIStreamEvent.Text(piece))
            }
            emit(AIStreamEvent.Stop(AIStopReason.Complete))
            emit(AIStreamEvent.Usage(null, pieces.size))
        }
        emit(AIStreamEvent.End)
    }

    override suspend fun data(request: AIHTTPRequest): ByteArray =
        """{"object":"list","data":[{"id":"deepseek-v4-flash"},{"id":"deepseek-v4-pro"}]}""".toByteArray()

    /** Up to four characters at a time, ending at spaces and line breaks, as a model streams. */
    private fun pieces(text: String): List<String> {
        val pieces = mutableListOf<String>()
        val piece = StringBuilder()
        for (character in text) {
            piece.append(character)
            if (character == ' ' || character == '\n' || piece.length >= 4) {
                pieces += piece.toString()
                piece.clear()
            }
        }
        if (piece.isNotEmpty()) pieces += piece.toString()
        return pieces
    }
}
