import SwiftUI

/// 결과 공개 — 10 사진(세로 피드) · 11 그림(2열 격자) · 12 글·새 틀(카드). 집계 틀은 위에 "그룹 결과" 카드.
/// 작품을 누르면 14 작품 상세(스티커·댓글)로 가요.
/// 들어올 때·앱으로 돌아올 때마다 서버 피드를 받아요. 내가 올리지 않았으면 서버가 막아요(423 FEED_LOCKED).
struct ResultsView: View {
    let groupID: Int
    let missionID: Int
    /// 아직 안 올렸어요 — 05 로 돌아가요.
    var onLocked: () -> Void = {}
    /// 그룹에서 빠졌거나 미션이 없어요 — 홈으로 가요.
    var onHome: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(DataStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(Services.self) private var services
    @State private var feed: ResultFeed?
    @State private var failed = false
    @State private var reload = 0
    @State private var aggregate: AggregateLoad = .loading
    @State private var aggregateRetry = 0

    private struct AggregateKey: Equatable {
        let reload: Int
        let retry: Int
        let template: String?
    }

    private var template: String? { store.result(groupID: groupID, missionID: missionID)?.template }

    var body: some View {
        Group {
            if failed {
                VStack(spacing: 0) {
                    HNavBar(leading: .back { dismiss() })
                        .padding(.horizontal, HLayout.screenPaddingCompact)
                    LoadFailedView(title: "결과를 불러오지 못했어요") { reload += 1 }
                }
            } else if let feed, feed.loadedResult != nil {
                ResultsContent(feed: feed, aggregate: aggregate) { aggregateRetry += 1 }
            } else {
                Color.bg
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            if feed == nil { feed = ResultFeed(store: store, groupID: groupID, missionID: missionID) }
        }
        .task(id: reload) { await load() }
        // 집계 틀의 그룹 결과. 제출이 늘면 값이 바뀌어서 피드와 같이 다시 불러요. 오류로 화면을 닫는 건 피드 쪽만 해요.
        .task(id: AggregateKey(reload: reload, retry: aggregateRetry, template: template)) { await loadAggregate() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { reload += 1 }
        }
    }

    private func load() async {
        failed = false
        do {
            let submissions = try await services.submissions.feed(missionID: missionID, myUserID: session.account.userID)
            if session.state == .signedIn { store.applyFeed(submissions, missionID: missionID, groupID: groupID) }
        } catch is CancellationError {
            return
        } catch {
            switch error.apiCode {
            case "FEED_LOCKED": onLocked()
            case "NOT_GROUP_MEMBER", "MISSION_NOT_FOUND": onHome()
            default:
                // 이미 받아 둔 피드가 있으면 그대로 보여 줘요.
                if feed?.loadedResult == nil { failed = true }
            }
            return
        }
        // 달력에서 연 지난 미션은 밸런스 선택지·그림 대상을 모르니 미션 정보를 한 번 받아 와요
        // (실패하면 라벨 없이, AI 추측은 판정 없이 보여요).
        guard let template, MissionTemplates.isAggregate(template) || template.hasPrefix("DRAW_"),
              store.needsMissionSpec(groupID: groupID, missionID: missionID) else { return }
        if let mission = try? await services.missions.mission(missionID: missionID), session.state == .signedIn {
            store.applyMissionSpec(missionID, template: mission.template, spec: mission.spec)
        }
    }

    private func loadAggregate() async {
        guard let template, MissionTemplates.isAggregate(template) else { return }
        if case .loaded = aggregate {} else { aggregate = .loading }
        do {
            aggregate = try await services.submissions.result(missionID: missionID).map(AggregateLoad.loaded) ?? .hidden
        } catch is CancellationError {
        } catch {
            if ["FEED_LOCKED", "RESULT_NOT_SUPPORTED", "NOT_GROUP_MEMBER", "MISSION_NOT_FOUND"].contains(error.apiCode ?? "") {
                aggregate = .hidden
            } else if case .loaded = aggregate {
            } else {
                aggregate = .failed
            }
        }
    }
}

private struct ResultsContent: View {
    let feed: ResultFeed
    let aggregate: AggregateLoad
    let onRetryAggregate: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var openedID: Submission.ID?

    private var result: MissionResult { feed.result }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(
                "\(result.date.koreanMonthDay) · \(result.type.displayName)",
                titleFont: .plex(15, .semibold),
                leading: .back { dismiss() }
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    title
                    if MissionTemplates.isAggregate(result.template) {
                        AggregateCard(load: aggregate, spec: result.spec, onRetry: onRetryAggregate)
                            .padding(.bottom, 20)
                    }
                    content
                    if !result.pending.isEmpty {
                        PendingMembers(members: result.pending)
                            .padding(.top, 28)
                    }
                }
                .padding(.bottom, 32)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(item: $openedID) { id in
            SubmissionDetailView(feed: feed, submissionID: id)
        }
    }

    /// 주제 22 Bold + 완료 현황 13 (위 8 · 아래 18)
    private var title: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(result.topic)
                .font(.plex(22, .bold))
                .foregroundStyle(.ink)
            Text("\(ResultsFormat.progressText(done: result.submissions.count, memberCount: result.memberCount)) · \(ResultsFormat.hintText(result.type))")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
        }
        .padding(.top, 8)
        .padding(.bottom, 18)
    }

    @ViewBuilder
    private var content: some View {
        switch result.type {
        case .photo: photoFeed
        case .draw: drawingGrid
        case .write: answerCards
        }
    }

    // MARK: - 10 사진

    private var photoFeed: some View {
        VStack(spacing: 24) {
            ForEach(result.submissions) { submission in
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 10) {
                        HAvatar(content: submission.author.avatar, size: 34)
                        Text(submission.author.nickname)
                            .font(.plex(15, .semibold))
                            .foregroundStyle(.ink)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(submission.createdAt.koreanTime)
                            .font(.caption13)
                            .foregroundStyle(.textSecondary)
                        if submission.isLate {
                            Text(LateLabel.text)
                                .font(.caption12)
                                .foregroundStyle(.textSecondary)
                        }
                    }
                    Button { openedID = submission.id } label: {
                        PhotoArtwork(submission: submission, height: 260)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - 11 그림

    /// 2열 · 가로 12 · 세로 14 · 칸 165 높이 · 모서리 18
    private var drawingGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 2), spacing: 14) {
            ForEach(result.submissions) { submission in
                VStack(spacing: 6) {
                    Button { openedID = submission.id } label: {
                        DrawingArtwork(submission: submission, height: 165, cornerRadius: 18, drawingSize: 130)
                    }
                    .buttonStyle(.plain)
                    Button { openedID = submission.id } label: {
                        HStack(spacing: 8) {
                            HAvatar(content: submission.author.avatar, size: 26)
                            Text(submission.author.nickname)
                                .font(.plex(14, .semibold))
                                .foregroundStyle(.ink)
                                .lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            if submission.isLate {
                                Text(LateLabel.text)
                                    .font(.caption12)
                                    .foregroundStyle(.textSecondary)
                            }
                        }
                        .padding(.horizontal, 2)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - 12 글 · 새 틀

    /// surface · 모서리 20 · 안쪽 18 · 간격 12
    private var answerCards: some View {
        VStack(spacing: 12) {
            ForEach(result.submissions) { submission in
                Button { openedID = submission.id } label: {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(spacing: 8) {
                            HAvatar(content: submission.author.avatar, size: 30)
                                .background(.bg, in: Circle())
                            Text(submission.author.nickname)
                                .font(.plex(14, .semibold))
                                .foregroundStyle(.ink)
                                .lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(submission.createdAt.koreanTime)
                                .font(.caption12)
                                .foregroundStyle(.textSecondary)
                            if submission.isLate {
                                Text(LateLabel.text)
                                    .font(.caption12)
                                    .foregroundStyle(.textSecondary)
                            }
                        }
                        TemplateAnswer(submission: submission, spec: result.spec)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(18)
                    .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

#Preview {
    let store = DataStore.preview()
    let group = store.groups[0]
    NavigationStack {
        ResultsView(groupID: group.id, missionID: group.missions.last!.id)
    }
    .environment(store)
    .environment(AppSession(state: .signedIn))
    .environment(Services.preview)
}
