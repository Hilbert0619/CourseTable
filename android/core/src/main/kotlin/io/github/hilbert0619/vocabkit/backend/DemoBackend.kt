package io.github.hilbert0619.vocabkit.backend

import io.github.hilbert0619.coursekit.LocalDate
import io.github.hilbert0619.coursekit.Weekday
import io.github.hilbert0619.vocabkit.AuthLink
import io.github.hilbert0619.vocabkit.Card
import io.github.hilbert0619.vocabkit.Rating
import io.github.hilbert0619.vocabkit.ReviewEvent
import io.github.hilbert0619.vocabkit.ReviewMode
import io.github.hilbert0619.vocabkit.Scheduler
import io.github.hilbert0619.vocabkit.SessionMode
import io.github.hilbert0619.vocabkit.StudyClock
import io.github.hilbert0619.vocabkit.VocabStats
import io.github.hilbert0619.vocabkit.WordEntry
import io.github.hilbert0619.vocabkit.CardState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An in-memory backend for tests and screenshots (iOS `DemoBackend`): a small 考研 book, optionally a signed-in learner
 * with two weeks of answers. Nothing leaves the device.
 *
 * @param signedIn start with the demo learner signed in.
 * @param history give the learner two weeks of answers ending yesterday, a few favourites and settings.
 */
