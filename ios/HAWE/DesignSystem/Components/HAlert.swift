import SwiftUI

/// Figma 17-4 알림창 — 화면 어둡게(ink 45%) + 흰 카드(모서리 24 · 좌우 28 여백)
/// 제목 20 Bold, 내용 14 (줄 높이 1.6), 버튼 두 개(높이 50) — 취소는 gray, 확정은 danger
struct HAlert: View {
    let title: String
    let message: String
    var cancelTitle = "취소"
    let confirmTitle: String
    var confirmKind: HButton.Kind = .danger
    let onCancel: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        ZStack {
            Color.ink.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture(perform: onCancel)

            VStack(spacing: 10) {
                Text(title)
                    .font(.plex(20, .bold))
                    .foregroundStyle(.ink)
                Text(message)
                    .font(.plex(14))
                    .plexLineHeight(1.6, size: 14)
                    .foregroundStyle(.textSecondary)
                HStack(spacing: 8) {
                    HButton(cancelTitle, kind: .gray, height: 50, action: onCancel)
                    HButton(confirmTitle, kind: confirmKind, height: 50, action: onConfirm)
                }
                .padding(.top, 14)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.top, 28)
            .padding(.bottom, 20)
            .padding(.horizontal, 22)
            .background(.bg, in: RoundedRectangle(cornerRadius: HRadius.largeCard, style: .continuous))
            .padding(.horizontal, 28)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
        }
        .transition(.opacity)
    }
}

#Preview {
    HAlert(
        title: "로그아웃할까요?",
        message: "다시 로그인하면 그룹과 기록은\n그대로 남아 있어요.",
        confirmTitle: "로그아웃",
        onCancel: {},
        onConfirm: {}
    )
}
