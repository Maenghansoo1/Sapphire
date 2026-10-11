import SwiftUI

// Figma 변수 `color/*` 와 CLAUDE.md §3-2 토큰.
// 화면은 화이트 톤, 색은 CTA·로고에만. 선택 상태는 accent 가 아니라 ink 로 표시해요.
//
// ShapeStyle(Self == Color) 에 정의해서 `Color.ink` 와 `.foregroundStyle(.ink)` 둘 다 쓸 수 있어요.
extension ShapeStyle where Self == Color {
    static var bg: Color { Color(hex: 0xFFFFFF) }
    static var surface: Color { Color(hex: 0xF6F6F7) }
    static var surfaceStrong: Color { Color(hex: 0xF1F1F3) }
    static var line: Color { Color(hex: 0xECECEE) }
    static var border: Color { Color(hex: 0xE2E2E5) }
    static var ink: Color { Color(hex: 0x111111) }
    static var textSecondary: Color { Color(hex: 0x6E6E73) }
    static var textTertiary: Color { Color(hex: 0x8E8E93) }
    /// Figma 화면 CTA·배지·워드마크 색 (사용자 결정 2026-10-12, 변수 color/accent #1E5C91 대신)
    static var accent: Color { Color(hex: 0x164F7D) }
    /// Figma 에 눌림 색이 없어서 accent 를 한 단계 어둡게 잡았어요.
    static var accentPressed: Color { Color(hex: 0x103D61) }
    static var accentSubtle: Color { Color(hex: 0xEAF1F7) }
    static var onAccent: Color { Color(hex: 0xFFFFFF) }
    static var danger: Color { Color(hex: 0xC92A2A) }
    static var disabled: Color { Color(hex: 0xE5E5EA) }

    // Figma 컴포넌트에 변수 없이 들어간 값 — 토큰으로 묶어 둬요.
    /// Checkbox(Off) 테두리
    static var controlBorder: Color { Color(hex: 0xC7C7CC) }
    /// Toggle(Off) 트랙
    static var controlOff: Color { Color(hex: 0xD1D1D6) }

    // 카카오 로그인 버튼 — 카카오 디자인 가이드 고정 색
    static var kakaoContainer: Color { Color(hex: 0xFEE500) }
    static var kakaoLabel: Color { Color(hex: 0x191919) }
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}
