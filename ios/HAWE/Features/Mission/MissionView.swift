import SwiftUI

/// 05 그룹 · 오늘의 미션 (Figma 1:542)
/// 서버의 지금 미션들(`/missions/today`) — 하루에 여러 개면 위에서 고르고(임시 칩), 유형·주제·카운트다운·마감,
/// 틀별 CTA, 멤버별 완료 여부와 콕 찌르기. 발행 전이면 빈 상태와 다음 발행까지 남은 시간을 보여요.
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
    @Environment(DevMode.self) private var devMode
    @State private var selectedID: Int?
    @State private var performing: PerformTarget?
    @State private var toast: String?
    @State private var reload = 0
    /// 보내는 중인 재촉 (미션, 멤버, 종류)
    @State private var sending: Set<PokeKey> = []
    @State private var creating = false
    @State private var rerollConfirmation: Int?

    private struct PerformTarget: Identifiable {
        let id: Int
    }

    private struct PokeKey: Hashable {
        let missionID: Int
        let memberID: Int
        let kind: PokeKind
    }

    private var group: StoredGroup? { store.storedGroup(groupID) }
    private var missions: [TodayMission] { store.todayMissions(groupID) }
    private var mission: TodayMission? { MissionFormat.selectedMission(missions, selectedID: selectedID) }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(group?.detail.name ?? "", leading: .back { dismiss() }) {
                HStack(spacing: 16) {
                    headerButton(.calendar, label: "기록 캘린더") { path.append(.calendar(groupID: groupID)) }
                    headerButton(.more, label: "그룹 정보") { path.append(.groupInfo(groupID: groupID)) }
                }
            }

            if missions.count >= 2 { missionChips }

            content
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .fullScreenCover(item: $performing) { target in
            if let mission = store.todayMission(groupID, missionID: target.id), let type = mission.type {
                MissionPerformView(type: type, topic: mission.topic, template: mission.template, spec: mission.spec) { content in
                    await submit(content, missionID: mission.id)
                }
            }
        }
        .overlay {
            if let missionID = rerollConfirmation {
                HAlert(
                    title: "이 미션을 새로 뽑을까요?",
                    message: "이미 낸 제출·반응·댓글·콕이 모두 삭제돼요.",
                    confirmTitle: "리롤",
                    onCancel: { rerollConfirmation = nil },
                    onConfirm: {
                        rerollConfirmation = nil
                        reroll(missionID)
                    }
                )
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
            try? await Task.sleep(for: .seconds(max(0, next.timeIntervalSince(AppClock.now()))))
            for attempt in 0..<3 {
                guard !Task.isCancelled else { return }
                reload += 1
                if attempt < 2 { try? await Task.sleep(for: .seconds(30)) }
            }
        }
        // 고른 미션이 닫혀 사라지면 첫 미션으로 돌아가요.
        .onChange(of: missions.map(\.id)) { _, ids in
            if let selectedID, !ids.contains(selectedID) { self.selectedID = ids.first }
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
                    if devMode.enabled { devButtons(mission) }
                    statusHeader(mission)
                    memberList(mission)
                    if !mission.didSubmit, let type = mission.type, MissionTemplates.isSupported(mission.template) {
                        lockNotice(type, template: mission.template).padding(.top, 20)
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
                if devMode.enabled {
                    HButton("[개발자] 미션 생성", action: createMission)
                        .disabled(creating)
                        .padding(.top, 10)
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

    /// 임시 UI: 슬롯별 선택 칩 (하루 여러 미션)
    private var missionChips: some View {
        HStack(spacing: 8) {
            ForEach(Array(missions.enumerated()), id: \.element.id) { index, item in
                let selected = item.id == mission?.id
                Button { selectedID = item.id } label: {
                    HStack(spacing: 4) {
                        Text("\(index + 1)")
                            .font(.plex(14, .semibold))
                        if item.didSubmit {
                            HIconView(icon: .checkSmall, size: 14, color: selected ? .bg : .ink)
                        }
                    }
                    .foregroundStyle(selected ? Color.bg : Color.ink)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(selected ? Color.ink : Color.surface, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(index + 1)번째 미션" + (item.didSubmit ? ", 올림" : ""))
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 8)
    }

    // MARK: - Mission card

    /// 테두리 line · 모서리 24 · 안쪽 22 · 간격 18
    private func missionCard(_ mission: TodayMission) -> some View {
        let index = missions.firstIndex { $0.id == mission.id } ?? 0
        let supported = mission.type != nil && MissionTemplates.isSupported(mission.template)
        return VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 8) {
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
                .fixedSize()

                Spacer(minLength: 0)

                Text((missions.count >= 2 ? "\(index + 1)번째 · " : "") + "\(MissionFormat.dateText(mission.opensAt)) · #\(mission.number)")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
                    .lineLimit(1)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text(mission.topic)
                    .font(.plex(24, .bold))
                    .plexLineHeight(1.35, size: 24, weight: .bold)
                    .foregroundStyle(.ink)
                    .fixedSize(horizontal: false, vertical: true)
                if let guide = MissionFormat.templateGuideText(mission.template, spec: mission.spec) {
                    Text(guide)
                        .font(.plex(14))
                        .foregroundStyle(.textSecondary)
                }
            }

            MissionCountdown(closesAt: mission.closesAt, deadlineAt: mission.deadlineAt)

            if let type = mission.type, supported {
                if mission.didSubmit {
                    HButton(MissionFormat.resultsActionTitle(template: mission.template)) {
                        path.append(.results(groupID: groupID, missionID: mission.id))
                    }
                } else {
                    HButton(MissionFormat.actionTitle(type, template: mission.template)) {
                        performing = PerformTarget(id: mission.id)
                    }
                }
            } else {
                Text(MissionFormat.unsupportedText)
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

    /// 개발자 모드에서만 — 미션 만들기·새로 뽑기
    private func devButtons(_ mission: TodayMission) -> some View {
        VStack(spacing: 8) {
            HButton("[개발자] 미션 생성", action: createMission)
                .disabled(creating)
            HButton("[개발자] 미션 리롤") { rerollConfirmation = mission.id }
                .disabled(creating)
        }
        .padding(.top, 12)
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

    /// 줄 높이 55 · 아바타 38 · 아래 구분선
    private func memberList(_ mission: TodayMission) -> some View {
        VStack(spacing: 0) {
            ForEach(mission.members) { member in
                HStack(spacing: 12) {
                    HAvatar(content: member.profile.avatar, size: 38)
                    Text(MissionFormat.memberDisplayName(member))
                        .font(.plex(15, .medium))
                        .foregroundStyle(.ink)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    status(for: member, missionID: mission.id)
                }
                .frame(height: 55)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(.line).frame(height: 1)
                }
            }
        }
    }

    @ViewBuilder
    private func status(for member: MissionMember, missionID: Int) -> some View {
        if member.isDone {
            HStack(spacing: 4) {
                HIconView(icon: .check, size: 16)
                Text("완료")
                    .font(.plex(13, .semibold))
                    .foregroundStyle(.ink)
            }
        } else if member.isMe {
            Text("아직 안 했어요")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
        } else {
            HStack(spacing: 6) {
                ForEach(PokeKind.allCases, id: \.self) { kind in
                    PokeButton(
                        kind: kind, nickname: member.profile.nickname,
                        sent: member.poked.contains(kind),
                        busy: sending.contains(PokeKey(missionID: missionID, memberID: member.id, kind: kind))
                    ) {
                        sendPoke(missionID: missionID, member: member, kind: kind)
                    }
                }
            }
        }
    }

    /// surface · 모서리 18 · 안쪽 18 · 자물쇠 22 + 14
    private func lockNotice(_ type: MissionType, template: String) -> some View {
        HStack(spacing: 14) {
            HIconView(icon: .lock, size: 22, color: .textSecondary)
            Text(MissionFormat.lockNoticeText(type, template: template))
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
            let index = max(0, missions.firstIndex { $0.id == missionID } ?? 0)
            selectedID = missionID
            toast = MissionFormat.submittedToastText(index: index, total: missions.count, late: submission.isLate)
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
                performing = nil
                onReturnHome()
                return .done
            }
        }
    }

    private func sendPoke(missionID: Int, member: MissionMember, kind: PokeKind) {
        let key = PokeKey(missionID: missionID, memberID: member.id, kind: kind)
        guard !sending.contains(key) else { return }
        sending.insert(key)
        Task {
            do {
                let sent = try await services.pokes.send(groupID: groupID, toUserID: member.id, kind: kind, missionID: missionID)
                sending.remove(key)
                guard session.state == .signedIn else { return }
                store.markPoked(member.id, kind: sent.kind, missionID: sent.missionID, groupID: groupID)
                toast = MissionFormat.pokeSentText(kind, nickname: member.profile.nickname)
            } catch {
                sending.remove(key)
                guard !(error is CancellationError), session.state == .signedIn else { return }
                let failure = PokeFailure(error: error)
                switch failure.followUp {
                case .markSent:
                    store.markPoked(member.id, kind: kind, missionID: missionID, groupID: groupID)
                    reload += 1
                case .refresh:
                    reload += 1
                case .leave:
                    dismiss()
                case .none:
                    break
                }
                if let message = failure.message { toast = message }
            }
        }
    }

    private func createMission() {
        guard devMode.enabled, !creating else { return }
        creating = true
        Task {
            defer { creating = false }
            do {
                let result = try await services.dev.publish(groupID: groupID)
                reload += 1
                toast = result.message ?? "미션 \(result.created?.count ?? 0)개를 생성했어요"
            } catch {
                toast = "미션 생성 실패: \(error.devDescription)"
            }
        }
    }

    private func reroll(_ missionID: Int) {
        guard devMode.enabled, !creating else { return }
        creating = true
        Task {
            defer { creating = false }
            do {
                let result = try await services.dev.reroll(missionID: missionID)
                store.clearMissionForReroll(missionID, groupID: groupID)
                reload += 1
                toast = "미션을 새로 뽑았어요 (\(result.template))"
            } catch {
                toast = "미션 리롤 실패: \(error.devDescription)"
            }
        }
    }

    private func showNotice() {
        guard let notice else { return }
        toast = notice
        self.notice = nil
        reload += 1
    }
}

/// 콕 버튼. 보내면 체크와 강조색으로 바뀌고 다시 누를 수 없어요 (미션마다 1번).
private struct PokeButton: View {
    let kind: PokeKind
    let nickname: String
    let sent: Bool
    let busy: Bool
    let action: () -> Void

    var body: some View {
        let color: Color = sent ? .accentPressed : .ink
        Button(action: action) {
            HStack(spacing: 4) {
                HIconView(icon: sent ? .checkSmall : .pokeTemp, size: 16, color: color)
                Text(MissionFormat.pokeButtonLabel(kind))
                    .font(.plex(13, .semibold))
                    .foregroundStyle(color)
            }
            .padding(.horizontal, 10)
            .frame(height: 34)
            .background(sent ? Color.accentSubtle : Color.bg, in: Capsule())
            .overlay(Capsule().strokeBorder(sent ? Color.accentSubtle : Color.border, lineWidth: 1))
            .opacity(busy ? 0.5 : 1)
            .frame(minHeight: HLayout.minTouch)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(sent || busy)
        .accessibilityLabel(MissionFormat.pokeButtonDescription(kind, nickname: nickname, sent: sent))
    }
}

/// "다음 미션까지 HH:mm:ss" + 마감 안내 — 1초마다 갱신
struct MissionCountdown: View {
    let closesAt: Date
    var deadlineAt: Date? = nil

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { _ in
            let now = AppClock.now()
            VStack(alignment: .leading, spacing: 4) {
                Text(now < closesAt ? "다음 미션까지 \(MissionFormat.countdown(to: closesAt, now: now))" : "곧 열려요")
                    .monospacedDigit()
                if let deadlineAt, let text = MissionFormat.deadlineText(deadlineAt: deadlineAt, closesAt: closesAt, now: now) {
                    Text(text)
                }
            }
            .font(.caption13)
            .foregroundStyle(.textSecondary)
        }
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
    .environment(DevMode.shared)
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
    .environment(DevMode.shared)
}
