import SwiftUI

// 모서리·여백 (CLAUDE.md §3-4, Figma 화면 값)
enum HRadius {
    /// 입력창
    static let input: CGFloat = 14
    /// 버튼
    static let button: CGFloat = 16
    /// 카드
    static let card: CGFloat = 20
    /// 큰 카드·알림창
    static let largeCard: CGFloat = 24
    /// 체크박스
    static let checkbox: CGFloat = 6
}

enum HLayout {
    /// 화면 좌우 여백 (가입·생성 화면 24, 목록·설정 화면 20)
    static let screenPadding: CGFloat = 24
    static let screenPaddingCompact: CGFloat = 20
    /// 상단 헤더 높이
    static let headerHeight: CGFloat = 52
    /// 하단 고정 CTA 높이
    static let ctaHeight: CGFloat = 56
    /// 최소 터치 영역
    static let minTouch: CGFloat = 44
}
