import SwiftUI

/// 로그인 상태에 따라 첫 화면을 골라요.
/// 로그아웃 → A1 로그인, 로그인 + 그룹 있음 → 04 홈, 로그인 + 그룹 없음 → 01 시작
struct RootView: View {
    @Environment(AppSession.self) private var session

    var body: some View {
        Group {
            switch session.state {
            case .signedOut:
                AuthFlowView()
            case .signedIn(let hasGroups):
                MainFlowView(hasGroups: hasGroups)
                    // 01 → 04 로 바뀔 때 쌓인 화면을 새로 시작해요.
                    .id(hasGroups)
            }
        }
        .animation(.default, value: session.state)
    }
}

#Preview("로그아웃") {
    RootView().environment(AppSession())
}

#Preview("그룹 있음") {
    RootView().environment(AppSession(state: .signedIn(hasGroups: true)))
}
