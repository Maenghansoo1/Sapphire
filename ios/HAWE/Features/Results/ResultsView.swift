import SwiftUI

/// 결과 공개 — 10 사진(세로 피드) · 11 그림(2열 격자) · 12 글(카드) · 13 음성(목록 + 전체 이어 듣기)
/// 작품을 누르면 14 작품 상세로 가요. 좋아요·댓글은 서버에 생기면(7단계) 다시 보여요.
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

    var body: some View {
        Group {
            if failed {
                VStack(spacing: 0) {
                    HNavBar(leading: .back { dismiss() })
                        .padding(.horizontal, HLayout.screenPaddingCompact)
                    LoadFailedView(title: "결과를 불러오지 못했어요") { reload += 1 }
                }
            } else if let feed, feed.loadedResult != nil {
                ResultsContent(feed: feed)
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
        } catch {
            switch error.apiCode {
            case "FEED_LOCKED": onLocked()
            case "NOT_GROUP_MEMBER", "MISSION_NOT_FOUND": onHome()
            default:
                // 이미 받아 둔 피드가 있으면 그대로 보여 줘요.
                if feed?.loadedResult == nil { failed = true }
            }
        }
    }
}

/// 지각 제출 표시
enum LateLabel {
    static let text = "지각"
}

private struct ResultsContent: View {
    let feed: ResultFeed

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
        .onDisappear { feed.player.stop() }
    }

    /// 주제 22 Bold + 완료 현황 13 (위 8 · 아래 18)
    private var title: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(result.topic)
                .font(.plex(22, .bold))
                .foregroundStyle(.ink)
            Text("\(progressText) · \(hintText)")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
        }
        .padding(.top, 8)
        .padding(.bottom, 18)
    }

    private var progressText: String {
        let done = result.submissions.count
        return done == result.memberCount ? "\(done)명 모두 완료" : "\(done) / \(result.memberCount)명 완료"
    }

    private var hintText: String {
        result.type == .voice ? "눌러서 듣고 자세히 보기" : "\(result.type.displayName)을 눌러서 자세히 보기"
    }

    @ViewBuilder
    private var content: some View {
        switch result.type {
        case .photo: photoFeed
        case .draw: drawingGrid
        case .write: textCards
        case .voice: voiceList
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
                Button { openedID = submission.id } label: {
                    VStack(spacing: 8) {
                        DrawingArtwork(submission: submission, height: 165, cornerRadius: 18, drawingSize: 130)
                        HStack(spacing: 6) {
                            HAvatar(content: submission.author.avatar, size: 26)
                            Text(submission.author.nickname)
                                .font(.plex(14, .semibold))
                                .foregroundStyle(.ink)
                                .lineLimit(1)
                            Spacer(minLength: 4)
                            if submission.isLate {
                                Text(LateLabel.text)
                                    .font(.caption12)
                                    .foregroundStyle(.textSecondary)
                            }
                        }
                        .padding(.horizontal, 2)
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - 12 글

    /// surface · 모서리 20 · 안쪽 18 · 간격 12, 본문 16 · 줄 높이 1.65
    private var textCards: some View {
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
                            Text(submission.createdAt.koreanTime)
                                .font(.caption12)
                                .foregroundStyle(.textSecondary)
                            if submission.isLate {
                                Text(LateLabel.text)
                                    .font(.caption12)
                                    .foregroundStyle(.textSecondary)
                            }
                        }
                        Text(submission.text ?? "")
                            .font(.plex(16))
                            .plexLineHeight(1.65, size: 16)
                            .foregroundStyle(.ink)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(18)
                    .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - 13 음성

    private var voiceList: some View {
        VStack(spacing: 10) {
            Button { feed.player.playAll(result.submissions) } label: {
                HStack(spacing: 8) {
                    HIconView(icon: .play, size: 16)
                    Text("전체 이어 듣기")
                        .font(.plex(15, .semibold))
                        .foregroundStyle(.ink)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .overlay(
                    RoundedRectangle(cornerRadius: HRadius.input, style: .continuous)
                        .strokeBorder(.border, lineWidth: 1)
                )
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            ForEach(result.submissions) { submission in
                voiceRow(submission)
            }
        }
    }

    /// 테두리 line · 모서리 18 · 안쪽 14 · 재생 44 (멈춤 ink / 재생 중 accent)
    private func voiceRow(_ submission: Submission) -> some View {
        let isPlaying = feed.player.playingID == submission.id
        return HStack(spacing: 12) {
            Button { feed.player.toggle(submission) } label: {
                HIconView(icon: isPlaying ? .pause : .play, size: 16, color: .onAccent)
                    .frame(width: 44, height: 44)
                    .background(isPlaying ? Color.accent : Color.ink, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPlaying ? "일시정지" : "\(submission.author.nickname) 음성 재생")

            Button { openedID = submission.id } label: {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        HStack(spacing: 6) {
                            HAvatar(content: submission.author.avatar, size: 24)
                            Text(submission.author.nickname)
                                .font(.plex(14, .semibold))
                                .foregroundStyle(.ink)
                        }
                        Spacer(minLength: 4)
                        Text((submission.duration ?? 0).shortDuration + (submission.isLate ? " · \(LateLabel.text)" : ""))
                            .font(.caption12)
                            .foregroundStyle(.textSecondary)
                    }
                    VoiceBars(levels: submission.waveform, progress: isPlaying ? feed.player.progress : 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
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
