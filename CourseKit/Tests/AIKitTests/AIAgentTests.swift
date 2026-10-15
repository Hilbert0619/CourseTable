import CourseKit
import Foundation
import Testing
@testable import AIKit

@Suite struct AIAgentSessionTests {
    static let read = AITool(name: "look", description: "Reads.", parameters: [AIToolParameter("n", .integer(1...5), "N")],
                             effect: .reads)
    static let change = AITool(name: "add", description: "Changes.",
                               parameters: [AIToolParameter("title", .text(maxLength: 20), "T", required: true)], effect: .changes)

    static func answer(_ text: String = "", calls: [(String, String, String)] = [], thinking: Bool = false) -> AIAnswer {
        var answer = AIAnswer()
        if thinking {
            answer.apply(.thinkingBlock(index: 0, redactedData: nil))
            answer.apply(.thinkingSignature(index: 0, "sig"))
        }
        if !text.isEmpty { answer.apply(.text(text)) }
        for (index, call) in calls.enumerated() {
            answer.apply(.toolCall(index: index + 1, id: call.0, name: call.1))
            answer.apply(.toolArguments(index: index + 1, call.2))
        }
        answer.apply(.stop(calls.isEmpty ? .complete : .toolUse))
        answer.apply(.end)
        return answer
    }

    @Test func aRoundOfReadingAndChangingCalls() {
        var session = AIAgentSession(tools: [Self.read, Self.change])
        session.send("Hi", system: "S1")
        #expect(session.isWaitingForModel)
        #expect(session.request().system == "S1")
        #expect(session.request().tools.map(\.name) == ["look", "add"])

        let pending = session.receive(Self.answer("Let me see.", calls: [
            ("a", "look", "{\"n\": 2}"), ("b", "add", "{\"title\": \"Milk\"}"), ("c", "look", "{\"n\": 9}"), ("d", "fly", "{}"),
        ]))
        // The bad call and the unknown tool are answered at once; the others wait.
        #expect(pending.map(\.id) == ["a", "b"])
        #expect(pending.map(\.needsConfirmation) == [false, true])
        #expect(pending[0].arguments.int("n") == 2)
        #expect(!session.isWaitingForModel)

        session.resolve("b", content: "Added.")
        #expect(!session.isWaitingForModel)
        session.resolve("a", content: "Two things.")
        #expect(session.isWaitingForModel)
        // One message with every result, in the calls' order.
        let results = session.messages.last!.toolResults
        #expect(results.map(\.callID) == ["a", "b", "c", "d"])
        #expect(results.map(\.isError) == [false, false, true, true])
        #expect(results[2].content == "Invalid arguments for look: \"n\" must be between 1 and 5.")
        #expect(results[3].content == "There is no tool named \"fly\". Tools: look, add.")

        #expect(session.receive(Self.answer("Done.")).isEmpty)
        #expect(!session.isWaitingForModel)
        #expect(session.messages.map(\.role) == [.user, .assistant, .user, .assistant])
        #expect(session.rounds == 1)
    }

    @Test func aNewMessageClosesCallsStillWaiting() {
        var session = AIAgentSession(tools: [Self.change])
        session.send("Add milk", system: "S")
        session.receive(Self.answer(calls: [("x", "add", "{\"title\": \"Milk\"}")]))
        session.send("Actually, no", system: "S2")
        let last = session.messages.last!
        #expect(last.text == "Actually, no")
        #expect(last.toolResults == [AIToolResult(callID: "x", content: AIAgentSession.interruptedResult)])
        #expect(session.pending.isEmpty)
        #expect(session.isWaitingForModel)
        #expect(session.request().system == "S2")
    }

    @Test func theRoundLimitStopsTheTurn() {
        var session = AIAgentSession(tools: [Self.read])
        session.send("Loop", system: "")
        for round in 1...AIAgentSession.maxRounds {
            let pending = session.receive(Self.answer(calls: [("r\(round)", "look", "{}")]))
            #expect(pending.count == 1)
            session.resolve("r\(round)", content: "ok")
        }
        #expect(session.isWaitingForModel)
        #expect(session.receive(Self.answer(calls: [("r9", "look", "{}")])).isEmpty)
        #expect(session.stoppedAtRoundLimit)
        #expect(!session.isWaitingForModel)
        #expect(session.messages.last!.toolResults == [AIToolResult(callID: "r9", content: AIAgentSession.roundLimitResult, isError: true)])
        // The next message starts a new turn.
        session.send("Go on", system: "")
        #expect(session.isWaitingForModel)
        #expect(session.rounds == 0)
    }

