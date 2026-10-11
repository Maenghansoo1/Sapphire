import PencilKit
import SwiftUI

/// 06 미션 수행 · 그림 (Figma 1:594)
/// PencilKit. 펜/지우개, 굵기 3단계, 7색, 되돌리기/다시 실행. 올리기 → PNG
struct DrawMissionView: View {
    let topic: String
    var onSubmit: () -> Void = {}

    @State private var canvas = DrawingCanvasModel()

    var body: some View {
        VStack(spacing: 16) {
            MissionHeader(hasDraft: !canvas.isEmpty) {
                HStack(spacing: 16) {
                    historyButton(.undo, label: "되돌리기", enabled: canvas.canUndo) { canvas.undo() }
                    historyButton(.redo, label: "다시 실행", enabled: canvas.canRedo) { canvas.redo() }
                }
            }

            MissionTitle(type: .draw, topic: topic)

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

            // TODO: 서버 연결 시 canvas.pngData() 를 Storage 에 올려요.
            HButton("올리기", action: onSubmit)
                .disabled(canvas.isEmpty)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        // 올리기 버튼은 화면 아래 끝에서 32 위 (Figma pb 32)
        .padding(.bottom, 32)
        .ignoresSafeArea(.container, edges: .bottom)
        .background(.bg)
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

        func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) {
            // 되돌리기 기록이 쌓인 다음에 상태를 읽어요.
            DispatchQueue.main.async { [model] in model.refresh() }
        }
    }
}

#Preview {
    DrawMissionView(topic: "요즘 제일 웃겼던 순간")
}
