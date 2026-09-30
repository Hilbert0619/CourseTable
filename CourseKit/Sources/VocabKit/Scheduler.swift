import Foundation

/// Turns answers into card states with FSRS-6 and the learning steps, following the reference
/// scheduler (ts-fsrs `BasicScheduler`, FSRS-6) except that elapsed time is counted in study days,
/// so a review at 23:00 and one at 01:00 fall on the same day. Fuzz is off: the same answers always
/// give the same schedule, which keeps replaying events deterministic.
public struct Scheduler: Sendable {
    public let fsrs: FSRS

    public init(parameters: FSRSParameters = FSRSParameters()) {
        fsrs = FSRS(parameters: parameters)
    }

    public var parameters: FSRSParameters { fsrs.parameters }

    /// The card after answering `rating` at `time`, which falls on study day `day`.
    public func review(_ card: Card, rating: Rating, at time: Date, day: Int) -> Card {
        var next = card
        let elapsed = card.state == .new ? 0 : max(0, day - (card.lastReviewDay ?? day))
        next.reps += 1
        next.lastReview = time
        next.lastReviewDay = day
        if next.firstReviewDay == nil { next.firstReviewDay = day }

        switch card.state {
        case .new:
            next.memory = fsrs.initialState(rating)
            applySteps(to: &next, rating: rating, from: .new, step: card.step, target: .learning, time: time, day: day)
        case .learning, .relearning:
            next.memory = fsrs.nextState(card.memory, elapsedDays: Double(elapsed), rating: rating)
            applySteps(to: &next, rating: rating, from: card.state, step: card.step, target: card.state, time: time, day: day)
        case .review:
            if rating == .again {
                next.memory = fsrs.nextState(card.memory, elapsedDays: Double(elapsed), rating: .again)
                applySteps(to: &next, rating: .again, from: .review, step: card.step, target: .relearning, time: time, day: day)
                next.lapses += 1
            } else {
                // Hard < Good < Easy by at least a day each, as in the reference scheduler.
                let states = [Rating.hard, .good, .easy].map {
                    fsrs.nextState(card.memory, elapsedDays: Double(elapsed), rating: $0)
                }
                var hard = fsrs.interval(stability: states[0].stability)
                var good = fsrs.interval(stability: states[1].stability)
                hard = min(hard, good)
                good = max(good, hard + 1)
                let easy = max(fsrs.interval(stability: states[2].stability), good + 1)
                let (memory, interval) = switch rating {
                case .hard: (states[0], hard)
                case .good: (states[1], good)
                default: (states[2], easy)
                }
                next.memory = memory
                graduate(&next, interval: interval, day: day)
            }
        }
        return next
    }

    /// Rebuilds one word's card from its events, oldest first.
    public func replay(_ events: [ReviewEvent], headword: String) -> Card {
        Self.ordered(events).reduce(Card(headword: headword)) { card, event in
            review(card, rating: event.rating, at: event.reviewedAt, day: event.studyDay)
        }
    }

    /// Rebuilds the cards of every word that appears in `events`.
    public func replay(_ events: [ReviewEvent]) -> [String: Card] {
        Dictionary(grouping: events, by: \.headword).reduce(into: [:]) { cards, group in
            cards[group.key] = replay(group.value, headword: group.key)
        }
    }

    /// Chance of recalling the word on study day `day`; `nil` for words never answered.
    public func retrievability(of card: Card, on day: Int) -> Double? {
        guard card.state != .new, let memory = card.memory, let last = card.lastReviewDay else { return nil }
        return fsrs.retrievability(elapsedDays: Double(max(0, day - last)), stability: memory.stability)
    }

    /// Replay order: by time, then by id so events recorded in the same instant still sort the same
    /// way on every device.
    public static func ordered(_ events: [ReviewEvent]) -> [ReviewEvent] {
        events.sorted { ($0.reviewedAt, $0.id.uuidString) < ($1.reviewedAt, $1.id.uuidString) }
    }

    // MARK: - Learning steps

    private func applySteps(to card: inout Card, rating: Rating, from state: CardState, step: Int,
                            target: CardState, time: Date, day: Int) {
        if let (minutes, nextStep) = stepOutcome(rating: rating, state: state, step: step), minutes > 0 {
            card.state = target
            card.step = nextStep
            card.scheduledDays = 0
            card.due = .moment(time.addingTimeInterval(TimeInterval(minutes * 60)))
        } else {
            graduate(&card, interval: fsrs.interval(stability: card.memory?.stability ?? 0), day: day)
        }
    }

    private func graduate(_ card: inout Card, interval: Int, day: Int) {
        card.state = .review
        card.step = 0
        card.scheduledDays = interval
        card.due = .day(day + interval)
    }

    /// Minutes until the word comes back and the step it will be on, or `nil` when it graduates.
    /// Mirrors ts-fsrs's `BasicLearningStepsStrategy`: Again restarts the steps, Hard repeats the
    /// current step with a longer wait, Good moves to the next step, Easy always graduates.
    private func stepOutcome(rating: Rating, state: CardState, step: Int) -> (minutes: Int, step: Int)? {
        let steps = state == .relearning || state == .review ? parameters.relearningSteps : parameters.learningSteps
        guard !steps.isEmpty, step < steps.count else { return nil }
        if state == .review {
            return rating == .again ? (steps[max(0, step)], 0) : nil
        }
        switch rating {
        case .again:
            return (steps[0], 0)
        case .hard:
            let wait = steps.count == 1 ? Double(steps[0]) * 1.5 : Double(steps[0] + steps[1]) / 2
            return (Int(wait.rounded(.toNearestOrAwayFromZero)), step)
        case .good:
            return step + 1 < steps.count ? (steps[step + 1], step + 1) : nil
        case .easy:
            return nil
        }
    }
}
