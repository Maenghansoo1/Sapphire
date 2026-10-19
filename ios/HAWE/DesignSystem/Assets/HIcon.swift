import SwiftUI

/// Figma `icon/*` 컴포넌트 (Assets.xcassets/Icons, template 렌더링이라 색을 입힐 수 있어요)
/// heartFill 은 Figma icon/heart 를 채운 모양이에요 (좋아요 누른 상태, Figma 에 없음).
enum HIcon: String, CaseIterable {
    case bell, calendar, camera, check, checkSmall = "check-small"
    case chevronLeft = "chevron-left", chevronRight = "chevron-right"
    case close, comment, copy, flash, flip, heart, heartFill = "heart-fill", image, lock, more
    case pencil, person, plus, redo, text, undo
    // 임시 이미지: Figma 디자인이 나오면 같은 이름의 SVG 만 바꿔요 (Android `*_TEMP` 와 같은 이름)
    case pokeTemp = "poke-temp"
    case aiGuessTemp = "ai-guess-temp", insightTemp = "insight-temp"
}

extension Image {
    init(_ icon: HIcon) {
        self.init("icon-\(icon.rawValue)")
    }
}

/// 아이콘을 Figma 크기 그대로 그려요. 기본 색은 ink.
struct HIconView: View {
    let icon: HIcon
    var size: CGFloat = 24
    var color: Color = .ink

    var body: some View {
        Image(icon)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .foregroundStyle(color)
    }
}
