import SwiftUI

// 새 미션 틀 6종(A4) 수행 화면 — 한 단어 일기 · 한 줄 후기 · 밸런스 게임 · 슬라이더 3종.
// 디자인이 없어 별점(★/☆), A/B 카드, 슬라이더는 임시 UI 예요 (Figma 확정 시 교체).

/// 한 단어 일기(TEXT_ONE_WORD): 단어 하나(20자, 띄어쓰기 없음) + 이유(선택, 100자)
struct OneWordMissionView: View {
    let topic: String
    var onSubmit: (SubmissionContent) async -> Bool = { _ in true }

    @State private var state = OneWordState()

    var body: some View {
        TemplateMissionScaffold(
            type: .write, topic: topic,
            guide: MissionFormat.templateGuideText(MissionTemplates.textOneWord, spec: MissionSpec()),
            hasDraft: state.hasDraft, canSubmit: state.canSubmit,
            onSubmit: { await onSubmit(.scalar(state.content)) }
        ) {
            VStack(alignment: .leading, spacing: 20) {
                MissionTextBox(
                    text: $state.word, transform: TemplateInput.oneWord, placeholder: "한 단어", counter: state.wordCounter,
                    label: "한 단어", singleLine: true, minHeight: 64, font: .plex(22, .bold)
                )
                MissionTextBox(
                    text: $state.reason, transform: { TemplateInput.reason($0, limit: TemplateLimits.oneWordReason) },
                    placeholder: "왜 이 단어인지 적어도 좋아요", counter: state.reasonCounter, label: "이유 (선택)", minHeight: 120
                )
            }
        }
    }
}

/// 한 줄 후기(TEXT_REVIEW): 별점 1~5(★/☆ 텍스트 기호 — 임시 UI) + 한 줄(100자)
struct ReviewMissionView: View {
    let topic: String
    var onSubmit: (SubmissionContent) async -> Bool = { _ in true }

    @State private var state = ReviewState()

    var body: some View {
        TemplateMissionScaffold(
            type: .write, topic: topic,
            guide: MissionFormat.templateGuideText(MissionTemplates.textReview, spec: MissionSpec()),
            hasDraft: state.hasDraft, canSubmit: state.canSubmit,
            onSubmit: { await onSubmit(.scalar(state.content)) }
        ) {
            VStack(alignment: .leading, spacing: 0) {
                Text("별점")
                    .font(.fieldLabel)
                    .foregroundStyle(.textSecondary)
                HStack(spacing: 0) {
                    ForEach(1...TemplateLimits.stars, id: \.self) { count in
                        Button { state.stars = count } label: {
                            Text(count <= state.stars ? "★" : "☆")
                                .font(.plex(34))
                                .foregroundStyle(count <= state.stars ? Color.accent : Color.controlBorder)
                                .frame(width: 52, height: 52)
                                .contentShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("별 \(count)개")
                        .accessibilityAddTraits(state.stars == count ? .isSelected : [])
                    }
                }
                .padding(.top, 4)
                Text(state.stars == 0 ? "별점을 골라 주세요" : "\(state.stars)점")
                    .font(.plex(14, .semibold))
                    .foregroundStyle(state.stars == 0 ? Color.textSecondary : Color.ink)
                MissionTextBox(
                    text: $state.line, transform: TemplateInput.review, placeholder: "한 줄 후기를 남겨요",
                    counter: state.counter, label: "한 줄 후기", minHeight: 96
                )
                .padding(.top, 24)
            }
        }
    }
}

/// 밸런스 게임(TEXT_BALANCE): A/B 큰 카드 중 하나 + 이유(선택, 200자). 카드 문구는 서버 빈칸 optionA/B.
struct BalanceMissionView: View {
    let topic: String
    var spec = MissionSpec()
    var onSubmit: (SubmissionContent) async -> Bool = { _ in true }

    @State private var state = BalanceState()

