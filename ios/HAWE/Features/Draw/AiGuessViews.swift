import PencilKit
import SwiftUI

/// 제출 전 AI 미리보기 카드 상태를 들고 있어요.
@MainActor
@Observable
final class AiGuessRunner {
    enum State: Equatable {
        case thinking
        case done(AiVerdict, mine: Bool)
        case failed(String)
    }

    private(set) var state: State?
    /// 모델을 쓸 수 있을 때만 true — 불러오지 못하면 버튼을 숨겨요.
    private(set) var isAvailable = false
    @ObservationIgnored private var task: Task<Void, Never>?

    /// 임시 연출: 너무 빨리 끝나도 잠깐 생각하는 모습을 보여요.
    private static let minThinking: Duration = .milliseconds(800)

    func checkAvailability() async {
        isAvailable = await QuickDrawClassifier.shared() != nil
    }

    /// `load` 는 그림 픽셀을, `subjectOf` 는 미션 주제에 해당하는 범주(없으면 nil)를 줘요.
    func guess(mine: Bool, load: @escaping @Sendable () async -> InkImage?, subjectOf: @escaping ([DrawCategory]) -> DrawCategory?) {
        task?.cancel()
        state = .thinking
        task = Task {
            let started = ContinuousClock.now
            let outcome: State
            if let classifier = await QuickDrawClassifier.shared() {
                if let ink = await load() {
                    let result = await Task.detached(priority: .userInitiated) { () -> [AiGuess]?? in
                        guard let input = ink.modelInput else { return .some(nil) }
                        guard let probabilities = try? classifier.classify(input) else { return nil }
                        return .some(topGuesses(probabilities, categories: classifier.categories))
                    }.value
                    if let guesses = result {
                        outcome = .done(aiVerdict(guesses, subject: subjectOf(classifier.categories)), mine: mine)
                    } else {
                        outcome = .failed("AI가 지금은 쉬고 있어요")
                    }
                } else {
                    outcome = .failed("그림을 불러오지 못했어요")
                }
            } else {
                outcome = .failed("AI가 지금은 쉬고 있어요")
            }
            let elapsed = ContinuousClock.now - started
            if elapsed < Self.minThinking { try? await Task.sleep(for: Self.minThinking - elapsed) }
            guard !Task.isCancelled else { return }
            state = outcome
        }
    }

    func dismiss() {
        task?.cancel()
        state = nil
    }
}

extension View {
    /// AI 추측 결과 카드를 화면 위에 띄워요.
    func aiGuessCard(_ runner: AiGuessRunner) -> some View {
        overlay {
            if runner.state != nil {
                AiGuessCard(runner: runner)
            }
        }
        .animation(.easeOut(duration: 0.2), value: runner.state)
        .task { await runner.checkAvailability() }
    }
}

/// 결과 카드 (임시 UI: Figma 확정 시 교체). 1~3등 확률 막대와 판정 문구.
private struct AiGuessCard: View {
    let runner: AiGuessRunner

