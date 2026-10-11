import Observation

/// 앱 전체 로그인 상태와 내 계정. 지금은 목업이라 버튼을 누르면 바로 상태만 바꿔요.
/// 서버(Spring Boot) 연결 단계에서 실제 로그인·토큰 처리로 바꿔요.
@Observable
final class AppSession {
    enum State: Equatable {
        case signedOut
        /// 그룹이 있으면 04 홈, 없으면 01 시작 — DataStore 의 그룹 수로 정해요 (CLAUDE.md §4 A1)
        case signedIn
    }

    var state: State
    var account: MyAccount
    var notificationPrefs = NotificationPrefs()

    init(state: State = .signedOut, account: MyAccount = .mock) {
        self.state = state
        self.account = account
    }

    func signIn() {
        state = .signedIn
    }

    /// TODO: 서버 연결 시 토큰 삭제, 기기 푸시 토큰 해제
    func signOut() {
        state = .signedOut
    }

    /// 회원 탈퇴 — 작품·댓글 삭제, 모든 그룹에서 나감(방장 이전), 복구 불가 (CLAUDE.md §5-5)
    /// TODO: 서버 연결 시 탈퇴 요청
    func withdraw() {
        account = .mock
        notificationPrefs = NotificationPrefs()
        state = .signedOut
    }
}
