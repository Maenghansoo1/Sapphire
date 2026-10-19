import SwiftUI

/// 개발자 도구 (임시 UI: 개발자 전용 조작 화면) — 개발자 모드에서만 열려요. 개발 서버의 `/api/dev` 를 써요.
/// 서버 시계, 미션 생성·리롤·마감, 대리 제출·콕·반응·댓글, 시드, 인사이트 재생성, 그룹 설정, 오류 주입.
struct DevToolsView: View {
    @Binding var path: [MainRoute]

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @Environment(DataStore.self) private var store
    @Environment(Services.self) private var services
    @Environment(DevMode.self) private var devMode

    @State private var busy = false
    @State private var status: DevClockStatusDTO?
    @State private var groupID: Int?
    @State private var missionID: Int?
    @State private var memberID: Int?
    @State private var toMemberID: Int?
    @State private var submissionRows: [DevSubmissionDTO] = []
    @State private var selectedSubmission: Int?
    @State private var sticker = "heart"
    @State private var comment = "개발자 테스트 댓글"
    @State private var template: String?
    @State private var seedDays = "30"
    @State private var seedMembers = "5"
    @State private var seedDaily = "1"
    @State private var daily = 1
    @State private var slotTimes: [Date] = Array(repeating: Self.time(hour: 9, minute: 0), count: 5)
    @State private var month = InsightFormat.monthStart(AppClock.now())
    @State private var clock = AppClock.now()
    @State private var confirmReset = false
    @State private var confirmDelete = false
    @State private var rerollTarget: RerollTarget?
    @State private var faultRevision = 0
    @State private var results: [String] = []
    @State private var toast: String?

    private struct RerollTarget: Equatable {
        let groupID: Int
        let missionID: Int
        let template: String?
    }

    private static let templates = ["PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "TEXT_ACROSTIC", "TEXT_ONE_WORD",
                                    "TEXT_REVIEW", "TEXT_BALANCE", "TEXT_FILL_BLANK", "DRAW_SKETCH", "DRAW_A_TO_B"]
    private static let stickers = Sticker.allCases.map(\.rawValue)

    private var group: StoredGroup? { groupID.flatMap(store.storedGroup) }
    private var current: [TodayMission] { groupID.map(store.todayMissions) ?? [] }
    private var members: [GroupMember] { group?.detail.members ?? [] }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("개발자 도구", leading: .back { dismiss() })
            ScrollView {
                VStack(spacing: 12) {
                    statusSection
                    clockSection
                    targetSection
                    missionSection
                    actorSection
                    seedSection
                    insightSection
                    settingsSection
                    resetSection
                    faultSection
                    section("개발자 모드") {
                        action("개발자 모드 끄기") {
                            DevFaults.clear()
                            devMode.changeEnabled(false)
                            toast = "개발자 모드를 껐어요 · 서버 시계는 필요하면 초기화하세요"
                            dismiss()
                        }
                    }
                    section("최근 결과") {
                        Text(results.isEmpty ? "아직 결과가 없어요" : results.joined(separator: "\n"))
                            .font(.caption13)
                            .foregroundStyle(.ink)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.bottom, 30)
            }
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .overlay { alerts }
        .hToast($toast)
        .task {
            if groupID == nil { groupID = store.groups.first?.id }
            do {
                let next = try await services.dev.status()
                status = next
                devMode.setOffset(next.offsetSeconds ?? 0)
            } catch {
                record("상태 조회 실패: \(error.devDescription)")
            }
        }
        .task(id: groupID) { await selectGroup() }
        .onChange(of: current.map(\.id)) { _, ids in
            if let missionID, ids.contains(missionID) { return }
            missionID = ids.first
        }
    }

    // MARK: - Sections

    private var statusSection: some View {
        section("상태") {
            Text("서버 \(services.baseURL.absoluteString) · 유저 \(session.account.profile.nickname)(#\(session.account.userID))\n"
                 + "서버시간 \(status?.serverNow ?? "조회 중") (오프셋 \(status?.offsetSeconds ?? 0)초)\n"
                 + "빌드 \(AppInfo.version)(\(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "-"))")
                .font(.caption13)
                .foregroundStyle(.ink)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
            action("새로고침") {
                run("상태") {
                    let next = try await services.dev.status()
                    status = next
                    devMode.setOffset(next.offsetSeconds ?? 0)
                    return next.serverNow ?? ""
                }
            }
        }
    }

