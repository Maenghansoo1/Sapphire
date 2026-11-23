import Foundation

// 그리는 중 별이(AI) 실시간 추측 — Android `feature/draw/LiveGuess.kt` · `LiveGuessRunner.kt`(문구)를 옮겼어요.
// UI·Core ML 과 무관한 순수 로직이라 단위 테스트로 확인해요. 결과는 메모리에만 있고 저장·전송하지 않아요.

/// 그리는 중 "별이가 맞혔어요"로 치는 주제 확률. 보정 전 임시값(Android `LIVE_CORRECT_THRESHOLD` 와 같아요).
/// 제출 때 쓰는 `aiGuessThreshold`(0.55)보다 조금 높게 잡아, 잠깐 스친 확률로 맞혔다고 하지 않게 했어요.
let liveCorrectThreshold: Float = 0.6

/// 실시간 추측 설정
/// - alpha: 새 추론 결과를 얼마나 믿을지(지수이동평균). 1이면 마지막 결과 그대로.
/// - margin: 화면의 1등을 바꾸려면 새 후보가 지금 1등보다 이만큼 높아야 해요(1등이 흔들리지 않게).
/// - unsureThreshold: 1등 확률이 이보다 낮으면 "아직 모르겠어요".
/// - correctThreshold: 주제 확률이 이 이상인 상태가 `requiredStreak` 번 이어지면 맞힘.
struct LiveGuessConfig {
    var alpha: Float = 0.5
    var margin: Float = 0.1
    var unsureThreshold: Float = aiGuessThreshold
    var correctThreshold: Float = liveCorrectThreshold
    var requiredStreak = 2
    var topCount = 3
}

enum LiveGuessStatus: Equatable {
    /// 그림이 비었거나 너무 작아서 추론하지 않음
    case tooEmpty
    /// 그림은 있지만 아직 첫 추론 결과가 없음(모델을 처음 불러오는 중 등)
    case thinking
    /// 1등 확률이 기준 미만
    case unsure
    /// 1등을 골랐지만 주제는 아님, 또는 주제를 범주로 알 수 없어 추측만
    case guessed
    /// 주제를 맞힌 상태가 이어지는 중
    case correct
}

/// - guessed: 이번 그림에서 한 번이라도 맞혔는지. 지우개로 지워도 남고 `reset()` 에서만 초기화.
/// - elapsedMillis: 첫 획부터 처음 맞힐 때까지 걸린 시간. 시작 시각을 모르면 nil.
struct LiveGuessState: Equatable {
    var status: LiveGuessStatus = .tooEmpty
    var guesses: [AiGuess] = []
    var guessed = false
    var guessedAtMillis: Int64?
    var elapsedMillis: Int64?
}

/// 그리는 중 계속 들어오는 추론 결과를 화면에 보여 줄 "안정된 추측"과 "맞혔다" 판정으로 바꿔요. 한 스레드(메인)에서만 불러 주세요.
/// `subjects` 가 비어 있으면(주제를 범주로 모름, `[대상]` 미션) 판정 없이 추측만 해요.
final class LiveGuessTracker {
    private let config: LiveGuessConfig
    private let subjectIDs: Set<Int>
    private var smoothed: [Float]?
    private var leader: Int?
    private var streak = 0
    private var startedAtMillis: Int64?

    private(set) var state = LiveGuessState()

    init(config: LiveGuessConfig = LiveGuessConfig(), subjects: [DrawCategory]) {
        self.config = config
        subjectIDs = Set(subjects.map(\.id))
    }

    /// 획을 긋기 시작할 때. 그림의 첫 획 시각만 기억해서 "○초 만에"를 계산해요.
    func onStrokeStart(at millis: Int64) {
        if startedAtMillis == nil { startedAtMillis = millis }
    }