    @Test func missingAndRepeatedCallIDsAreReplaced() {
        var session = AIAgentSession(tools: [Self.read])
        session.send("Hi", system: "")
        let first = session.receive(Self.answer(calls: [("", "look", "{}"), ("", "look", "{}")]))
        #expect(first.map(\.id) == ["call_1_1", "call_1_2"])
        for call in first { session.resolve(call.id, content: "ok") }
        let second = session.receive(Self.answer(calls: [("call_1_1", "look", "{}")]))
        #expect(second.map(\.id) == ["call_2_1"])
    }

    @Test func thinkingBlocksLastOnlyForTheirTurn() {
        var session = AIAgentSession(tools: [Self.read])
        session.send("Hi", system: "")
        let pending = session.receive(Self.answer("", calls: [("a", "look", "{}")], thinking: true))
        session.resolve(pending[0].id, content: "ok")
        // Within the turn they go back unchanged…
        #expect(session.request().messages[1].thinking == [AIThinkingBlock(signature: "sig")])
        session.receive(Self.answer("Done.", thinking: true))
        // …and a new turn drops them all (a leading run, which Anthropic allows).
        session.send("Next", system: "")
        #expect(session.messages.allSatisfy { $0.thinking.isEmpty })
    }

    @Test func aSavedChatWithUnansweredCallsCanContinue() {
        let saved = [AIMessage(.user, "Add milk"),
                     AIMessage(.assistant, "", toolCalls: [AIToolCall(id: "x", name: "add", arguments: "{}")],
                               thinking: [AIThinkingBlock(signature: "s")])]
        var session = AIAgentSession(messages: saved, tools: [Self.change])
        #expect(session.messages.count == 3)
        #expect(session.messages[2].toolResults == [AIToolResult(callID: "x", content: AIAgentSession.closedResult)])
        #expect(session.messages[1].thinking.isEmpty)
        session.send("Hello again", system: "")
        #expect(session.isWaitingForModel)
    }

    @Test func aStoppedAnswerKeepsOnlyItsText() {
        var session = AIAgentSession(tools: [Self.read])
        session.send("Hi", system: "")
        var answer = Self.answer("Half an ans", calls: [("a", "look", "{\"n\"")])
        answer.stop = nil
        session.receiveStopped(answer)
        #expect(session.messages.last == AIMessage(.assistant, "Half an ans"))
        #expect(session.pending.isEmpty)
    }

    @Test func aCutOffAnswerDropsItsUnfinishedCall() {
        var session = AIAgentSession(tools: [Self.read])
        session.send("Hi", system: "")
        var answer = Self.answer("Text", calls: [("a", "look", "{\"n\": 1}"), ("b", "look", "{\"n\"")])
        answer.apply(.stop(.cutOff))
        let pending = session.receive(answer)
        #expect(pending.map(\.id) == ["a"])
        #expect(session.messages.last!.toolCalls.map(\.id) == ["a"])
    }
}

@Suite struct AIMessageStorageTests {
    @Test func chatsSavedBy04StillOpen() throws {
        let old = #"[{"role":"user","text":"Hi"},{"role":"assistant","text":"Hello"}]"#
        let messages = try JSONDecoder().decode([AIMessage].self, from: Data(old.utf8))
        #expect(messages == [AIMessage(.user, "Hi"), AIMessage(.assistant, "Hello")])
        // A plain message is saved as before.
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        #expect(String(decoding: try encoder.encode(AIMessage(.user, "Hi")), as: UTF8.self) == #"{"role":"user","text":"Hi"}"#)
    }

    @Test func savedMessagesDropPicturesAndThinking() throws {
        let message = AIMessage(.user, "Look", images: [AIImage(data: Data([1, 2, 3]))])
        #expect(message.forStorage.images == [AIImage(data: Data())])
        #expect(!message.forStorage.images[0].isAvailable)
        let answer = AIMessage(.assistant, "Hm", reasoning: "r", thinking: [AIThinkingBlock(signature: "s")])
        #expect(answer.forStorage.thinking.isEmpty)
        #expect(answer.forStorage.reasoning == "r")
        let round = try JSONDecoder().decode(AIMessage.self, from: JSONEncoder().encode(message.forStorage))
        #expect(round == message.forStorage)
    }