    private var clockSection: some View {
        section("서버 시계") {
            DatePicker("시각", selection: $clock)
                .environment(\.locale, Locale(identifier: "ko_KR"))
                .environment(\.timeZone, TodayMission.seoul)
                .font(.plex(15))
            action("선택 시각으로") {
                run("시계 설정") {
                    let target = Self.isoSeoul(clock)
                    clockResult(try await services.dev.setClock(now: target))
                    await refreshAll()
                    return target
                }
            }
            HStack(spacing: 6) {
                ForEach([("+1시간", 3600), ("+1일", 86_400), ("+7일", 604_800)], id: \.0) { label, seconds in
                    chip(label, enabled: !busy) {
                        run(label) {
                            clockResult(try await services.dev.setClock(plusSeconds: seconds))
                            await refreshAll()
                            return "완료"
                        }
                    }
                }
            }
            action("다음 발행 시각으로", enabled: group?.nextPublishAt != nil) {
                run("다음 발행") {
                    let seconds = max(0, Int(group?.nextPublishAt?.timeIntervalSince(AppClock.now()) ?? 0))
                    clockResult(try await services.dev.setClock(plusSeconds: seconds))
                    await refreshAll()
                    return "완료"
                }
            }
            action("시계 초기화") {
                run("시계 초기화") {
                    clockResult(try await services.dev.resetClock())
                    await refreshAll()
                    return "완료"
                }
            }
        }
    }

    private var targetSection: some View {
        section("대상 그룹 / 미션") {
            chips(store.groups.map { ($0.id, $0.detail.name) }, selected: groupID) { groupID = $0 }
            chips(current.enumerated().map { ($0.element.id, "\($0.offset + 1) · #\($0.element.id)") }, selected: missionID) {
                missionID = $0
                submissionRows = []
            }
        }
    }

    private var missionSection: some View {
        section("미션") {
            action("미션 생성", enabled: groupID != nil) {
                run("미션 생성") {
                    let result = try await services.dev.publish(groupID: groupID)
                    await refreshMission()
                    return "\(result.created?.count ?? 0)개 · \(result.message ?? "")"
                }
            }
            caption("틀 선택 (생성·리롤에 적용)")
            chips(Self.templates.enumerated().map { ($0.offset, $0.element) },
                  selected: template.flatMap { Self.templates.firstIndex(of: $0) }) { template = Self.templates[$0] }
            action("선택 미션 리롤", enabled: groupID != nil && missionID != nil) {
                if let groupID, let missionID { rerollTarget = RerollTarget(groupID: groupID, missionID: missionID, template: nil) }
            }
            action("선택 미션을 \(template ?? "선택한 틀")로 리롤", enabled: groupID != nil && missionID != nil && template != nil) {
                if let groupID, let missionID { rerollTarget = RerollTarget(groupID: groupID, missionID: missionID, template: template) }
            }
            action("선택 틀로 생성", enabled: groupID != nil && template != nil) {
                run("선택 틀 생성") {
                    let result = try await services.dev.publish(groupID: groupID, template: template)
                    await refreshMission()
                    return "\(result.created?.count ?? 0)개"
                }
            }
            action("선택 미션 마감 당기기", enabled: missionID != nil) {
                run("마감") {
                    guard let missionID else { return "" }
                    _ = try await services.dev.deadlineNow(missionID: missionID)
                    await refreshMission()
                    return "완료"
                }
            }
            action("그룹 전체 마감 당기기", enabled: groupID != nil) {
                run("그룹 마감") {
                    guard let groupID else { return "" }
                    let result = try await services.dev.groupDeadlineNow(groupID: groupID)
                    await refreshMission()
                    return result.text
                }
            }
        }
    }

