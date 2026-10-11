import SwiftUI

/// '오늘의 타일' 심볼 (Figma `logo/symbol`, `logo/symbol-small`)
/// 60pt 이하에서는 얼굴 없는 버전을 써요 (CLAUDE.md §3-8).
struct HLogoSymbol: View {
    var size: CGFloat = 30

    var body: some View {
        Image(size > 60 ? "logo-symbol" : "logo-symbol-small")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// 워드마크 "HA'WE" — Fredoka SemiBold, 아포스트로피만 accent
struct HWordmark: View {
    var size: CGFloat = 24

    var body: some View {
        Text("HA\(Text("'").foregroundStyle(.accent))WE")
            .font(.wordmark(size))
            .foregroundStyle(.ink)
            .accessibilityLabel("HA'WE")
    }
}

/// 심볼 + 워드마크 (Figma `logo/lockup`: 심볼 30, 워드마크 24, 간격 8)
struct HLogoLockup: View {
    var body: some View {
        HStack(spacing: 8) {
            HLogoSymbol(size: 30)
            HWordmark(size: 24)
        }
    }
}

#Preview {
    VStack(spacing: 24) {
        HLogoLockup()
        HStack(spacing: 24) {
            HLogoSymbol(size: 62)
            HLogoSymbol(size: 30)
        }
        HWordmark(size: 26)
    }
    .padding()
}
