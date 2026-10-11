import SwiftUI

/// 로그인 상태에 따라 첫 화면을 골라요.
/// 로그아웃 → A1 로그인, 로그인 → 그룹이 있으면 04 홈, 없으면 01 시작 (MainFlowView)
struct RootView: View {
    @Environment(AppSession.self) private var session

    var body: some View {
        Group {
            switch session.state {
            case .signedOut:
                AuthFlowView()
            case .signedIn:
                MainFlowView()
            }
        }
        .animation(.default, value: session.state)
    }
}

#Preview("로그아웃") {
    RootView()
        .environment(AppSession())
        .environment(DataStore())
}

#Preview("체험 그룹 참여") {
    RootView()
        .environment(AppSession(state: .signedIn))
        .environment(DataStore.preview())
}
