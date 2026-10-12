import Foundation

/// 로그인 방법 (CLAUDE.md §2)
enum LoginProvider: String, CaseIterable, Identifiable, Codable {
    case email, kakao, apple, google

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .email: "이메일"
        case .kakao: "카카오"
        case .apple: "Apple"
        case .google: "Google"
        }
    }
}

/// 알림 설정 (DB `notification_prefs`, CLAUDE.md §7)
struct NotificationPrefs: Hashable, Codable {
    var all = true
    var topic = true
    var poke = true
    /// 기본 OFF (CLAUDE.md §7)
    var friendDone = false
    var comment = true
}

/// 17 마이페이지 활동 요약
struct ActivityStats: Hashable {
    var completedMissions: Int
    var streakDays: Int
    var groupCount: Int
    var byType: [MissionType: Int]
}

/// 내 계정 — 기본 프로필, 로그인 연결 (활동 요약은 서버 `/api/me/stats` 값을 DataStore.stats 에 둬요)
struct MyAccount: Hashable {
    var profile: Profile
    var email: String?
    var joinedAt: Date
    /// 처음 가입한 방법
    var signUpProvider: LoginProvider
    var linkedProviders: Set<LoginProvider>
    /// 서버 userId — 그룹 멤버 중 '나'를 찾을 때 써요.
    var userID: Int = 0

    /// 비밀번호 변경은 이메일 가입자만 (CLAUDE.md §4 17-2)
    var canChangePassword: Bool { linkedProviders.contains(.email) }
}

extension MyAccount {
    /// Figma 17 · 17-2 화면 값
    static let mock = MyAccount(
        profile: .mockMine,
        email: "sky@email.com",
        joinedAt: Calendar.current.date(from: DateComponents(year: 2026, month: 6, day: 2)) ?? .now,
        signUpProvider: .kakao,
        linkedProviders: [.kakao]
    )
}
