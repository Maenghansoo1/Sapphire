import Foundation
import Observation

/// 새 틀(한 단어·후기·밸런스)의 글·숫자·선택 필드. nil 인 필드는 보내지 않아요.
struct ScalarAnswer: Equatable {
    var number: Int? = nil
    var choice: String? = nil
    var text: String? = nil
    var caption: String? = nil
}

/// 미션 수행 화면에서 올리는 내용
enum SubmissionContent {
    /// 그림(PNG)·사진(JPEG)
    case image(Data, fileExtension: String, aiGuesses: [AiGuessEntry] = [])
    case text(String)
    case scalar(ScalarAnswer)
}

/// 초대 코드 조회 결과
enum InviteLookupError: Error, Equatable {
    case notFound, full, alreadyMember, tooMany, network, unavailable

    var message: String {
        switch self {
        case .notFound: "초대 코드를 찾을 수 없어요. 다시 확인해 주세요."
        case .full: "이 그룹은 정원이 다 찼어요."
        case .alreadyMember: "이미 참여 중인 그룹이에요."
        case .tooMany: "시도가 너무 많아요. 잠시 후 다시 시도해 주세요"
        case .network: "네트워크 연결을 확인해 주세요"
        case .unavailable: "잠시 후 다시 시도해 주세요"
        }
    }

    init(error: Error) {
        if error is NetworkError {
            self = .network
            return
        }
        switch error.apiCode {
        case "INVITE_NOT_FOUND": self = .notFound
        case "GROUP_FULL": self = .full
        case "ALREADY_MEMBER": self = .alreadyMember
        case "TOO_MANY_REQUESTS": self = .tooMany
        default: self = .unavailable
        }
    }
}

/// 서버에서 불러온 상태
enum LoadState: Hashable {
    case notLoaded, loaded, failed
}

/// 그룹 하나와 그 그룹의 미션
struct StoredGroup: Identifiable, Hashable {
    var detail: GroupDetail
    /// 서버 연결 뒤에는 지금 열린 미션들(슬롯 순서)만 들고 있어요. 체험 데이터는 지난 미션도 있어요.
    var missions: [StoredMission] = []
    /// 그룹 목록이 알려 준 오늘 미션 요약 (홈 카드)
    var summaries: [MissionSummary] = []
    /// 미션이 없을 때 다음 미션이 열리는 시각
    var nextPublishAt: Date?
    var missionState: LoadState = .notLoaded

    var id: Int { detail.id }
    var summary: MissionSummary? { summaries.first }
}

/// 미션 하나와 제출물
struct StoredMission: Identifiable, Hashable {
    let id: Int
    var type: MissionType?
    var topic: String
    var number: Int
    var opensAt: Date
    var closesAt: Date
    var submissions: [Submission] = []
    /// 이 미션에서 내가 보낸 재촉 (userId → 종류)
    var poked: [Int: Set<PokeKind>] = [:]
    var deadlineAt: Date? = nil
    var template: String = ""
    var spec = MissionSpec()
    /// 서버가 알려 준 멤버별 제출 여부 (userId → 제출)
    var serverMembers: [Int: Bool] = [:]
    var serverSubmittedByMe = false
    /// 결과 피드를 서버에서 받아 왔는지
    var feedLoaded = false
}

/// 화면이 읽는 앱 안 저장소. 서버에서 받은 값을 들고 있고, 화면이 바뀌면 다시 받아 와요.
/// 로그아웃하면 비워요.
@MainActor
@Observable
final class DataStore {
    private(set) var groups: [StoredGroup] = []
    private(set) var groupsState: LoadState = .notLoaded
    /// 내 기본 프로필 (마이페이지)
    private(set) var myProfile: Profile = .mockMine
    /// 17 마이페이지 활동 요약 — 서버에서 아직 못 받았으면 nil
    private(set) var stats: ActivityStats?
    /// 15 달력 — (그룹, 달) → 그 달 기록
    private var calendars: [CalendarKey: [DayRecord]] = [:]
    /// 달력에서 연 지난 미션의 피드 (missionID → 제출물)
    private var pastFeeds: [Int: [Submission]] = [:]
    /// 달력에서 연 지난 미션의 틀·빈칸(밸런스 선택지, 그림 대상). 달력 응답에는 없어서 따로 받아 둬요.
    private var pastSpecs: [Int: (template: String, spec: MissionSpec)] = [:]