class DemoBackend(
    signedIn: Boolean,
    history: Boolean,
    reviewMode: SessionMode = SessionMode.MIXED,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) : VocabBackend {
    private val mutex = Mutex()
    private var account: Account? = null
    private val confirmedEmails = mutableSetOf(DEMO_EMAIL)
    private val bookRows: MutableList<RemoteBook>
    private val entryRows: MutableList<RemoteEntry>
    private val reviewRows = mutableListOf<RemoteReview>()
    private val favoriteRows = mutableListOf<RemoteFavorite>()
    private var settingsRow: RemoteSettings? = null
    private var clock: Instant = now

    init {
        val kaoyan = SystemBook.KAOYAN
        val book = RemoteBook(
            id = kaoyan.id, ownerId = null, name = kaoyan.name, description = kaoyan.description, source = kaoyan.source,
            licence = kaoyan.licence, wordCount = VocabDemo.words.size, updatedAt = now.minus(Duration.ofDays(30)), deletedAt = null,
        )
        bookRows = mutableListOf(book)
        entryRows = VocabDemo.words.mapIndexed { index, entry ->
            RemoteEntry(
                id = UUID.nameUUIDFromBytes("demo-entry-${entry.headword}".toByteArray()), bookId = book.id, position = index + 1,
                headword = entry.headword, wordId = index + 1L, updatedAt = book.updatedAt, word = RemoteWord.of(index + 1L, entry),
            )
        }.toMutableList()
        if (signedIn) account = DEMO_ACCOUNT
        if (history) {
            reviewRows += VocabDemo.history(now, zone).map { RemoteReview.of(it).copy(receivedAt = it.reviewedAt) }
            favoriteRows += listOf("ambiguous", "anticipate", "arbitrary").map {
                RemoteFavorite(it, isFavorite = true, updatedAt = now.minus(Duration.ofDays(1)))
            }
            settingsRow = RemoteSettings(
                bookId = book.id, dailyNewWords = 5, reviewMode = reviewMode.rawValue, examDate = VocabDemo.examDate(now, zone),
                dayStartMinutes = 240, desiredRetention = 0.9, updatedAt = now.minus(Duration.ofDays(1)),
            )
        }
    }

    // Auth

    override suspend fun restoreAccount(): Account? = mutex.withLock { account }

    override suspend fun signUp(email: String, password: String): SignUpOutcome {
        if ("@" !in email) throw VocabAuthError.InvalidEmail
        if (password.length < 8) throw VocabAuthError.WeakPassword
        return SignUpOutcome.ConfirmationSent
    }

    override suspend fun signIn(email: String, password: String): Account = mutex.withLock {
        if (email.lowercase() !in confirmedEmails) throw VocabAuthError.EmailNotConfirmed
        if (password.length < 8) throw VocabAuthError.InvalidCredentials
        DEMO_ACCOUNT.copy(email = email.lowercase()).also { account = it }
    }

    override suspend fun signOut(): Unit = mutex.withLock { account = null }

    override suspend fun sendPasswordReset(email: String) {
        if ("@" !in email) throw VocabAuthError.InvalidEmail
    }

    override suspend fun openLink(url: String): Account = mutex.withLock {
        val link = AuthLink.of(url)
        if (link is AuthLink.Failed) throw VocabAuthError.LinkExpired
        if (!link.signsIn) throw VocabAuthError.Other("Not a sign-in link")
        DEMO_ACCOUNT.also { account = it }
    }

    override suspend fun updatePassword(password: String): Unit = mutex.withLock {
        if (account == null) throw VocabAuthError.Other("Not signed in")
        if (password.length < 8) throw VocabAuthError.WeakPassword
    }

    override suspend fun deleteAccount(): Unit = mutex.withLock {
        requireAccount()
        account = null
        reviewRows.clear()
        favoriteRows.clear()
        settingsRow = null
        bookRows.removeAll { it.ownerId != null }
        entryRows.removeAll { entry -> bookRows.none { it.id == entry.bookId } }
    }

    // Data

    override suspend fun books(): List<RemoteBook> = mutex.withLock {
        val user = requireAccount()
        bookRows.filter { it.ownerId == null || it.ownerId == user.id }
    }

    override suspend fun entries(book: UUID, from: Int, to: Int): List<RemoteEntry> = mutex.withLock {
        requireAccount()
        val rows = entryRows.filter { it.bookId == book }.sortedBy { it.position }
        if (from >= rows.size) emptyList() else rows.subList(from, minOf(to, rows.size - 1) + 1).toList()
    }

    override suspend fun saveBooks(books: List<RemoteBook>): Unit = mutex.withLock {
        val user = requireAccount()
        for (book in books) {
            if (book.ownerId != user.id) throw VocabAuthError.Other("Not your book")
            val index = bookRows.indexOfFirst { it.id == book.id }
            if (index < 0) bookRows += book else if (book.updatedAt >= bookRows[index].updatedAt) bookRows[index] = book
        }
    }

    override suspend fun saveEntries(entries: List<RemoteEntry>): Unit = mutex.withLock {
        val user = requireAccount()
        for (entry in entries) {
            if (bookRows.none { it.id == entry.bookId && it.ownerId == user.id }) throw VocabAuthError.Other("Not your book")
            val stored = entry.copy(word = null)
            val index = entryRows.indexOfFirst { it.id == entry.id }
            if (index < 0) entryRows += stored else if (entry.updatedAt >= entryRows[index].updatedAt) entryRows[index] = stored
        }
    }

    override suspend fun uploadReviews(reviews: List<RemoteReview>): Unit = mutex.withLock {
        requireAccount()
        val known = reviewRows.map { it.id }.toMutableSet()
        for (review in reviews) {
            if (!known.add(review.id)) continue
            reviewRows += review.copy(receivedAt = nextReceived())
        }
    }

    override suspend fun reviews(receivedAfter: Instant?, from: Int, to: Int): List<RemoteReview> = mutex.withLock {
        requireAccount()
        val rows = reviewRows
            .filter { row -> receivedAfter == null || (row.receivedAt ?: Instant.MIN) > receivedAfter }
            .sortedBy { it.receivedAt ?: Instant.MIN }
        if (from >= rows.size) emptyList() else rows.subList(from, minOf(to, rows.size - 1) + 1).toList()
    }

    override suspend fun favorites(): List<RemoteFavorite> = mutex.withLock {
        requireAccount()
        favoriteRows.toList()
    }

    override suspend fun saveFavorites(favorites: List<RemoteFavorite>): Unit = mutex.withLock {
        requireAccount()
        for (favorite in favorites) {
            val index = favoriteRows.indexOfFirst { it.headword == favorite.headword }
            if (index < 0) favoriteRows += favorite else if (favorite.updatedAt >= favoriteRows[index].updatedAt) favoriteRows[index] = favorite
        }
    }

    override suspend fun settings(): RemoteSettings? = mutex.withLock {
        requireAccount()
        settingsRow
    }

    override suspend fun saveSettings(settings: RemoteSettings): Unit = mutex.withLock {
        requireAccount()
        val current = settingsRow
        if (current == null || settings.updatedAt >= current.updatedAt) settingsRow = settings
    }

    // For tests

    /** Answers stored on the "server". */
    suspend fun storedReviews(): List<RemoteReview> = mutex.withLock { reviewRows.toList() }
    suspend fun storedFavorites(): List<RemoteFavorite> = mutex.withLock { favoriteRows.toList() }
    suspend fun storedSettings(): RemoteSettings? = mutex.withLock { settingsRow }
    suspend fun storedBooks(): List<RemoteBook> = mutex.withLock { bookRows.toList() }
    suspend fun storedEntries(): List<RemoteEntry> = mutex.withLock { entryRows.toList() }

    /** An answer uploaded by another device. */
    suspend fun addReviewFromAnotherDevice(event: ReviewEvent): Unit = mutex.withLock {
        reviewRows += RemoteReview.of(event).copy(receivedAt = nextReceived())
    }

    private fun nextReceived(): Instant {
        clock = maxOf(clock.plusMillis(1), Instant.now())
        return clock
    }

    private fun requireAccount(): Account = account ?: throw VocabAuthError.Other("Not signed in")

    companion object {
        const val DEMO_EMAIL = "learner@example.com"
        val DEMO_ACCOUNT = Account(UUID.fromString("0D3E0000-0000-4000-8000-000000000001"), DEMO_EMAIL)
    }
}

