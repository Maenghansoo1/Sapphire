import SwiftUI

/// 00 스플래시 — 흰 바탕 가운데에 심볼(48) + 워드마크(38)를 가로로 나란히, 간격 10 (Figma `00 스플래시` 대비 75% 축소)
struct SplashView: View {
    var body: some View {
        HStack(spacing: 10) {
            HLogoSymbol(size: 48)
            HWordmark(size: 38)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.bg)
    }
}

#Preview {
    SplashView()
}
