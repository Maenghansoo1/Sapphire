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

    /// 회원 탈퇴 — 작품·댓글 삭제, 모든 그룹에서 나감(방장 이전), 복구 불가 (CLAUDE.md §5-5)
    /// TODO: 서버 탈퇴 API 가 생기면 연결해요. 지금 화면에서는 준비 중이에요.
    func withdraw() {
        onCleared()
        account = .mock
        notificationPrefs = NotificationPrefs()
        state = .signedOut
    }
}