    private var actorSection: some View {
        let options = members.map { ($0.id, $0.displayed.nickname) }
        return section("대리 행동") {
            caption("행동 유저")
            chips(options, selected: memberID) { memberID = $0 }
            caption("콕 받을 유저")
            chips(options, selected: toMemberID) { toMemberID = $0 }
            action("대리 제출", enabled: missionID != nil && memberID != nil) {
                run("대리 제출") {
                    guard let missionID, let memberID else { return "" }
                    _ = try await services.dev.submitAs(missionID: missionID, userID: memberID)
                    await refreshMission()
                    return "완료"
                }
            }
            action("미제출자 전원 제출", enabled: missionID != nil) {
                run("전원 제출") {
                    guard let missionID else { return "" }
                    let result = try await services.dev.submitMissing(missionID: missionID)
                    await refreshMission()
                    return result.map { "\($0.userId):\($0.errorCode ?? "OK")" }.joined(separator: ", ")
                }
            }
            HStack(spacing: 8) {
                ForEach([("콕", "poke")], id: \.1) { label, kind in
                    chip(label, enabled: !busy && groupID != nil && memberID != nil && toMemberID != nil && memberID != toMemberID) {
                        run(label) {
                            guard let groupID, let memberID, let toMemberID else { return "" }
                            _ = try await services.dev.poke(groupID: groupID, fromUserID: memberID, toUserID: toMemberID,
                                                           type: kind, missionID: missionID)
                            return "완료"
                        }
                    }
                }
            }
            action("제출 목록 조회", enabled: missionID != nil) {
                run("제출 목록") {
                    guard let missionID else { return "" }
                    submissionRows = try await services.dev.submissions(missionID: missionID)
                    selectedSubmission = submissionRows.first?.submissionId
                    return "\(submissionRows.count)건"
                }
            }
            chips(submissionRows.map { ($0.submissionId, "\($0.nickname ?? "") #\($0.submissionId)") }, selected: selectedSubmission) {
                selectedSubmission = $0
            }
            chips(Self.stickers.enumerated().map { ($0.offset, $0.element) }, selected: Self.stickers.firstIndex(of: sticker)) {
                sticker = Self.stickers[$0]
            }
            action("반응", enabled: selectedSubmission != nil && memberID != nil) {
                run("반응") {
                    guard let selectedSubmission, let memberID else { return "" }
                    _ = try await services.dev.react(submissionID: selectedSubmission, userID: memberID, stickerType: sticker)
                    return "완료"
                }
            }
            HTextField(placeholder: "댓글", text: $comment)
            action("댓글", enabled: selectedSubmission != nil && memberID != nil && !comment.trimmingCharacters(in: .whitespaces).isEmpty) {
                run("댓글") {
                    guard let selectedSubmission, let memberID else { return "" }
                    _ = try await services.dev.comment(submissionID: selectedSubmission, userID: memberID, content: comment)
                    return "완료"
                }
            }
        }
    }

    private var seedSection: some View {
        section("시드") {
            HTextField(placeholder: "일수 1~90", text: digits($seedDays), keyboard: .numberPad)
            HTextField(placeholder: "멤버 수 2~6", text: digits($seedMembers), keyboard: .numberPad)
            HTextField(placeholder: "하루 미션 수 1~5", text: digits($seedDaily), keyboard: .numberPad)
            action("시드 생성") {
                run("시드 생성") {
                    let seeded = try await services.dev.seed(days: Int(seedDays) ?? 30, memberCount: Int(seedMembers) ?? 5,
                                                            dailyMissionCount: Int(seedDaily) ?? 1)
                    await refreshGroups()
                    groupID = seeded.groupId
                    return "그룹 #\(seeded.groupId), 미션 \(seeded.missionsCreated ?? 0)개 · 안 보이면 dev1로 전환하세요"
                }
            }
            action("[DEV] 그룹 전체 삭제") { confirmReset = true }
        }
    }

    private var insightSection: some View {
        section("인사이트") {
            HStack(spacing: 8) {
                chip("이전 달", enabled: !busy) { month = DataStore.calendar.date(byAdding: .month, value: -1, to: month) ?? month }
                chip(CalendarRepository.monthString(month), selected: true, enabled: false) {}
                chip("다음 달", enabled: !busy) { month = DataStore.calendar.date(byAdding: .month, value: 1, to: month) ?? month }
            }
            action("강제 재생성", enabled: groupID != nil) {
                run("인사이트") {
                    guard let groupID else { return "" }
                    return try await services.dev.regenerateInsight(groupID: groupID, month: CalendarRepository.monthString(month)).text
                }
            }
            action("인사이트 열기", enabled: groupID != nil) {
                if let groupID { path.append(.groupInsight(groupID: groupID)) }
            }
        }
    }