    private struct CalendarKey: Hashable {
        let groupID: Int
        let month: String
    }

    init() {}

    // MARK: - 내 정보

    func setMyProfile(_ profile: Profile) {
        myProfile = profile
        for index in groups.indices {
            for memberIndex in groups[index].detail.members.indices where groups[index].detail.members[memberIndex].isMe {
                groups[index].detail.members[memberIndex].defaultProfile = profile
            }
        }
    }

    /// 로그아웃·회원 탈퇴·유저 전환 — 모두 지워요
    func reset() {
        groups = []
        groupsState = .notLoaded
        myProfile = .mockMine
        stats = nil
        calendars = [:]
        pastFeeds = [:]
        pastSpecs = [:]
    }

    /// 17 마이페이지 활동 요약 (`/api/me/stats`)
    func applyStats(_ stats: ActivityStats) {
        self.stats = stats
    }

    // MARK: - 그룹 (서버)

    func group(_ id: Int) -> GroupDetail? {
        groups.first { $0.id == id }?.detail
    }

    func storedGroup(_ id: Int) -> StoredGroup? {
        groups.first { $0.id == id }
    }

    /// 04 홈 카드 — 오늘 미션 요약은 그룹 목록 응답을 그대로 써요.
    var summaries: [GroupSummary] {
        groups.map { stored in
            let detail = stored.detail
            let summary = stored.summary
            let missionCount = stored.summaries.count
            let mySubmittedCount = stored.summaries.filter(\.submittedByMe).count
            return GroupSummary(
                id: detail.id,
                name: detail.name,
                relationship: detail.relationship,
                members: detail.members.map(\.displayed.avatar),
                startDate: detail.startDate,
                missionType: summary?.type,
                topic: summary?.topic,
                submittedCount: summary?.submittedCount ?? 0,
                didSubmit: missionCount > 0 && mySubmittedCount == missionCount,
                missionCount: missionCount,
                mySubmittedCount: mySubmittedCount
            )
        }
    }

