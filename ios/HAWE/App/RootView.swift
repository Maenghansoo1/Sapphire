import SwiftUI

/// 로그인 상태에 따라 첫 화면을 골라요.
/// 로그아웃 → A1 로그인, 로그인 → 그룹이 있으면 04 홈, 없으면 01 시작 (MainFlowView)
/// 앱을 켜면 먼저 00 스플래시를 잠깐 보여줘요.
struct RootView: View {
    @Environment(AppSession.self) private var session
    @State private var showsSplash: Bool

    init(showsSplash: Bool = true) {
        _showsSplash = State(initialValue: showsSplash)
    }

    var body: some View {
        ZStack {
            Group {
                switch session.state {
                case .signedOut:
                    AuthFlowView()
                case .signedIn:
                    MainFlowView()
                }
            }
            .animation(.default, value: session.state)

            if showsSplash {
                SplashView()
                    .transition(.opacity)
            }
        }
        .task {
            guard showsSplash else { return }
            try? await Task.sleep(for: .seconds(1.2))
            withAnimation(.easeOut(duration: 0.3)) { showsSplash = false }
        }
    }
}

#Preview("로그아웃") {
    RootView(showsSplash: false)
        .environment(AppSession())
        .environment(DataStore())
}

#Preview("체험 그룹 참여") {
    RootView(showsSplash: false)
        .environment(AppSession(state: .signedIn))
        .environment(DataStore.preview())
}