    private var settingsSection: some View {
        section("그룹 설정") {
            HStack(spacing: 8) {
                chip("−", enabled: !busy && daily > 1) { daily -= 1 }
                chip("하루 \(daily)개", selected: true, enabled: false) {}
                chip("+", enabled: !busy && daily < 5) { daily += 1 }
            }
            ForEach(0..<daily, id: \.self) { index in
                DatePicker("\(index + 1)번 슬롯 발행", selection: $slotTimes[index], displayedComponents: .hourAndMinute)
                    .environment(\.locale, Locale(identifier: "ko_KR"))
                    .environment(\.timeZone, TodayMission.seoul)
                    .font(.plex(15))
            }
            action("그룹 설정 저장", enabled: groupID != nil) {
                run("그룹 설정") {
                    guard let groupID else { return "" }
                    _ = try await services.dev.updateSettings(groupID: groupID, dailyMissionCount: daily,
                                                             slotPublishTimes: slotTimes.prefix(daily).map(Self.hourMinute))
                    store.applyServerGroup(try await services.groups.detail(groupID: groupID, myUserID: session.account.userID))
                    return "완료"
                }
            }
        }
    }

    private var resetSection: some View {
        section("초기화") {
            action("로컬 캐시 초기화") {
                run("로컬 캐시") {
                    store.reset()
                    store.setMyProfile(session.account.profile)
                    await refreshGroups()
                    return "완료"
                }
            }
            action("토큰 삭제 후 로그아웃") { session.signOut() }
            action("선택 그룹 삭제", enabled: groupID != nil) { confirmDelete = true }
        }
    }

