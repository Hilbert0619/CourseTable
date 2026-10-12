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