    /// 그림은 있는데 추론 결과를 기다리는 중. 이미 보여 주는 추측이 있으면 그대로 둬서 깜빡이지 않아요.
    @discardableResult
    func onInkPending() -> LiveGuessState {
        if state.guesses.isEmpty, state.status != .thinking { state.status = .thinking }
        return state
    }

    /// 추론 결과 하나를 반영해요. 길이가 다르거나 NaN·무한대가 섞이면 이번 결과는 버려요(크래시 없음).
    @discardableResult
    func onInference(_ probabilities: [Float], categories: [DrawCategory], at millis: Int64) -> LiveGuessState {
        guard !categories.isEmpty, probabilities.count == categories.count, probabilities.allSatisfy(\.isFinite) else { return state }
        let raw = probabilities.map { min(max($0, 0), 1) }
        let previous = smoothed.flatMap { $0.count == raw.count ? $0 : nil }
        let values = previous.map { previous in raw.indices.map { config.alpha * raw[$0] + (1 - config.alpha) * previous[$0] } } ?? raw
        smoothed = values
        if previous == nil { leader = nil }

        // 같은 확률이면 번호가 작은 범주
        func better(_ a: Int, _ b: Int) -> Bool { values[a] != values[b] ? values[a] > values[b] : a < b }
        let best = values.indices.min(by: better)!
        if let current = leader {
            if best != current, values[best] - values[current] >= config.margin { leader = best }
        } else {
            leader = best
        }

        let subjectIndices = subjectIDs.isEmpty ? [] : categories.indices.filter { subjectIDs.contains(categories[$0].id) }
        let bestSubject = subjectIndices.min(by: better)
        streak = bestSubject.map { values[$0] >= config.correctThreshold } == true ? streak + 1 : 0
        let correct = bestSubject != nil && streak >= config.requiredStreak
        if correct { leader = bestSubject }  // 맞힌 범주를 1등으로 보여 줘요

        var next = state
        if correct, !next.guessed {
            next.guessed = true
            next.guessedAtMillis = millis
            next.elapsedMillis = startedAtMillis.map { max(0, millis - $0) }
        }

        let top = leader!
        next.status = correct ? .correct : values[top] < config.unsureThreshold ? .unsure : .guessed
        let others = values.indices.filter { $0 != top }.sorted(by: better).prefix(config.topCount - 1)
        next.guesses = ([top] + others).map { AiGuess(category: categories[$0], probability: (values[$0] * 1000).rounded() / 1000) }
        state = next
        return state
    }

    /// 지우개로 지워서 잉크가 모자랄 때. 평활 기록은 버리지만 이미 맞혔다는 기록은 남겨요.
    @discardableResult
    func onEmpty() -> LiveGuessState {
        smoothed = nil
        leader = nil
        streak = 0
        state.status = .tooEmpty
        state.guesses = []
        return state
    }

    /// 새 그림을 시작할 때. 맞힌 기록과 시작 시각까지 모두 지워요.
    @discardableResult
    func reset() -> LiveGuessState {
        smoothed = nil
        leader = nil
        streak = 0
        startedAtMillis = nil
        state = LiveGuessState()
        return state
    }
}

/// 추론을 언제 부를지 정해요(매번 부르지 않게).
/// - 획이 끝나면 바로 허용해요.
/// - 그 밖에는 마지막 추론을 시작한 뒤 `minIntervalMillis` 가 지나야 허용해요.
/// - 추론이 도는 중에 들어온 요청은 쌓지 않고 "하나 더 필요함"만 표시했다가, 끝나면 한 번만 다시 돌려요.
struct InferenceGate {
    var minIntervalMillis: Int64 = 500
    private(set) var running = false
    private(set) var pending = false
    private var pendingStrokeEnd = false
    private var lastStartedAtMillis: Int64?

    init(minIntervalMillis: Int64 = 500) {
        self.minIntervalMillis = minIntervalMillis
    }

