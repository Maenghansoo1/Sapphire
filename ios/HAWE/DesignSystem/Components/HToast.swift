import SwiftUI

/// 잠깐 떴다 사라지는 안내 — Figma 에 없어서 ink 알약 모양으로 정했어요.
struct HToast: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.plex(14, .semibold))
            .foregroundStyle(.onAccent)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .frame(minHeight: 44)
            .background(Color.ink.opacity(0.9), in: Capsule())
            .padding(.horizontal, 20)
            .accessibilityAddTraits(.updatesFrequently)
    }
}

extension View {
    /// 화면 아래에 토스트를 띄우고 1.6초 뒤 지워요. `text` 에 문구를 넣으면 떠요.
    func hToast(_ text: Binding<String?>, bottomPadding: CGFloat = 24) -> some View {
        modifier(HToastModifier(text: text, bottomPadding: bottomPadding))
    }
}

private struct HToastModifier: ViewModifier {
    @Binding var text: String?
    var bottomPadding: CGFloat

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let text {
                    HToast(text: text)
                        .padding(.bottom, bottomPadding)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .task(id: text) {
                            try? await Task.sleep(for: .seconds(1.6))
                            if self.text == text { self.text = nil }
                        }
                }
            }
            .animation(.snappy, value: text)
    }
}