    var body: some View {
        TemplateMissionScaffold(
            type: .write, topic: topic,
            guide: MissionFormat.templateGuideText(MissionTemplates.textBalance, spec: spec),
            hasDraft: state.hasDraft, canSubmit: state.canSubmit,
            onSubmit: { await onSubmit(.scalar(state.content)) }
        ) {
            VStack(alignment: .leading, spacing: 24) {
                VStack(spacing: 10) {
                    BalanceOption(letter: "A", option: spec.optionA, selected: state.choice == "A") { state.choice = "A" }
                    Text("VS")
                        .font(.plex(14, .bold))
                        .foregroundStyle(.textTertiary)
                        .frame(maxWidth: .infinity)
                    BalanceOption(letter: "B", option: spec.optionB, selected: state.choice == "B") { state.choice = "B" }
                }
                MissionTextBox(
                    text: $state.reason, transform: { TemplateInput.reason($0, limit: TemplateLimits.balanceReason) },
                    placeholder: "왜 골랐는지 적어도 좋아요", counter: state.counter, label: "이유 (선택)", minHeight: 120
                )
            }
        }
    }
}

/// 임시 UI: 고른 카드는 강조색 테두리와 체크로 표시해요.
private struct BalanceOption: View {
    let letter: String
    let option: String?
    let selected: Bool
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
        Button(action: action) {
            HStack(spacing: 14) {
                Text(letter)
                    .font(.plex(15, .bold))
                    .foregroundStyle(selected ? Color.onAccent : Color.ink)
                    .frame(width: 32, height: 32)
                    .background(selected ? Color.accent : Color.surface, in: Circle())
                Text(option.nonBlank ?? letter)
                    .font(.plex(18, .semibold))
                    .plexLineHeight(1.4, size: 18, weight: .semibold)
                    .foregroundStyle(.ink)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if selected {
                    HIconView(icon: .check, size: 20, color: .accentPressed)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 16)
            .frame(minHeight: 84)
            .background(selected ? Color.accentSubtle : Color.bg, in: shape)
            .overlay(shape.strokeBorder(selected ? Color.accent : Color.border, lineWidth: selected ? 2 : 1))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 슬라이더 3종(SLIDER_POSITION·SLIDER_TASTE·SLIDER_CONSENSUS): 양 끝 라벨 사이 0~100, 기본 50.
/// 다수결 예측은 "내 위치"와 "그룹 평균 예측" 슬라이더 두 개예요.
struct SliderMissionView: View {
    let topic: String
    var template = MissionTemplates.sliderPosition
    var spec = MissionSpec()
    var onSubmit: (SubmissionContent) async -> Bool = { _ in true }

    @State private var state = SliderState()

    private var consensus: Bool { template == MissionTemplates.sliderConsensus }

    var body: some View {
        TemplateMissionScaffold(
            type: .slider, topic: topic,
            guide: MissionFormat.templateGuideText(template, spec: spec),
            hasDraft: state.touched, canSubmit: true,
            onSubmit: {
                var answer = state
                answer.consensus = consensus
                return await onSubmit(.scalar(answer.content))
            }
        ) {
            VStack(alignment: .leading, spacing: 16) {
                if let question = spec.sliderQuestion.nonBlank, question != topic {
                    Text(question)
                        .font(.plex(16, .semibold))
                        .foregroundStyle(.ink)
                        .padding(.bottom, -4)
                }
                SliderInput(label: consensus ? "내 위치" : nil, value: state.number, spec: spec) {
                    state.number = $0
                    state.touched = true
                }
                if consensus {
                    SliderInput(label: "우리 그룹 평균은 어디쯤일까?", value: state.guess, spec: spec) {
                        state.guess = $0
                        state.touched = true
                    }
                }
            }
        }
    }
}

/// 임시 UI: 기본 슬라이더를 디자인 토큰 색으로 맞췄어요 (Figma 확정 시 교체).
private struct SliderInput: View {
    let label: String?
    let value: Int
    let spec: MissionSpec
    let onChange: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(label ?? "")
                    .font(.fieldLabel)
                    .foregroundStyle(.textSecondary)
                Spacer(minLength: 12)
                Text("\(value)")
                    .font(.plex(26, .bold))
                    .monospacedDigit()
                    .foregroundStyle(.ink)
            }
            Slider(
                value: Binding(get: { Double(value) }, set: { onChange(TemplateInput.sliderValue($0)) }),
                in: 0...Double(TemplateLimits.sliderMax), step: 1
            )
            .tint(.accent)
            .accessibilityLabel([label, spec.left.map { "왼쪽 \($0)" }, spec.right.map { "오른쪽 \($0)" }].compactMap { $0 }.joined(separator: ", "))
            HSliderEndLabels(left: spec.left, right: spec.right)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
    }
}

// MARK: - 공통

/// 새 틀 수행 화면의 공통 뼈대: 닫기 헤더, 제목·안내, 입력, 올리기 버튼
struct TemplateMissionScaffold<Content: View>: View {
    let type: MissionType
    let topic: String
    let guide: String?
    let hasDraft: Bool
    let canSubmit: Bool
    let onSubmit: () async -> Bool
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 0) {
            MissionHeader(hasDraft: hasDraft)
                .padding(.horizontal, HLayout.screenPaddingCompact)

            HCTAScaffold(horizontalPadding: HLayout.screenPaddingCompact, bottomInset: 32) {
                VStack(alignment: .leading, spacing: 0) {
                    MissionTitle(type: type, topic: topic)
                    if let guide {
                        Text(guide)
                            .font(.caption13)
                            .foregroundStyle(.textSecondary)
                            .padding(.top, 6)
                    }
                    content.padding(.top, 24)
                }
                .padding(.top, 16)
            } cta: {
                SubmitButton(enabled: canSubmit, onSubmit: onSubmit)
            }
        }
        .background(.bg)
    }
}

