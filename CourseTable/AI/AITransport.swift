import Foundation
import AIKit

/// Sends AI requests. The real one uses URLSession; UI tests and screenshots use canned answers.
protocol AITransport: Sendable {
    /// The streamed answer to a chat request. Fails with an `AIError`.
    func stream(_ request: AIHTTPRequest, format: AIWireFormat) -> AsyncThrowingStream<AIStreamEvent, Error>
    /// A plain request's body (the models list). Fails with an `AIError`.
    func data(for request: AIHTTPRequest) async throws -> Data
}

struct URLSessionAITransport: AITransport {
    /// Reasoning models can think for a while before the first word.
    static let timeout: TimeInterval = 120

    func stream(_ request: AIHTTPRequest, format: AIWireFormat) -> AsyncThrowingStream<AIStreamEvent, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let (bytes, response) = try await URLSession.shared.bytes(for: Self.urlRequest(request))
                    let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                    guard (200..<300).contains(status) else {
                        var body = Data()
                        for try await byte in bytes {
                            body.append(byte)
                            if body.count >= 64 * 1024 { break }
                        }
                        throw AIError.from(status: status, body: body)
                    }
                    var parser = SSEParser()
                    let decoder = AIStreamDecoder(format: format)
                    var line: [UInt8] = []
                    // Kept until the first event, in case the provider answered in one piece instead.
                    var whole: [UInt8]? = []
                    var sawEvent = false
                    func handle(_ events: [SSEEvent]) throws {
                        for event in events {
                            sawEvent = true
                            whole = nil
                            for item in try decoder.decode(event) { continuation.yield(item) }
                        }
                    }
                    for try await byte in bytes {
                        line.append(byte)
                        if whole != nil, whole!.count < 1024 * 1024 { whole!.append(byte) }
                        if byte == UInt8(ascii: "\n") {
                            try handle(parser.feed(line))
                            line.removeAll(keepingCapacity: true)
                        }
                    }
                    try handle(parser.feed(line) + parser.finish())
                    if !sawEvent, let whole, let events = try decoder.decodeComplete(Data(whole)) {
                        for item in events { continuation.yield(item) }
                    }
                    continuation.finish()
                } catch let error as AIError {
                    continuation.finish(throwing: error)
                } catch let error as URLError where error.code == .cancelled {
                    continuation.finish()
                } catch is CancellationError {
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: AIError.network(message: error.localizedDescription))
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func data(for request: AIHTTPRequest) async throws -> Data {
        do {
            let (data, response) = try await URLSession.shared.data(for: Self.urlRequest(request))
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            guard (200..<300).contains(status) else { throw AIError.from(status: status, body: data) }
            return data
        } catch let error as AIError {
            throw error
        } catch {
            throw AIError.network(message: error.localizedDescription)
        }
    }

    private static func urlRequest(_ request: AIHTTPRequest) -> URLRequest {
        var urlRequest = URLRequest(url: request.url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: timeout)
        urlRequest.httpMethod = request.method
        urlRequest.httpBody = request.body
        for (name, value) in request.headers { urlRequest.setValue(value, forHTTPHeaderField: name) }
        return urlRequest
    }
}

/// Canned answers for UI tests and screenshots (`-aiDemo`): streamed word by word, never touching the network.
struct DemoAITransport: AITransport {
    func stream(_ request: AIHTTPRequest, format: AIWireFormat) -> AsyncThrowingStream<AIStreamEvent, Error> {
        let body = request.body.map { String(decoding: $0, as: UTF8.self) } ?? ""
        let text = Self.answer(for: body)
        return AsyncThrowingStream { continuation in
            let task = Task {
                var pieces: [String] = []
                var piece = ""
                for character in text {
                    piece.append(character)
                    if character == " " || character == "\n" || piece.count >= 4 {
                        pieces.append(piece)
                        piece = ""
                    }
                }
                if !piece.isEmpty { pieces.append(piece) }
                continuation.yield(.usage(input: 180, output: nil))
                for piece in pieces {
                    try? await Task.sleep(for: .milliseconds(8))
                    if Task.isCancelled { break }
                    continuation.yield(.text(piece))
                }
                continuation.yield(.stop(.complete))
                continuation.yield(.usage(input: nil, output: pieces.count))
                continuation.yield(.end)
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func data(for request: AIHTTPRequest) async throws -> Data {
        Data(#"{"object":"list","data":[{"id":"deepseek-v4-flash"},{"id":"deepseek-v4-pro"}]}"#.utf8)
    }

    /// Unescaped JSON body text tells the requests apart.
    private static func answer(for body: String) -> String {
        let chinese = body.contains("简体中文")
        if body.contains("single word OK") { return "OK" }
        if body.contains("one to-do item") {
            return #"{"title": "交论文", "date": "2026-10-13", "time": null, "afterCourse": "高等数学"}"#
        }
        if body.contains("got this word wrong") {
            return chinese
                ? "**abandon** 指彻底放弃、离开（人、地方或计划），带有“不再回来”的意味；你选的 **adopt** 是“采用、收养”，方向正好相反。\n\n**小技巧：** *a-band-on* —— 把乐队（band）丢下不管 → 放弃。"
                : "**abandon** means to leave someone or something for good; **adopt**, which you picked, means to take something on — almost the opposite.\n\n**Tip:** think *a-BAND-on*: walking out on your band for good."
        }
        return chinese
            ? """
            ## 核心含义
            **abandon** /əˈbændən/ 指*彻底*放弃或离开，强调不再回来，常带有不负责任的意味。

            ## 考研常见用法
            - **abandon a plan / an idea**：放弃计划、想法
            - **abandon oneself to**：沉溺于（某种情绪）
            - 名词 **with abandon**：放纵地、尽情地

            ## 例句
            1. The government had to *abandon* its plan to raise taxes.
               政府不得不放弃加税的计划。
            2. They *abandoned* the car and walked the rest of the way.
               他们弃车步行走完了剩下的路。
            """
            : """
            ## Core meaning
            **abandon** /əˈbændən/ means to leave or give up *completely*, usually for good and often irresponsibly.

            ## In the exam
            - **abandon a plan / an idea** — give it up
            - **abandon oneself to** — let a feeling take over
            - noun **with abandon** — wildly, without restraint

            ## Examples
            1. The government had to *abandon* its plan to raise taxes.
               政府不得不放弃加税的计划。
            2. They *abandoned* the car and walked the rest of the way.
               他们弃车步行走完了剩下的路。
            """
    }
}
