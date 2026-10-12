import Foundation
import Observation

/// 미션 수행 화면에서 올리는 내용
enum SubmissionContent {
    /// 그림(PNG)·사진(JPEG)
    case image(Data, fileExtension: String)
    case text(String)
    case voice(fileURL: URL, duration: TimeInterval, waveform: [CGFloat])
}

/// 초대 코드 조회 결과
enum InviteLookupError: Error, Equatable {
    case notFound, full, alreadyMember, network, unavailable

    var message: String {
        switch self {
        case .notFound: "초대 코드를 찾을 수 없어요. 다시 확인해 주세요."
        case .full: "이 그룹은 정원이 다 찼어요."
        case .alreadyMember: "이미 참여 중인 그룹이에요."
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
    /// 오래된 순 (서버 연결 뒤에는 지금 열린 미션 하나만 들고 있어요)
    var missions: [StoredMission] = []
    /// 그룹 목록이 알려 준 오늘 미션 요약 (홈 카드)
    var summary: MissionSummary?
    /// 미션이 없을 때 다음 미션이 열리는 시각
    var nextPublishAt: Date?
    var missionState: LoadState = .notLoaded

    var id: Int { detail.id }
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
    /// 오늘 내가 콕 찌른 멤버
    var poked: Set<Int> = []
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

    /// 로그아웃·회원 탈퇴 — 모두 지워요
    func reset() {
        groups = []
        groupsState = .notLoaded
        myProfile = .mockMine
        stats = nil
        calendars = [:]
        pastFeeds = [:]
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
            return GroupSummary(
                id: detail.id,
                name: detail.name,
                relationship: detail.relationship,
                members: detail.members.map(\.displayed.avatar),
                startDate: detail.startDate,
                missionType: summary?.type,
                topic: summary?.topic,
                submittedCount: summary?.submittedCount ?? 0,
                didSubmit: summary?.submittedByMe ?? false
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
                summary: item.mission,
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

    // MARK: - 미션 (서버)

    /// 05 화면에서 받은 지금 미션. 같은 미션이면 받아 둔 피드·제출 상태를 유지해요.
    func applyCurrentMission(_ state: CurrentMissionState, groupID: Int) {
        update(groupID) { group in
            let previous = group.missions.last.flatMap { $0.id == state.mission?.id ? $0 : nil }
            let current = state.mission.map { row in
                StoredMission(
                    id: row.id,
                    type: row.type,
                    topic: row.topic,
                    number: row.number,
                    opensAt: row.publishedAt,
                    closesAt: row.closesAt,
                    submissions: previous?.submissions ?? [],
                    poked: previous?.poked ?? [],
                    deadlineAt: row.deadlineAt,
                    template: row.template,
                    spec: row.spec,
                    serverMembers: row.members.merging((previous?.serverMembers ?? [:]).filter(\.value)) { a, b in a || b },
                    serverSubmittedByMe: row.submittedByMe || (previous?.serverSubmittedByMe ?? false),
                    feedLoaded: previous?.feedLoaded ?? false
                )
            }
            group.missions = current.map { [$0] } ?? []
            group.nextPublishAt = state.nextPublishAt
            group.missionState = .loaded
        }
    }

    func markMissionFailed(_ groupID: Int) {
        update(groupID) { group in
            if group.missions.isEmpty { group.missionState = .failed }
        }
    }

    /// 05 화면용 오늘 미션
    func todayMission(_ groupID: Int) -> TodayMission? {
        guard let stored = groups.first(where: { $0.id == groupID }), let mission = stored.missions.last else { return nil }
        let members = stored.detail.members.map { member in
            MissionMember(
                id: member.id,
                profile: member.displayed,
                isMe: member.isMe,
                isDone: mission.serverMembers[member.id] == true || (member.isMe && mission.serverSubmittedByMe),
                pokedToday: mission.poked.contains(member.id)
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
            guard let index = group.missions.indices.last, group.missions[index].id == missionID else { return }
            group.missions[index].serverSubmittedByMe = true
            if let myID = group.detail.me?.id { group.missions[index].serverMembers[myID] = true }
            if !group.missions[index].submissions.contains(where: { $0.id == submission.id }) {
                group.missions[index].submissions.append(submission)
            }
            if var summary = group.summary, summary.missionID == missionID {
                if !summary.submittedByMe { summary.submittedCount += 1 }
                summary.submittedByMe = true
                group.summary = summary
            }
        }
    }

    /// 10~13 결과 피드 — 달력에서 연 지난 미션이면 pastFeeds 에 따로 둬요.
    func applyFeed(_ submissions: [Submission], missionID: Int, groupID: Int) {
        let isCurrent = groups.first { $0.id == groupID }?.missions.last?.id == missionID
        if !isCurrent && calendarRecord(groupID: groupID, missionID: missionID) != nil {
            pastFeeds[missionID] = submissions
            return
        }
        update(groupID) { group in
            guard let index = group.missions.indices.last, group.missions[index].id == missionID else { return }
            group.missions[index].submissions = submissions
            group.missions[index].feedLoaded = true
        }
    }

    /// 콕 찌르기 — 서버 기능이 생기기 전까지 화면에서는 숨겨요.
    func poke(_ memberID: Int, groupID: Int) {
        update(groupID) { group in
            guard let index = group.missions.indices.last else { return }
            group.missions[index].poked.insert(memberID)
        }
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
            pending: stored.detail.members.filter { !done.contains($0.id) }.map(\.displayed)
        )
    }

    /// 달력에서 연 지난 미션 — 미션 정보는 달력 기록, 제출물은 pastFeeds 에서 가져와요.
    private func pastResult(_ stored: StoredGroup, missionID: Int) -> MissionResult? {
        guard let record = calendarRecord(groupID: stored.id, missionID: missionID),
              let submissions = pastFeeds[missionID] else { return nil }
        let done = Set(submissions.compactMap(\.authorID))
        return MissionResult(
            id: missionID,
            groupName: stored.detail.name,
            type: record.type,
            topic: record.topic,
            date: record.date,
            memberCount: record.memberCount,
            submissions: submissions,
            pending: stored.detail.members.filter { !done.contains($0.id) }.map(\.displayed)
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
            createdAt: .now,
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
                    id: LocalID.next(), author: me.displayed, isMine: true, type: type, createdAt: .now,
                    text: type == .write ? "미리보기에서 올린 글이에요." : nil,
                    duration: type == .voice ? 8 : nil,
                    waveform: type == .voice ? Array(repeating: 0.5, count: 28) : [],
                    authorID: me.id
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
        case .voice: ["오늘 하루를 10초로 말해줘", "요즘 흥얼거리는 노래 한 소절", "지금 들리는 소리", "내일의 나에게 한마디", "아침 인사 한마디"]
        }
    }
}