    private var faultSection: some View {
        section("네트워크 오류 주입") {
            let _ = faultRevision
            ForEach(DevFault.allCases, id: \.self) { fault in
                HStack(spacing: 8) {
                    chip("\(fault.rawValue) (\(DevFaults.count(fault)))", enabled: !busy) {
                        DevFaults.set(fault, count: 0)
                        faultRevision += 1
                    }
                    .layoutPriority(1)
                    chip("+1", enabled: !busy) {
                        DevFaults.set(fault, count: DevFaults.count(fault) + 1)
                        faultRevision += 1
                    }
                    chip("+5", enabled: !busy) {
                        DevFaults.set(fault, count: DevFaults.count(fault) + 5)
                        faultRevision += 1
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var alerts: some View {
        if confirmReset {
            HAlert(title: "[DEV] 그룹을 모두 삭제할까요?", message: "서버의 모든 [DEV] 그룹이 삭제돼요.", confirmTitle: "전체 삭제",
                   onCancel: { confirmReset = false },
                   onConfirm: {
                       confirmReset = false
                       run("전체 삭제") {
                           let result = try await services.dev.reset()
                           devMode.setOffset(0)
                           await refreshGroups()
                           groupID = store.groups.first?.id
                           return "\(result.deletedGroups ?? 0)개 삭제"
                       }
                   })
        } else if confirmDelete {
            HAlert(title: "선택 그룹을 삭제할까요?", message: "#\(groupID.map(String.init) ?? "") 그룹과 기록이 삭제돼요.",
                   confirmTitle: "그룹 삭제",
                   onCancel: { confirmDelete = false },
                   onConfirm: {
                       confirmDelete = false
                       run("그룹 삭제") {
                           guard let id = groupID else { return "" }
                           try await services.dev.deleteGroup(groupID: id)
                           store.leave(id)
                           groupID = store.groups.first?.id
                           return "#\(id) 삭제"
                       }
                   })
        } else if let target = rerollTarget {
            HAlert(title: "이 미션을 새로 뽑을까요?", message: "이미 낸 제출·반응·댓글·콕이 모두 삭제돼요.", confirmTitle: "리롤",
                   onCancel: { rerollTarget = nil },
                   onConfirm: {
                       rerollTarget = nil
                       run("미션 리롤") {
                           let result = try await services.dev.reroll(missionID: target.missionID, template: target.template)
                           store.clearMissionForReroll(target.missionID, groupID: target.groupID)
                           submissionRows = []
                           selectedSubmission = nil
                           await refreshMission()
                           return "\(result.template) · \(result.content)"
                       }
                   })
        }
    }

    // MARK: - 동작

    private func record(_ message: String) {
        results = Array(([message] + results).prefix(20))
        toast = String(message.prefix(100))
    }

    private func run(_ label: String, _ body: @escaping () async throws -> String) {
        guard !busy else { return }
        busy = true
        Task {
            defer { busy = false }
            do {
                record("\(label): \(try await body())")
            } catch is CancellationError {
            } catch {
                record("\(label) 실패: \(error.devDescription)")
            }
        }
    }

    private func clockResult(_ value: DevClockStatusDTO) {
        status = value
        devMode.setOffset(value.offsetSeconds ?? 0)
        clock = AppClock.now()
        record(value.warning ?? "서버 시각 \(value.serverNow ?? "")")
    }

    private func refreshGroups() async {
        if let loaded = try? await services.groups.loadAll(myUserID: session.account.userID) {
            store.applyServerGroups(loaded)
        }
    }

    private func refreshMission() async {
        guard let groupID else { return }
        if let state = try? await services.missions.current(groupID: groupID) {
            store.applyCurrentMission(state, groupID: groupID)
        }
    }

    private func refreshAll() async {
        await refreshGroups()
        await refreshMission()
    }

    private func selectGroup() async {
        missionID = current.first?.id
        memberID = members.first?.id
        toMemberID = members.first { $0.id != memberID }?.id
        submissionRows = []
        guard let groupID else { return }
        if let detail = try? await services.groups.detail(groupID: groupID, myUserID: session.account.userID) {
            store.applyServerGroup(detail)
            memberID = memberID ?? detail.members.first?.id
            toMemberID = toMemberID ?? detail.members.first { $0.id != memberID }?.id
        }
        await refreshMission()
    }

    // MARK: - 모양

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.plex(17, .bold))
                .foregroundStyle(.ink)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.button, style: .continuous))
    }

    private func action(_ title: String, enabled: Bool = true, _ perform: @escaping () -> Void) -> some View {
        HButton(title, kind: .secondary, height: 42, action: perform)
            .disabled(busy || !enabled)
    }

    private func caption(_ text: String) -> some View {
        Text(text)
            .font(.caption13)
            .foregroundStyle(.textSecondary)
    }

    private func chip(_ label: String, selected: Bool = false, enabled: Bool, _ perform: @escaping () -> Void) -> some View {
        Button(action: perform) {
            Text(label)
                .font(.caption13)
                .foregroundStyle(.ink)
                .lineLimit(1)
                .padding(.horizontal, 10)
                .padding(.vertical, 9)
                .frame(maxWidth: .infinity)
                .background(selected ? Color.accentSubtle : Color.bg, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func chips(_ items: [(Int, String)], selected: Int?, _ onSelect: @escaping (Int) -> Void) -> some View {
        FlowLayout(spacing: 6) {
            ForEach(items, id: \.0) { id, label in
                Button { onSelect(id) } label: {
                    Text(label)
                        .font(.caption13)
                        .foregroundStyle(.ink)
                        .lineLimit(1)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 9)
                        .background(id == selected ? Color.accentSubtle : Color.bg, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(busy)
            }
        }
    }

    private func digits(_ text: Binding<String>) -> Binding<String> {
        Binding(get: { text.wrappedValue }, set: { text.wrappedValue = $0.filter(\.isNumber) })
    }

    private static func time(hour: Int, minute: Int) -> Date {
        DataStore.calendar.date(bySettingHour: hour, minute: minute, second: 0, of: .now) ?? .now
    }

    /// 서울 시각 "09:00"
    private static func hourMinute(_ date: Date) -> String {
        let parts = DataStore.calendar.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }

    /// 서울 시각 ISO-8601 (`2026-10-18T21:00:00+09:00`)
    private static func isoSeoul(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TodayMission.seoul
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }
}
