import SwiftUI

/// 그리는 중 계속 별이(AI)가 추측하게 해요 (Android `LiveGuessRunner`).
/// 획이 끝날 때마다 추론하고(`InferenceGate` 로 겹치지 않게), 결과를 `LiveGuessTracker` 로 안정화해서 `state` 에 둬요.
/// 그림·추론 결과는 메모리에만 있고 저장·전송하지 않아요. 올릴 때는 지금처럼 `guessForUpload` 를 따로 불러요.
@MainActor
@Observable
final class LiveGuessRunner {
    private(set) var state = LiveGuessState()
    /// 모델을 쓸 수 있을 때만 true — 불러오지 못하면 말풍선을 숨겨요.
    private(set) var isAvailable = false

    @ObservationIgnored private let subjectsOf: ([DrawCategory]) -> [DrawCategory]
    @ObservationIgnored private var gate = InferenceGate()
    @ObservationIgnored private var tracker: LiveGuessTracker?
    /// 모델을 불러오기 전에 그리기 시작한 시각
    @ObservationIgnored private var startedAtMillis: Int64?
    @ObservationIgnored private var latest: (() -> InkImage?)?
    @ObservationIgnored private var task: Task<Void, Never>?

    init(subjectsOf: @escaping ([DrawCategory]) -> [DrawCategory]) {
        self.subjectsOf = subjectsOf
    }

    func checkAvailability() async {
        isAvailable = await QuickDrawClassifier.shared() != nil
    }

    /// 펜 획을 긋기 시작할 때. 그림의 첫 획 시각부터 "○초 만에"를 세요.
    func onStrokeStart() {
        let now = Self.now()
        if let tracker { tracker.onStrokeStart(at: now) } else if startedAtMillis == nil { startedAtMillis = now }
    }

    /// 그림이 바뀌었을 때(획 끝, 되돌리기). `ink` 는 추론을 시작할 때 불러서 그 순간의 그림을 떠요.
    func request(strokeEnded: Bool, hasInk: Bool, ink: @escaping () -> InkImage?) {
        guard isAvailable else { return }
        latest = ink
        if hasInk, state.guesses.isEmpty, state.status != .thinking {
            state = tracker?.onInkPending() ?? { var next = state; next.status = .thinking; return next }()
        }
        if gate.request(at: Self.now(), strokeEnded: strokeEnded) { launchInference() }
    }

    /// 화면을 떠날 때 진행 중 추론을 멈춰요.
    func cancel() {
        task?.cancel()
        task = nil
        gate.reset()
    }

    private func launchInference() {
        task = Task {
            await infer()
            guard !Task.isCancelled else { return }
            if gate.finish(at: Self.now()) { launchInference() }
        }
    }

    private func infer() async {
        guard let classifier = await QuickDrawClassifier.shared() else { isAvailable = false; return }
        let active: LiveGuessTracker
        if let tracker {
            active = tracker
        } else {
            active = LiveGuessTracker(subjects: subjectsOf(classifier.categories))
            if let startedAtMillis { active.onStrokeStart(at: startedAtMillis) }
            startedAtMillis = nil
            tracker = active
        }
        guard let ink = latest?() else { return }
        let probabilities = await Task.detached(priority: .userInitiated) { () -> [Float]?? in
            guard let input = ink.modelInput else { return .some(nil) }  // 잉크가 모자라요
            guard let probabilities = try? classifier.classify(input) else { return nil }  // 이번 결과만 건너뛰어요
            return .some(probabilities)
        }.value
        guard !Task.isCancelled, let probabilities else { return }
        if let probabilities {
            state = active.onInference(probabilities, categories: classifier.categories, at: Self.now())
        } else {
            state = active.onEmpty()
        }
    }

    private static func now() -> Int64 {
        Int64(ProcessInfo.processInfo.systemUptime * 1000)
    }
}

/// 그림판 위 별이 말풍선 (Figma 06-3·06-4·06-5 그림 · 별이 실시간 추측).
/// 왼쪽에 테두리 원 속 별이(40), 오른쪽에 말풍선(높이 52 고정 — 문구가 바뀌어도 그림판이 들썩이지 않아요).
/// 맞히면 말풍선이 accent 로 바뀌고 VoiceOver 가 알려 줘요.
struct LiveGuessBubble: View {
    let state: LiveGuessState

    var body: some View {
        let copy = liveGuessCopy(state)
        let celebrate = copy.celebrate
        HStack(spacing: 8) {
            Image("byeol")
                .resizable()
                .scaledToFit()
                .frame(width: 28, height: 28)
                .frame(width: 40, height: 40)
                .background(celebrate ? Color.bg : Color.accentSubtle, in: Circle())
                .overlay(Circle().strokeBorder(.accent, lineWidth: 1.5))

            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(copy.title)
                        .font(.plex(14, .semibold))
                        .foregroundStyle(celebrate ? Color.onAccent : Color.ink)
                        .lineLimit(1)
                    if let detail = copy.detail {
                        Text(detail)
                            .font(.caption12)
                            .monospacedDigit()
                            .foregroundStyle(celebrate ? Color.onAccent.opacity(0.85) : Color.textSecondary)
                            .lineLimit(1)
                    }
                    if !copy.candidates.isEmpty {
                        HStack(spacing: 4) {
                            ForEach(Array(copy.candidates.enumerated()), id: \.offset) { index, guess in
                                CandidateChip(guess: guess, leading: index == 0)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let badge = copy.badge {
                    Text(badge)
                        .font(.plex(12, .semibold))
                        .foregroundStyle(.accentPressed)
                        .lineLimit(1)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(.bg, in: Capsule())
                }
            }
            .padding(.leading, 14)
            .padding(.trailing, 12)
            .frame(height: 52)
            .background(celebrate ? Color.accent : Color.surface,
                        in: UnevenRoundedRectangle(topLeadingRadius: 6, bottomLeadingRadius: 18,
                                                   bottomTrailingRadius: 18, topTrailingRadius: 18, style: .continuous))
        }
        .animation(.easeOut(duration: 0.2), value: celebrate)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(copy.description)
        .onChange(of: celebrate) { _, isCorrect in
            if isCorrect { AccessibilityNotification.Announcement(copy.description).post() }
        }
    }
}

/// 후보 칩 — 1등은 accentSubtle 바탕, 2등은 흰 바탕
private struct CandidateChip: View {
    let guess: AiGuess
    let leading: Bool

    var body: some View {
        let color: Color = leading ? .accentPressed : .textSecondary
        HStack(spacing: 4) {
            Text(guess.category.ko).font(.plex(12, .medium))
            Text(percentText(guess.probability)).font(.caption12).monospacedDigit()
        }
        .foregroundStyle(color)
        .lineLimit(1)
        .padding(.horizontal, 7)
        .padding(.vertical, 1)
        .background(leading ? Color.accentSubtle : Color.bg, in: Capsule())
    }
}

#Preview {
    let cat = DrawCategory(id: 0, quickdraw: "cat", ko: "고양이")
    let tiger = DrawCategory(id: 1, quickdraw: "tiger", ko: "호랑이")
    VStack(spacing: 12) {
        LiveGuessBubble(state: LiveGuessState())
        LiveGuessBubble(state: LiveGuessState(status: .guessed, guesses: [
            AiGuess(category: tiger, probability: 0.41), AiGuess(category: cat, probability: 0.33),
        ]))
        LiveGuessBubble(state: LiveGuessState(status: .correct, guesses: [AiGuess(category: cat, probability: 0.8)],
                                              guessed: true, guessedAtMillis: 8000, elapsedMillis: 8000))
    }
    .padding(20)
}
