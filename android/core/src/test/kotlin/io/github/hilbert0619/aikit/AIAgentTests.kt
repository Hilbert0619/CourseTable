package io.github.hilbert0619.aikit

import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.TimeOfDay
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer

/** The Swift package's `AIAgentTests.swift`, test for test. */
class AIAgentSessionTests {
    private val read = AITool(
        "look", "Reads.", listOf(AIToolParameter("n", AIToolParameter.Kind.Integer(1..5), "N")), AITool.Effect.READS,
    )
    private val change = AITool(
        "add", "Changes.", listOf(AIToolParameter("title", AIToolParameter.Kind.Text(20), "T", isRequired = true)), AITool.Effect.CHANGES,
    )

    private fun answer(text: String = "", calls: List<Triple<String, String, String>> = emptyList(), thinking: Boolean = false): AIAnswer {
        val answer = AIAnswer()
        if (thinking) {
            answer.apply(AIStreamEvent.ThinkingBlock(0, null))
            answer.apply(AIStreamEvent.ThinkingSignature(0, "sig"))
        }
        if (text.isNotEmpty()) answer.apply(AIStreamEvent.Text(text))
        calls.forEachIndexed { index, (id, name, arguments) ->
            answer.apply(AIStreamEvent.ToolCall(index + 1, id, name))
            answer.apply(AIStreamEvent.ToolArguments(index + 1, arguments))
        }
        answer.apply(AIStreamEvent.Stop(if (calls.isEmpty()) AIStopReason.Complete else AIStopReason.ToolUse))
        answer.apply(AIStreamEvent.End)
        return answer
    }

    @Test
    fun aRoundOfReadingAndChangingCalls() {
        val session = AIAgentSession(tools = listOf(read, change))
        session.send("Hi", system = "S1")
        assertTrue(session.isWaitingForModel)
        assertEquals("S1", session.request().system)
        assertEquals(listOf("look", "add"), session.request().tools.map { it.name })

        val pending = session.receive(
            answer(
                "Let me see.",
                listOf(
                    Triple("a", "look", "{\"n\": 2}"), Triple("b", "add", "{\"title\": \"Milk\"}"), Triple("c", "look", "{\"n\": 9}"),
                    Triple("d", "fly", "{}"),
                ),
            ),
        )
        // The bad call and the unknown tool are answered at once; the others wait.
        assertEquals(listOf("a", "b"), pending.map { it.id })
        assertEquals(listOf(false, true), pending.map { it.needsConfirmation })
        assertEquals(2, pending[0].arguments.int("n"))
        assertFalse(session.isWaitingForModel)

        session.resolve("b", "Added.")
        assertFalse(session.isWaitingForModel)
        session.resolve("a", "Two things.")
        assertTrue(session.isWaitingForModel)
        // One message with every result, in the calls' order.
        val results = session.messages.last().toolResults
        assertEquals(listOf("a", "b", "c", "d"), results.map { it.callId })
        assertEquals(listOf(false, false, true, true), results.map { it.isError })
        assertEquals("Invalid arguments for look: \"n\" must be between 1 and 5.", results[2].content)
        assertEquals("There is no tool named \"fly\". Tools: look, add.", results[3].content)

        assertTrue(session.receive(answer("Done.")).isEmpty())
        assertFalse(session.isWaitingForModel)
        assertEquals(
            listOf(AIMessage.Role.USER, AIMessage.Role.ASSISTANT, AIMessage.Role.USER, AIMessage.Role.ASSISTANT),
            session.messages.map { it.role },
        )
        assertEquals(1, session.rounds)
    }

    @Test
    fun aNewMessageClosesCallsStillWaiting() {
        val session = AIAgentSession(tools = listOf(change))
        session.send("Add milk", system = "S")
        session.receive(answer(calls = listOf(Triple("x", "add", "{\"title\": \"Milk\"}"))))
        session.send("Actually, no", system = "S2")
        val last = session.messages.last()
        assertEquals("Actually, no", last.text)
        assertEquals(listOf(AIToolResult("x", AIAgentSession.INTERRUPTED_RESULT)), last.toolResults)
        assertTrue(session.pending.isEmpty())
        assertTrue(session.isWaitingForModel)
        assertEquals("S2", session.request().system)
    }

    @Test
    fun theRoundLimitStopsTheTurn() {
        val session = AIAgentSession(tools = listOf(read))
        session.send("Loop", system = "")
        for (round in 1..AIAgentSession.MAX_ROUNDS) {
            val pending = session.receive(answer(calls = listOf(Triple("r$round", "look", "{}"))))
            assertEquals(1, pending.size)
            session.resolve("r$round", "ok")
        }
        assertTrue(session.isWaitingForModel)
        assertTrue(session.receive(answer(calls = listOf(Triple("r9", "look", "{}")))).isEmpty())
        assertTrue(session.stoppedAtRoundLimit)
        assertFalse(session.isWaitingForModel)
        assertEquals(listOf(AIToolResult("r9", AIAgentSession.ROUND_LIMIT_RESULT, isError = true)), session.messages.last().toolResults)
        // The next message starts a new turn.
        session.send("Go on", system = "")
        assertTrue(session.isWaitingForModel)
        assertEquals(0, session.rounds)
    }

