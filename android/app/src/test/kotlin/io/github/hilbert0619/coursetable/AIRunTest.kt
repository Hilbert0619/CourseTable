package io.github.hilbert0619.coursetable

import io.github.hilbert0619.aikit.AIConnection
import io.github.hilbert0619.aikit.AIError
import io.github.hilbert0619.aikit.AIHTTPRequest
import io.github.hilbert0619.aikit.AIPrompts
import io.github.hilbert0619.aikit.AIProvider
import io.github.hilbert0619.aikit.AIRequest
import io.github.hilbert0619.aikit.AIStopReason
import io.github.hilbert0619.aikit.AIStreamEvent
import io.github.hilbert0619.aikit.AIWireFormat
import io.github.hilbert0619.coursetable.ai.AIRun
import io.github.hilbert0619.coursetable.ai.AIStore
import io.github.hilbert0619.coursetable.ai.AITransport
import io.github.hilbert0619.coursetable.ai.CachedAIAnswer
import io.github.hilbert0619.coursetable.ai.DemoAITransport
import io.github.hilbert0619.coursetable.ai.MemoryAIKeys
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One request and its streamed answer ([AIRun]) and the store around it ([AIStore]), on the JVM with fake transports. */
class AIRunTest {
    private val deepSeek = AIConnection(AIProvider.preset("deepseek")!!)
    private val now = Instant.parse("2026-10-15T02:00:00Z")

    /** Answers every request with [events], or fails with [error] after them. */
    private class ScriptedTransport(val events: List<AIStreamEvent>, val error: AIError? = null) : AITransport {
        var requests = 0
        override fun stream(request: AIHTTPRequest, format: AIWireFormat): Flow<AIStreamEvent> = flow {
            requests += 1
            events.forEach { emit(it) }
            error?.let { throw it }
        }

        override suspend fun data(request: AIHTTPRequest): ByteArray = ByteArray(0)
    }

    private fun store(transport: AITransport) = AIStore(null, null, transport, MemoryAIKeys(), clock = { now }, zone = ZoneId.of("Asia/Shanghai")).also {
        it.save(deepSeek, "sk-test")
    }

    @Test
    fun aStreamedAnswerIsShownAndCounted() = runTest {
        val store = store(DemoAITransport(pieceDelayMillis = 0))
        val run = AIRun(this)
        var completed: String? = null
        run.start(AIRequest("", "Explain abandon"), store, onComplete = { completed = it.text })
        assertTrue(run.state.isRunning)
        assertEquals("deepseek-v4-flash", run.state.model)
        advanceUntilIdle()
        assertFalse(run.state.isRunning)
        assertTrue(run.state.text.startsWith("## Core meaning"))
        assertEquals(run.state.text, completed)
        assertEquals(AIStopReason.Complete, run.state.stop)
        val usage = store.usage(deepSeek)
        assertEquals(1, usage.requests)
        assertEquals(180, usage.inputTokens)
    }

    @Test
    fun errorsEmptyAnswersAndCutOffs() = runTest {
        val failing = store(ScriptedTransport(listOf(AIStreamEvent.Text("Half")), AIError.RateLimited("slow down")))
        val run = AIRun(this)
        var completed = false
        var ended = false
        run.start(AIRequest("", "Hi"), failing, onComplete = { completed = true }, onEnd = { ended = true })
        advanceUntilIdle()
        assertEquals(AIError.RateLimited("slow down"), run.state.error)
        assertEquals("Half", run.state.text)
        assertFalse(completed)
        assertTrue(ended)

        run.start(AIRequest("", "Hi"), store(ScriptedTransport(listOf(AIStreamEvent.End))))
        advanceUntilIdle()
        assertEquals(AIError.Empty, run.state.error)

        completed = false
        val cutOff = listOf(AIStreamEvent.Text("Long answer"), AIStreamEvent.Stop(AIStopReason.CutOff), AIStreamEvent.End)
        run.start(AIRequest("", "Hi"), store(ScriptedTransport(cutOff)), onComplete = { completed = true })
        advanceUntilIdle()
        assertNull(run.state.error)
        assertEquals(AIStopReason.CutOff, run.state.stop)
        assertFalse(completed)
    }

    @Test
    fun aBadConnectionFailsBeforeAnyRequest() = runTest {
        val transport = ScriptedTransport(emptyList())
        val store = store(transport)
        val run = AIRun(this)
        run.start(AIRequest("", "Hi"), store, connection = deepSeek.copy(model = " "))
        advanceUntilIdle()
        assertEquals(AIError.NoModel, run.state.error)
        assertEquals(0, transport.requests)
    }

    @Test
    fun aSavedAnswerShowsWithoutAsking() = runTest {
        val run = AIRun(this)
        run.show(CachedAIAnswer("Saved text", "glm-4.7-flash", 0))
        assertEquals("Saved text", run.state.text)
        assertEquals("glm-4.7-flash", run.state.model)
        assertFalse(run.state.isRunning)
        assertTrue(run.state.hasContent)
    }

    @Test
    fun theConnectionTestsAnswerAsAModelWould() = runTest {
        val store = store(DemoAITransport(pieceDelayMillis = 0))
        val run = AIRun(this)
        run.start(AIPrompts.toolTest, store)
        advanceUntilIdle()
        assertEquals(listOf("ping"), run.answer.toolCalls.map { it.name })
        run.start(AIPrompts.connectionTest, store)
        advanceUntilIdle()
        assertEquals("OK", run.state.text)
    }
}

class AIStoreTest {
    private val now = Instant.parse("2026-10-15T02:00:00Z")
    private fun store() = AIStore(null, null, DemoAITransport(0), MemoryAIKeys(), clock = { now }, zone = ZoneId.of("Asia/Shanghai"))

    @Test
    fun connectionsKeysAndTheDefault() {
        val store = store()
        assertFalse(store.isSetUp)
        val glm = AIConnection(AIProvider.preset("glm")!!)
        val qwen = AIConnection(AIProvider.preset("qwen")!!)
        store.save(glm, " key-1 ")
        store.save(qwen, "key-2")
        // The first one added is the default until another is chosen.
        assertEquals(glm.id, store.defaultConnection?.id)
        assertEquals("key-1", store.key(glm))
        store.makeDefault(qwen)
        assertEquals(qwen.id, store.defaultConnection?.id)
        // A null key keeps the stored one; an empty one removes it.
        store.save(qwen.copy(model = "qwen-max"), null)
        assertEquals("key-2", store.key(qwen))
        assertEquals("qwen-max", store.state.value.connections.last().model)
        store.save(qwen, "")
        assertFalse(store.hasKey(qwen))
        // Deleting the default moves it to the first left.
        store.delete(qwen)
        assertEquals(glm.id, store.defaultConnection?.id)
        assertEquals(listOf(glm.id), store.state.value.connections.map { it.id })
    }

    @Test
    fun savedAnswersAreCappedOldestFirst() {
        var time = now
        val store = AIStore(null, null, DemoAITransport(0), MemoryAIKeys(), clock = { time })
        repeat(AIStore.MAX_CACHED_ANSWERS + 3) { index ->
            time = now.plusSeconds(index.toLong())
            store.saveAnswer("answer $index", "m", "key $index")
        }
        assertEquals(AIStore.MAX_CACHED_ANSWERS, store.state.value.savedAnswerCount)
        assertNull(store.cachedAnswer("key 0"))
        assertNull(store.cachedAnswer("key 2"))
        assertEquals("answer 3", store.cachedAnswer("key 3")?.text)
        store.clearSavedAnswers()
        assertEquals(0, store.state.value.savedAnswerCount)
    }
}
