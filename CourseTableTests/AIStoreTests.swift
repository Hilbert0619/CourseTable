import Foundation
import Testing
import AIKit
@testable import CourseTable

@MainActor
@Suite struct AIStoreTests {
    func store(defaults: UserDefaults? = nil, cacheURL: URL? = nil) -> AIStore {
        AIStore(defaults: defaults ?? UserDefaults(suiteName: "ai-test-\(UUID().uuidString)")!, cacheURL: cacheURL,
                transport: DemoAITransport(), memoryKeys: [:])
    }

    @Test func connectionsKeysAndTheDefault() throws {
        let defaults = try #require(UserDefaults(suiteName: "ai-test-\(UUID().uuidString)"))
        let ai = store(defaults: defaults)
        #expect(!ai.isSetUp)
        let deepSeek = AIConnection(provider: try #require(AIProvider.preset(id: "deepseek")))
        var qwen = AIConnection(provider: try #require(AIProvider.preset(id: "qwen")))
        ai.save(deepSeek, key: " sk-1 ")
        ai.save(qwen, key: "sk-2")
        #expect(ai.defaultConnection == deepSeek)  // the first one added
        #expect(ai.key(for: deepSeek) == "sk-1")

        qwen.model = "qwen-max"
        ai.save(qwen, key: nil)  // keeps the key
        #expect(ai.key(for: qwen) == "sk-2")
        #expect(ai.connections.map(\.model) == ["deepseek-v4-flash", "qwen-max"])
        ai.makeDefault(qwen)
        #expect(ai.defaultConnection?.id == qwen.id)

        // Connections (not keys) survive a relaunch.
        let reopened = store(defaults: defaults)
        #expect(reopened.connections == ai.connections)
        #expect(reopened.defaultConnection?.id == qwen.id)

        ai.delete(qwen)
        #expect(ai.defaultConnection?.id == deepSeek.id)
        #expect(ai.key(for: qwen) == nil)
        ai.save(deepSeek, key: "")  // an empty key removes it
        #expect(!ai.hasKey(deepSeek))
    }

    @Test func usageAddsUpPerMonth() throws {
        let ai = store()
        let deepSeek = AIConnection(provider: try #require(AIProvider.preset(id: "deepseek")))
        var answer = AIAnswer()
        answer.apply(.usage(input: 100, output: 20))
        let october = Date(timeIntervalSince1970: 1_791_000_000)
        ai.record(answer, for: deepSeek, at: october)
        ai.record(answer, for: deepSeek, at: october)
        #expect(ai.usage(of: deepSeek, in: october) == AIUsageTotal(requests: 2, inputTokens: 200, outputTokens: 40))
        #expect(ai.usage(of: deepSeek, in: october.addingTimeInterval(40 * 86_400)) == AIUsageTotal())
    }

    @Test func savedAnswersPersistAndAreCapped() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("ai-cache-\(UUID().uuidString).json")
        defer { try? FileManager.default.removeItem(at: url) }
        let ai = store(cacheURL: url)
        ai.saveAnswer("Answer", model: "m", for: "word|abandon|explain|english")
        #expect(store(cacheURL: url).cachedAnswer("word|abandon|explain|english")?.text == "Answer")
        for index in 0..<(AIStore.maxCachedAnswers + 5) {
            ai.saveAnswer("\(index)", model: "m", for: "key-\(index)")
        }
        #expect(ai.savedAnswerCount == AIStore.maxCachedAnswers)
        ai.clearSavedAnswers()
        #expect(ai.savedAnswerCount == 0)
        #expect(store(cacheURL: url).savedAnswerCount == 0)
    }

    @Test func aRunStreamsAndRecordsUsage() async throws {
        let ai = store()
        let deepSeek = AIConnection(provider: try #require(AIProvider.preset(id: "deepseek")))
        ai.save(deepSeek, key: "demo")
        let run = AIRun()
        let completed = Box()
        run.start(AIPrompts.connectionTest, store: ai) { completed.answer = $0 }
        for _ in 0..<200 where run.isRunning { try await Task.sleep(for: .milliseconds(20)) }
        #expect(run.answer.text == "OK")
        #expect(run.error == nil)
        #expect(completed.answer?.text == "OK")
        #expect(ai.usage(of: deepSeek).requests == 1)
    }

    @Test func chatsAreKeptNewestFirstAndCapped() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("ai-chats-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("AIAnswers.json")
        let ai = store(cacheURL: url)
        var first = AIConversation()
        first.messages = [AIMessage(.user, "Plan my week for the exam, please, with all the details you can give"), AIMessage(.assistant, "Sure")]
        ai.saveConversation(first)
        ai.saveConversation(AIConversation())  // empty: not kept
        var second = AIConversation()
        second.messages = [AIMessage(.user, "Hi")]
        ai.saveConversation(second)
        #expect(ai.conversations.map(\.id) == [second.id, first.id])
        #expect(ai.conversations[1].title == "Plan my week for the exam, please, with …")
        #expect(store(cacheURL: url).conversations.map(\.id) == [second.id, first.id])

        for _ in 0..<(AIStore.maxConversations + 3) {
            var chat = AIConversation()
            chat.messages = [AIMessage(.user, "x")]
            ai.saveConversation(chat)
        }
        #expect(ai.conversations.count == AIStore.maxConversations)
        ai.deleteConversation(ai.conversations[0].id)
        #expect(ai.conversations.count == AIStore.maxConversations - 1)
        ai.clearSavedAnswers()
        #expect(store(cacheURL: url).conversations.isEmpty)
    }

    @Test func storageClearsAnswersChatsAndUsageSeparately() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("ai-clear-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: folder) }
        let url = folder.appendingPathComponent("AIAnswers.json")
        let defaults = UserDefaults(suiteName: "ai-test-\(UUID().uuidString)")!
        let ai = store(defaults: defaults, cacheURL: url)
        let deepSeek = AIConnection(provider: try #require(AIProvider.preset(id: "deepseek")))
        ai.record(AIAnswer(), for: deepSeek)
        ai.record(AIAnswer(), for: deepSeek)
        ai.saveAnswer("Answer", model: "m", for: "key")
        var chat = AIConversation()
        chat.messages = [AIMessage(.user, "Hi")]
        ai.saveConversation(chat)
        #expect(ai.countedRequests == 2)

        ai.clearConversations()
        #expect(ai.conversations.isEmpty && ai.savedAnswerCount == 1)
        ai.clearAnswers()
        #expect(ai.savedAnswerCount == 0 && ai.countedRequests == 2)
        ai.clearUsage()
        #expect(ai.countedRequests == 0)
        let reopened = store(defaults: defaults, cacheURL: url)
        #expect(reopened.countedRequests == 0 && reopened.savedAnswerCount == 0 && reopened.conversations.isEmpty)
    }

    @MainActor final class Box {
        var answer: AIAnswer?
    }

    @Test func localServersAreAllowedOverHTTP() {
        let ats = Bundle.main.object(forInfoDictionaryKey: "NSAppTransportSecurity") as? [String: Any]
        #expect(ats?["NSAllowsLocalNetworking"] as? Bool == true)
        #expect(ats?["NSAllowsArbitraryLoads"] == nil)
        #expect(Bundle.main.object(forInfoDictionaryKey: "NSLocalNetworkUsageDescription") != nil)
    }
}
