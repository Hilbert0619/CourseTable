import Foundation

/// How well a word was remembered, as FSRS grades a review.
public enum Rating: Int, Codable, Sendable, CaseIterable, Comparable {
    case again = 1
    case hard = 2
    case good = 3
    case easy = 4

    /// Hard, Good and Easy all mean the word was recalled; only Again is a miss.
    public var isRecalled: Bool { self != .again }

    public static func < (lhs: Rating, rhs: Rating) -> Bool { lhs.rawValue < rhs.rawValue }
}

/// FSRS settings. The defaults are the published FSRS-6 weights with 90% target retention and the
/// Anki-style learning steps (1 min, 10 min) and relearning step (10 min).
public struct FSRSParameters: Hashable, Sendable {
    /// The 21 FSRS-6 model weights.
    public var weights: [Double]
    /// Probability of recall the schedule aims for when a word comes up again, `0.7...0.97`.
    public var desiredRetention: Double
    /// Longest interval between reviews, in days.
    public var maximumInterval: Int
    /// Minutes between the first answers to a new word before it graduates, each under a day.
    public var learningSteps: [Int]
    /// Minutes before a forgotten word comes back, each under a day.
    public var relearningSteps: [Int]
    /// Whether same-day reviews change stability (FSRS "short-term" scheduling).
    public var enableShortTerm: Bool

    /// FSRS-6.0 default weights (open-spaced-repetition, matching ts-fsrs and swift-fsrs).
    public static let defaultWeights: [Double] = [
        0.212, 1.2931, 2.3065, 8.2956, 6.4133,
        0.8334, 3.0194, 0.001, 1.8722, 0.1666,
        0.796, 1.4835, 0.0614, 0.2629, 1.6483,
        0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
        0.1542,
    ]

    public init(
        weights: [Double] = defaultWeights,
        desiredRetention: Double = 0.9,
        maximumInterval: Int = 36_500,
        learningSteps: [Int] = [1, 10],
        relearningSteps: [Int] = [10],
        enableShortTerm: Bool = true
    ) {
        precondition(weights.count == 21, "FSRS-6 needs 21 weights")
        self.weights = weights
        self.desiredRetention = min(max(desiredRetention, 0.7), 0.97)
        self.maximumInterval = max(1, maximumInterval)
        self.learningSteps = learningSteps.filter { (1..<1440).contains($0) }
        self.relearningSteps = relearningSteps.filter { (1..<1440).contains($0) }
        self.enableShortTerm = enableShortTerm
    }
}

/// What FSRS knows about one word's memory: stability (days until recall drops to 90%) and
/// difficulty (1 easiest to 10 hardest).
public struct MemoryState: Hashable, Sendable, Codable {
    public var stability: Double
    public var difficulty: Double

    public init(stability: Double, difficulty: Double) {
        self.stability = stability
        self.difficulty = difficulty
    }
}

/// The FSRS-6 memory model: the formulas from
/// https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm, rounded to 8 decimals at the
/// same points as the reference implementations (ts-fsrs, swift-fsrs) so results match theirs.
public struct FSRS: Sendable {
    public let parameters: FSRSParameters
    /// Exponent of the forgetting curve, `-w[20]`.
    let decay: Double
    /// Chosen so that recall is 90% after `stability` days.
    let factor: Double
    /// Scales stability to an interval that hits the desired retention (1 at 90%).
    let intervalModifier: Double

    static let minStability = 0.001
    static let maxStability = 36_500.0

    public init(parameters: FSRSParameters = FSRSParameters()) {
        self.parameters = parameters
        let decay = -parameters.weights[20]
        let factor = rounded8(exp(log(0.9) / decay) - 1)
        self.decay = decay
        self.factor = factor
        self.intervalModifier = rounded8((pow(parameters.desiredRetention, 1 / decay) - 1) / factor)
    }

    private var w: [Double] { parameters.weights }

    /// Memory after the very first answer to a word.
    public func initialState(_ rating: Rating) -> MemoryState {
        MemoryState(stability: initialStability(rating), difficulty: initialDifficulty(rating))
    }

    /// Probability of recalling a word `elapsedDays` after its last review.
    public func retrievability(elapsedDays: Double, stability: Double) -> Double {
        rounded8(pow(1 + factor * elapsedDays / stability, decay))
    }

    /// Memory after answering `rating`, `elapsedDays` whole study days after the previous answer.
    /// A `nil` state means the word has never been answered.
    public func nextState(_ state: MemoryState?, elapsedDays: Double, rating: Rating) -> MemoryState {
        guard let state, state.stability > 0 else { return initialState(rating) }
        let s = max(state.stability, Self.minStability)
        let d = min(max(state.difficulty, 1), 10)
        let r = retrievability(elapsedDays: elapsedDays, stability: s)

        var newStability = recallStability(d: d, s: s, r: r, rating: rating)
        if rating == .again {
            let (w17, w18) = parameters.enableShortTerm ? (w[17], w[18]) : (0, 0)
            let ceiling = forgetStability(d: d, s: s, r: r)
            newStability = min(max(s / exp(w17 * w18), Self.minStability), ceiling)
        }
        if elapsedDays == 0 && parameters.enableShortTerm {
            newStability = shortTermStability(s: s, rating: rating)
        }
        return MemoryState(stability: newStability, difficulty: nextDifficulty(d: d, rating: rating))
    }

    /// Days until the next review for a word with this stability.
    public func interval(stability: Double) -> Int {
        let days = (stability * intervalModifier).rounded()
        return Int(min(max(1, days), Double(parameters.maximumInterval)))
    }

    func initialStability(_ rating: Rating) -> Double {
        max(w[rating.rawValue - 1], 0.1)
    }

    func initialDifficulty(_ rating: Rating) -> Double {
        clampDifficulty(rawInitialDifficulty(rating))
    }

    func rawInitialDifficulty(_ rating: Rating) -> Double {
        rounded8(w[4] - exp(Double(rating.rawValue - 1) * w[5]) + 1)
    }

    func nextDifficulty(d: Double, rating: Rating) -> Double {
        let delta = -w[6] * Double(rating.rawValue - 3)
        let damped = d + rounded8(delta * (10 - d) / 9)
        let reverted = rounded8(w[7] * rawInitialDifficulty(.easy) + (1 - w[7]) * damped)
        return clampDifficulty(reverted)
    }

    func recallStability(d: Double, s: Double, r: Double, rating: Rating) -> Double {
        let hardPenalty = rating == .hard ? w[15] : 1
        let easyBonus = rating == .easy ? w[16] : 1
        let growth = exp(w[8]) * (11 - d) * pow(s, -w[9]) * (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus
        return clampStability(s * (1 + growth))
    }

    func forgetStability(d: Double, s: Double, r: Double) -> Double {
        clampStability(w[11] * pow(d, -w[12]) * (pow(s + 1, w[13]) - 1) * exp((1 - r) * w[14]))
    }

    func shortTermStability(s: Double, rating: Rating) -> Double {
        var increase = pow(s, -w[19]) * exp(w[17] * (Double(rating.rawValue) - 3 + w[18]))
        if rating >= .hard { increase = max(increase, 1) }
        return clampStability(s * increase)
    }

    private func clampDifficulty(_ d: Double) -> Double {
        min(max(rounded8(d), 1), 10)
    }

    private func clampStability(_ s: Double) -> Double {
        rounded8(min(max(s, Self.minStability), Self.maxStability))
    }
}

/// Rounds to 8 decimal places, as the reference implementations do between steps.
func rounded8(_ value: Double) -> Double {
    (value * 100_000_000).rounded() / 100_000_000
}
