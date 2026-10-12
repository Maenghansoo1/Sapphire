import SwiftUI

/// 마이페이지 안쪽 화면들
enum MyPageRoute: Hashable {
    case home
    case profileEdit
    case notifications
    case userInfo
    case changePassword
    case appInfo
    case licenses
    case withdraw
}

/// MainRoute.myPage 로 들어온 화면을 골라 보여줘요.
struct MyPageDestination: View {
    let route: MyPageRoute
    @Binding var path: [MainRoute]

    var body: some View {
        switch route {
        case .home: MyPageView(path: $path)
        case .profileEdit: ProfileEditView()
        case .notifications: NotificationSettingsView()
        case .userInfo: UserInfoView(path: $path)
        case .changePassword: ChangePasswordView()
        case .appInfo: AppInfoView(path: $path)
        case .licenses: LicensesView()
        case .withdraw: WithdrawView()
        }
    }
}

/// 17 마이페이지 (Figma 1:1561) + 17-4 로그아웃 확인 (Figma 1:1722)
/// 기본 프로필, 활동 요약(완료한 미션·연속 참여일·참여 그룹), 유형별 횟수, 메뉴, 로그아웃·회원 탈퇴(빨간 글씨)
struct MyPageView: View {
    @Binding var path: [MainRoute]

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @Environment(DataStore.self) private var store
    @State private var openedLink: WebLink?
    @State private var confirmsLogout = false
    @State private var toast: String?

    private var account: MyAccount { session.account }
    /// 활동 요약 — 서버 통계 API 가 생기면 다시 보여요.
    private var activity: ActivityStats { store.stats }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("마이페이지", leading: .back { dismiss() })

            ScrollView {
                VStack(spacing: 0) {
                    profile.padding(.top, 12)
                    // 활동 요약(stats · byType)은 서버 통계 API 가 생기기 전까지 숨겨요.
                    menu.padding(.top, 18)
                }
            }
            .scrollBounceBehavior(.basedOnSize)

            Text("버전 \(AppInfo.version)")
                .font(.caption12)
                .foregroundStyle(.textSecondary)
                .padding(.top, 12)
                .padding(.bottom, 24)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .safariSheet($openedLink)
        .overlay {
            if confirmsLogout {
                HAlert(
                    title: "로그아웃할까요?",
                    message: "다시 로그인하면 그룹과 기록은\n그대로 남아 있어요.",
                    confirmTitle: "로그아웃",
                    onCancel: { confirmsLogout = false },
                    onConfirm: {
                        confirmsLogout = false
                        session.signOut()
                    }
                )
            }
        }
        .animation(.easeOut(duration: 0.2), value: confirmsLogout)
        .hToast($toast)
    }

    /// 원 76 · 캐릭터 60 + 이름 22 Bold + 이메일·로그인 방법 13 (줄 높이 1.45) + 프로필 편집
    private var profile: some View {
        HStack(spacing: 16) {
            HAvatar(content: account.profile.avatar, size: 76, characterScale: 60 / 76)
            VStack(alignment: .leading, spacing: 4) {
                Text(account.profile.nickname)
                    .font(.plex(22, .bold))
                    .foregroundStyle(.ink)
                    .lineLimit(1)
                Text(accountLines.joined(separator: "\n"))
                    .font(.caption13)
                    .plexLineHeight(1.45, size: 13)
                    .foregroundStyle(.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // 프로필 수정 API 가 생기면 ProfileEditView(.profileEdit)로 보내요.
            Button { toast = ComingSoon.message } label: {
                Text("프로필 편집")
                    .font(.plex(13, .semibold))
                    .foregroundStyle(.ink)
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .overlay(Capsule().strokeBorder(.border, lineWidth: 1))
                    .contentShape(Rectangle().inset(by: -4))
            }
            .buttonStyle(.plain)
        }
    }

    /// 이메일(있으면) + 간편 로그인으로 가입했을 때만 "카카오 로그인" 같은 줄
    private var accountLines: [String] {
        var lines: [String] = []
        if let email = account.email, !email.isEmpty { lines.append(email) }
        if account.signUpProvider != .email { lines.append("\(account.signUpProvider.displayName) 로그인") }
        return lines
    }

    /// surface · 모서리 18 · 위아래 16, 세 칸 사이 세로 구분선(#E5E5EA)
    private var stats: some View {
        HStack(spacing: 0) {
            statCell("\(activity.completedMissions)", "완료한 미션")
            Rectangle().fill(.disabled).frame(width: 1)
            statCell("\(activity.streakDays)일", "연속 참여")
            Rectangle().fill(.disabled).frame(width: 1)
            statCell("\(activity.groupCount)개", "참여 그룹")
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.vertical, 16)
        .background(.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func statCell(_ value: String, _ label: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.plex(20, .bold))
                .foregroundStyle(.ink)
            Text(label)
                .font(.caption12)
                .foregroundStyle(.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    /// 유형별 횟수 — 네 칸 · 간격 8 · 테두리 line · 모서리 14
    private var byType: some View {
        HStack(spacing: 8) {
            ForEach(MissionType.allCases) { type in
                VStack(spacing: 2) {
                    Text(type.displayName)
                        .font(.caption12)
                        .foregroundStyle(.textSecondary)
                    Text("\(activity.byType[type] ?? 0)")
                        .font(.plex(15, .bold))
                        .foregroundStyle(.ink)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .overlay(
                    RoundedRectangle(cornerRadius: HRadius.input, style: .continuous)
                        .strokeBorder(.line, lineWidth: 1)
                )
                .accessibilityElement(children: .combine)
            }
        }
    }

    /// 목록 줄 48
    private var menu: some View {
        VStack(spacing: 0) {
            HListRow("알림 설정", height: 48) { path.append(.myPage(.notifications)) }
            HListRow("유저 정보", height: 48) { path.append(.myPage(.userInfo)) }
            HListRow("앱 정보", height: 48) { path.append(.myPage(.appInfo)) }
            HListRow("이용약관", height: 48) { openedLink = WebLink(url: AppLinks.termsOfService) }
            HListRow("개인정보 처리방침", height: 48) { openedLink = WebLink(url: AppLinks.privacyPolicy) }
            dangerRow("로그아웃", showsDivider: true) { confirmsLogout = true }
            // 탈퇴 API 가 생기면 WithdrawView(.withdraw)로 보내요.
            dangerRow("회원 탈퇴", showsDivider: false) { toast = ComingSoon.message }
        }
    }

    private func dangerRow(_ title: String, showsDivider: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.body15)
                .foregroundStyle(.danger)
                .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
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

/// 앱 버전 등 고정 정보
enum AppInfo {
    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.0"
    }
}

#Preview {
    NavigationStack {
        MyPageView(path: .constant([]))
    }
    .environment(AppSession(state: .signedIn))
    .environment(DataStore.preview())
    .environment(Services.preview)
}
