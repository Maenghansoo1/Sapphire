import Foundation

/// "잘 모르겠어요" 기준. 1등 확률이 이보다 낮으면 답하지 않아요.
/// M1 6단계에서 팀원이 그림판으로 그린 249장(`ml/quickdraw/EVAL.md`)으로 정한 값 — 모델 설명서(`export.json` 의
/// `unsure_threshold`)와 같아야 해요 (Android `AI_GUESS_THRESHOLD`, 다르면 단위 테스트 실패).
let aiGuessThreshold: Float = 0.55

/// 주제가 2·3등이어도 확률이 이보다 낮으면 "아깝게"가 아니라 "못 맞혔어요"예요 (0%짜리 3등을 아깝다고 하지 않게).
let aiCloseMinProbability: Float = 0.15

/// 모델 범주 하나 (`quickdraw-categories.json`, M1). `id` 가 모델 출력의 번호예요.
struct DrawCategory: Decodable, Hashable {
    let id: Int
    let quickdraw: String
    let ko: String
    let aliasesKo: [String]

    enum CodingKeys: String, CodingKey {
        case id, quickdraw, ko
        case aliasesKo = "aliases_ko"
    }

    init(id: Int, quickdraw: String, ko: String, aliasesKo: [String] = []) {
        self.id = id
        self.quickdraw = quickdraw
        self.ko = ko
        self.aliasesKo = aliasesKo
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int.self, forKey: .id)
        quickdraw = try container.decode(String.self, forKey: .quickdraw)
        ko = try container.decode(String.self, forKey: .ko)
        aliasesKo = try container.decodeIfPresent([String].self, forKey: .aliasesKo) ?? []
    }

    var names: [String] { [ko] + aliasesKo }

    struct InvalidCategories: Error {}

    /// 범주 번호가 0부터 빠짐없이 이어지지 않으면 모델 출력과 맞출 수 없어서 실패해요.
    static func parse(_ data: Data) throws -> [DrawCategory] {
        let categories = try JSONDecoder().decode([DrawCategory].self, from: data).sorted { $0.id < $1.id }
        guard !categories.isEmpty, categories.enumerated().allSatisfy({ $0.offset == $0.element.id }) else {
            throw InvalidCategories()
        }
        return categories
    }
}

struct AiGuess: Hashable {
    let category: DrawCategory
    let probability: Float
}

/// 그림을 올릴 때 후보만 전달한다. 모델이 없거나 그림이 비어도 제출은 계속한다.
func guessForUpload(_ ink: InkImage?) async -> [AiGuessEntry] {
    guard let ink, let classifier = await QuickDrawClassifier.shared() else { return [] }
    return await Task.detached(priority: .userInitiated) {
        guard let input = ink.modelInput, let probabilities = try? classifier.classify(input) else { return [] }
        return uploadGuessEntries(probabilities, categories: classifier.categories)
    }.value
}

func uploadGuessEntries(_ probabilities: [Float], categories: [DrawCategory]) -> [AiGuessEntry] {
    topGuesses(probabilities, categories: categories)
        .map { AiGuessEntry(label: $0.category.ko, probability: $0.probability) }
}

/// 확률이 높은 순서로 `count`개. 같은 확률이면 범주 번호 순.
func topGuesses(_ probabilities: [Float], categories: [DrawCategory], count: Int = 3) -> [AiGuess] {
    precondition(probabilities.count == categories.count, "모델 출력 수와 범주 수가 달라요")
    return probabilities.indices
        .sorted { probabilities[$0] != probabilities[$1] ? probabilities[$0] > probabilities[$1] : $0 < $1 }
        .prefix(count)
        .map { AiGuess(category: categories[$0], probability: probabilities[$0]) }
}

/// 그림을 올릴 때 후보 이름과 확률을 그룹에 보여 줘요.
enum AiVerdict: Hashable {
    /// 그림이 너무 비어서 추론하지 않음
    case tooEmpty
    /// 1등 확률이 기준 미만
    case unsure([AiGuess])
    /// 주제를 범주로 알 수 없어 판정 없이 추측만
    case guessed([AiGuess])
    /// 1등이 주제와 같음
    case correct([AiGuess])
    /// 1등은 아니지만 2·3등 안에 주제가 있고 확률도 꽤 있음
    case close([AiGuess], subject: DrawCategory, rank: Int)
    /// 3등 안에 주제가 없거나 있어도 확률이 아주 낮음
    case missed([AiGuess], subject: DrawCategory)

    var guesses: [AiGuess] {
        switch self {
        case .tooEmpty: []
        case .unsure(let guesses), .guessed(let guesses), .correct(let guesses): guesses
        case .close(let guesses, _, _), .missed(let guesses, _): guesses
        }
    }
}

func aiVerdict(_ guesses: [AiGuess]?, subject: DrawCategory?, threshold: Float = aiGuessThreshold) -> AiVerdict {
    guard let guesses, let first = guesses.first else { return .tooEmpty }
    if first.probability < threshold { return .unsure(guesses) }
    guard let subject else { return .guessed(guesses) }
    let rank = guesses.firstIndex { $0.category.id == subject.id }
    if rank == 0 { return .correct(guesses) }
    if let rank, rank > 0, guesses[rank].probability >= aiCloseMinProbability {
        return .close(guesses, subject: subject, rank: rank + 1)
    }
    return .missed(guesses, subject: subject)
}