/// 올리는 동안 다시 누를 수 없고, 실패하면 다시 누를 수 있어요. 입력은 화면에 그대로 남아요.
struct SubmitButton: View {
    let enabled: Bool
    let onSubmit: () async -> Bool

    @State private var submitting = false

    var body: some View {
        HButton(submitting ? "올리는 중..." : "올리기") {
            guard !submitting else { return }
            submitting = true
            Task {
                if await !onSubmit() { submitting = false }
            }
        }
        .disabled(!enabled || submitting)
    }
}

/// 새 틀의 글 입력 칸. 한도·필터는 `transform` 으로 입력할 때마다 적용해요 (`TemplateInputRules`).
struct MissionTextBox: View {
    @Binding var text: String
    let transform: (String) -> String
    let placeholder: String
    let counter: String
    var label: String? = nil
    var singleLine = false
    var minHeight: CGFloat = 54
    var font: Font = .plex(16)

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let label {
                Text(label)
                    .font(.fieldLabel)
                    .foregroundStyle(.textSecondary)
            }
            let shape = RoundedRectangle(cornerRadius: HRadius.input, style: .continuous)
            Group {
                if singleLine {
                    TextField("", text: $text, prompt: Text(placeholder).foregroundStyle(.textTertiary))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } else {
                    TextField("", text: $text, prompt: Text(placeholder).foregroundStyle(.textTertiary), axis: .vertical)
                }
            }
            .font(font)
            .foregroundStyle(.ink)
            .tint(.ink)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: minHeight, alignment: singleLine ? .leading : .topLeading)
            .overlay(shape.strokeBorder(.border, lineWidth: 1))
            .contentShape(shape)

            Text(counter)
                .font(.caption13)
                .monospacedDigit()
                .foregroundStyle(.textSecondary)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .onChange(of: text) { _, newValue in
            let next = transform(newValue)
            if next != newValue { text = next }
        }
    }
}

#Preview("한 단어") {
    OneWordMissionView(topic: "오늘을 한 단어로")
}

#Preview("밸런스") {
    BalanceMissionView(topic: "평생 하나만 먹는다면?", spec: MissionSpec(optionA: "짜장면", optionB: "짬뽕"))
}

#Preview("다수결 예측") {
    SliderMissionView(topic: "우리 그룹은 아침형일까?", template: MissionTemplates.sliderConsensus,
                      spec: MissionSpec(left: "아침형", right: "저녁형"))
}
