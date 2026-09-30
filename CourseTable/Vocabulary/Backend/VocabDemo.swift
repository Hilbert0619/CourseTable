import Foundation
import CourseKit
import VocabKit

/// Demo content for `DemoBackend`: 30 words of the 考研 list and a learner's first two weeks.
enum VocabDemo {
    static let words: [WordEntry] = [
        entry("abandon", "əˈbændən", "vt. 放弃, 抛弃, 遗弃\nn. 放任, 放纵", "v. give up completely; leave behind", 2057, "p:abandoned/d:abandoned/i:abandoning/3:abandons"),
        entry("ability", "əˈbɪləti", "n. 能力, 才能", "n. the quality of being able to do something", 375, "s:abilities"),
        entry("academic", "ˌækəˈdemɪk", "a. 学术的, 学院的\nn. 大学教师", "a. associated with study at a university", 1283, "s:academics"),
        entry("access", "ˈækses", "n. 接近, 进入, 使用权\nvt. 访问, 存取", "n. the right or opportunity to use something", 890, "p:accessed/d:accessed/i:accessing/3:accesses"),
        entry("accommodate", "əˈkɒmədeɪt", "vt. 容纳, 使适应, 提供住宿", "v. provide space or lodging for; adapt", 5642, "p:accommodated/d:accommodated/i:accommodating/3:accommodates"),
        entry("accompany", "əˈkʌmpəni", "vt. 陪伴, 伴随, 为…伴奏", "v. go somewhere with someone", 3105, "p:accompanied/d:accompanied/i:accompanying/3:accompanies"),
        entry("accumulate", "əˈkjuːmjəleɪt", "vt. 积累, 堆积\nvi. 增加", "v. gather together or acquire an increasing number of", 7020, "p:accumulated/d:accumulated/i:accumulating/3:accumulates"),
        entry("accurate", "ˈækjərət", "a. 准确的, 精确的", "a. correct in all details; exact", 3410, ""),
        entry("acknowledge", "əkˈnɒlɪdʒ", "vt. 承认, 致谢, 告知收到", "v. accept or admit the existence or truth of", 3570, "p:acknowledged/d:acknowledged/i:acknowledging/3:acknowledges"),
        entry("acquire", "əˈkwaɪə", "vt. 获得, 学到, 取得", "v. buy or obtain for oneself; learn", 2622, "p:acquired/d:acquired/i:acquiring/3:acquires"),
        entry("adapt", "əˈdæpt", "vt. 使适应, 改编\nvi. 适应", "v. make suitable for a new use or purpose", 4870, "p:adapted/d:adapted/i:adapting/3:adapts"),
        entry("adequate", "ˈædɪkwət", "a. 足够的, 适当的, 胜任的", "a. satisfactory or acceptable in quality or quantity", 3280, ""),
        entry("adjust", "əˈdʒʌst", "vt. 调整, 调节\nvi. 适应", "v. alter or move slightly to achieve the right result", 3890, "p:adjusted/d:adjusted/i:adjusting/3:adjusts"),
        entry("administration", "ədˌmɪnɪˈstreɪʃn", "n. 管理, 行政, 政府", "n. the process of running an organization", 1150, "s:administrations"),
        entry("adopt", "əˈdɒpt", "vt. 采用, 收养, 正式通过", "v. take up or start to use; legally take a child as one's own", 2360, "p:adopted/d:adopted/i:adopting/3:adopts"),
        entry("advocate", "ˈædvəkeɪt", "vt. 提倡, 拥护\nn. 提倡者, 辩护律师", "v. publicly recommend or support", 6780, "p:advocated/d:advocated/i:advocating/3:advocates"),
        entry("affect", "əˈfekt", "vt. 影响, 感动, 假装", "v. have an effect on; make a difference to", 1040, "p:affected/d:affected/i:affecting/3:affects"),
        entry("aggressive", "əˈɡresɪv", "a. 侵略性的, 好斗的, 有进取心的", "a. ready or likely to attack or confront", 5230, ""),
        entry("allocate", "ˈæləkeɪt", "vt. 分配, 拨出", "v. distribute for a particular purpose", 6320, "p:allocated/d:allocated/i:allocating/3:allocates"),
        entry("alter", "ˈɔːltə", "vt. 改变, 修改\nvi. 变化", "v. change in character or composition", 3060, "p:altered/d:altered/i:altering/3:alters"),
        entry("ambiguous", "æmˈbɪɡjuəs", "a. 模棱两可的, 含糊不清的", "a. open to more than one interpretation", 9120, ""),
        entry("analyze", "ˈænəlaɪz", "vt. 分析, 分解", "v. examine methodically and in detail", 4410, "p:analyzed/d:analyzed/i:analyzing/3:analyzes"),
        entry("anticipate", "ænˈtɪsɪpeɪt", "vt. 预期, 期望, 预料", "v. regard as probable; expect or predict", 4650, "p:anticipated/d:anticipated/i:anticipating/3:anticipates"),
        entry("apparent", "əˈpærənt", "a. 显然的, 表面上的", "a. clearly visible or understood; obvious", 2140, ""),
        entry("appreciate", "əˈpriːʃieɪt", "vt. 欣赏, 感激, 领会\nvi. 增值", "v. recognize the full worth of; be grateful for", 3180, "p:appreciated/d:appreciated/i:appreciating/3:appreciates"),
        entry("approach", "əˈprəʊtʃ", "n. 方法, 途径, 接近\nvt. 接近, 着手处理", "n. a way of dealing with something", 610, "p:approached/d:approached/i:approaching/3:approaches"),
        entry("appropriate", "əˈprəʊpriət", "a. 适当的, 恰当的\nvt. 拨出, 挪用", "a. suitable or proper in the circumstances", 1570, ""),
        entry("arbitrary", "ˈɑːbɪtrəri", "a. 任意的, 武断的, 专制的", "a. based on random choice or personal whim", 8840, ""),
        entry("assess", "əˈses", "vt. 评估, 估价, 评定", "v. evaluate or estimate the nature or quality of", 3020, "p:assessed/d:assessed/i:assessing/3:assesses"),
        entry("assume", "əˈsjuːm", "vt. 假定, 承担, 采取", "v. suppose to be the case without proof", 1310, "p:assumed/d:assumed/i:assuming/3:assumes"),
    ]

