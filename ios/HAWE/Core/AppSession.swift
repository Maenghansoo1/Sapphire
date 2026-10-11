import Observation

/// 앱 전체 로그인 상태. 지금은 목업이라 버튼을 누르면 바로 상태만 바꿔요.
/// 서버(Spring Boot) 연결 단계에서 실제 로그인·토큰 처리로 바꿔요.
@Observable
final class AppSession {
    enum State: Equatable {
        case signedOut
        /// 그룹이 있으면 04 홈, 없으면 01 시작 (CLAUDE.md §4 A1)
        case signedIn(hasGroups: Bool)
    }

    var state: State

    init(state: State = .signedOut) {
        self.state = state
    }

    func signIn(hasGroups: Bool) {
        state = .signedIn(hasGroups: hasGroups)
    }

    func signOut() {
        state = .signedOut
    }
}