    var body: some View {
        ZStack {
            Color.ink.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture { runner.dismiss() }

            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 8) {
                    HIconView(icon: .aiGuessTemp, size: 24, color: .accentPressed)
                    Text("AI의 추측")
                        .font(.plex(14, .semibold))
                        .foregroundStyle(.accentPressed)
                }
                content
                HButton("닫기", height: 48) { runner.dismiss() }
            }
            .padding(24)
            .background(.bg, in: RoundedRectangle(cornerRadius: HRadius.largeCard, style: .continuous))
            .padding(.horizontal, 28)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
        }
        .transition(.opacity)
    }

    @ViewBuilder
    private var content: some View {
        switch runner.state {
        case .thinking:
            HStack(spacing: 12) {
                ProgressView().tint(.accent)
                Text("AI가 생각 중…")
                    .font(.plex(18, .bold))
                    .foregroundStyle(.ink)
            }
            .frame(minHeight: 72)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("AI가 생각 중이에요")
        case .failed(let message):
            Text(message)
                .font(.plex(18, .bold))
                .foregroundStyle(.ink)
        case .done(let verdict, let mine):
            let copy = aiVerdictCopy(verdict, mine: mine)
            VStack(alignment: .leading, spacing: 6) {
                Text(copy.title)
                    .font(.plex(20, .bold))
                    .plexLineHeight(1.35, size: 20, weight: .bold)
                    .foregroundStyle(.ink)
                Text(copy.detail)
                    .font(.plex(14))
                    .plexLineHeight(1.5, size: 14)
                    .foregroundStyle(.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if !verdict.guesses.isEmpty {
                VStack(spacing: 10) {
                    ForEach(Array(verdict.guesses.enumerated()), id: \.offset) { index, guess in
                        GuessBar(rank: index + 1, guess: guess, highlighted: index == 0)
                    }
                }
            }
            Text("AI 추측은 재미용이에요. 그림은 휴대폰 안에서 분석하고, 올리면 별이의 추측을 그룹에 보여줘요.")
                .font(.caption12)
                .foregroundStyle(.textTertiary)
        case nil:
            EmptyView()
        }
    }
}

private struct GuessBar: View {
    let rank: Int
    let guess: AiGuess
    let highlighted: Bool

    var body: some View {
        let percent = percentText(guess.probability)
        VStack(spacing: 5) {
            HStack {
                Text("\(rank)  \(guess.category.ko)")
                    .font(.plex(15, highlighted ? .bold : .medium))
                    .foregroundStyle(.ink)
                    .lineLimit(1)
                Spacer(minLength: 8)
                Text(percent)
                    .font(.caption13)
                    .monospacedDigit()
                    .foregroundStyle(.textSecondary)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(.surfaceStrong)
                    let fraction = CGFloat(min(max(guess.probability, 0), 1))
                    if fraction > 0 {
                        Capsule().fill(highlighted ? Color.accent : Color.textTertiary)
                            .frame(width: proxy.size.width * fraction)
                    }
                }
            }
            .frame(height: 8)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(rank)등 \(guess.category.ko) \(percent)")
    }
}

/// 그림 화면의 "AI에게 보여주기" 버튼 (올리기 옆)
struct AiShowButton: View {
    var enabled = true
    var fillsWidth = false
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: HRadius.button, style: .continuous)
        let color: Color = enabled ? .ink : .textDisabled
        Button(action: action) {
            HStack(spacing: 6) {
                HIconView(icon: .aiGuessTemp, size: 20, color: color)
                Text("AI에게 보여주기")
                    .font(.plex(15, .semibold))
                    .foregroundStyle(color)
                    .lineLimit(1)
            }
            .padding(.horizontal, 14)
            .frame(maxWidth: fillsWidth ? .infinity : nil)
            .frame(height: 56)
            .background(.bg, in: shape)
            .overlay(shape.strokeBorder(.border, lineWidth: 1))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

// MARK: - 잉크 픽셀

enum InkSource {
    /// 내가 지금 그린 획 → 투명 배경 + 검정 잉크 (색은 무시, 지우개는 지운 채로) — 명세 (1) "내가 방금 그린 그림".
    /// 해상도는 그림판 크기 그대로(배율 1)라 충분히 작고, 비율 기반인 전처리 결과에는 영향이 없어요.
    static func ink(drawing: PKDrawing, bounds: CGRect) -> InkImage? {
        let black = PKDrawing(strokes: drawing.strokes.map { stroke in
            PKStroke(ink: PKInk(stroke.ink.inkType, color: .black), path: stroke.path, transform: stroke.transform, mask: stroke.mask)
        })
        var image: UIImage?
        UITraitCollection(userInterfaceStyle: .light).performAsCurrent {
            image = black.image(from: bounds, scale: 1)
        }
        return image.flatMap { rgba($0, width: Int(bounds.width.rounded()), height: Int(bounds.height.rounded())) }
    }

    /// 미리 곱하지 않은 RGBA 8비트 픽셀로 그려요 (행 우선).
    static func rgba(_ image: UIImage, width: Int, height: Int) -> InkImage? {
        guard width > 0, height > 0, let cg = image.cgImage else { return nil }
        var premultiplied = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = premultiplied.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return nil }
        // 반투명 칸은 색을 원래 값으로 되돌려요 (Android getPixels 와 같은 값).
        for offset in stride(from: 0, to: premultiplied.count, by: 4) {
            let alpha = Int(premultiplied[offset + 3])
            guard alpha > 0, alpha < 255 else { continue }
            for channel in 0..<3 {
                premultiplied[offset + channel] = UInt8(min(255, Int(premultiplied[offset + channel]) * 255 / alpha))
            }
        }
        return InkImage(rgba: premultiplied, width: width, height: height)
    }
}
