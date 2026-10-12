import SwiftUI

/// 05 그룹 · 오늘의 미션 (Figma 1:542)
/// 서버의 지금 미션(`/missions/today`) — 유형·주제, 다음 미션까지 카운트다운·마감, 유형별 CTA, 멤버별 완료 여부.
/// 발행 전이면 빈 상태와 다음 발행까지 남은 시간을 보여요. 콕 찌르기는 서버 기능이 생기기 전까지 숨겨요.
struct MissionView: View {
    let groupID: Int
    @Binding var path: [MainRoute]
    /// 결과 화면 등에서 돌아올 때 띄울 안내
    @Binding var notice: String?
    /// 그룹에서 빠졌을 때 — 홈으로 가서 목록을 다시 받아요.
    var onReturnHome: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(DataStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(Services.self) private var services
    @State private var isPerforming = false
    @State private var toast: String?
    @State private var reload = 0

    private var group: StoredGroup? { store.storedGroup(groupID) }
    private var mission: TodayMission? { store.todayMission(groupID) }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(group?.detail.name ?? "", leading: .back { dismiss() }) {
                HStack(spacing: 16) {
                    // 기록 API 가 생기면 15 캘린더(.calendar)로 보내요.
                    headerButton(.calendar, label: "기록 캘린더") { toast = ComingSoon.message }
                    headerButton(.more, label: "그룹 정보") { path.append(.groupInfo(groupID: groupID)) }
                }
            }

            content
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .fullScreenCover(isPresented: $isPerforming) {
            if let mission, let type = mission.type {
                MissionPerformView(type: type, topic: mission.topic, template: mission.template, spec: mission.spec) { content in
                    await submit(content, missionID: mission.id)
                }
            }
        }
        .hToast($toast)
        // 들어올 때·앱으로 돌아올 때마다 서버에서 다시 받아요.
        .task(id: reload) { await load() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { reload += 1 }
        }
        // 발행 시각이 되면 새 미션을 받아요 (서버가 조금 늦을 수 있어 30초 간격으로 세 번).
        .task(id: group?.nextPublishAt) {
            guard let next = group?.nextPublishAt else { return }
            try? await Task.sleep(for: .seconds(max(0, next.timeIntervalSinceNow)))
            for attempt in 0..<3 {
                guard !Task.isCancelled else { return }
                reload += 1
                if attempt < 2 { try? await Task.sleep(for: .seconds(30)) }
            }
        }
        .onAppear { showNotice() }
        .onChange(of: notice) { _, _ in showNotice() }
    }