    private static func entry(_ word: String, _ phonetic: String, _ translation: String, _ definition: String,
                              _ rank: Int, _ exchange: String) -> WordEntry {
        WordEntry(headword: word, phonetic: phonetic, translation: translation, definition: definition,
                  collins: 3, oxford: rank < 4000, tags: ["cet4", "cet6", "ky"], bnc: rank, frq: rank, exchange: exchange)
    }

    /// A learner's first two weeks, ending the study day before `now`: two new words a day, reviews on
    /// the days FSRS schedules them, and a few forgotten ones (so there are hard words).
    static func history(endingBefore now: Date, calendar: Calendar = .current) -> [ReviewEvent] {
        let clock = StudyClock(calendar: calendar)
        let scheduler = Scheduler()
        let today = clock.day(of: now)
        var cards: [String: Card] = [:]
        var events: [ReviewEvent] = []
        var sequence = 0
        func answer(_ word: String, _ rating: Rating, day: Int, minute: Int, mode: ReviewMode) {
            let time = clock.start(of: day).addingTimeInterval(TimeInterval((16 * 60 + minute) * 60))
            sequence += 1
            let id = UUID(uuidString: String(format: "DE000000-0000-4000-8000-%012ld", sequence))!
            let event = ReviewEvent(id: id, headword: word, rating: rating, mode: mode, reviewedAt: time,
                                    studyDay: day, durationMs: 3000 + (sequence * 1733) % 7000)
            cards[word] = scheduler.review(cards[word] ?? Card(headword: word), rating: rating, at: time, day: day)
            events.append(event)
        }
        for offset in (1...13).reversed() {
            let day = today - offset
            var minute = 0
            // Reviews that are due, a few of them forgotten.
            for (word, card) in cards.sorted(by: { $0.key < $1.key }) where card.state == .review && card.isDue(on: day, dayEnd: clock.end(of: day)) {
                let forgotten = (word.count + offset) % 6 == 0 || ["arbitrary", "ambiguous"].contains(word) && offset % 3 == 0
                answer(word, forgotten ? .again : .good, day: day, minute: minute, mode: .recognition)
                if forgotten { answer(word, .good, day: day, minute: minute + 10, mode: .choice) }
                minute += 1
            }
            // Two new words, learned with two steps.
            let learned = cards.count
            for word in words.dropFirst(learned).prefix(2) {
                answer(word.headword, .good, day: day, minute: minute, mode: .choice)
                answer(word.headword, .good, day: day, minute: minute + 10, mode: .recognition)
                minute += 1
            }
        }
        return events
    }

    /// An exam date for the demo: the third Saturday of December after `now`, as `yyyy-MM-dd`.
    static func examDate(from now: Date, calendar: Calendar = .current) -> String {
        let today = LocalDate(now, calendar: calendar)
        let year = today.month == 12 && today.day > 20 ? today.year + 1 : today.year
        var day = LocalDate(year: year, month: 12, day: 1)
        var saturdays = 0
        while true {
            if day.weekday == .saturday {
                saturdays += 1
                if saturdays == 3 { break }
            }
            day = day.adding(days: 1)
        }
        return day.description
    }
}
