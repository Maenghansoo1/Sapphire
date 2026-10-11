import SwiftUI

/// Figma `ListRow` — 라벨(15) + 값(14 textSecondary) + 꺾쇠(16), 아래 구분선. 기본 높이 52.
struct HListRow: View {
    private let label: String
    private let value: String?
    private let height: CGFloat
    private let showsDivider: Bool
    private let showsChevron: Bool
    private let action: () -> Void

    init(_ label: String, value: String? = nil, height: CGFloat = 52, showsDivider: Bool = true, showsChevron: Bool = true, action: @escaping () -> Void) {
        self.label = label
        self.value = value
        self.height = height
        self.showsDivider = showsDivider
        self.showsChevron = showsChevron
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(label)
                    .font(.body15)
                    .foregroundStyle(.ink)
                Spacer(minLength: 12)
                if let value {
                    Text(value)
                        .font(.plex(14))
                        .foregroundStyle(.textSecondary)
                }
                if showsChevron {
                    HIconView(icon: .chevronRight, size: 16, color: .textTertiary)
                }
            }
            .lineLimit(1)
            .frame(height: height)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .overlay(alignment: .bottom) {
            if showsDivider {
                Rectangle().fill(.line).frame(height: 1)
            }
        }
    }
}

#Preview {
    VStack(spacing: 0) {
        HListRow("주제 도착 시간", value: "오전 9:00") {}
        HListRow("알림", value: "켜짐") {}
        HListRow("공지사항", height: 54) {}
    }
    .padding(.horizontal, 20)
}
