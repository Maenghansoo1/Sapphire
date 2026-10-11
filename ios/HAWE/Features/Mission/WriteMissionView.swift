import SwiftUI

/// 08 미션 수행 · 글 (Figma 1:658) — 최대 300자, 글자 수 표시
struct WriteMissionView: View {
    let topic: String
    var onSubmit: () -> Void = {}

    static let maxLength = 300
    /// Figma 줄 높이 1.7 (17pt → 28.9pt)
    private static let lineSpacing: CGFloat = {
        let natural = UIFont(name: PlexWeight.regular.postScriptName, size: 17)?.lineHeight ?? 22
        return max(0, 17 * 1.7 - natural)
    }()

    @State private var text = ""
    @FocusState private var isFocused: Bool

    private var trimmed: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        VStack(spacing: 0) {
            MissionHeader(hasDraft: !trimmed.isEmpty) {
                Text("\(text.count) / \(Self.maxLength)")
                    .font(.caption13)
                    .monospacedDigit()
                    .foregroundStyle(.textSecondary)
            }
            .padding(.horizontal, HLayout.screenPaddingCompact)

            HCTAScaffold(horizontalPadding: HLayout.screenPaddingCompact, bottomInset: 32) {
                VStack(alignment: .leading, spacing: 16) {
                    MissionTitle(type: .write, topic: topic)
                    editor
                }
                .padding(.top, 16)
            } cta: {
                // TODO: 서버 연결 시 submissions.text 저장
                HButton("올리기", action: onSubmit)
                    .disabled(trimmed.isEmpty)
            }
        }
        .background(.bg)
        .onChange(of: text) { _, newValue in
            if newValue.count > Self.maxLength {
                text = String(newValue.prefix(Self.maxLength))
            }
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

#Preview {
    WriteMissionView(topic: "너에게 해주고 싶은 말 한 줄")
}
