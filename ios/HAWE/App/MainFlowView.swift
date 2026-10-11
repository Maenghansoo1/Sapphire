import SwiftUI

/// 로그인 뒤 화면 흐름: 01 시작 또는 04 홈에서 02 그룹 만들기 / 03 초대 코드로 참여
enum MainRoute: Hashable {
    case createGroup
    case joinGroup(code: String?)
}

struct MainFlowView: View {
    let hasGroups: Bool

    @Environment(AppSession.self) private var session
    @State private var path: [MainRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if hasGroups {
                    HomeView(groups: GroupSummary.mock, path: $path)
                } else {
                    StartView(path: $path)
                }
            }
            .navigationDestination(for: MainRoute.self) { route in
                switch route {
                case .createGroup:
                    CreateGroupView(onCreated: finish)
                case .joinGroup(let code):
                    JoinGroupView(initialCode: code ?? "", onJoined: finish)
                }
            }
        }
    }

    /// 그룹을 만들거나 참여하면 홈으로 돌아가요.
    /// TODO: CLAUDE.md §4 — 그룹을 만든 뒤에는 16 그룹 정보(초대 코드 공유)로 가야 해요. 3단계에서 16 을 만들면 바꿔요.
    private func finish() {
        path.removeAll()
        session.signIn(hasGroups: true)
    }
}

#Preview("그룹 없음") {
    MainFlowView(hasGroups: false).environment(AppSession(state: .signedIn(hasGroups: false)))
}

#Preview("그룹 있음") {
    MainFlowView(hasGroups: true).environment(AppSession(state: .signedIn(hasGroups: true)))
}
