import SwiftUI
import VocabKit

/// The word, its phonetic spelling and buttons to hear it (American and British voices).
struct WordHeader: View {
    let entry: WordEntry
    var large = true

    var body: some View {
        VStack(spacing: 6) {
            Text(verbatim: entry.display)
                .font(large ? .system(size: 40, weight: .bold, design: .rounded) : .title.bold())
                .minimumScaleFactor(0.5)
                .lineLimit(1)
                .accessibilityIdentifier("wordHeadline")
            HStack(spacing: 12) {
                if !entry.phonetic.isEmpty {
                    Text(verbatim: "/\(entry.phonetic)/")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
                SpeakButton(text: entry.display, accent: .american, label: "US")
                SpeakButton(text: entry.display, accent: .british, label: "UK")
            }
        }
    }
}

struct SpeakButton: View {
    let text: String
    let accent: Pronunciation.Accent
    let label: String

    var body: some View {
        Button {
            Pronunciation.shared.speak(text, accent: accent)
        } label: {
            Label {
                Text(verbatim: label)
            } icon: {
                Image(systemName: "speaker.wave.2.fill")
            }
            .font(.subheadline)
        }
        .buttonStyle(.bordered)
        .controlSize(.small)
        .accessibilityLabel(accent == .american ? Text("American pronunciation") : Text("British pronunciation"))
    }
}

/// The Chinese meanings, one line per part of speech.
struct MeaningsView: View {
    let entry: WordEntry
    var alignment = HorizontalAlignment.leading

    var body: some View {
        let senses = entry.senses
        VStack(alignment: alignment, spacing: 6) {
            if senses.isEmpty {
                Text("No meaning available.")
                    .foregroundStyle(.secondary)
            }
            ForEach(Array(senses.enumerated()), id: \.offset) { _, sense in
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    if let tag = sense.partOfSpeechTag {
                        Text(verbatim: tag)
                            .font(.callout.italic())
                            .foregroundStyle(.secondary)
                    } else if let label = sense.label {
                        Text(verbatim: "[\(label)]")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                    Text(verbatim: sense.meanings.joined(separator: "，"))
                        .font(.body)
                }
            }
        }
        .accessibilityIdentifier("meanings")
    }
}

/// How long until a word comes back: minutes in learning steps, otherwise days, months or years.
struct IntervalText: View {
    let due: Due?
    let from: Date
    let today: Int

    var body: some View {
        switch due {
        case .moment(let moment):
            let minutes = max(1, Int((moment.timeIntervalSince(from) / 60).rounded()))
            Text("\(minutes) min")
        case .day(let day):
            let days = max(1, day - today)
            if days < 60 {
                Text("\(days) d")
            } else if days < 365 {
                Text("\(days / 30) mo")
            } else {
                let years = (Double(days) / 365).formatted(.number.precision(.fractionLength(1)))
                Text("\(years) y")
            }
        case nil:
            Text(verbatim: "–")
        }
    }
}

extension Rating {
    var title: LocalizedStringKey {
        switch self {
        case .again: "Again"
        case .hard: "Hard"
        case .good: "Good"
        case .easy: "Easy"
        }
    }

    var color: Color {
        switch self {
        case .again: .red
        case .hard: .orange
        case .good: .green
        case .easy: .blue
        }
    }
}
