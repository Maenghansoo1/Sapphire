import SwiftUI

/// Figma `icon/*` 컴포넌트 (Assets.xcassets/Icons, template 렌더링이라 색을 입힐 수 있어요)
/// 아직 받지 않은 아이콘(heart, comment)은 해당 화면 단계에서 추가해요.
enum HIcon: String, CaseIterable {
    case calendar, camera, check, checkSmall = "check-small"
    case chevronLeft = "chevron-left", chevronRight = "chevron-right"
    case close, copy, flash, flip, image, lock, mic, more
    case pause, pencil, person, play, plus, redo, text, undo
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
