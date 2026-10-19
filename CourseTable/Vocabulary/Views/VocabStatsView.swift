import SwiftUI
import Charts
import CourseKit
import VocabKit

/// Statistics from the stored answers and cards only (docs/vocabulary-spec.md §7). Each number that
/// depends on a threshold says what the threshold is.
struct VocabStatsView: View {
    @Environment(VocabApp.self) private var vocab
    @State private var analysing = false

    var body: some View {
        let model = vocab.model
        let today = model.today
        let days = VocabStats.series(model.events, endingOn: today, count: 30)
        // Axis labels start at their tick, so the last tick is six days before today: one near the right
        // edge would have its date cut off.
        let weekTicks = days.filter { (today - $0.day) % 7 == 6 }.map { model.clock.date(of: $0.day).dateValue }
        let todayStats = days.last ?? DayStats(day: today)
        let streak = VocabStats.streak(model.events, today: today)
        let buckets = VocabStats.buckets(model.cards.values, today: today)
        let book = VocabStats.progress(book: model.bookWords, cards: model.cards, today: today)
        List {
            Section {
                Button {
                    analysing = true
                } label: {
                    Label("Analyse My Progress", systemImage: "sparkles")
                }
                .accessibilityIdentifier("analyseProgress")
            } footer: {
                Text("The Assistant reads these numbers and your hard words, and explains how you're doing.")
            }

            Section("Today") {
                LabeledContent("New words") { Text(verbatim: "\(todayStats.newWords)") }
                LabeledContent("Words reviewed") { Text(verbatim: "\(todayStats.reviewedWords)") }
                LabeledContent("Accuracy") { percent(todayStats.accuracy) }
                LabeledContent("Time") {
                    Text(Duration.milliseconds(todayStats.durationMs), format: .units(allowed: [.hours, .minutes], width: .abbreviated))
                }
                LabeledContent("Study streak") {
                    Text("\(streak.days) days")
                }
            }

            Section {
                Chart(days, id: \.day) { day in
                    BarMark(x: .value("Day", model.clock.date(of: day.day).dateValue, unit: .day),
                            y: .value("Words", day.newWords))
                        .foregroundStyle(by: .value("Kind", String(localized: "New")))
                    BarMark(x: .value("Day", model.clock.date(of: day.day).dateValue, unit: .day),
                            y: .value("Words", day.reviewedWords))
                        .foregroundStyle(by: .value("Kind", String(localized: "Reviewed")))
                }
                .chartXAxis {
                    AxisMarks(values: weekTicks) { _ in
                        AxisGridLine()
                        AxisValueLabel(format: .dateTime.month(.defaultDigits).day())
                    }
                }
                .frame(height: 180)
                .accessibilityIdentifier("wordsChart")
            } header: {
                Text("Words a Day, Last 30 Days")
            }

            let answered = days.filter(\.studied)
            if answered.count >= 2 {
                Section {
                    Chart(answered, id: \.day) { day in
                        LineMark(x: .value("Day", model.clock.date(of: day.day).dateValue, unit: .day),
                                 y: .value("Accuracy", day.accuracy ?? 0))
                        PointMark(x: .value("Day", model.clock.date(of: day.day).dateValue, unit: .day),
                                  y: .value("Accuracy", day.accuracy ?? 0))
                    }
                    .chartYScale(domain: 0...1)
                    .chartYAxis {
                        AxisMarks(values: [0, 0.5, 1]) { value in
                            AxisGridLine()
                            AxisValueLabel {
                                if let fraction = value.as(Double.self) {
                                    Text(verbatim: fraction.formatted(.percent.precision(.fractionLength(0))))
                                }
                            }
                        }
                    }
                    .frame(height: 140)
                } header: {
                    Text("Accuracy")
                } footer: {
                    Text("Answers that weren't “Again”, on days you studied.")
                }
            }

            Section {
                LabeledContent("Needs review") { Text(verbatim: "\(buckets.needsReview)") }
                LabeledContent("Coming up") { Text(verbatim: "\(buckets.approaching)") }
                LabeledContent("Stable") { Text(verbatim: "\(buckets.stable)") }
            } header: {
                Text("Learned Words")
            } footer: {
                Text("Needs review: due today. Coming up: due within \(VocabStats.approachingDays) days. Stable: due later.")
            }

            if let current = model.currentBook {
                Section {
                    LabeledContent("Words") { Text(verbatim: "\(book.total)") }
                    LabeledContent("Learned") { Text(verbatim: "\(book.learned)") }
                    LabeledContent("Mastered") { Text(verbatim: "\(book.mastered)") }
                    LabeledContent("Due today") { Text(verbatim: "\(book.due)") }
                    ProgressView(value: Double(book.learned), total: Double(max(book.total, 1)))
                } header: {
                    Text(verbatim: current.name)
                } footer: {
                    Text("Learned: answered at least once. Mastered: expected to stay above 90% recall for \(Int(VocabStats.masteredStability)) days or more.")
                }
            }
        }
        .paperBackground()
        .navigationTitle("Statistics")
        .sheet(isPresented: $analysing) {
            AssistantView(startMessage: String(localized: "Analyse my vocabulary progress: how am I doing, which words do I keep forgetting, and will I finish the book before the exam?"))
        }
    }

    private func percent(_ value: Double?) -> Text {
        Text(verbatim: value.map { $0.formatted(.percent.precision(.fractionLength(0))) } ?? "–")
    }
}