    @ViewBuilder
    private var content: some View {
        if let mission {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    missionCard(mission)
                    statusHeader(mission)
                    memberList(mission)
                    if !mission.didSubmit, let type = mission.type {
                        lockNotice(type).padding(.top, 20)
                    }
                }
                .padding(.bottom, 32)
            }
            .scrollBounceBehavior(.basedOnSize)
        } else if group?.missionState == .failed {
            LoadFailedView(title: "미션을 불러오지 못했어요") { reload += 1 }
        } else if group?.missionState == .loaded {
            VStack(spacing: 10) {
                Text("아직 오늘의 미션이 열리지 않았어요")
                    .font(.sectionTitle)
                    .foregroundStyle(.ink)
                if let next = group?.nextPublishAt {
                    MissionCountdown(closesAt: next)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            Color.bg.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func headerButton(_ icon: HIcon, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HIconView(icon: icon, size: 22)
                .contentShape(Rectangle().inset(by: -11))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    // MARK: - Mission card

    /// 테두리 line · 모서리 24 · 안쪽 22 · 간격 18
    private func missionCard(_ mission: TodayMission) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                HStack(spacing: 8) {
                    if let type = mission.type {
                        HIconView(icon: type.icon, size: 16)
                    }
                    Text("오늘은 \(mission.type?.displayName ?? "미션")")
                        .font(.plex(13, .semibold))
                        .foregroundStyle(.ink)
                }
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(.surface, in: Capsule())

                Spacer()

                Text("\(Self.dateText(mission.opensAt)) · #\(mission.number)")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
                    .lineLimit(1)
            }

            Text(mission.topic)
                .font(.plex(24, .bold))
                .plexLineHeight(1.35, size: 24, weight: .bold)
                .foregroundStyle(.ink)
                .fixedSize(horizontal: false, vertical: true)

            MissionCountdown(closesAt: mission.closesAt, deadlineAt: mission.deadlineAt)

            if let type = mission.type {
                if mission.didSubmit {
                    HButton("친구들 작품 보기") { path.append(.results(groupID: groupID, missionID: mission.id)) }
                } else {
                    HButton(type.actionTitle) { isPerforming = true }
                }
            } else {
                Text("이 미션은 아직 앱에서 열 수 없어요")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
            }
        }
        .padding(22)
        .overlay(
            RoundedRectangle(cornerRadius: HRadius.largeCard, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
    }

    // MARK: - Members

    private func statusHeader(_ mission: TodayMission) -> some View {
        HStack {
            Text("참여 현황")
                .font(.sectionTitle)
                .foregroundStyle(.ink)
            Spacer()
            Text("\(mission.doneCount) / \(mission.members.count)명 완료")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
        }
        .padding(.top, 28)
        .padding(.bottom, 4)
    }

    /// 줄 높이 56 · 아바타 38 · 아래 구분선
    private func memberList(_ mission: TodayMission) -> some View {
        VStack(spacing: 0) {
            ForEach(mission.members) { member in
                HStack(spacing: 12) {
                    HAvatar(content: member.profile.avatar, size: 38)
                    Text(member.isMe ? "\(member.profile.nickname) (나)" : member.profile.nickname)
                        .font(.plex(15, .medium))
                        .foregroundStyle(.ink)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    status(for: member)
                }
                .frame(height: 56)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(.line).frame(height: 1)
                }
            }
        }
    }

    @ViewBuilder
    private func status(for member: MissionMember) -> some View {
        if member.isDone {
            HStack(spacing: 4) {
                HIconView(icon: .check, size: 16)
                Text("완료")
                    .font(.plex(13, .semibold))
                    .foregroundStyle(.ink)
            }
        } else {
            Text("아직 안 했어요")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
        }
    }

    /// surface · 모서리 18 · 안쪽 18 · 자물쇠 22 + 14
    private func lockNotice(_ type: MissionType) -> some View {
        HStack(spacing: 14) {
            HIconView(icon: .lock, size: 22, color: .textSecondary)
            Text("내 미션을 올리면 친구들의 \(type.subjectForm) 열려요.")
                .font(.plex(14))
                .foregroundStyle(.textBody)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    // MARK: - 서버

    private func load() async {
        do {
            let state = try await services.missions.current(groupID: groupID)
            if session.state == .signedIn { store.applyCurrentMission(state, groupID: groupID) }
        } catch is CancellationError {
        } catch {
            guard session.state == .signedIn else { return }
            if store.todayMission(groupID) == nil {
                store.markMissionFailed(groupID)
            } else {
                toast = "네트워크 연결을 확인해 주세요"
            }
        }
    }

    /// 06~09 올리기. `.done` 이면 수행 화면을 닫고, `.stay` 면 입력을 그대로 두고 안내해요.
    private func submit(_ content: SubmissionContent, missionID: Int) async -> MissionPerformView.Reply {
        do {
            let submission = try await services.submissions.submit(
                missionID: missionID, content: content, myUserID: session.account.userID
            )
            if session.state == .signedIn {
                store.applySubmitted(submission, missionID: missionID, groupID: groupID)
            }
            toast = submission.isLate ? "지각으로 올렸어요" : "오늘의 미션을 올렸어요"
            return .done
        } catch is CancellationError {
            return .stay("잠시 후 다시 시도해 주세요")
        } catch {
            switch SubmitOutcome(error: error) {
            case .stay(let message):
                return .stay(message)
            case .alreadySubmitted:
                // 앞선 요청이 이미 닿았어요 — 서버 상태를 다시 받아요.
                toast = "이미 올렸어요"
                reload += 1
                return .done
            case .missionClosed:
                toast = "새 미션이 열려서 마감됐어요"
                reload += 1
                return .done
            case .returnHome:
                isPerforming = false
                onReturnHome()
                return .done
            }
        }
    }

    private func showNotice() {
        guard let notice else { return }
        toast = notice
        self.notice = nil
        reload += 1
    }

    private static func dateText(_ date: Date) -> String {
        var style = Date.FormatStyle.dateTime.month(.defaultDigits).day().locale(Locale(identifier: "ko_KR"))
        style.timeZone = TodayMission.seoul
        return date.formatted(style)
    }
}

/// "다음 미션까지 HH:mm:ss" + 마감 안내 — 1초마다 갱신
struct MissionCountdown: View {
    let closesAt: Date
    var deadlineAt: Date? = nil

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let now = context.date
            VStack(alignment: .leading, spacing: 4) {
                Text(now < closesAt ? "다음 미션까지 \(Self.countdown(to: closesAt, now: now))" : "곧 열려요")
                    .monospacedDigit()
                if let deadlineAt, let text = Self.deadlineText(deadlineAt: deadlineAt, closesAt: closesAt, now: now) {
                    Text(text)
                }
            }
            .font(.caption13)
            .foregroundStyle(.textSecondary)
        }
    }

    /// 남은 시간 HH:mm:ss
    static func countdown(to end: Date, now: Date) -> String {
        let seconds = max(0, Int(end.timeIntervalSince(now)))
        return String(format: "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
    }

    /// "마감 오후 11:00" · "마감 내일 오전 9:00" · 마감 뒤에는 지각 안내
    static func deadlineText(deadlineAt: Date, closesAt: Date, now: Date) -> String? {
        guard now < closesAt else { return nil }
        guard now < deadlineAt else { return "마감이 지났어요 · 지금 올리면 지각이에요" }
        let calendar = DataStore.calendar
        let today = calendar.startOfDay(for: now)
        let deadlineDay = calendar.startOfDay(for: deadlineAt)
        let parts = calendar.dateComponents([.month, .day, .hour, .minute], from: deadlineAt)
        let date: String
        if deadlineDay == today {
            date = ""
        } else if deadlineDay == calendar.date(byAdding: .day, value: 1, to: today) {
            date = "내일 "
        } else {
            date = "\(parts.month ?? 0)월 \(parts.day ?? 0)일 "
        }
        let hour24 = parts.hour ?? 0
        let hour = hour24 % 12 == 0 ? 12 : hour24 % 12
        return "마감 \(date)\(hour24 < 12 ? "오전" : "오후") \(hour):" + String(format: "%02d", parts.minute ?? 0)
    }
}

#Preview("내 미션 남음") {
    @Previewable @State var notice: String?
    let store = DataStore.preview(submitted: false)
    NavigationStack {
        MissionView(groupID: store.groups[0].id, path: .constant([]), notice: $notice)
    }
    .environment(store)
    .environment(AppSession(state: .signedIn))
    .environment(Services.preview)
}

#Preview("올린 뒤") {
    @Previewable @State var notice: String?
    let store = DataStore.preview()
    NavigationStack {
        MissionView(groupID: store.groups[0].id, path: .constant([]), notice: $notice)
    }
    .environment(store)
    .environment(AppSession(state: .signedIn))
    .environment(Services.preview)
}
