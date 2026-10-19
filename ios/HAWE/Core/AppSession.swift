import Observation

/// 앱 전체 로그인 상태와 내 계정.
/// 토큰은 키체인(AuthRepository)에 있고, 앱을 켜면 저장된 세션으로 내 정보를 다시 받아요(restoring).
@Observable
final class AppSession {
    enum State: Equatable {
        case signedOut
        /// 그룹이 있으면 04 홈, 없으면 01 시작 — DataStore 의 그룹 수로 정해요 (CLAUDE.md §4 A1)
        case signedIn
        /// 저장된 세션으로 내 정보를 받는 중
        case restoring
    }

    private(set) var state: State
    var account: MyAccount
    var notificationPrefs = NotificationPrefs()
    /// 로그아웃할 때 토큰·앱 안 데이터를 지워요 (HAWEApp 에서 연결).
    @ObservationIgnored var onCleared: () -> Void = {}

    init(state: State = .signedOut, account: MyAccount = .mock) {
        self.state = state
        self.account = account
    }

    /// 개발자 모드 유저 전환 — 새 토큰으로 내 정보를 받는 동안
    func beginRestoring() {
        state = .restoring
    }

    func signIn(_ account: MyAccount) {
        self.account = account
        state = .signedIn
    }

    func failRestoring() {
        state = .signedOut
    }

    /// TODO: 서버 연결 시 기기 푸시 토큰 해제
    func signOut() {
        guard state != .signedOut else { return }
        onCleared()
        state = .signedOut
    }

    /// 회원 탈퇴 — 서버(`DELETE /api/me`)가 지운 뒤에 불러요. 토큰·앱 안 데이터를 지우고 로그인 화면으로 가요.
    func withdraw() {
        onCleared()
        account = .mock
        notificationPrefs = NotificationPrefs()
        state = .signedOut
    }
}
