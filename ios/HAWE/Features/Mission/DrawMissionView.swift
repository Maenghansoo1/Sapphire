import PencilKit
import SwiftUI

/// 06 미션 수행 · 그림 (Figma 1:594)
/// PencilKit. 펜/지우개, 굵기 3단계, 7색, 되돌리기/다시 실행. 올리기 → PNG
/// 30초 스케치(`spec.seconds`)는 첫 획부터 시간을 재고, 시간이 끝나면 더 그릴 수 없어요.
/// "AI에게 보여주기"는 기기 안 모델로 그림을 맞혀 봐요 (재미용 — 올리기·도장과 무관, 그림은 밖으로 나가지 않아요, A8).
struct DrawMissionView: View {
    let topic: String
    var template = MissionTemplates.drawSketch
    var spec = MissionSpec()
    var onSubmit: (SubmissionContent) async -> Bool = { _ in true }

    @State private var canvas = DrawingCanvasModel()
    @State private var isSubmitting = false
    @State private var toast: String?
    @State private var aiGuess = AiGuessRunner()

    var body: some View {
        VStack(spacing: 16) {
            MissionHeader(hasDraft: !canvas.isEmpty) {
                HStack(spacing: 16) {
                    if let seconds = spec.seconds {
                        TimelineView(.periodic(from: .now, by: 0.2)) { context in
                            Text("0:" + String(format: "%02d", canvas.remainingSeconds(total: seconds, now: context.date)))
                                .font(.caption13)
                                .monospacedDigit()
                                .foregroundStyle(.textSecondary)
                        }
                    }
                    historyButton(.undo, label: "되돌리기", enabled: canvas.canUndo) { canvas.undo() }
                    historyButton(.redo, label: "다시 실행", enabled: canvas.canRedo) { canvas.redo() }
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                MissionTitle(type: .draw, topic: topic)
                if let from = spec.drawFrom, let to = spec.drawTo {
                    Text("‘\(from)’를 ‘\(to)’로")
                        .font(.caption13)
                        .foregroundStyle(.textSecondary)
                }
            }

            // 테두리 line · 모서리 20 · 높이 400 (작은 화면에서는 줄어들어요)
            PencilCanvas(model: canvas)
                .frame(minHeight: 220, maxHeight: 400)
                .clipShape(RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
                        .strokeBorder(.line, lineWidth: 1)
                )
                .layoutPriority(-1)

            tools
            colors

            Spacer(minLength: 0)

            HStack(spacing: 10) {
                if aiGuess.isAvailable {
                    AiShowButton(enabled: !canvas.isEmpty, action: showAI)
                }
                HButton(isSubmitting ? "올리는 중..." : "올리기", action: submit)
                    .disabled(canvas.isEmpty || isSubmitting)
            }
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        // 올리기 버튼은 화면 아래 끝에서 32 위 (Figma pb 32)
        .padding(.bottom, 32)
        .ignoresSafeArea(.container, edges: .bottom)
        .background(.bg)
        .hToast($toast)
        .aiGuessCard(aiGuess)
        // 첫 획을 그으면 시간을 재기 시작해요.
        .task(id: canvas.startedAt) {
            guard let seconds = spec.seconds, canvas.startedAt != nil else { return }
            while canvas.remainingSeconds(total: seconds, now: .now) > 0 {
                try? await Task.sleep(for: .milliseconds(200))
                if Task.isCancelled { return }
            }
            canvas.lock()
            toast = "시간이 끝났어요"
        }
    }

    private func submit() {
        guard !isSubmitting, let png = canvas.pngData() else { return }
        isSubmitting = true
        Task {
            if await !onSubmit(.image(png, fileExtension: "png")) { isSubmitting = false }
        }
    }

    private func showAI() {
        let ink = canvas.inkImage()
        let topic = topic, template = template, spec = spec
        aiGuess.guess(mine: true, load: { ink }) {
            DrawSubjectMatcher.match(template: template, topic: topic, spec: spec, categories: $0)
        }
    }

    private func historyButton(_ icon: HIcon, label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HIconView(icon: icon, size: 22, color: enabled ? .ink : .controlBorder)
                .contentShape(Rectangle().inset(by: -11))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    /// 펜/지우개 칩 (간격 6) + 굵기 3단계 (44 원, 고른 것만 surfaceStrong 바탕, 점 4·8·14)
    private var tools: some View {
        HStack {
            HStack(spacing: 6) {
                HChip("펜", isSelected: canvas.tool == .pen) { canvas.tool = .pen }
                HChip("지우개", isSelected: canvas.tool == .eraser) { canvas.tool = .eraser }
            }
            Spacer(minLength: 8)
            HStack(spacing: 4) {
                ForEach(DrawingCanvasModel.Thickness.allCases, id: \.self) { thickness in
                    Button {
                        canvas.thickness = thickness
                    } label: {
                        Circle()
                            .fill(canvas.thickness == thickness ? Color.surfaceStrong : Color.bg)
                            .frame(width: 44, height: 44)
                            .overlay(Circle().fill(.ink).frame(width: thickness.dot, height: thickness.dot))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(thickness.label)
                    .accessibilityAddTraits(canvas.thickness == thickness ? .isSelected : [])
                }
            }
        }
    }

    /// 7색 — 44 칸에 지름 30 원, 고른 색은 ink 테두리 2
    private var colors: some View {
        HStack(spacing: 0) {
            ForEach(Array(DrawingCanvasModel.palette.enumerated()), id: \.offset) { index, color in
                if index > 0 { Spacer(minLength: 0) }
                let isSelected = canvas.colorIndex == index && canvas.tool == .pen
                Button {
                    canvas.colorIndex = index
                    canvas.tool = .pen
                } label: {
                    Circle()
                        .fill(color)
                        .frame(width: 30, height: 30)
                        .frame(width: 44, height: 44)
                        .overlay {
                            if isSelected {
                                Circle().strokeBorder(.ink, lineWidth: 2)
                            }
                        }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("색 \(index + 1)")
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
    }
}

/// 그림판 상태 — 도구·굵기·색과 되돌리기 상태를 들고 있어요.
@Observable
final class DrawingCanvasModel {
    enum Tool { case pen, eraser }

    enum Thickness: CaseIterable {
        case thin, medium, thick

        /// Figma 굵기 버튼 안 점 지름
        var dot: CGFloat {
            switch self {
            case .thin: 4
            case .medium: 8
            case .thick: 14
            }
        }

        /// 실제 펜 굵기 — Figma 에 없어서 점 크기에 비례해 정했어요.
        var penWidth: CGFloat {
            switch self {
            case .thin: 3
            case .medium: 6
            case .thick: 12
            }
        }

        var label: String {
            switch self {
            case .thin: "가는 굵기"
            case .medium: "보통 굵기"
            case .thick: "굵은 굵기"
            }
        }
    }

    /// Figma 06 색상 7개
    static let palette: [Color] = [
        .ink,
        Color(hex: 0x8E8E93),
        Color(hex: 0xE5484D),
        Color(hex: 0xF5A524),
        Color(hex: 0xF2D74E),
        Color(hex: 0x30A46C),
        Color(hex: 0x2F5BFF),
    ]

    var tool: Tool = .pen { didSet { applyTool() } }
    var thickness: Thickness = .medium { didSet { applyTool() } }
    var colorIndex = 0 { didSet { applyTool() } }

    private(set) var isEmpty = true
    private(set) var canUndo = false
    private(set) var canRedo = false
    /// 첫 획을 그은 시각 (스케치 타이머)
    private(set) var startedAt: Date?
    /// 시간이 끝나 더 그릴 수 없어요.
    private(set) var isLocked = false

    @ObservationIgnored weak var canvasView: PKCanvasView?

    var pkTool: PKTool {
        switch tool {
        case .pen:
            PKInkingTool(.pen, color: UIColor(Self.palette[colorIndex]), width: thickness.penWidth)
        case .eraser:
            PKEraserTool(.bitmap, width: thickness.penWidth * 3)
        }
    }

    func applyTool() {
        canvasView?.tool = pkTool
    }

    func markStarted() {
        if startedAt == nil { startedAt = .now }
    }

    func remainingSeconds(total: Int, now: Date) -> Int {
        guard let startedAt else { return total }
        return max(0, total - Int(now.timeIntervalSince(startedAt)))
    }

    func lock() {
        isLocked = true
        canvasView?.drawingGestureRecognizer.isEnabled = false
    }

    func undo() {
        canvasView?.undoManager?.undo()
        refresh()
    }

    func redo() {
        canvasView?.undoManager?.redo()
        refresh()
    }

    func refresh() {
        guard let canvasView else { return }
        isEmpty = canvasView.drawing.strokes.isEmpty
        canUndo = canvasView.undoManager?.canUndo ?? false
        canRedo = canvasView.undoManager?.canRedo ?? false
    }

    /// AI 추측용 잉크 — 검정 잉크 + 투명 배경 (명세 (1))
    func inkImage() -> InkImage? {
        guard let canvasView else { return nil }
        return InkSource.ink(drawing: canvasView.drawing, bounds: canvasView.bounds)
    }

    /// 흰 바탕 PNG (올리기용)
    func pngData(scale: CGFloat = 2) -> Data? {
        guard let canvasView else { return nil }
        let bounds = canvasView.bounds
        let renderer = UIGraphicsImageRenderer(bounds: bounds, format: {
            let format = UIGraphicsImageRendererFormat()
            format.scale = scale
            return format
        }())
        let image = renderer.image { context in
            UIColor.white.setFill()
            context.fill(bounds)
            canvasView.drawing.image(from: bounds, scale: scale).draw(in: bounds)
        }
        return image.pngData()
    }
}

/// PKCanvasView 를 SwiftUI 에 붙여요. 손가락으로도 그릴 수 있어요.
struct PencilCanvas: UIViewRepresentable {
    let model: DrawingCanvasModel

    func makeUIView(context: Context) -> PKCanvasView {
        let view = PKCanvasView()
        view.drawingPolicy = .anyInput
        view.backgroundColor = .white
        view.isOpaque = true
        view.delegate = context.coordinator
        model.canvasView = view
        model.applyTool()
        return view
    }

    func updateUIView(_ view: PKCanvasView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(model: model) }

    final class Coordinator: NSObject, PKCanvasViewDelegate {
        let model: DrawingCanvasModel
        init(model: DrawingCanvasModel) { self.model = model }

        func canvasViewDidBeginUsingTool(_ canvasView: PKCanvasView) {
            DispatchQueue.main.async { [model] in model.markStarted() }
        }

        func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) {
            // 되돌리기 기록이 쌓인 다음에 상태를 읽어요.
            DispatchQueue.main.async { [model] in model.refresh() }
        }
    }
}

#Preview {
    DrawMissionView(topic: "요즘 제일 웃겼던 순간")
}

#Preview("30초 스케치") {
    DrawMissionView(topic: "30초 안에 그려 보세요", spec: MissionSpec(seconds: 30, subject: "고양이"))
}