    @Test func trimmingKeepsToolRoundsWhole() {
        let messages = [
            AIMessage(.user, String(repeating: "a", count: 100)),
            AIMessage(.assistant, "x", thinking: [AIThinkingBlock(signature: "s")]),
            AIMessage(.user, "second"),
            AIMessage(.assistant, "", toolCalls: [AIToolCall(id: "1", name: "look", arguments: String(repeating: "b", count: 100))],
                      thinking: [AIThinkingBlock(signature: "t")]),
            AIMessage(.user, "", toolResults: [AIToolResult(callID: "1", content: String(repeating: "c", count: 100))]),
        ]
        let request = AIRequest(system: "", messages: messages)
        let trimmed = request.trimmed(maxCharacters: 250)
        #expect(trimmed.messages.map(\.text) == ["second", "", ""])
        // Something was dropped, so the remaining thinking blocks go too.
        #expect(trimmed.messages.allSatisfy { $0.thinking.isEmpty })
        #expect(request.trimmed(maxCharacters: 10_000).messages == messages)
        #expect(request.withoutThinking.messages.allSatisfy { $0.thinking.isEmpty })
    }
}

@Suite struct AIConnectionCapabilityTests {
    @Test func presetsSayWhatTheirModelsCanDo() throws {
        let glm = AIConnection(provider: try #require(AIProvider.preset(id: "glm")))
        #expect(glm.usesTools)
        #expect(!glm.readsImages)
        #expect(glm.photoModel == "glm-4.6v-flash")
        #expect(glm.pictureConnection?.model == "glm-4.6v-flash")
        #expect(glm.pictureConnection?.readsImages == true)

        let deepSeek = AIConnection(provider: try #require(AIProvider.preset(id: "deepseek")))
        #expect(deepSeek.pictureConnection == nil)

        var claude = AIConnection(provider: try #require(AIProvider.preset(id: "anthropic")))
        #expect(claude.readsImages)
        #expect(claude.pictureConnection == claude)
        // Another model name isn't assumed to read pictures; the switch decides then.
        claude.model = "claude-something-new"
        #expect(!claude.readsImages)
        claude.imagesSwitch = true
        #expect(claude.readsImages)

        var custom = AIConnection(providerID: "custom", name: "Mine", format: .openAI, baseURL: "https://x.example", model: "m")
        #expect(custom.usesTools)
        #expect(custom.photoModel == nil)
        custom.toolsSwitch = false
        custom.photoModelChoice = " vision-1 "
        #expect(!custom.usesTools)
        #expect(custom.pictureConnection?.model == "vision-1")
    }

    @Test func connectionsSavedBy04StillOpen() throws {
        let old = #"{"id":"6B4E0B4A-2B7A-4E2F-9A57-2D4C2B9D8C11","providerID":"glm","name":"GLM","format":"openAI","baseURL":"https://open.bigmodel.cn/api/paas/v4","model":"glm-4.7-flash"}"#
        let connection = try JSONDecoder().decode(AIConnection.self, from: Data(old.utf8))
        #expect(connection.toolsSwitch == nil)
        #expect(connection.usesTools)
    }

    @Test func rejectionsAboutToolsOrPictures() {
        #expect(AIError.rejected(status: 400, message: "This model does not support function calling").rejectsTools)
        #expect(AIError.rejected(status: 400, message: "registry.ollama.ai/library/gemma does not support tools").rejectsTools)
        #expect(AIError.rejected(status: 400, message: "Invalid image_url: the model is not multimodal").rejectsImages)
        #expect(!AIError.rejected(status: 400, message: "Bad request").rejectsTools)
        #expect(!AIError.busy(message: "tool overloaded").rejectsTools)
    }

    @Test func assistantPromptMentionsToolsOnlyWhenUsed() {
        let today = LocalDate(year: 2026, month: 10, day: 15)
        let plain = AIPrompts.assistant(language: .english, today: today, schedule: nil, weakWords: nil)
        #expect(plain.contains("Today is 2026-10-15 (Thursday)."))
        #expect(!plain.contains("tools"))
        let agent = AIPrompts.assistant(language: .english, today: today, now: TimeOfDay(hour: 9, minute: 5), schedule: nil,
                                        weakWords: nil, usesTools: true)
        #expect(agent.contains("Today is 2026-10-15 (Thursday), now 09:05."))
        #expect(agent.contains("tap Confirm"))
    }

    @Test func toolAndPictureChecks() throws {
        let deepSeek = AIConnection(provider: try #require(AIProvider.preset(id: "deepseek")))
        let data = try #require(try deepSeek.chatRequest(AIPrompts.toolTest, key: "k").body)
        let body = try JSONDecoder().decode(JSONValue.self, from: data)
        #expect(body["tools"] != nil)
        let picture = AIPrompts.imageTest(AIImage(data: Data([0xFF, 0xD8])))
        #expect(picture.messages[0].images.count == 1)
    }
}
