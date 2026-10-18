import SwiftUI

/// 00 스플래시 — 흰 바탕 가운데에 심볼(64) + 워드마크(50)를 가로로 나란히, 간격 14 (Figma `00 스플래시`)
struct SplashView: View {
    var body: some View {
        HStack(spacing: 14) {
            HLogoSymbol(size: 64)
            HWordmark(size: 50)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.bg)
    }
}

#Preview {
    SplashView()
}