/** Demo content for [DemoBackend] (iOS `VocabDemo`): 30 words of the 考研 list and a learner's first two weeks. */
object VocabDemo {
    val words: List<WordEntry> = listOf(
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
    )

    private fun entry(word: String, phonetic: String, translation: String, definition: String, rank: Int, exchange: String) = WordEntry(
        headword = word, phonetic = phonetic, translation = translation, definition = definition, collins = 3, oxford = rank < 4000,
        tags = listOf("cet4", "cet6", "ky"), bnc = rank, frq = rank, exchange = exchange,
    )

    /**
     * A learner's first two weeks, ending the study day before [now]: two new words a day, reviews on the days FSRS
     * schedules them, and a few forgotten ones; the first two words twice (hard words).
     */
    fun history(now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<ReviewEvent> {
        val clock = StudyClock(zone)
        val scheduler = Scheduler()
        val today = clock.day(now)
        val cards = mutableMapOf<String, Card>()
        val events = mutableListOf<ReviewEvent>()
        var sequence = 0
        fun answer(word: String, rating: Rating, day: Int, minute: Int, mode: ReviewMode) {
            val time = clock.start(day).plusSeconds((16 * 60L + minute) * 60)
            sequence += 1
            val id = UUID.fromString("DE000000-0000-4000-8000-%012d".format(sequence))
            events += ReviewEvent(id, word, rating, mode, time, day, durationMs = 3000 + (sequence * 1733) % 7000)
            cards[word] = scheduler.review(cards[word] ?: Card.of(word), rating, time, day)
        }
        for (offset in 13 downTo 1) {
            val day = today - offset
            var minute = 0
            // Reviews that are due, a few of them forgotten.
            for ((word, card) in cards.toSortedMap().entries.toList()) {
                if (card.state != CardState.REVIEW || !card.isDue(day, clock.end(day))) continue
                val forgotten = (word.length + offset) % 6 == 0 ||
                    word in listOf("arbitrary", "ambiguous") && offset % 3 == 0 ||
                    word in listOf("abandon", "ability") && card.lapses < VocabStats.HARD_WORD_LAPSES
                answer(word, if (forgotten) Rating.AGAIN else Rating.GOOD, day, minute, ReviewMode.RECOGNITION)
                if (forgotten) answer(word, Rating.GOOD, day, minute + 10, ReviewMode.CHOICE)
                minute += 1
            }
            // Two new words, learned with two steps.
            for (word in words.drop(cards.size).take(2)) {
                answer(word.headword, Rating.GOOD, day, minute, ReviewMode.CHOICE)
                answer(word.headword, Rating.GOOD, day, minute + 10, ReviewMode.RECOGNITION)
                minute += 1
            }
        }
        return events
    }

    /** An exam date for the demo: the third Saturday of December after [now], as `yyyy-MM-dd`. */
    fun examDate(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = LocalDate.from(now, zone)
        val year = if (today.month == 12 && today.day > 20) today.year + 1 else today.year
        var day = LocalDate(year, 12, 1)
        var saturdays = 0
        while (true) {
            if (day.weekday == Weekday.SATURDAY) {
                saturdays++
                if (saturdays == 3) break
            }
            day = day.adding(1)
        }
        return day.toString()
    }
}
