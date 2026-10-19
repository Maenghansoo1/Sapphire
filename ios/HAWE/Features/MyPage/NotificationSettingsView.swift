import SwiftUI

/// 17-1 알림 설정 (Figma 1:1614)
/// 전체 알림 / 오늘의 주제 도착 / 콕 찌르기 / 내 작품에 댓글. 전체 OFF 면 나머지 비활성.
struct NotificationSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session

    var body: some View {
        @Bindable var session = session

        VStack(spacing: 0) {
            HNavBar("알림 설정", leading: .back { dismiss() })

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // 전체 알림 — surface · 모서리 16 · 좌우 16 · 위아래 14
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("전체 알림")
                                .font(.plex(16, .bold))
                                .foregroundStyle(.ink)
                            Text("끄면 모든 푸시 알림이 오지 않아요")
                                .font(.caption12)
                                .foregroundStyle(.textSecondary)
                        }
                        Spacer(minLength: 0)
                        HToggle(isOn: $session.notificationPrefs.all, accessibilityLabel: "전체 알림")
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.button, style: .continuous))
                    .padding(.top, 8)

                    sectionLabel("미션")
                    row("오늘의 주제 도착", subtitle: "그룹마다 정한 시간에 알려드려요", isOn: $session.notificationPrefs.topic)

                    sectionLabel("친구 활동")
                    row("콕 찌르기", isOn: $session.notificationPrefs.poke)
                    row("내 작품에 댓글", isOn: $session.notificationPrefs.comment)

                    Text("그룹별 알림은 각 그룹의 '그룹 정보'에서 따로 끌 수 있어요.")
                        .font(.caption12)
                        .foregroundStyle(.textSecondary)
                        .padding(.top, 16)
                }
                .padding(.bottom, 32)
                // TODO: 서버 연결 시 notification_prefs 저장
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func sectionLabel(_ title: String) -> some View {
        Text(title)
            .font(.plex(13, .semibold))
            .foregroundStyle(.textSecondary)
            .padding(.top, 22)
            .padding(.bottom, 4)
    }

    /// 위아래 10 · 아래 구분선 · 15 (+ 설명 12) + 토글. 전체 알림이 꺼지면 흐려지고 못 바꿔요.
    private func row(_ title: String, subtitle: String? = nil, isOn: Binding<Bool>) -> some View {
        let enabled = session.notificationPrefs.all
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.body15)
                    .foregroundStyle(enabled ? Color.ink : Color.textTertiary)
                if let subtitle {
                    Text(subtitle)
                        .font(.caption12)
                        .foregroundStyle(.textSecondary)
                }
            }
            Spacer(minLength: 0)
            HToggle(isOn: isOn, accessibilityLabel: title)
        }
        .padding(.vertical, 10)
        .overlay(alignment: .bottom) {
            Rectangle().fill(.line).frame(height: 1)
        }
        // 토글은 HToggleStyle 이 비활성일 때 스스로 흐려져요.
        .disabled(!enabled)
    }
}

#Preview {
    NavigationStack {
        NotificationSettingsView()
    }
    .environment(AppSession(state: .signedIn))
}