/// 결과 카드 제목·설명. 그린 사람을 탓하지 않는 말투로 (정확도가 낮을 때도). `mine` 은 내가 방금 그린 그림인지.
struct AiVerdictCopy: Equatable {
    let title: String
    let detail: String
}

func aiVerdictCopy(_ verdict: AiVerdict, mine: Bool) -> AiVerdictCopy {
    switch verdict {
    case .tooEmpty:
        return mine ? AiVerdictCopy(title: "조금 더 그려 볼까요?", detail: "AI가 알아보기엔 아직 그림이 너무 작아요")
            : AiVerdictCopy(title: "AI가 알아보기 어려워요", detail: "그림이 너무 작거나 비어 있어요")
    case .unsure(let guesses):
        return AiVerdictCopy(title: "잘 모르겠어요", detail: mine ? "AI가 아직 배우는 중이에요. 조금 더 그려 볼까요?"
            : "AI도 헷갈린대요. ‘\(guesses[0].category.ko)’일까요?")
    case .guessed(let guesses):
        return AiVerdictCopy(title: "AI는 \(quoted(guesses[0].category.ko, "이라고", "라고")) 생각해요", detail: "AI가 고른 후보예요")
    case .correct(let guesses):
        return AiVerdictCopy(title: "AI가 맞혔어요!", detail: "‘\(guesses[0].category.ko)’ 그림이 맞대요")
    case .close(let guesses, let subject, let rank):
        return AiVerdictCopy(title: "아깝게 놓쳤어요", detail: "\(quoted(subject.ko, "을", "를")) \(rank)등으로 골랐어요. "
            + "AI는 \(quoted(guesses[0].category.ko, "이라고", "라고")) 생각했어요")
    case .missed(let guesses, _):
        return AiVerdictCopy(title: "AI가 못 맞혔어요", detail: "AI는 \(quoted(guesses[0].category.ko, "이라고", "라고")) 생각했어요")
    }
}

/// ‘달’이라고, ‘해’라고 — 따옴표 안 낱말의 받침에 맞춘 조사
private func quoted(_ word: String, _ withConsonant: String, _ withoutConsonant: String) -> String {
    "‘\(word)’" + KoreanText.josa(word, withConsonant, withoutConsonant)
}

/// 확률 막대 옆 숫자. 반올림해서 0~100%.
func percentText(_ probability: Float) -> String {
    "\(Int((min(max(probability, 0), 1) * 100).rounded()))%"
}

/// 미션 주제가 모델 범주 중 무엇인지 찾아요. 찾으면 "AI가 맞혔는지" 판정하고, 못 찾으면 추측만 보여요.
/// - 30초 스케치는 빈칸 `subject`(없으면 주제 문장), A를 B로는 결과물인 `b`(없으면 주제 문장)로 판정해요.
/// - 사람(`[대상]`)을 그리는 스케치는 범주에 없으니 판정하지 않아요.
/// - 한 글자 범주(해·달·별·산·차·개…)는 다른 낱말 속 글자와 헷갈리기 쉬워서 낱말이 통째로 같을 때만 맞다고 봐요.
enum DrawSubjectMatcher {
    private static let particles = ["으로", "이랑", "랑", "을", "를", "이", "가", "은", "는", "의", "로", "와", "과", "도", "에"]

    static func match(template: String, topic: String, spec: MissionSpec, categories: [DrawCategory]) -> DrawCategory? {
        let text: String
        if template == MissionTemplates.drawAToB {
            text = spec.drawTo.nonBlank ?? topic
        } else {
            if spec.hasTarget { return nil }
            text = spec.subject.nonBlank ?? topic
        }
        return matchText(text, categories: categories)
    }

    static func matchText(_ text: String, categories: [DrawCategory]) -> DrawCategory? {
        let whole = normalize(text)
        guard !whole.isEmpty else { return nil }
        let words = Set(text.split { !($0.isLetter || $0.isNumber) }.map(String.init)
            .flatMap { [normalize($0), normalize(stripParticle($0))] })
        // 이름이 길수록 구체적이에요("생일케이크" > "케이크"). 같은 길이면 범주 번호 순.
        let candidates = categories.flatMap { category in category.names.map { (normalize($0), category) } }
            .filter { !$0.0.isEmpty }
            .enumerated()
            .sorted { $0.element.0.count != $1.element.0.count ? $0.element.0.count > $1.element.0.count : $0.offset < $1.offset }
            .map(\.element)
        return candidates.first { $0.0 == whole || words.contains($0.0) }?.1
            ?? candidates.first { $0.0.count >= 2 && whole.contains($0.0) }?.1
    }

    private static func normalize(_ value: String) -> String {
        String(value.filter { !$0.isWhitespace }).lowercased()
    }

    private static func stripParticle(_ word: String) -> String {
        guard let particle = particles.first(where: { word.count > $0.count && word.hasSuffix($0) }) else { return word }
        return String(word.dropLast(particle.count))
    }
}