    @Test
    fun missingAndRepeatedCallIDsAreReplaced() {
        val session = AIAgentSession(tools = listOf(read))
        session.send("Hi", system = "")
        val first = session.receive(answer(calls = listOf(Triple("", "look", "{}"), Triple("", "look", "{}"))))
        assertEquals(listOf("call_1_1", "call_1_2"), first.map { it.id })
        for (call in first) session.resolve(call.id, "ok")
        val second = session.receive(answer(calls = listOf(Triple("call_1_1", "look", "{}"))))
        assertEquals(listOf("call_2_1"), second.map { it.id })
    }

    @Test
    fun thinkingBlocksLastOnlyForTheirTurn() {
        val session = AIAgentSession(tools = listOf(read))
        session.send("Hi", system = "")
        val pending = session.receive(answer("", listOf(Triple("a", "look", "{}")), thinking = true))
        session.resolve(pending[0].id, "ok")
        // Within the turn they go back unchanged…
        assertEquals(listOf(AIThinkingBlock(signature = "sig")), session.request().messages[1].thinking)
        session.receive(answer("Done.", thinking = true))
        // …and a new turn drops them all (a leading run, which Anthropic allows).
        session.send("Next", system = "")
        assertTrue(session.messages.all { it.thinking.isEmpty() })
    }

    @Test
    fun aSavedChatWithUnansweredCallsCanContinue() {
        val saved = listOf(
            AIMessage(AIMessage.Role.USER, "Add milk"),
            AIMessage(
                AIMessage.Role.ASSISTANT, "", toolCalls = listOf(AIToolCall("x", "add", "{}")),
                thinking = listOf(AIThinkingBlock(signature = "s")),
            ),
        )
        val session = AIAgentSession(saved, tools = listOf(change))
        assertEquals(3, session.messages.size)
        assertEquals(listOf(AIToolResult("x", AIAgentSession.CLOSED_RESULT)), session.messages[2].toolResults)
        assertTrue(session.messages[1].thinking.isEmpty())
        session.send("Hello again", system = "")
        assertTrue(session.isWaitingForModel)
    }

    @Test
    fun aStoppedAnswerKeepsOnlyItsText() {
        val session = AIAgentSession(tools = listOf(read))
        session.send("Hi", system = "")
        val answer = answer("Half an ans", listOf(Triple("a", "look", "{\"n\"")))
        answer.stop = null
        session.receiveStopped(answer)
        assertEquals(AIMessage(AIMessage.Role.ASSISTANT, "Half an ans"), session.messages.last())
        assertTrue(session.pending.isEmpty())
    }

    @Test
    fun aCutOffAnswerDropsItsUnfinishedCall() {
        val session = AIAgentSession(tools = listOf(read))
        session.send("Hi", system = "")
        val answer = answer("Text", listOf(Triple("a", "look", "{\"n\": 1}"), Triple("b", "look", "{\"n\"")))
        answer.apply(AIStreamEvent.Stop(AIStopReason.CutOff))
        val pending = session.receive(answer)
        assertEquals(listOf("a"), pending.map { it.id })
        assertEquals(listOf("a"), session.messages.last().toolCalls.map { it.id })
    }
}

class AIMessageStorageTests {
    @Test
    fun chatsSavedBy04StillOpen() {
        val old = """[{"role":"user","text":"Hi"},{"role":"assistant","text":"Hello"}]"""
        val messages = AIKitJson.decodeFromString(ListSerializer(AIMessage.serializer()), old)
        assertEquals(listOf(AIMessage(AIMessage.Role.USER, "Hi"), AIMessage(AIMessage.Role.ASSISTANT, "Hello")), messages)
        // A plain message is saved as before.
        assertEquals("""{"role":"user","text":"Hi"}""", AIKitJson.encodeToString(AIMessage.serializer(), AIMessage(AIMessage.Role.USER, "Hi")))
    }

    @Test
    fun savedMessagesDropPicturesAndThinking() {
        val message = AIMessage(AIMessage.Role.USER, "Look", images = listOf(AIImage(byteArrayOf(1, 2, 3))))
        assertEquals(listOf(AIImage(ByteArray(0))), message.forStorage.images)
        assertFalse(message.forStorage.images[0].isAvailable)
        val answer = AIMessage(AIMessage.Role.ASSISTANT, "Hm", reasoning = "r", thinking = listOf(AIThinkingBlock(signature = "s")))
        assertTrue(answer.forStorage.thinking.isEmpty())
        assertEquals("r", answer.forStorage.reasoning)
        val round = AIKitJson.decodeFromString(AIMessage.serializer(), AIKitJson.encodeToString(AIMessage.serializer(), message.forStorage))
        assertEquals(message.forStorage, round)
    }

