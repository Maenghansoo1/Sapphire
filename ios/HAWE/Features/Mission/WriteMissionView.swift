import SwiftUI

/// 08 미션 수행 · 글 (Figma 1:658) — 최대 300자(빈칸 채우기는 100자), 글자 수 표시.
/// 삼행시(TEXT_ACROSTIC)는 정확히 3줄, 줄마다 제시어 글자로 시작해야 올릴 수 있어요.
struct WriteMissionView: View {
    let topic: String
    var template = ""
    var spec = MissionSpec()
    var onSubmit: (SubmissionContent) async -> Bool = { _ in true }

    static let maxLength = 300
    /// Figma 줄 높이 1.7 (17pt → 28.9pt)
    private static let lineSpacing: CGFloat = {
        let natural = UIFont(name: PlexWeight.regular.postScriptName, size: 17)?.lineHeight ?? 22
        return max(0, 17 * 1.7 - natural)
    }()

    @State private var text = ""
    @State private var isSubmitting = false
    @FocusState private var isFocused: Bool

    private var rules: WriteMissionRules { WriteMissionRules(template: template, spec: spec) }
    private var trimmed: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        VStack(spacing: 0) {
            MissionHeader(hasDraft: !trimmed.isEmpty) {
                // Swift 의 String.count 는 이모지·한글 조합도 한 글자로 세요 (서버와 같은 기준).
                Text("\(text.count) / \(rules.maxLength)")
                    .font(.caption13)
                    .monospacedDigit()
                    .foregroundStyle(.textSecondary)
            }
            .padding(.horizontal, HLayout.screenPaddingCompact)

            HCTAScaffold(horizontalPadding: HLayout.screenPaddingCompact, bottomInset: 32) {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        MissionTitle(type: .write, topic: topic)
                        if let word = rules.acrosticWord {
                            Text("각 줄을 \(word.map { "‘\($0)’" }.joined(separator: " "))로 시작해요")
                                .font(.caption13)
                                .foregroundStyle(.textSecondary)
                        }
                    }
                    editor
                }
                .padding(.top, 16)
            } cta: {
                HButton(isSubmitting ? "올리는 중..." : "올리기", action: submit)
                    .disabled(!rules.canSubmit(text) || isSubmitting)
            }
        }
        .background(.bg)
        .onChange(of: text) { _, newValue in
            if newValue.count > rules.maxLength {
                text = String(newValue.prefix(rules.maxLength))
            }
        }
    }

    private func submit() {
        guard rules.canSubmit(text), !isSubmitting else { return }
        isSubmitting = true
        let body = trimmed
        Task {
            if await !onSubmit(.text(body)) { isSubmitting = false }
        }
    }

    /// 테두리 line · 모서리 20 · 안쪽 20 · 높이 360 · 17pt 줄 높이 1.7
    private var editor: some View {
        ZStack(alignment: .topLeading) {
            TextEditor(text: $text)
                .font(.plex(17))
                .lineSpacing(Self.lineSpacing)
                .foregroundStyle(.ink)
                .tint(.ink)
                .scrollContentBackground(.hidden)
                .focused($isFocused)
                .disabled(isSubmitting)
                // TextEditor 기본 안쪽 여백(위 8 · 좌우 5)을 빼서 Figma 안쪽 20 에 맞춰요.
                .padding(.horizontal, 15)
                .padding(.vertical, 12)

            if text.isEmpty {
                Text("여기에 적어보세요")
                    .font(.plex(17))
                    .foregroundStyle(.textTertiary)
                    .padding(20)
                    .allowsHitTesting(false)
            }
        }
        .frame(height: 360)
        .overlay(
            RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
        .contentShape(Rectangle())
        .onTapGesture { isFocused = true }
    }
}

/// 글 미션 규칙 — 서버 검사(MISSIONS_API.md)와 같게 맞춰요.
struct WriteMissionRules {
    var maxLength: Int
    /// 삼행시 제시어
    var acrosticWord: String?

    init(template: String, spec: MissionSpec) {
        maxLength = template == "TEXT_FILL_BLANK" ? 100 : WriteMissionView.maxLength
        acrosticWord = template == "TEXT_ACROSTIC" ? spec.word : nil
    }

    func canSubmit(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return !trimmed.isEmpty && text.count <= maxLength && acrosticError(text) == nil
    }

    /// 3줄이 아니거나 줄 첫 글자가 제시어와 다르면 안내 문구
    func acrosticError(_ text: String) -> String? {
        guard let word = acrosticWord else { return nil }
        var lines = text.components(separatedBy: .newlines)
        while let last = lines.last, last.trimmingCharacters(in: .whitespaces).isEmpty { lines.removeLast() }
        guard lines.count == 3 else { return "3줄로 써 주세요" }
        let letters = Array(word)
        for index in 0..<3 {
            let line = lines[index].trimmingCharacters(in: .whitespaces)
            let expected = index < letters.count ? letters[index] : nil
            if line.first == nil || line.first != expected {
                return "‘\(expected.map(String.init) ?? "")’로 시작해 주세요"
            }
        }
        return nil
    }
}

#Preview {
    WriteMissionView(topic: "너에게 해주고 싶은 말 한 줄")
}

#Preview("삼행시") {
    WriteMissionView(topic: "제시어로 삼행시를 지어 보세요", template: "TEXT_ACROSTIC", spec: MissionSpec(word: "하루살"))
}
