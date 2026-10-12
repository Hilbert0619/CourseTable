import SwiftUI
import UIKit
import AIKit

/// An AI answer as it streams in: the text, a "thinking" or "asking" line before it, what went wrong, and the
/// "AI-generated" label.
struct AIAnswerView: View {
    let run: AIRun
    var onRetry: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if run.isRunning && run.answer.text.isEmpty {
                HStack(spacing: 8) {
                    ProgressView()
                    run.answer.isThinking ? Text("Thinking…") : Text("Asking \(run.model)…")
                }
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("aiWaiting")
            }
            if !run.answer.text.isEmpty {
                MarkdownText(text: run.answer.text)
                    .accessibilityIdentifier("aiAnswer")
            }
            if let error = run.error {
                AIErrorView(error: error, onRetry: onRetry)
            } else if run.answer.stop == .cutOff {
                Label("The answer was cut off by the provider's length limit.", systemImage: "scissors")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            } else if run.answer.stop == .refused {
                Label("The provider declined to answer this.", systemImage: "hand.raised")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }
            if !run.answer.text.isEmpty && !run.isRunning {
                HStack {
                    Label {
                        Text("AI-generated · \(run.model)")
                    } icon: {
                        Image(systemName: "sparkles")
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    Spacer()
                    Button {
                        UIPasteboard.general.string = run.answer.text
                    } label: {
                        Image(systemName: "doc.on.doc")
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(Text("Copy"))
                }
            }
        }
    }
}

struct AIErrorView: View {
    let error: AIError
    var onRetry: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label {
                AIErrorView.text(error)
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
            }
            .accessibilityIdentifier("aiError")
            if let message = error.providerMessage {
                Text(verbatim: message)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(4)
            }
            if let onRetry {
                Button("Try Again", action: onRetry)
                    .buttonStyle(.bordered)
            }
        }
    }

    static func text(_ error: AIError) -> Text {
        switch error {
        case .wrongKey: Text("The provider refused the key. Check it in Settings → AI.")
        case .noCredit: Text("Your account with this provider has no credit or quota left.")
        case .notFound: Text("The provider doesn't know this model or address. Check them in Settings → AI.")
        case .rateLimited: Text("Too many requests right now. Try again in a minute.")
        case .busy: Text("The provider is busy. Try again soon.")
        case .rejected: Text("The provider rejected the request.")
        case .network: Text("Couldn't reach the provider. Check your internet connection.")
        case .badAddress: Text("The address isn't a valid http(s) URL.")
        case .noModel: Text("Choose a model first.")
        case .unreadable: Text("The provider's answer couldn't be read.")
        case .empty: Text("The provider sent an empty answer.")
        }
    }
}

/// Markdown from an AI answer. SwiftUI's `Text` handles only inline Markdown, so the blocks (headings, lists,
/// quotes, code) come from `MarkdownBlock`.
struct MarkdownText: View {
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(MarkdownBlock.parse(text).enumerated()), id: \.offset) { _, block in
                view(for: block)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .textSelection(.enabled)
    }

    @ViewBuilder
    private func view(for block: MarkdownBlock) -> some View {
        switch block {
        case .heading(let level, let text):
            inline(text)
                .font(level <= 2 ? .headline : .subheadline.weight(.semibold))
                .padding(.top, 4)
        case .bullet(let depth, let text):
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(verbatim: depth == 0 ? "•" : "◦")
                inline(text)
            }
            .padding(.leading, CGFloat(depth) * 16)
        case .numbered(let depth, let number, let text):
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(verbatim: "\(number).")
                    .monospacedDigit()
                inline(text)
            }
            .padding(.leading, CGFloat(depth) * 16)
        case .quote(let text):
            inline(text)
                .foregroundStyle(.secondary)
                .padding(.leading, 10)
                .overlay(alignment: .leading) {
                    Rectangle()
                        .fill(.tertiary)
                        .frame(width: 3)
                }
        case .code(let text):
            Text(verbatim: text)
                .font(.callout.monospaced())
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.quaternary, in: RoundedRectangle(cornerRadius: 6))
        case .rule:
            Divider()
        case .paragraph(let text):
            inline(text)
        }
    }

    private func inline(_ text: String) -> Text {
        let options = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        if let attributed = try? AttributedString(markdown: text, options: options) {
            return Text(attributed)
        }
        return Text(verbatim: text)
    }
}

/// Where AI help would be: a link to set it up.
struct AISetupLink: View {
    var body: some View {
        NavigationLink {
            AISettingsView()
        } label: {
            Label("Set Up AI Help…", systemImage: "sparkles")
        }
        .accessibilityIdentifier("aiSetupLink")
    }
}