    @Test
    fun trimmingKeepsToolRoundsWhole() {
        val messages = listOf(
            AIMessage(AIMessage.Role.USER, "a".repeat(100)),
            AIMessage(AIMessage.Role.ASSISTANT, "x", thinking = listOf(AIThinkingBlock(signature = "s"))),
            AIMessage(AIMessage.Role.USER, "second"),
            AIMessage(
                AIMessage.Role.ASSISTANT, "", toolCalls = listOf(AIToolCall("1", "look", "b".repeat(100))),
                thinking = listOf(AIThinkingBlock(signature = "t")),
            ),
            AIMessage(AIMessage.Role.USER, "", toolResults = listOf(AIToolResult("1", "c".repeat(100)))),
        )
        val request = AIRequest("", messages)
        val trimmed = request.trimmed(250)
        assertEquals(listOf("second", "", ""), trimmed.messages.map { it.text })
        // Something was dropped, so the remaining thinking blocks go too.
        assertTrue(trimmed.messages.all { it.thinking.isEmpty() })
        assertEquals(messages, request.trimmed(10_000).messages)
        assertTrue(request.withoutThinking.messages.all { it.thinking.isEmpty() })
    }
}

class AIConnectionCapabilityTests {
    @Test
    fun presetsSayWhatTheirModelsCanDo() {
        val glm = AIConnection(AIProvider.preset("glm")!!)
        assertTrue(glm.usesTools)
        assertFalse(glm.readsImages)
        assertEquals("glm-4.6v-flash", glm.photoModel)
        assertEquals("glm-4.6v-flash", glm.pictureConnection?.model)
        assertEquals(true, glm.pictureConnection?.readsImages)

        val deepSeek = AIConnection(AIProvider.preset("deepseek")!!)
        assertNull(deepSeek.pictureConnection)

        var claude = AIConnection(AIProvider.preset("anthropic")!!)
        assertTrue(claude.readsImages)
        assertEquals(claude, claude.pictureConnection)
        // Another model name isn't assumed to read pictures; the switch decides then.
        claude = claude.copy(model = "claude-something-new")
        assertFalse(claude.readsImages)
        claude = claude.copy(imagesSwitch = true)
        assertTrue(claude.readsImages)

        var custom = AIConnection(providerID = "custom", name = "Mine", format = AIWireFormat.OPEN_AI, baseURL = "https://x.example", model = "m")
        assertTrue(custom.usesTools)
        assertNull(custom.photoModel)
        custom = custom.copy(toolsSwitch = false, photoModelChoice = " vision-1 ")
        assertFalse(custom.usesTools)
        assertEquals("vision-1", custom.pictureConnection?.model)
    }

    @Test
    fun connectionsSavedBy04StillOpen() {
        val old = """{"id":"6B4E0B4A-2B7A-4E2F-9A57-2D4C2B9D8C11","providerID":"glm","name":"GLM","format":"openAI","baseURL":"https://open.bigmodel.cn/api/paas/v4","model":"glm-4.7-flash"}"""
        val connection = AIKitJson.decodeFromString(AIConnection.serializer(), old)
        assertNull(connection.toolsSwitch)
        assertTrue(connection.usesTools)
        assertEquals(UUID.fromString("6B4E0B4A-2B7A-4E2F-9A57-2D4C2B9D8C11"), connection.id)
        assertEquals(AIWireFormat.OPEN_AI, connection.format)
    }

    @Test
    fun rejectionsAboutToolsOrPictures() {
        assertTrue(AIError.Rejected(400, "This model does not support function calling").rejectsTools)
        assertTrue(AIError.Rejected(400, "registry.ollama.ai/library/gemma does not support tools").rejectsTools)
        assertTrue(AIError.Rejected(400, "Invalid image_url: the model is not multimodal").rejectsImages)
        assertFalse(AIError.Rejected(400, "Bad request").rejectsTools)
        assertFalse(AIError.Busy("tool overloaded").rejectsTools)
    }

    @Test
    fun assistantPromptMentionsToolsOnlyWhenUsed() {
        val today = LocalDate(2026, 10, 15)
        val plain = AIPrompts.assistant(AIAnswerLanguage.ENGLISH, today, schedule = null, weakWords = null)
        assertTrue(plain.contains("Today is 2026-10-15 (Thursday)."))
        assertFalse(plain.contains("tools"))
        val agent = AIPrompts.assistant(AIAnswerLanguage.ENGLISH, today, TimeOfDay(9, 5), schedule = null, weakWords = null, usesTools = true)
        assertTrue(agent.contains("Today is 2026-10-15 (Thursday), now 09:05."))
        assertTrue(agent.contains("tap Confirm"))
    }

    @Test
    fun toolAndPictureChecks() {
        val deepSeek = AIConnection(AIProvider.preset("deepseek")!!)
        val body = parseJson(String(assertNotNull(deepSeek.chatRequest(AIPrompts.toolTest, "k").body), Charsets.UTF_8))
        assertNotNull(body?.get("tools"))
        val picture = AIPrompts.imageTest(AIImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertEquals(1, picture.messages[0].images.size)
    }
}
