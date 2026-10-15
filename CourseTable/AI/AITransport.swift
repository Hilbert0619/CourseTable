import Foundation
import AIKit
import CourseKit

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
/// With tools, a few scripted conversations call them the way a model would: a reminder (looks at tomorrow's
/// classes, then proposes a to-do after the first one), vocabulary progress (two lookups, then an analysis), a
/// focus session, and courses from a timetable photo.
struct DemoAITransport: AITransport {
    struct Call: Sendable {
        var name: String
        var arguments: String
    }

    enum Reply: Sendable {
        case text(String)
        case calls([Call])
    }

    func stream(_ request: AIHTTPRequest, format: AIWireFormat) -> AsyncThrowingStream<AIStreamEvent, Error> {
        let reply = Self.reply(to: request.body)
        return AsyncThrowingStream { continuation in
            let task = Task {
                continuation.yield(.usage(input: 180, output: nil))
                switch reply {
                case .calls(let calls):
                    for (index, call) in calls.enumerated() {
                        try? await Task.sleep(for: .milliseconds(80))
                        if Task.isCancelled { break }
                        continuation.yield(.toolCall(index: index, id: "call_demo_\(UUID().uuidString.prefix(8))", name: call.name))
                        continuation.yield(.toolArguments(index: index, call.arguments))
                    }
                    continuation.yield(.stop(.toolUse))
                    continuation.yield(.usage(input: nil, output: 30))
                case .text(let text):
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
                    for piece in pieces {
                        try? await Task.sleep(for: .milliseconds(8))
                        if Task.isCancelled { break }
                        continuation.yield(.text(piece))
                    }
                    continuation.yield(.stop(.complete))
                    continuation.yield(.usage(input: nil, output: pieces.count))
                }
                continuation.yield(.end)
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func data(for request: AIHTTPRequest) async throws -> Data {
        Data(#"{"object":"list","data":[{"id":"deepseek-v4-flash"},{"id":"deepseek-v4-pro"}]}"#.utf8)
    }

    /// A message's text: its content, or the text parts of its content (a picture as "[image]").
    private static func text(of message: [String: Any]) -> String {
        if let text = message["content"] as? String { return text }
        let parts = message["content"] as? [[String: Any]] ?? []
        return parts.map { ($0["text"] as? String) ?? ($0["type"] as? String == "image_url" ? "[image]" : "") }
            .joined(separator: "\n")
    }

    /// The system prompt and messages of a request body, which tell the requests apart.
    private static func promptText(_ json: [String: Any]) -> String {
        var parts = [json["system"] as? String ?? ""]
        for message in json["messages"] as? [[String: Any]] ?? [] { parts.append(text(of: message)) }
        return parts.joined(separator: "\n")
    }

    private static func arguments(_ values: [String: Any]) -> String {
        let data = (try? JSONSerialization.data(withJSONObject: values, options: [.sortedKeys])) ?? Data("{}".utf8)
        return String(decoding: data, as: UTF8.self)
    }

    private static func reply(to body: Data?) -> Reply {
        guard let body, let json = try? JSONSerialization.jsonObject(with: body) as? [String: Any] else { return .text("") }
        let all = promptText(json)
        let messages = json["messages"] as? [[String: Any]] ?? []
        guard json["tools"] != nil, let userIndex = messages.lastIndex(where: { $0["role"] as? String == "user" }) else {
            return .text(answer(for: all))
        }
        let user = text(of: messages[userIndex])
        let lower = user.lowercased()
        // The tools called since the user's message, and their latest results by tool name.
        var names: [String: String] = [:]
        var called: [String] = []
        var results: [String: String] = [:]
        for message in messages[(userIndex + 1)...] {
            for call in message["tool_calls"] as? [[String: Any]] ?? [] {
                guard let id = call["id"] as? String, let name = (call["function"] as? [String: Any])?["name"] as? String
                else { continue }
                names[id] = name
                called.append(name)
            }
            if message["role"] as? String == "tool", let id = message["tool_call_id"] as? String, let name = names[id] {
                results[name] = message["content"] as? String ?? ""
            }
        }
        let system = messages.first { $0["role"] as? String == "system" }.map(text(of:)) ?? ""
        let chinese = system.contains("The user's language: Simplified Chinese")
        let today = system.range(of: #"Today is \d{4}-\d{2}-\d{2}"#, options: .regularExpression)
            .flatMap { LocalDate(String(system[$0].suffix(10))) } ?? LocalDate(.now)
        let tomorrow = today.adding(days: 1).description

        if user.contains("Call the ping tool") {
            return called.isEmpty ? .calls([Call(name: "ping", arguments: "{}")]) : .text("OK")
        }
        if user.contains("[image]") || user.contains("<photo_text>") {
            guard called.isEmpty else {
                return .text((results["add_courses"] ?? "").hasPrefix("The user imported")
                    ? (chinese ? "好了，课程已经加进课程表。" : "Done — the courses are in your timetable now.")
                    : (chinese ? "好的，没有添加课程。" : "OK, I didn't add any courses."))
            }
            return .calls([Call(name: "add_courses", arguments: arguments(["csv": photoCSV]))])
        }
        if lower.contains("remind") || user.contains("提醒") {
            let title = chinese ? "交论文" : "Hand in the paper"
            if called.isEmpty {
                return .calls([Call(name: "get_schedule", arguments: arguments(["from": tomorrow, "to": tomorrow]))])
            }
            if called.last == "get_schedule" {
                if let course = firstCourse(in: results["get_schedule"] ?? "") {
                    return .calls([Call(name: "add_todo", arguments: arguments(["title": title, "date": tomorrow, "afterCourse": course]))])
                }
                return .calls([Call(name: "add_todo", arguments: arguments(["title": title, "date": tomorrow, "time": "17:30"]))])
            }
            return .text((results["add_todo"] ?? "").hasPrefix("Added")
                ? (chinese ? "好了，明天会提醒你交论文。" : "Done — I'll remind you to hand in the paper tomorrow.")
                : (chinese ? "好的，没有添加。" : "OK, I didn't add it."))
        }
        if lower.contains("focus") || user.contains("专注") {
            if called.isEmpty {
                return .calls([Call(name: "start_focus", arguments: arguments(["minutes": 25, "goal": chinese ? "高数复习" : "Calculus review"]))])
            }
            return .text((results["start_focus"] ?? "").hasPrefix("The focus session started")
                ? (chinese ? "开始了，加油！该休息时会提醒你。" : "Started — good luck! I'll let you know when it's time for a break.")
                : (chinese ? "好的，先不开始。" : "OK, not now."))
        }
        if lower.contains("word") || lower.contains("progress") || user.contains("单词") || user.contains("进度") {
            if called.isEmpty {
                return .calls([Call(name: "get_vocab_overview", arguments: "{}"),
                               Call(name: "get_hard_words", arguments: arguments(["limit": 5]))])
            }
            return .text(chinese ? progressZH : progressEN)
        }
        return .text(answer(for: all))
    }

    /// The course of the first line in `get_schedule`'s answer, e.g. `Fri 2026-10-16 08:00–09:40 Data Structures (C305, Zhao) · week 7`.
    private static func firstCourse(in schedule: String) -> String? {
        guard let pattern = try? Regex(#"\d{2}:\d{2}–\d{2}:\d{2} (.+?)(?: \(| · )"#) else { return nil }
        for line in schedule.split(separator: "\n") {
            if let match = String(line).firstMatch(of: pattern), let name = match.output[1].substring {
                return String(name)
            }
        }
        return nil
    }

    /// What the sample timetable picture (`SampleTimetablePicture`) holds.
    static let photoCSV = """
        课程,教师,教室,星期,节次,周次
        线性代数,,C305,1,1-2,1-16
        线性代数,,C305,5,1-2,1-16
        中国近代史,,D102,2,3-4,1-16
        体育,,操场,4,5-6,1-16
        """

    private static let progressEN = """
        Here's how your words are going:

        - **Streak:** you've studied on most days lately — keep it going.
        - **Accuracy:** most answers are right; the misses cluster on a few hard words.
        - **Hard words:** review them once more tonight and use each one in a sentence.
        - **Exam pace:** at your current number of new words a day you'll see the whole book before the exam.

        **Next step:** finish today's reviews first, then the new words.
        """
    private static let progressZH = """
        你的单词学习情况：

        - **连续学习：** 最近大部分日子都学了，继续保持。
        - **正确率：** 大多数回答正确，错误集中在几个难词上。
        - **难词：** 今晚再复习一遍，每个词造一个句子。
        - **考试进度：** 按现在每天的新词数量，考试前能学完整本词书。

        **下一步：** 先做完今天的复习，再学新词。
        """

    private static func answer(for body: String) -> String {
        let chinese = body.contains("简体中文")
        if body.contains("single word OK") { return "OK" }
        if body.contains("You read a university timetable") {
            return "```csv\n\(photoCSV)\n```\n```periods\n1,08:00,08:45\n2,08:50,09:35\n3,09:55,10:40\n4,10:45,11:30\n5,14:00,14:45\n6,14:50,15:35\n7,15:55,16:40\n8,16:45,17:30\n```"
        }
        if body.contains("What colour fills this picture") { return "Red" }
        if body.contains("one to-do item") { return todoAnswer(body) }
        if body.contains("long-sentence") { return chinese ? sentenceZH : sentenceEN }
        if body.contains("exam marker") { return chinese ? essayZH : essayEN }
        if body.contains("study assistant in CourseTable") {
            return body.contains("The user's language: Simplified Chinese") ? chatZH : chatEN
        }
        if body.contains("I spelled it like this") {
            return chinese
                ? "你漏掉了中间的字母。按音节拆开记：**a·ban·don** —— 每个音节都有一个元音。\n\n**小技巧：** 先慢慢读出三个音节，再一个音节一个音节地拼写。"
                : "You left out letters in the middle. Split it into syllables: **a·ban·don** — each one has its own vowel.\n\n**Tip:** say the three syllables slowly, then spell one syllable at a time."
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

    /// After the first class from tomorrow on in the prompt's class list (lines like
    /// `Tue 2026-10-13 08:00–09:40 Calculus (A101)`), or tomorrow at 17:30 without one.
    private static func todoAnswer(_ prompt: String) -> String {
        let today = prompt.range(of: #"Today is \d{4}-\d{2}-\d{2}"#, options: .regularExpression)
            .flatMap { LocalDate(String(prompt[$0].suffix(10))) } ?? LocalDate(.now)
        let tomorrow = today.adding(days: 1)
        let list = prompt.components(separatedBy: "Classes in the next 7 days:\n").last ?? ""
        for line in list.components(separatedBy: "\n").prefix(while: { !$0.isEmpty }) {
            let parts = line.split(separator: " ").map(String.init)
            guard parts.count >= 4, let day = LocalDate(parts[1]), day >= tomorrow else { continue }
            var name = parts[3...].joined(separator: " ")
            if name.hasSuffix(")"), let open = name.range(of: " (", options: .backwards) {
                name = String(name[..<open.lowerBound])
            }
            return #"{"title": "交论文", "date": "\#(day)", "time": null, "afterCourse": "\#(name)"}"#
        }
        return #"{"title": "交论文", "date": "\#(tomorrow)", "time": "17:30", "afterCourse": null}"#
    }

    private static let chatEN = """
        Here's a simple plan for this week:

        1. **Mornings (30 min):** new words and reviews in the Words tab.
        2. **After classes:** one long sentence from a past paper each day.
        3. **Weekend:** one essay, then check it with *Writing Feedback*.

        Short, regular sessions beat long cramming.
        """
    private static let chatZH = """
        这周可以这样安排：

        1. **每天早上（30 分钟）：** 在“单词”里学新词、做复习。
        2. **下课后：** 每天分析一个真题长难句。
        3. **周末：** 写一篇作文，再用“作文批改”检查。

        短而规律的学习，比临时突击更有效。
        """

    private static let sentenceEN = """
        ## Main clause
        **The study** (subject) **suggests** (verb) **that…** (object clause)

        ## Clauses and modifiers
        - *that people who sleep less are more likely to abandon their goals* — object clause of **suggests**
        - *who sleep less* — relative clause modifying **people**

        ## Translation
        这项研究表明，睡眠较少的人更有可能放弃自己的目标。

        ## Key words
        - **abandon** — 放弃
        - **be likely to** — 很可能
        """
    private static let sentenceZH = """
        ## 主干
        **The study**（主语）**suggests**（谓语）**that…**（宾语从句）

        ## 从句与修饰成分
        - *that people who sleep less are more likely to abandon their goals*：**suggests** 的宾语从句
        - *who sleep less*：定语从句，修饰 **people**

        ## 译文
        这项研究表明，睡眠较少的人更有可能放弃自己的目标。

        ## 重点词汇
        - **abandon**：放弃
        - **be likely to**：很可能
        """
    private static let essayEN = """
        ## Score
        Band 3 (estimated **12/20**): clear ideas, some errors.

        ## Errors
        1. "people *is* busy" → "people **are** busy"
        2. "more and more *peoples*" → "more and more **people**"

        ## A better version
        As is vividly depicted in the picture, …
        """
    private static let essayZH = """
        ## 评分
        第三档（估计 **12/20**）：观点清楚，有一些语言错误。

        ## 错误
        1. "people *is* busy" → "people **are** busy"（主谓一致）
        2. "more and more *peoples*" → "more and more **people**"

        ## 改进版本
        As is vividly depicted in the picture, …
        """
}
