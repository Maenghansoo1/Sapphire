import AVFoundation
import PhotosUI
import SwiftUI

/// 07 미션 수행 · 사진 (Figma 1:638)
/// 카메라(플래시, 전·후면 전환) + 앨범 선택. 3:4 로 잘라서 올려요.
struct PhotoMissionView: View {
    let topic: String
    var onSubmit: () -> Void = {}

    @State private var camera = CameraModel()
    @State private var photo: UIImage?
    @State private var albumItem: PhotosPickerItem?

    var body: some View {
        VStack(spacing: 16) {
            MissionHeader(hasDraft: photo != nil) {
                if photo == nil && camera.isAvailable {
                    Button { camera.isFlashOn.toggle() } label: {
                        HIconView(icon: .flash, size: 22, color: camera.isFlashOn ? .ink : .textTertiary)
                            .contentShape(Rectangle().inset(by: -11))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(camera.isFlashOn ? "플래시 끄기" : "플래시 켜기")
                }
            }

            MissionTitle(type: .photo, topic: topic)

            preview

            Spacer(minLength: 0)

            if photo == nil {
                captureControls
            } else {
                // Figma 에 찍은 뒤 화면이 없어서 '다시 찍기 / 올리기' 두 버튼으로 정했어요.
                HStack(spacing: 8) {
                    HButton("다시 찍기", kind: .secondary) { photo = nil }
                    // TODO: 서버 연결 시 JPEG 로 Storage 에 올려요.
                    HButton("올리기", action: onSubmit)
                }
            }
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .padding(.bottom, 40)
        .ignoresSafeArea(.container, edges: .bottom)
        .background(.bg)
        .task { await camera.start() }
        .onDisappear { camera.stop() }
        .onChange(of: albumItem) { _, item in
            Task { await loadAlbumPhoto(item) }
        }
    }

    /// surfaceStrong · 모서리 24 · 3:4
    private var preview: some View {
        let shape = RoundedRectangle(cornerRadius: HRadius.largeCard, style: .continuous)
        return ZStack {
            shape.fill(.surfaceStrong)
            if let photo {
                Image(uiImage: photo)
                    .resizable()
                    .scaledToFill()
            } else if camera.isAvailable {
                CameraPreview(session: camera.session)
            } else {
                VStack(spacing: 10) {
                    HIconView(icon: .camera, size: 34, color: .textTertiary)
                    Text(camera.isDenied ? "설정에서 카메라 권한을 켜 주세요" : "앨범에서 사진을 골라 주세요")
                        .font(.plex(14))
                        .foregroundStyle(.textSecondary)
                }
            }
        }
        .aspectRatio(3 / 4, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .clipShape(shape)
        .layoutPriority(-1)
    }

    /// 좌우 20 · 앨범 52(모서리 14) · 셔터 84 · 전환 52(원)
    private var captureControls: some View {
        HStack {
            PhotosPicker(selection: $albumItem, matching: .images) {
                HIconView(icon: .image, size: 22)
                    .frame(width: 52, height: 52)
                    .overlay(
                        RoundedRectangle(cornerRadius: HRadius.input, style: .continuous)
                            .strokeBorder(.border, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("앨범에서 고르기")

            Spacer()

            Button {
                Task {
                    if let image = await camera.capture() {
                        photo = image.croppedToThreeByFour()
                    }
                }
            } label: {
                ZStack {
                    Circle().strokeBorder(.accent, lineWidth: 4).padding(2)
                    Circle().fill(.accent).frame(width: 64, height: 64)
                }
                .frame(width: 84, height: 84)
            }
            .buttonStyle(.plain)
            .disabled(!camera.isAvailable)
            .opacity(camera.isAvailable ? 1 : 0.4)
            .accessibilityLabel("사진 찍기")

            Spacer()

            Button { camera.flip() } label: {
                HIconView(icon: .flip, size: 22)
                    .frame(width: 52, height: 52)
                    .overlay(Circle().strokeBorder(.border, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(!camera.isAvailable)
            .opacity(camera.isAvailable ? 1 : 0.4)
            .accessibilityLabel("카메라 전환")
        }
        .padding(.horizontal, 20)
    }

    private func loadAlbumPhoto(_ item: PhotosPickerItem?) async {
        guard let item,
              let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else { return }
        photo = image.croppedToThreeByFour()
        albumItem = nil
    }
}

// MARK: - Camera

/// 카메라 세션 — 권한, 전·후면 전환, 플래시, 촬영
@MainActor
@Observable
final class CameraModel: NSObject {
    let session = AVCaptureSession()
    private(set) var isAvailable = false
    private(set) var isDenied = false
    var isFlashOn = false

    private let output = AVCapturePhotoOutput()
    @ObservationIgnored private var position: AVCaptureDevice.Position = .back
    private let queue = DispatchQueue(label: "hawe.camera")
    @ObservationIgnored private var continuation: CheckedContinuation<UIImage?, Never>?

    func start() async {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: break
        case .notDetermined:
            guard await AVCaptureDevice.requestAccess(for: .video) else { isDenied = true; return }
        default:
            isDenied = true
            return
        }
        // 시뮬레이터처럼 카메라가 없으면 앨범만 써요.
        guard configure(position: position) else { return }
        isAvailable = true
        queue.async { [session] in session.startRunning() }
    }

    func stop() {
        queue.async { [session] in session.stopRunning() }
    }

    func flip() {
        let next: AVCaptureDevice.Position = position == .back ? .front : .back
        if configure(position: next) { position = next }
    }

    func capture() async -> UIImage? {
        guard isAvailable, continuation == nil else { return nil }
        let settings = AVCapturePhotoSettings()
        if output.supportedFlashModes.contains(.on) {
            settings.flashMode = isFlashOn ? .on : .off
        }
        return await withCheckedContinuation { continuation in
            self.continuation = continuation
            output.capturePhoto(with: settings, delegate: self)
        }
    }

    @discardableResult
    private func configure(position: AVCaptureDevice.Position) -> Bool {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
              let input = try? AVCaptureDeviceInput(device: device) else { return false }
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .photo
        session.inputs.forEach { session.removeInput($0) }
        guard session.canAddInput(input) else { return false }
        session.addInput(input)
        if !session.outputs.contains(output), session.canAddOutput(output) {
            session.addOutput(output)
        }
        return true
    }
}

extension CameraModel: AVCapturePhotoCaptureDelegate {
    nonisolated func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let image = photo.fileDataRepresentation().flatMap(UIImage.init(data:))
        Task { @MainActor in
            self.continuation?.resume(returning: image)
            self.continuation = nil
        }
    }
}

/// 카메라 미리보기 (화면을 꽉 채우게)
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }
}

extension UIImage {
    /// 가운데를 기준으로 3:4(세로) 로 잘라요.
    func croppedToThreeByFour() -> UIImage {
        let normalized = normalizedOrientation()
        guard let cg = normalized.cgImage else { return self }
        let width = CGFloat(cg.width), height = CGFloat(cg.height)
        let target: CGFloat = 3 / 4
        var rect = CGRect(x: 0, y: 0, width: width, height: height)
        if width / height > target {
            rect.size.width = height * target
            rect.origin.x = (width - rect.width) / 2
        } else {
            rect.size.height = width / target
            rect.origin.y = (height - rect.height) / 2
        }
        guard let cropped = cg.cropping(to: rect.integral) else { return self }
        return UIImage(cgImage: cropped, scale: normalized.scale, orientation: .up)
    }

    private func normalizedOrientation() -> UIImage {
        guard imageOrientation != .up else { return self }
        return UIGraphicsImageRenderer(size: size, format: {
            let format = UIGraphicsImageRendererFormat()
            format.scale = scale
            return format
        }()).image { _ in draw(in: CGRect(origin: .zero, size: size)) }
    }
}

#Preview {
    PhotoMissionView(topic: "지금 창밖으로 보이는 풍경")
}
