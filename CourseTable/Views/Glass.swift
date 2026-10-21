import SwiftUI

/// The app's look (Settings → Appearance, docs/design-0.7.md §5): glass on a wallpaper, the default since 0.7.1,
/// or the classic warm paper of 0.7.0.
enum AppStyle: String, CaseIterable, Identifiable, Sendable {
    case glass
    case classic

    var id: Self { self }
}

enum AppAppearance {
    static let styleKey = "appearance.style"
    static let defaultStyle = AppStyle.glass
}

extension View {
    /// Behind a main tab's List or Form: the wallpaper in the glass look, the paper in the classic one.
    @ViewBuilder
    func tabBackground(_ style: AppStyle) -> some View {
        switch style {
        case .glass:
            scrollContentBackground(.hidden)
                .background { AppBackdrop(style: .glass) }
        case .classic:
            paperBackground()
        }
    }

    /// The rows of a list section on the wallpaper: frosted glass. The classic look keeps the system's rows.
    @ViewBuilder
    func glassRows(_ style: AppStyle) -> some View {
        if style == .glass {
            listRowBackground(FrostedFill())
        } else {
            self
        }
    }

    /// A card that draws its own background in glass: Liquid Glass on iOS 26 (the wallpaper blurred, its colours
    /// lifted, a bright rim and refraction at the edges), frosted glass before; `tint` mixes a colour in.
    func glassCard(tint: Color? = nil, cornerRadius: CGFloat = Theme.cardRadius) -> some View {
        modifier(GlassSurface(shape: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous), tint: tint))
    }

    /// A capsule of glass behind a chip or a small control; `interactive` ones respond to touch on iOS 26.
    func glassCapsule(tint: Color? = nil, interactive: Bool = false) -> some View {
        modifier(GlassSurface(shape: Capsule(), tint: tint, interactive: interactive))
    }

    /// A card's own background in the look: glass with `tint` mixed in, or `tint` (else the row colour) on the paper.
    @ViewBuilder
    func surface(_ style: AppStyle, tint: Color? = nil) -> some View {
        switch style {
        case .glass:
            glassCard(tint: tint?.opacity(0.75))
        case .classic:
            background(tint ?? Theme.card)
                .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
        }
    }

    /// Buttons in glass in the glass look on iOS 26 (`prominent`: the screen's main action, in the accent colour),
    /// bordered otherwise. (A custom button style in a List row would make the whole row its button.)
    @ViewBuilder
    func glassButtonStyle(_ style: AppStyle, prominent: Bool = false) -> some View {
        if #available(iOS 26, *) {
            switch (style, prominent) {
            case (.glass, true): buttonStyle(.glassProminent)
            case (.glass, false): buttonStyle(.glass)
            case (.classic, true): buttonStyle(.borderedProminent)
            case (.classic, false): buttonStyle(.bordered)
            }
        } else if prominent {
            buttonStyle(.borderedProminent)
        } else {
            buttonStyle(.bordered)
        }
    }
}

/// The glass recipe: blur and stronger colour from what's behind (Liquid Glass, or a material before iOS 26), a 10 %
/// white layer for the frosted look, a 1-pixel bright edge for the glass's thickness, and refraction at the edges
/// (Liquid Glass bends what's behind them; before iOS 26 a soft glow inside the rim stands in for it).
private struct GlassSurface<S: InsettableShape>: ViewModifier {
    let shape: S
    var tint: Color?
    var interactive = false
    @Environment(\.displayScale) private var displayScale
    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder
    func body(content: Content) -> some View {
        let layered = content
            .background(Color.white.opacity(0.10), in: shape)
            .overlay { shape.strokeBorder(edge, lineWidth: 1 / displayScale) }
        if #available(iOS 26, *) {
            layered.glassEffect(glass, in: shape)
        } else {
            layered
                .background {
                    ZStack {
                        shape.fill(.ultraThinMaterial)
                        if let tint {
                            shape.fill(tint)
                        }
                    }
                }
                .overlay {
                    shape.strokeBorder(Color.white.opacity(colorScheme == .dark ? 0.12 : 0.28), lineWidth: 5)
                        .blur(radius: 4)
                        .clipShape(shape)
                        .allowsHitTesting(false)
                }
        }
    }

    @available(iOS 26, *)
    private var glass: Glass {
        var glass = Glass.regular
        if let tint {
            glass = glass.tint(tint)
        }
        return interactive ? glass.interactive() : glass
    }

    /// Bright at the top left, where the light falls, fainter round the rest.
    private var edge: LinearGradient {
        let strength = colorScheme == .dark ? 0.55 : 1.0
        return LinearGradient(
            colors: [.white.opacity(0.8 * strength), .white.opacity(0.15 * strength), .white.opacity(0.4 * strength)],
            startPoint: .topLeading, endPoint: .bottomTrailing
        )
    }
}

/// A list row's background on the wallpaper: the wallpaper behind it blurred, with the 10 % white layer.
struct FrostedFill: View {
    var body: some View {
        Rectangle()
            .fill(.ultraThinMaterial)
            .overlay(Color.white.opacity(0.10))
    }
}
