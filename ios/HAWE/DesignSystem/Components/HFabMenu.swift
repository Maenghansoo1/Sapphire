import SwiftUI

/// 오른쪽 아래에 떠 있는 동그란 메뉴 버튼 (Figma 04 홈 · 메뉴 버튼 / 메뉴 열림)
/// 누르면 화면이 어두워지고 버튼 위로 메뉴가 떠요. 버튼은 + → × 로 바뀌어요.
/// 화면 전체를 덮는 overlay 로 붙여요: `.overlay { HFabMenu(isOpen: $open, items: …) }`
struct HFabMenu: View {
    struct Item: Identifiable {
        let id = UUID()
        let title: String
        let icon: HIcon
        let action: () -> Void
    }

    @Binding var isOpen: Bool
    let items: [Item]

    /// FAB 지름 56 · 메뉴 동그라미 48 · 메뉴 사이 16 · 라벨과 동그라미 사이 12
    private let fabSize: CGFloat = 56
    private let itemSize: CGFloat = 48

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            if isOpen {
                Color.ink.opacity(0.4)
                    .ignoresSafeArea()
                    .onTapGesture { toggle() }
                    .accessibilityHidden(true)
                    .transition(.opacity)
            }

            VStack(alignment: .trailing, spacing: 16) {
                if isOpen {
                    VStack(alignment: .trailing, spacing: 16) {
                        ForEach(items) { item in
                            menuRow(item)
                        }
                    }
                    .padding(.trailing, (fabSize - itemSize) / 2)
                    .transition(.offset(y: 16).combined(with: .opacity))
                }
                fab
            }
            .padding(.trailing, HLayout.screenPaddingCompact)
            .padding(.bottom, 8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
    }

    private var fab: some View {
        Button(action: toggle) {
            HIconView(icon: .plus, size: 24, color: .onAccent)
                .rotationEffect(.degrees(isOpen ? 45 : 0))
                .frame(width: fabSize, height: fabSize)
                .background(.accent, in: Circle())
                .shadow(color: Color(hex: 0x121A26, opacity: 0.18), radius: 10, y: 8)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isOpen ? "메뉴 닫기" : "메뉴 열기")
    }

    private func menuRow(_ item: Item) -> some View {
        Button {
            isOpen = false
            item.action()
        } label: {
            HStack(spacing: 12) {
                Text(item.title)
                    .font(.plex(15, .semibold))
                    .foregroundStyle(.ink)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 9)
                    .background(.bg, in: Capsule())
                    .shadow(color: Color(hex: 0x121A26, opacity: 0.10), radius: 6, y: 4)
                HIconView(icon: item.icon, size: 22)
                    .frame(width: itemSize, height: itemSize)
                    .background(.bg, in: Circle())
                    .shadow(color: Color(hex: 0x121A26, opacity: 0.10), radius: 6, y: 4)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.title)
    }

    private func toggle() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { isOpen.toggle() }
    }
}

#Preview {
    @Previewable @State var open = true
    Color.surface.ignoresSafeArea()
        .overlay {
            HFabMenu(isOpen: $open, items: [
                .init(title: "새 그룹 만들기", icon: .plus) {},
                .init(title: "초대 코드로 참여하기", icon: .enter) {},
                .init(title: "마이페이지", icon: .person) {},
            ])
        }
}