    /// 지금 추론을 시작해도 되면 true(그때부터 실행 중으로 봐요).
    mutating func request(at millis: Int64, strokeEnded: Bool) -> Bool {
        if running {
            pending = true
            pendingStrokeEnd = pendingStrokeEnd || strokeEnded
            return false
        }
        if !strokeEnded, !intervalPassed(millis) { return false }
        start(millis)
        return true
    }

    /// 추론이 끝났을 때. 도는 동안 들어온 요청이 있으면 true — 바로 한 번 더 돌려 주세요.
    mutating func finish(at millis: Int64) -> Bool {
        running = false
        let rerun = pending && (pendingStrokeEnd || intervalPassed(millis))
        pending = false
        pendingStrokeEnd = false
        if rerun { start(millis) }
        return rerun
    }

    mutating func reset() {
        running = false
        pending = false
        pendingStrokeEnd = false
        lastStartedAtMillis = nil
    }

    private func intervalPassed(_ millis: Int64) -> Bool {
        lastStartedAtMillis.map { millis - $0 >= minIntervalMillis } ?? true
    }

    private mutating func start(_ millis: Int64) {
        running = true
        lastStartedAtMillis = millis
    }
}

// MARK: - 말풍선 문구

/// 앱 안에서 AI 는 '별이'예요(이모지는 쓰지 않아요). 그린 사람을 탓하지 않는 말투로.
/// `candidates` 는 말풍선 아래 칩으로 보여 줄 상위 후보(최대 2개), `badge` 는 이미 맞힌 뒤 그림이 바뀌었을 때 남기는 표시예요.
struct LiveGuessCopy: Equatable {
    let title: String
    let detail: String?
    let candidates: [AiGuess]
    let badge: String?
    let celebrate: Bool

    /// 화면 낭독용 설명
    var description: String {
        let candidateText = candidates.map { "\($0.category.ko) \(percentText($0.probability))" }.joined(separator: ", ")
        return [title, detail, candidateText.isEmpty ? nil : candidateText, badge].compactMap { $0 }.joined(separator: ", ")
    }
}

func liveGuessCopy(_ state: LiveGuessState) -> LiveGuessCopy {
    let top = state.guesses.first?.category.ko
    let candidates = Array(state.guesses.prefix(2))
    let badge = state.guessed && state.status != .correct
        ? state.elapsedMillis.map { "\(elapsedSeconds($0))초 만에 맞혔어요" } ?? "맞혔어요"
        : nil
    switch state.status {
    case .tooEmpty:
        return LiveGuessCopy(title: "그리면 별이가 맞혀 볼게요", detail: "그리는 동안 계속 추측해요", candidates: [], badge: badge, celebrate: false)
    case .thinking:
        return LiveGuessCopy(title: "별이가 지켜보는 중…", detail: nil, candidates: [], badge: badge, celebrate: false)
    case .unsure:
        return LiveGuessCopy(title: "음… 아직 모르겠어요", detail: nil, candidates: candidates, badge: badge, celebrate: false)
    case .guessed:
        let title = top.map { "별이는 ‘\($0)’\(KoreanText.josa($0, "이라고", "라고")) 생각해요" } ?? "음… 아직 모르겠어요"
        return LiveGuessCopy(title: title, detail: nil, candidates: candidates, badge: badge, celebrate: false)
    case .correct:
        let detail = [top.map { "‘\($0)’" }, state.elapsedMillis.map { "\(elapsedSeconds($0))초 만에" }]
            .compactMap { $0 }.joined(separator: " · ")
        return LiveGuessCopy(title: "별이가 맞혔어요!", detail: detail.isEmpty ? nil : detail, candidates: [], badge: nil, celebrate: true)
    }
}

/// 반올림한 초, 최소 1초(0초 만에 맞혔다고 하지 않게)
func elapsedSeconds(_ millis: Int64) -> Int64 {
    max(1, (max(0, millis) + 500) / 1000)
}
