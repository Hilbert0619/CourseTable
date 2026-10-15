package io.github.hilbert0619.coursetable

import io.github.hilbert0619.aikit.AIConnection
import io.github.hilbert0619.aikit.AIError
import io.github.hilbert0619.aikit.AIProvider
import io.github.hilbert0619.aikit.AIRequest
import io.github.hilbert0619.aikit.AIStopReason
import io.github.hilbert0619.aikit.AIStreamEvent
import io.github.hilbert0619.aikit.AIWireFormat
import io.github.hilbert0619.aikit.chatRequest
import io.github.hilbert0619.aikit.modelIDs
import io.github.hilbert0619.aikit.modelsRequest
import io.github.hilbert0619.coursetable.ai.KtorAITransport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The real transport over Ktor, against canned HTTP responses (Ktor's mock engine; plain JVM). */
class KtorAITransportTest {
    private val deepSeek = AIConnection(AIProvider.preset("deepseek")!!)
    private val request = deepSeek.chatRequest(AIRequest("Be brief.", "Hi"), "sk-test")

    private fun transport(status: HttpStatusCode, body: String, seen: MutableList<HttpRequestData> = mutableListOf()) =
        KtorAITransport(
            MockEngine { data ->
                seen += data
                respond(ByteReadChannel(body.toByteArray()), status, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            },
        )

    @Test
    fun aStreamBecomesEventsAndTheRequestCarriesKeyAndBody() = runTest {
        val sse = listOf(
            """data: {"choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"}}]}""",
            "",
            """data: {"choices":[{"index":0,"delta":{"content":"lo 你好"},"finish_reason":"stop"}]}""",
            "",
            """data: {"choices":[],"usage":{"prompt_tokens":7,"completion_tokens":3}}""",
            "",
            "data: [DONE]",
            "",
        ).joinToString("\n")
        val seen = mutableListOf<HttpRequestData>()
        val events = transport(HttpStatusCode.OK, sse, seen).stream(request, AIWireFormat.OPEN_AI).toList()
        assertEquals(
            listOf(
                AIStreamEvent.Text("Hel"), AIStreamEvent.Text("lo 你好"), AIStreamEvent.Stop(AIStopReason.Complete),
                AIStreamEvent.Usage(7, 3), AIStreamEvent.End,
            ),
            events,
        )
        val sent = seen.single()
        assertEquals("https://api.deepseek.com/chat/completions", sent.url.toString())
        assertEquals("Bearer sk-test", sent.headers[HttpHeaders.Authorization])
        assertEquals("text/event-stream", sent.headers[HttpHeaders.Accept])
        val body = sent.body as OutgoingContent.ByteArrayContent
        assertEquals(ContentType.Application.Json, body.contentType?.withoutParameters())
        assertTrue(String(body.bytes()).contains("\"stream\":true"))
    }

    @Test
    fun anErrorStatusBecomesItsKind() = runTest {
        val body = """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error","code":"invalid_api_key"}}"""
        try {
            transport(HttpStatusCode.Unauthorized, body).stream(request, AIWireFormat.OPEN_AI).toList()
            fail("Expected an error")
        } catch (error: AIError) {
            assertEquals(AIError.WrongKey("Incorrect API key provided"), error)
        }
    }

    @Test
    fun aWholeAnswerIsReadWhenTheProviderDoesntStream() = runTest {
        val whole = """{"choices":[{"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":1}}"""
        val events = transport(HttpStatusCode.OK, whole).stream(request, AIWireFormat.OPEN_AI).toList()
        assertEquals(
            listOf(AIStreamEvent.Text("OK"), AIStreamEvent.Stop(AIStopReason.Complete), AIStreamEvent.Usage(5, 1), AIStreamEvent.End),
            events,
        )
    }

    @Test
    fun theModelsList() = runTest {
        val list = """{"object":"list","data":[{"id":"deepseek-v4-pro"},{"id":"deepseek-v4-flash"}]}"""
        val seen = mutableListOf<HttpRequestData>()
        val data = transport(HttpStatusCode.OK, list, seen).data(deepSeek.modelsRequest("sk-test"))
        assertEquals(listOf("deepseek-v4-flash", "deepseek-v4-pro"), modelIDs(data))
        assertEquals("https://api.deepseek.com/models", seen.single().url.toString())
    }
}