    /// 홈에서 받은 그룹 목록으로 바꿔요. 이미 받아 둔 미션은 유지해요.
    func applyServerGroups(_ loaded: [LoadedGroup]) {
        let old = Dictionary(groups.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        groups = loaded.map { item in
            let previous = old[item.detail.id]
            return StoredGroup(
                detail: item.detail,
                missions: previous?.missions ?? [],
                summaries: item.missions,
                nextPublishAt: previous?.nextPublishAt,
                missionState: previous?.missionState ?? .notLoaded
            )
        }
        groupsState = .loaded
    }

    /// 그룹 하나를 넣거나 바꿔요 (만들기·참여·16 그룹 정보).
    func applyServerGroup(_ detail: GroupDetail) {
        if let index = groups.firstIndex(where: { $0.id == detail.id }) {
            groups[index].detail = detail
        } else {
            groups.append(StoredGroup(detail: detail))
        }
    }

    func markGroupsFailed() {
        if groupsState == .notLoaded { groupsState = .failed }
    }

    func leave(_ groupID: Int) {
        groups.removeAll { $0.id == groupID }
    }

    func setTopicTime(_ time: DateComponents, groupID: Int) {
        update(groupID) { $0.detail.topicTime = time }
    }

    func setNotify(_ on: Bool, groupID: Int) {
        update(groupID) { $0.detail.notify = on }
    }

    // MARK: - 미션 (서버)

    /// 05 화면에서 받은 지금 미션들. 같은 미션(같은 주제·틀·빈칸)이면 받아 둔 피드·재촉·제출 상태를 유지해요.
    func applyCurrentMission(_ state: CurrentMissionState, groupID: Int) {
        update(groupID) { group in
            let previousByID = Dictionary(group.missions.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            group.missions = state.missions.map { row in
                let previous = previousByID[row.id].flatMap {
                    $0.topic == row.topic && $0.template == row.template && $0.spec == row.spec ? $0 : nil
                }
                return StoredMission(
                    id: row.id,
                    type: row.type,
                    topic: row.topic,
                    number: row.number,
                    opensAt: row.publishedAt,
                    closesAt: row.closesAt,
                    submissions: previous?.submissions ?? [],
                    poked: Self.mergePoked(row.pokedByMe, previous?.poked ?? [:]),
                    deadlineAt: row.deadlineAt,
                    template: row.template,
                    spec: row.spec,
                    serverMembers: row.members.merging((previous?.serverMembers ?? [:]).filter(\.value)) { a, b in a || b },
                    serverSubmittedByMe: row.submittedByMe || (previous?.serverSubmittedByMe ?? false),
                    feedLoaded: previous?.feedLoaded ?? false
                )
            }
            let byID = Dictionary(state.missions.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            group.summaries = group.summaries.map { summary in
                guard let row = byID[summary.missionID] else { return summary }
                return MissionSummary(missionID: row.id, type: row.type, topic: row.topic,
                                      submittedCount: row.members.values.filter { $0 }.count, submittedByMe: row.submittedByMe)
            }
            group.nextPublishAt = state.nextPublishAt
            group.missionState = .loaded
        }
    }

    /// 개발자 모드 리롤 — 서버가 제출·반응·댓글·콕을 지웠으니 앱이 들고 있던 것도 지워요.
    func clearMissionForReroll(_ missionID: Int, groupID: Int) {
        update(groupID) { group in
            for index in group.missions.indices where group.missions[index].id == missionID {
                group.missions[index].submissions = []
                group.missions[index].poked = [:]
                group.missions[index].serverMembers = [:]
                group.missions[index].serverSubmittedByMe = false
                group.missions[index].feedLoaded = false
            }
            for index in group.summaries.indices where group.summaries[index].missionID == missionID {
                group.summaries[index].submittedCount = 0
                group.summaries[index].submittedByMe = false
            }
        }
    }

    func markMissionFailed(_ groupID: Int) {
        update(groupID) { group in
            if group.missions.isEmpty { group.missionState = .failed }
        }
    }

    /// 05 화면용 지금 열린 미션들. 체험 데이터(음수 id)는 지난 미션도 들고 있어서 마지막 것만 보여요.
    func todayMissions(_ groupID: Int) -> [TodayMission] {
        guard let stored = groups.first(where: { $0.id == groupID }) else { return [] }
        let current = groupID < 0 ? Array(stored.missions.suffix(1)) : stored.missions
        return current.map { Self.todayMission(stored, $0) }
    }

    func todayMission(_ groupID: Int) -> TodayMission? {
        todayMissions(groupID).first
    }

    func todayMission(_ groupID: Int, missionID: Int) -> TodayMission? {
        todayMissions(groupID).first { $0.id == missionID }
    }

    private static func todayMission(_ stored: StoredGroup, _ mission: StoredMission) -> TodayMission {
        let members = stored.detail.members.map { member in
            MissionMember(
                id: member.id,
                profile: member.displayed,
                isMe: member.isMe,
                isDone: mission.serverMembers[member.id] == true || (member.isMe && mission.serverSubmittedByMe),
                poked: mission.poked[member.id] ?? []
            )
        }
        return TodayMission(
            id: mission.id,
            group: stored.detail,
            type: mission.type,
            topic: mission.topic,
            number: mission.number,
            opensAt: mission.opensAt,
            closesAt: mission.closesAt,
            members: members,
            deadlineAt: mission.deadlineAt,
            template: mission.template,
            spec: mission.spec
        )
    }

    /// 06~09 올리기 성공 — 서버가 돌려준 제출을 반영해요.
    func applySubmitted(_ submission: Submission, missionID: Int, groupID: Int) {
        update(groupID) { group in
            guard let index = group.missions.firstIndex(where: { $0.id == missionID }) else { return }
            group.missions[index].serverSubmittedByMe = true
            if let myID = group.detail.me?.id { group.missions[index].serverMembers[myID] = true }
            if !group.missions[index].submissions.contains(where: { $0.id == submission.id }) {
                group.missions[index].submissions.append(submission)
            }
            for summaryIndex in group.summaries.indices where group.summaries[summaryIndex].missionID == missionID {
                if !group.summaries[summaryIndex].submittedByMe { group.summaries[summaryIndex].submittedCount += 1 }
                group.summaries[summaryIndex].submittedByMe = true
            }
        }
    }

    /// 서버가 재촉을 받은 뒤에만 불러요. 그사이 미션이 바뀌었으면 반영하지 않아요.
    func markPoked(_ memberID: Int, kind: PokeKind, missionID: Int, groupID: Int) {
        update(groupID) { group in
            for index in group.missions.indices where group.missions[index].id == missionID {
                group.missions[index].poked = Self.mergePoked(group.missions[index].poked, [memberID: [kind]])
            }
        }
    }

    /// 재촉은 취소가 없어서 서버 값과 앱이 아는 값을 합쳐요 (보낸 직후 늦게 온 새로고침이 표시를 지우지 않게).
    private static func mergePoked(_ server: [Int: Set<PokeKind>], _ local: [Int: Set<PokeKind>]) -> [Int: Set<PokeKind>] {
        server.merging(local) { a, b in a.union(b) }
    }

    /// 10~13 결과 피드 — 달력에서 연 지난 미션이면 pastFeeds 에 따로 둬요.
    func applyFeed(_ submissions: [Submission], missionID: Int, groupID: Int) {
        let isCurrent = groups.first { $0.id == groupID }?.missions.contains { $0.id == missionID } ?? false
        if !isCurrent && calendarRecord(groupID: groupID, missionID: missionID) != nil {
            pastFeeds[missionID] = submissions
            return
        }
        update(groupID) { group in
            for index in group.missions.indices where group.missions[index].id == missionID {
                group.missions[index].submissions = submissions
                group.missions[index].feedLoaded = true
            }
        }
    }

    func applyMissionSpec(_ missionID: Int, template: String, spec: MissionSpec) {
        pastSpecs[missionID] = (template, spec)
    }

    /// 결과 화면이 지난 미션의 틀·빈칸을 서버에서 받아 와야 하는지. 지금 미션은 이미 알고 있어요.
    func needsMissionSpec(groupID: Int, missionID: Int) -> Bool {
        guard let stored = groups.first(where: { $0.id == groupID }) else { return false }
        return !stored.missions.contains { $0.id == missionID } && pastSpecs[missionID] == nil
    }

    // MARK: - 결과

    /// 10~13 결과 — 피드를 받아 온 미션만 (내가 올려야 서버가 피드를 줘요, §5-2)
    func result(groupID: Int, missionID: Int) -> MissionResult? {
        guard let stored = groups.first(where: { $0.id == groupID }) else { return nil }
        guard let mission = stored.missions.first(where: { $0.id == missionID }) else {
            return pastResult(stored, missionID: missionID)
        }
        guard mission.feedLoaded, let type = mission.type else { return nil }
        let done = Set(mission.submissions.compactMap(\.authorID))
        return MissionResult(
            id: mission.id,
            groupName: stored.detail.name,
            type: type,
            topic: mission.topic,
            date: mission.opensAt,
            memberCount: stored.detail.members.count,
            submissions: mission.submissions,
            pending: stored.detail.members.filter { !done.contains($0.id) }.map(\.displayed),
            template: mission.template,
            spec: mission.spec
        )
    }

    /// 달력에서 연 지난 미션 — 미션 정보는 달력 기록, 제출물은 pastFeeds 에서 가져와요.
    private func pastResult(_ stored: StoredGroup, missionID: Int) -> MissionResult? {
        guard let record = calendarRecord(groupID: stored.id, missionID: missionID),
              let submissions = pastFeeds[missionID] else { return nil }
        let done = Set(submissions.compactMap(\.authorID))
        // 틀은 미션 정보가 아직 없어도 제출물에서 알 수 있어요 (한 미션의 제출은 모두 같은 틀).
        let known = pastSpecs[missionID] ?? (submissions.first?.template ?? "", MissionSpec())
        return MissionResult(
            id: missionID,
            groupName: stored.detail.name,
            type: record.type,
            topic: record.topic,
            date: record.date,
            memberCount: record.memberCount,
            submissions: submissions,
            pending: stored.detail.members.filter { !done.contains($0.id) }.map(\.displayed),
            template: known.template,
            spec: known.spec
        )
    }

    // MARK: - 기록

    /// 15 캘린더 — 서버에서 받은 그 달 기록 (`/api/groups/{id}/calendar`)
    func applyCalendar(_ records: [DayRecord], groupID: Int, month: Date) {
        calendars[CalendarKey(groupID: groupID, month: CalendarRepository.monthString(month))] = records
    }

    /// 아직 불러오지 않은 달이면 nil
    func records(_ groupID: Int, month: Date) -> [DayRecord]? {
        calendars[CalendarKey(groupID: groupID, month: CalendarRepository.monthString(month))]
    }

    func calendarRecord(groupID: Int, missionID: Int) -> DayRecord? {
        calendars.filter { $0.key.groupID == groupID }.values.joined().first { $0.missionID == missionID }
    }

    // MARK: - 앱 안 체험 (미리보기 전용 — 실제 화면은 GroupRepository 를 써요)

    /// 체험 그룹 코드 조회 (DemoSeed)
    func lookUpInvite(code: String) -> Result<GroupInvitePreview, InviteLookupError> {
        if groups.contains(where: { $0.detail.inviteCode == code }) {
            return .failure(.alreadyMember)
        }
        guard let demo = DemoSeed.group(code: code) else { return .failure(.notFound) }
        let preview = GroupInvitePreview(
            name: demo.detail.name,
            relationship: demo.detail.relationship,
            members: demo.detail.members.map(\.displayed.avatar)
        )
        return preview.isFull ? .failure(.full) : .success(preview)
    }

    @discardableResult
    func join(code: String) -> Int? {
        guard case .success = lookUpInvite(code: code), var demo = DemoSeed.group(code: code) else { return nil }
        demo.detail.members.append(GroupMember(id: LocalID.next(), defaultProfile: myProfile, isMe: true))
        groups.append(demo)
        groupsState = .loaded
        return demo.id
    }

    @discardableResult
    func createGroup(name: String, relationship: Relationship, startDate: Date?) -> Int {
        var detail = GroupDetail(
            id: LocalID.next(),
            name: name,
            relationship: relationship,
            createdAt: AppClock.now(),
            missionCount: 0,
            inviteCode: "H4W3K2",
            topicTime: DateComponents(hour: 9, minute: 0),
            notify: true,
            members: [GroupMember(id: LocalID.next(), defaultProfile: myProfile, isMe: true, isOwner: true)]
        )
        detail.startDate = relationship == .lover ? startDate : nil
        groups.append(StoredGroup(detail: detail, missionState: .loaded))
        groupsState = .loaded
        return detail.id
    }

    /// 미리보기에서 오늘 미션을 올린 상태로 만들어요.
    func markSubmittedForPreview(_ groupID: Int) {
        update(groupID) { group in
            guard let index = group.missions.indices.last else { return }
            group.missions[index].serverSubmittedByMe = true
            group.missions[index].feedLoaded = true
            if let me = group.detail.me {
                group.missions[index].serverMembers[me.id] = true
                let type = group.missions[index].type ?? .write
                group.missions[index].submissions.append(Submission(
                    id: LocalID.next(), author: me.displayed, isMine: true, type: type, createdAt: AppClock.now(),
                    text: type == .write ? "미리보기에서 올린 글이에요." : nil,
                    authorID: me.id,
                    template: group.missions[index].template
                ))
            }
        }
    }

    // MARK: - 내부

    nonisolated static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TodayMission.seoul
        return calendar
    }()

    private func update(_ groupID: Int, _ change: (inout StoredGroup) -> Void) {
        guard let index = groups.firstIndex(where: { $0.id == groupID }) else { return }
        change(&groups[index])
    }
}

/// 유형별 주제 풀 (체험 데이터용)
enum TopicPool {
    static func topics(for type: MissionType) -> [String] {
        switch type {
        case .draw: ["요즘 제일 웃겼던 순간", "오늘 기분을 날씨로 그려줘", "내 방 책상 위", "지금 제일 먹고 싶은 것", "서로의 첫인상"]
        case .photo: ["지금 창밖으로 보이는 풍경", "오늘 먹은 것 중 최고", "지금 신고 있는 신발", "오늘의 하늘", "내 주변에서 가장 파란 것"]
        case .write: ["오늘 들은 가장 따뜻한 말", "너에게 해주고 싶은 말 한 줄", "요즘 빠져 있는 것", "오늘 하루를 세 단어로", "지금 가장 고마운 사람"]
        }
    }

    /// 목업 미션에 붙이는 대표 틀 (서버 미션은 늘 실제 틀을 가져와요)
    static func template(for type: MissionType) -> String {
        switch type {
        case .draw: MissionTemplates.drawSketch
        case .photo: MissionTemplates.photoNow
        case .write: MissionTemplates.textFillBlank
        }
    }
}
