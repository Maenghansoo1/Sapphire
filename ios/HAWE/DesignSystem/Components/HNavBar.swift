import SwiftUI

/// Figma `Header` — 왼쪽 44×44(뒤로/닫기) + 가운데 제목(17 Bold) + 오른쪽 44 영역. 높이 52.
struct HNavBar<Trailing: View>: View {
    enum Leading {
        case none
        case back(() -> Void)
        case close(() -> Void)
    }

    private let title: String?
    private let titleFont: Font
    private let leading: Leading
    private let trailing: Trailing

    init(_ title: String? = nil, titleFont: Font = .navTitle, leading: Leading = .none, @ViewBuilder trailing: () -> Trailing) {
        self.title = title
        self.titleFont = titleFont
        self.leading = leading
        self.trailing = trailing()
    }

    var body: some View {
        ZStack {
            if let title {
                Text(title)
                    .font(titleFont)
                    .foregroundStyle(.ink)
                    .lineLimit(1)
                    .padding(.horizontal, HLayout.minTouch + 8)
            }
            HStack(spacing: 0) {
                leadingButton
                Spacer(minLength: 0)
                trailing
                    .frame(minWidth: HLayout.minTouch, minHeight: HLayout.minTouch, alignment: .trailing)
            }
        }
        .frame(height: HLayout.headerHeight)
    }

    @ViewBuilder
    private var leadingButton: some View {
        switch leading {
        case .none:
            Color.clear.frame(width: HLayout.minTouch, height: HLayout.minTouch)
        case .back(let action):
            iconButton("뒤로", action: action) { HIconView(icon: .chevronLeft) }
        case .close(let action):
            iconButton("닫기", action: action) { HIconView(icon: .close, size: 22) }
        }
    }

    private func iconButton(_ label: String, action: @escaping () -> Void, @ViewBuilder icon: () -> some View) -> some View {
        Button(action: action) {
            icon().frame(width: HLayout.minTouch, height: HLayout.minTouch, alignment: .leading)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

extension HNavBar where Trailing == EmptyView {
    init(_ title: String? = nil, titleFont: Font = .navTitle, leading: Leading = .none) {
        self.init(title, titleFont: titleFont, leading: leading) { EmptyView() }
    }
}

/// 헤더 오른쪽에 들어가는 단계 표시 (예: "1 / 2")
struct HNavStep: View {
    let text: String
    var body: some View {
        Text(text).font(.plex(14, .semibold)).foregroundStyle(.textSecondary)
    }
}

#Preview {
    VStack(spacing: 0) {
        HNavBar("그룹 정보", leading: .back {})
        HNavBar(leading: .back {}) { HNavStep(text: "1 / 2") }
        HNavBar("알림 설정", leading: .close {})
    }
    .padding(.horizontal, 20)
}
