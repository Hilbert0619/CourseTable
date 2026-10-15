import Foundation
import Observation
import SwiftUI
import PhotosUI
import UIKit
import Vision
import AIKit
import CourseKit

/// Reads a timetable from a picture (docs/ai-agent-spec.md §2): the picture goes to the connection when it (or
/// its photo model) reads pictures; otherwise the phone reads the text and where it sits, and the text goes
/// instead. Either way the answer is CSV for the import preview.
@MainActor @Observable
final class TimetablePhotoReader {
    enum Phase: Equatable {
        case idle
        /// Reading the text on this iPhone.
        case scanning
        /// Waiting for the model.
        case asking
        case noText
        case noTimetable
        case done
    }

    private(set) var phase = Phase.idle
    let run = AIRun()
    /// The last picture, for "Try Again".
    @ObservationIgnored private var lastPhoto: Data?

    var isWorking: Bool { phase == .scanning || phase == .asking }

    func read(_ photo: Data, store: AIStore, timetable: Timetable, onReading: @escaping (TimetablePhoto.Reading) -> Void) {
        lastPhoto = photo
        run.reset()
        guard let connection = store.defaultConnection else { return }
        let key = store.key(for: connection)
        let jpeg = PhotoPrep.jpeg(photo)
        Task {
            if let pictures = connection.pictureConnection, let jpeg {
                phase = .asking
                let request = AIPrompts.timetableFromPhoto(image: AIImage(data: jpeg), ocrText: nil, timetable: timetable,
                                                           language: AIStore.answerLanguage)
                run.start(request, store: store, connection: pictures, key: key) { [weak self] answer in
                    self?.finish(answer, onReading: onReading)
                } onEnd: { [weak self] _ in
                    // A model that turns out not to read pictures: read the text here instead.
                    guard let self, let error = run.error, error.rejectsImages else { return }
                    Task { await self.readText(jpeg, store: store, connection: connection, key: key, timetable: timetable,
                                               onReading: onReading) }
                }
            } else {
                await readText(jpeg ?? photo, store: store, connection: connection, key: key, timetable: timetable,
                               onReading: onReading)
            }
        }
    }

    func retry(store: AIStore, timetable: Timetable, onReading: @escaping (TimetablePhoto.Reading) -> Void) {
        guard let lastPhoto else { return }
        read(lastPhoto, store: store, timetable: timetable, onReading: onReading)
    }

    private func readText(_ photo: Data, store: AIStore, connection: AIConnection, key: String?, timetable: Timetable,
                          onReading: @escaping (TimetablePhoto.Reading) -> Void) async {
        run.reset()
        phase = .scanning
        let boxes = await TextRecognizer.boxes(in: photo)
        guard !boxes.isEmpty else {
            phase = .noText
            return
        }
        phase = .asking
        let request = AIPrompts.timetableFromPhoto(image: nil, ocrText: TimetablePhoto.ocrText(boxes), timetable: timetable,
                                                   language: AIStore.answerLanguage)
        run.start(request, store: store, connection: connection, key: key) { [weak self] answer in
            self?.finish(answer, onReading: onReading)
        }
    }

    private func finish(_ answer: AIAnswer, onReading: (TimetablePhoto.Reading) -> Void) {
        guard let reading = TimetablePhoto.reading(from: answer.text) else {
            phase = .noTimetable
            return
        }
        phase = .done
        onReading(reading)
    }

    func reset() {
        run.reset()
        phase = .idle
    }
}

/// Pictures as they are sent: JPEG, at most 1600 pixels on the long side.
enum PhotoPrep {
    static let maxPixels: CGFloat = 1600

    static func jpeg(_ data: Data) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        return jpeg(image)
    }

    static func jpeg(_ image: UIImage) -> Data? {
        let size = image.size
        let scale = min(1, maxPixels / max(size.width, size.height, 1))
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
        return resized.jpegData(compressionQuality: 0.8)
    }
}

extension TextRecognizer {
    /// The text in a picture with where each piece is (fractions of the picture from its top left), in Chinese
    /// and English.
    static func boxes(in data: Data) async -> [TimetablePhoto.TextBox] {
        await Task.detached(priority: .userInitiated) {
            guard let image = UIImage(data: data), let cgImage = image.cgImage else { return [] }
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            request.recognitionLanguages = ["zh-Hans", "en-US"]
            let handler = VNImageRequestHandler(cgImage: cgImage, orientation: orientation(image.imageOrientation))
            try? handler.perform([request])
            return (request.results ?? []).compactMap { observation -> TimetablePhoto.TextBox? in
                guard let text = observation.topCandidates(1).first?.string else { return nil }
                let box = observation.boundingBox  // origin at the bottom left
                return TimetablePhoto.TextBox(text: text, x: box.minX, y: 1 - box.maxY, width: box.width, height: box.height)
            }
        }.value
    }
}

/// "Photo" and "Camera" buttons that hand over the picture's data. UI tests (`-aiDemoPhoto`) get a sample
/// timetable picture instead.
struct PhotoSourceButtons: View {
    let onPhoto: (Data) -> Void
    @State private var photo: PhotosPickerItem?
    @State private var showingCamera = false

    var body: some View {
        HStack(spacing: 12) {
            PhotosPicker(selection: $photo, matching: .images) {
                Label("Photo", systemImage: "photo")
            }
            if UIImagePickerController.isSourceTypeAvailable(.camera) {
                Button {
                    showingCamera = true
                } label: {
                    Label("Camera", systemImage: "camera")
                }
            }
            if UITestLaunch.wantsAIDemoPhoto {
                Button("Sample") {
                    if let data = SampleTimetablePicture.data() { onPhoto(data) }
                }
                .accessibilityIdentifier("samplePhoto")
            }
        }
        .buttonStyle(.bordered)
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                photo = nil
                if let data { onPhoto(data) }
            }
        }
        .fullScreenCover(isPresented: $showingCamera) {
            CameraPicker { image in
                showingCamera = false
                if let data = image?.jpegData(compressionQuality: 0.9) { onPhoto(data) }
            }
            .ignoresSafeArea()
        }
    }
}

/// A drawn timetable for UI tests: weekday columns, period rows, three courses.
enum SampleTimetablePicture {
    static func data() -> Data? {
        let size = CGSize(width: 1200, height: 900)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(origin: .zero, size: size))
            let font = UIFont.systemFont(ofSize: 30, weight: .medium)
            let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.black]
            func draw(_ text: String, _ x: CGFloat, _ y: CGFloat) {
                (text as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: attributes)
            }
            let days = ["星期一", "星期二", "星期三", "星期四", "星期五"]
            for (index, day) in days.enumerated() { draw(day, 220 + CGFloat(index) * 190, 40) }
            let rows = ["1-2节", "3-4节", "5-6节"]
            for (index, row) in rows.enumerated() { draw(row, 40, 160 + CGFloat(index) * 240) }
            UIColor.gray.setStroke()
            for index in 0...5 { context.stroke(CGRect(x: 200 + CGFloat(index) * 190, y: 20, width: 0.5, height: 840)) }
            for index in 0...3 { context.stroke(CGRect(x: 20, y: 110 + CGFloat(index) * 240, width: 1150, height: 0.5)) }
            draw("线性代数", 215, 150)
            draw("C305", 215, 195)
            draw("线性代数", 975, 150)
            draw("C305", 975, 195)
            draw("中国近代史", 405, 390)
            draw("D102", 405, 435)
            draw("体育", 785, 630)
            draw("操场", 785, 675)
        }
        return image.jpegData(compressionQuality: 0.9)
    }
}
