import SwiftUI

/// 로그인 뒤 화면 흐름. 그룹이 하나도 없으면 01 시작, 있으면 04 홈이 첫 화면이에요.
/// 04 홈의 + 는 01 시작을 (뒤로 가기와 함께) 띄워요.
/// 화면 사이에는 그룹·미션 id(서버 id) 만 넘기고, 내용은 DataStore 에서 읽어요.
enum MainRoute: Hashable {
    case start
    case createGroup
    case joinGroup(code: String?)
    case groupInfo(groupID: Int)
    case groupProfile(groupID: Int)
    case successorPicker(groupID: Int)
    case groupInsight(groupID: Int)
    case mission(groupID: Int)
    case results(groupID: Int, missionID: Int)
    case calendar(groupID: Int)
    case myPage(MyPageRoute)
}

struct MainFlowView: View {
    @Environment(DataStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(Services.self) private var services
    @State private var path: [MainRoute] = []
    /// 홈 목록을 다시 받을 때 올려요.
    @State private var groupsReload = 0
    /// 결과 화면 등에서 05 로 돌아갈 때 05 가 띄울 안내
    @State private var missionNotice: String?

    var body: some View {
        NavigationStack(path: $path) {
            root
                // 홈으로 돌아올 때마다 그룹 목록을 다시 받아요.
                .task(id: groupsReload) { await loadGroups() }
                .navigationDestination(for: MainRoute.self) { route in
                    switch route {
                    case .start:
                        StartView(path: $path, showsBack: true)
                    case .createGroup:
                        CreateGroupView { id in
                            // 그룹을 만든 뒤에는 16 그룹 정보(초대 코드 공유)로 가요. 뒤로 가면 홈이에요. (CLAUDE.md §4)
                            path = [.groupInfo(groupID: id)]
                        }
                    case .joinGroup(let code):
                        JoinGroupView(initialCode: code ?? "") {
                            path.removeAll()
                        }
                    case .groupInfo(let groupID):
                        GroupInfoView(groupID: groupID, path: $path) {
                            path.removeAll()
                        }
                    case .groupProfile(let groupID):
                        GroupProfileView(groupID: groupID) {
                            path.removeAll()
                        }
                    case .successorPicker(let groupID):
                        SuccessorPickerView(groupID: groupID) {
                            path.removeAll()
                            groupsReload += 1
                        }
                    case .groupInsight(let groupID):
                        GroupInsightView(groupID: groupID, path: $path) {
                            path.removeAll()
                        }
                    case .mission(let groupID):
                        MissionView(groupID: groupID, path: $path, notice: $missionNotice) {
                            path.removeAll()
                            groupsReload += 1
                        }
                    case .results(let groupID, let missionID):
                        ResultsView(groupID: groupID, missionID: missionID, onLocked: {
                            missionNotice = "먼저 미션을 올려 주세요"
                            if !path.isEmpty { path.removeLast() }
                        }, onHome: {
                            path.removeAll()
                            groupsReload += 1
                        })
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

    @ViewBuilder
    private var root: some View {
        if store.groupsState == .notLoaded {
            Color.bg.ignoresSafeArea()
        } else if store.groupsState == .failed && store.groups.isEmpty {
            LoadFailedView(title: "그룹을 불러오지 못했어요") { groupsReload += 1 }
        } else if store.groups.isEmpty {
            StartView(path: $path)
        } else {
            HomeView(path: $path)
        }
    }

    private func loadGroups() async {
        do {
            let groups = try await services.groups.loadAll(myUserID: session.account.userID)
            if session.state == .signedIn { store.applyServerGroups(groups) }
        } catch is CancellationError {
        } catch {
            if session.state == .signedIn { store.markGroupsFailed() }
        }
    }

    /// 알림을 눌렀을 때 갈 화면 — 알림의 그룹이 없으면 첫 그룹으로 가요.
    private func open(_ destination: PushDestination) {
        let requested = destination.groupID.flatMap(Int.init)
        guard let group = store.groups.first(where: { $0.id == requested }) ?? store.groups.first else { return }
        switch destination.kind {
        case .topic, .poke, .friendDone:
            path = [.mission(groupID: group.id)]
        case .comment:
            // 하루 여러 미션이면 내가 낸 마지막 미션의 결과로 가요 (Android `pushTarget`).
            if let mission = group.missions.last(where: \.serverSubmittedByMe) ?? group.missions.last, mission.serverSubmittedByMe {
                path = [.mission(groupID: group.id), .results(groupID: group.id, missionID: mission.id)]
            } else {
                path = [.mission(groupID: group.id)]
            }
        }
    }
}

/// 서버에서 불러오지 못했을 때 — 제목 + 네트워크 안내 + 다시 시도
struct LoadFailedView: View {
    let title: String
    var message = "네트워크 연결을 확인해 주세요"
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.screenTitle)
                .foregroundStyle(.ink)
            Text(message)
                .font(.body15)
                .foregroundStyle(.textSecondary)
            HButton("다시 시도", action: onRetry)
                .padding(.top, 16)
        }
        .padding(.horizontal, HLayout.screenPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
    }
}

#Preview("그룹 없음") {
    MainFlowView()
        .environment(AppSession(state: .signedIn))
        .environment(DataStore())
        .environment(Services.preview)
}

#Preview("체험 그룹 참여") {
    MainFlowView()
        .environment(AppSession(state: .signedIn))
        .environment(DataStore.preview())
        .environment(Services.preview)
}
