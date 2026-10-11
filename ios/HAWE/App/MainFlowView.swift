import SwiftUI

/// 로그인 뒤 화면 흐름: 01 시작 또는 04 홈에서 02 그룹 만들기 / 03 초대 코드로 참여
/// 04 홈의 + 는 01 시작을 (뒤로 가기와 함께) 띄워요.
enum MainRoute: Hashable {
    case start
    case createGroup
    case joinGroup(code: String?)
    case groupInfo(GroupDetail)
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
                case .start:
                    StartView(path: $path, showsBack: true)
                case .createGroup:
                    CreateGroupView { name, relationship in
                        showCreatedGroup(.newlyCreated(name: name, relationship: relationship))
                    }
                case .joinGroup(let code):
                    JoinGroupView(initialCode: code ?? "", onJoined: finish)
                case .groupInfo(let group):
                    GroupInfoView(group: group, onLeft: finish)
                }
            }
        }
    }

    /// 그룹을 만든 뒤에는 16 그룹 정보(초대 코드 공유)로 가요. 거기서 뒤로 가면 홈이에요. (CLAUDE.md §4)
    private func showCreatedGroup(_ group: GroupDetail) {
        session.signIn(hasGroups: true)
        path = [.groupInfo(group)]
    }

    /// 그룹에 참여하거나 나가면 홈으로 돌아가요.
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
