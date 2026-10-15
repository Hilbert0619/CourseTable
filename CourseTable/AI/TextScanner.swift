import SwiftUI
import PhotosUI
import UIKit
import Vision

/// "Photo" and "Camera" buttons that read English text from a picture on the device (Vision) and hand it over for
/// the user to check before anything is sent.
struct TextScanButtons: View {
    let onText: (String) -> Void
    @State private var photo: PhotosPickerItem?
    @State private var showingCamera = false
    @State private var reading = false
    @State private var foundNothing = false

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
            if reading {
                ProgressView()
            } else if foundNothing {
                Text("No text found")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.bordered)
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task {
                let data = try? await item.loadTransferable(type: Data.self)
                photo = nil
                if let data { await read(data) }
            }
        }
        .fullScreenCover(isPresented: $showingCamera) {
            CameraPicker { image in
                showingCamera = false
                if let data = image?.jpegData(compressionQuality: 0.9) {
                    Task { await read(data) }
                }
            }
            .ignoresSafeArea()
        }
    }

    private func read(_ data: Data) async {
        reading = true
        foundNothing = false
        let text = await TextRecognizer.text(in: data)
        reading = false
        if text.isEmpty {
            foundNothing = true
        } else {
            onText(text)
        }
    }
}

/// On-device text recognition (no network).
enum TextRecognizer {
    static func text(in data: Data) async -> String {
        await Task.detached(priority: .userInitiated) {
            guard let image = UIImage(data: data), let cgImage = image.cgImage else { return "" }
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            request.recognitionLanguages = ["en-US"]
            let handler = VNImageRequestHandler(cgImage: cgImage, orientation: orientation(image.imageOrientation))
            try? handler.perform([request])
            let lines = (request.results ?? []).compactMap { $0.topCandidates(1).first?.string }
            return lines.joined(separator: "\n")
        }.value
    }

    static func orientation(_ orientation: UIImage.Orientation) -> CGImagePropertyOrientation {
        switch orientation {
        case .up: .up
        case .upMirrored: .upMirrored
        case .down: .down
        case .downMirrored: .downMirrored
        case .left: .left
        case .leftMirrored: .leftMirrored
        case .right: .right
        case .rightMirrored: .rightMirrored
        @unknown default: .up
        }
    }
}

/// The system camera, returning one photo (or `nil` when cancelled).
struct CameraPicker: UIViewControllerRepresentable {
    let onFinish: (UIImage?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onFinish: (UIImage?) -> Void

        init(onFinish: @escaping (UIImage?) -> Void) {
            self.onFinish = onFinish
        }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            onFinish(info[.originalImage] as? UIImage)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onFinish(nil)
        }
    }
}
