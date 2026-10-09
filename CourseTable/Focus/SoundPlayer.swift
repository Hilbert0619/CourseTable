import AVFoundation
import Foundation
import OSLog

/// A background sound for focusing: a recorded loop bundled with the app (`sound-<id>.m4a`, see
/// content/sounds/SOURCES.md).
enum FocusSound: String, CaseIterable, Identifiable, Sendable {
    case none, rain, roofRain, campfire, airplane, ocean, stream, night, train, cafe

    var id: String { rawValue }

    var name: String {
        switch self {
        case .none: String(localized: "No Sound")
        case .rain: String(localized: "Rain")
        case .roofRain: String(localized: "Rain on a Roof")
        case .campfire: String(localized: "Campfire")
        case .airplane: String(localized: "Airplane Cabin")
        case .ocean: String(localized: "Ocean")
        case .stream: String(localized: "Stream")
        case .night: String(localized: "Night")
        case .train: String(localized: "Train")
        case .cafe: String(localized: "Café")
        }
    }

    var symbol: String {
        switch self {
        case .none: "speaker.slash"
        case .rain: "cloud.rain"
        case .roofRain: "house"
        case .campfire: "flame"
        case .airplane: "airplane"
        case .ocean: "water.waves"
        case .stream: "drop"
        case .night: "moon.stars"
        case .train: "tram"
        case .cafe: "cup.and.saucer"
        }
    }

    /// The loop's file, if it's in the app.
    var url: URL? {
        self == .none ? nil : Bundle.main.url(forResource: "sound-\(rawValue)", withExtension: "m4a")
    }

    /// "No sound" and the sounds whose files are in the app.
    static var available: [FocusSound] {
        allCases.filter { $0 == .none || $0.url != nil }
    }
}

/// Plays one sound in a seamless loop: the file is decoded once and scheduled with `.loops`, so there's no
/// gap at the seam (looping a compressed file directly leaves one). It mixes with other audio and keeps
/// playing in the background (Info.plist `UIBackgroundModes` `audio`), which also keeps the focus timer
/// running.
@MainActor
final class SoundPlayer {
    private static let logger = Logger(subsystem: "io.github.hilbert0619.CourseTable", category: "Sound")

    private let engine = AVAudioEngine()
    private let node = AVAudioPlayerNode()
    private var loaded: (sound: FocusSound, buffer: AVAudioPCMBuffer)?
    private(set) var playing: FocusSound = .none
    private var observers: [NSObjectProtocol] = []

    var volume: Float = 0.6 {
        didSet { node.volume = volume }
    }

    init() {
        engine.attach(node)
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            let type = (note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap { AVAudioSession.InterruptionType(rawValue: $0) }
            let ended = type == .ended
            guard ended else { return }
            MainActor.assumeIsolated { self?.restart() }
        })
    }

    /// Plays `sound` (stops for `.none`).
    func play(_ sound: FocusSound) {
        guard sound != playing || !node.isPlaying else { return }
        stop()
        guard let url = sound.url else { return }
        do {
            let buffer = try buffer(for: sound, url: url)
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try session.setActive(true)
            engine.disconnectNodeOutput(node)
            engine.connect(node, to: engine.mainMixerNode, format: buffer.format)
            if !engine.isRunning {
                try engine.start()
            }
            node.volume = volume
            node.scheduleBuffer(buffer, at: nil, options: [.loops])
            node.play()
            playing = sound
        } catch {
            Self.logger.error("Playing \(sound.rawValue) failed: \(error.localizedDescription)")
        }
    }

    func stop() {
        node.stop()
        engine.stop()
        playing = .none
        try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
    }

    /// After a phone call or another interruption.
    private func restart() {
        let sound = playing
        playing = .none
        play(sound)
    }

    private func buffer(for sound: FocusSound, url: URL) throws -> AVAudioPCMBuffer {
        if let loaded, loaded.sound == sound { return loaded.buffer }
        let file = try AVAudioFile(forReading: url)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: AVAudioFrameCount(file.length)) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        try file.read(into: buffer)
        loaded = (sound, buffer)
        return buffer
    }
}
