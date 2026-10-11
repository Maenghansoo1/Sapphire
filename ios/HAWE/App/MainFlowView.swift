import SwiftUI

/// 로그인 뒤 화면 흐름. 그룹이 하나도 없으면 01 시작, 있으면 04 홈이 첫 화면이에요.
/// 04 홈의 + 는 01 시작을 (뒤로 가기와 함께) 띄워요.
/// 화면 사이에는 그룹·미션 id 만 넘기고, 내용은 DataStore 에서 읽어요.
enum MainRoute: Hashable {
    case start
    case createGroup
    case joinGroup(code: String?)
    case groupInfo(groupID: UUID)
    case mission(groupID: UUID)
    case results(groupID: UUID, missionID: UUID)
    case calendar(groupID: UUID)
    case myPage(MyPageRoute)
}

struct MainFlowView: View {
    @Environment(DataStore.self) private var store
    @State private var path: [MainRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if store.groups.isEmpty {
                    StartView(path: $path)
                } else {
                    HomeView(path: $path)
                }
            }
            .navigationDestination(for: MainRoute.self) { route in
                switch route {
                case .start:
                    StartView(path: $path, showsBack: true)
                case .createGroup:
                    CreateGroupView { name, relationship, startDate in
                        let id = store.createGroup(name: name, relationship: relationship, startDate: startDate)
                        // 그룹을 만든 뒤에는 16 그룹 정보(초대 코드 공유)로 가요. 뒤로 가면 홈이에요. (CLAUDE.md §4)
                        path = [.groupInfo(groupID: id)]
                    }
                case .joinGroup(let code):
                    JoinGroupView(initialCode: code ?? "") { joinedCode in
                        store.join(code: joinedCode)
                        path.removeAll()
                    }
                case .groupInfo(let groupID):
                    GroupInfoView(groupID: groupID) {
                        path.removeAll()
                        store.leave(groupID)
                    }
                case .mission(let groupID):
                    MissionView(groupID: groupID, path: $path)
                case .results(let groupID, let missionID):
                    ResultsView(groupID: groupID, missionID: missionID)
                case .calendar(let groupID):
                    RecordCalendarView(groupID: groupID, path: $path)
                case .myPage(let route):
                    MyPageDestination(route: route, path: $path)
                }
            }
        }
        // 로그인 뒤 처음 들어왔을 때 푸시 권한을 물어봐요.
        .task { await PushCenter.shared.requestAuthorizationIfNeeded() }
        .onChange(of: PushCenter.shared.pendingDestination) { _, destination in
            guard let destination else { return }
            open(destination)
            PushCenter.shared.pendingDestination = nil
        }
    }

    /// 알림을 눌렀을 때 갈 화면
    /// TODO: 서버 연결 시 groupID·submissionID 로 정확한 그룹·작품을 열어요. 지금은 첫 그룹으로 가요.
    private func open(_ destination: PushDestination) {
        guard let group = store.groups.first else { return }
        switch destination.kind {
        case .topic, .poke, .friendDone:
            path = [.mission(groupID: group.id)]
        case .comment:
            if let mission = group.missions.last, mission.submissions.contains(where: \.isMine) {
                path = [.mission(groupID: group.id), .results(groupID: group.id, missionID: mission.id)]
            } else {
                path = [.mission(groupID: group.id)]
            }
        }
    }
}

#Preview("그룹 없음") {
    MainFlowView()
        .environment(AppSession(state: .signedIn))
        .environment(DataStore())
}

#Preview("체험 그룹 참여") {
    MainFlowView()
        .environment(AppSession(state: .signedIn))
        .environment(DataStore.preview())
}
