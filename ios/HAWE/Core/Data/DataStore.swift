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
    case notFound, full, alreadyMember

    var message: String {
        switch self {
        case .notFound: "초대 코드를 찾을 수 없어요. 다시 확인해 주세요."
        case .full: "이 그룹은 정원이 다 찼어요."
        case .alreadyMember: "이미 참여 중인 그룹이에요."
        }
    }
}

/// 그룹 하나와 그 그룹의 미션 기록
struct StoredGroup: Identifiable, Hashable {
    var detail: GroupDetail
    /// 오래된 순
    var missions: [StoredMission] = []

    var id: UUID { detail.id }
}

/// 하루치 미션과 제출물
struct StoredMission: Identifiable, Hashable {
    let id: UUID
    var type: MissionType
    var topic: String
    var number: Int
    var opensAt: Date
    var closesAt: Date
    var submissions: [Submission] = []
    /// 오늘 내가 콕 찌른 멤버
    var poked: Set<UUID> = []
}

/// 앱 안 임시 저장소 — 서버를 붙이기 전까지 모든 화면이 여기서 데이터를 읽고 써요.
/// 앱을 끄면 사라져요. Spring 서버를 붙일 때는 이 클래스의 공개 메서드 안쪽만 API 호출로 바꾸면 돼요.
@MainActor
@Observable
final class DataStore {
    private(set) var groups: [StoredGroup] = []
    /// 내 기본 프로필 (마이페이지)
    private(set) var myProfile: Profile = .mockMine

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

    /// 회원 탈퇴 — 모두 지워요 (CLAUDE.md §5-5)
    func reset() {
        groups = []
        myProfile = .mockMine
    }

    /// 17 마이페이지 활동 요약
    var stats: ActivityStats {
        let mine = groups.flatMap { group in
            group.missions.compactMap { mission in mission.submissions.first(where: \.isMine).map { (mission, $0) } }
        }
        var byType: [MissionType: Int] = [:]
        for (mission, _) in mine { byType[mission.type, default: 0] += 1 }
        return ActivityStats(
            completedMissions: mine.count,
            streakDays: streak(days: Set(mine.map { Self.calendar.startOfDay(for: $0.0.opensAt) })),
            groupCount: groups.count,
            byType: byType
        )
    }

    /// 오늘(또는 어제)부터 거꾸로 하루도 빠지지 않고 올린 날 수
    private func streak(days: Set<Date>) -> Int {
        var day = Self.calendar.startOfDay(for: .now)
        if !days.contains(day) {
            day = Self.calendar.date(byAdding: .day, value: -1, to: day) ?? day
        }
        var count = 0
        while days.contains(day) {
            count += 1
            day = Self.calendar.date(byAdding: .day, value: -1, to: day) ?? day
        }
        return count
    }

    // MARK: - 그룹

    func group(_ id: UUID) -> GroupDetail? {
        groups.first { $0.id == id }?.detail
    }

    /// 04 홈 카드
    var summaries: [GroupSummary] {
        groups.map { stored in
            let detail = stored.detail
            let mission = stored.missions.last
            return GroupSummary(
                id: detail.id,
                name: detail.name,
                relationship: detail.relationship,
                members: detail.members.map(\.displayed.avatar),
                startDate: detail.startDate,
                missionType: mission?.type ?? .draw,
                topic: mission?.topic ?? "",
                submittedCount: mission?.submissions.count ?? 0,
                didSubmit: mission?.submissions.contains(where: \.isMine) ?? false
            )
        }
    }

    /// 02 그룹 만들기 — 나 혼자, 방장. 오늘 미션도 바로 만들어요.
    @discardableResult
    func createGroup(name: String, relationship: Relationship, startDate: Date?) -> UUID {
        var detail = GroupDetail(
            id: UUID(),
            name: name,
            relationship: relationship,
            createdAt: .now,
            missionCount: 0,
            inviteCode: makeInviteCode(),
            topicTime: DateComponents(hour: 9, minute: 0),
            notify: true,
            members: [GroupMember(id: UUID(), defaultProfile: myProfile, isMe: true, isOwner: true)]
        )
        detail.startDate = relationship == .lover ? startDate : nil
        groups.append(StoredGroup(detail: detail))
        refreshTodayMissions()
        return detail.id
    }

    /// 03 초대 코드 조회 — 내 그룹 코드는 '이미 참여 중', 체험용 그룹은 DemoSeed 에 있어요.
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

    /// 03 참여하기
    @discardableResult
    func join(code: String) -> UUID? {
        guard case .success = lookUpInvite(code: code), var demo = DemoSeed.group(code: code) else { return nil }
        demo.detail.members.append(GroupMember(id: UUID(), defaultProfile: myProfile, isMe: true))
        groups.append(demo)
        refreshTodayMissions()
        return demo.id
    }

    /// 16 그룹 나가기 — 내 작품·댓글은 남아요(사용자 결정). 방장이면 가장 오래된 멤버에게 넘겨요(§5-3).
    /// 임시 저장소에서는 내 목록에서 그룹을 빼기만 해요.
    func leave(_ groupID: UUID) {
        groups.removeAll { $0.id == groupID }
    }

    func setTopicTime(_ time: DateComponents, for groupID: UUID) {
        update(groupID) { $0.detail.topicTime = time }
    }

    func setNotify(_ on: Bool, for groupID: UUID) {
        update(groupID) { $0.detail.notify = on }
    }

    /// 16-1 그룹 내 프로필 (nil 이면 기본 프로필)
    func setMyGroupProfile(_ profile: Profile?, for groupID: UUID) {
        update(groupID) { group in
            guard let index = group.detail.members.firstIndex(where: \.isMe) else { return }
            group.detail.members[index].groupProfile = profile
        }
    }

    // MARK: - 미션

    /// 그룹마다 지금 열려 있는 미션이 없으면 만들어요 (CLAUDE.md §5-1 — 원래는 서버 스케줄이 해요).
    func refreshTodayMissions(now: Date = .now) {
        for index in groups.indices {
            let window = TodayMission.window(topicTime: groups[index].detail.topicTime, now: now)
            if let last = groups[index].missions.last, last.opensAt >= window.opens { continue }
            let mission = makeMission(for: groups[index], opensAt: window.opens, closesAt: window.closes)
            groups[index].missions.append(mission)
            groups[index].detail.missionCount = groups[index].missions.count
        }
    }

    /// 05 화면용 오늘 미션
    func todayMission(_ groupID: UUID) -> TodayMission? {
        guard let stored = groups.first(where: { $0.id == groupID }), let mission = stored.missions.last else { return nil }
        let members = stored.detail.members.map { member in
            MissionMember(
                id: member.id,
                profile: member.displayed,
                isMe: member.isMe,
                isDone: mission.submissions.contains { $0.authorID == member.id },
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
            members: members
        )
    }

    /// 06~09 올리기 — 한 번 올리면 수정·삭제할 수 없어요 (§5-2)
    func submit(_ content: SubmissionContent, groupID: UUID) {
        update(groupID) { group in
            guard let me = group.detail.members.first(where: \.isMe),
                  let index = group.missions.indices.last,
                  !group.missions[index].submissions.contains(where: \.isMine) else { return }
            var submission = Submission(
                id: UUID(),
                author: me.displayed,
                isMine: true,
                type: group.missions[index].type,
                createdAt: .now,
                likeCount: 0
            )
            submission.authorID = me.id
            switch content {
            case .image(let data, let ext):
                submission.mediaURL = Self.saveMedia(data, fileExtension: ext)
            case .text(let text):
                submission.text = text
            case .voice(let fileURL, let duration, let waveform):
                if let data = try? Data(contentsOf: fileURL) {
                    submission.mediaURL = Self.saveMedia(data, fileExtension: "m4a")
                }
                submission.duration = duration
                submission.waveform = waveform
            }
            group.missions[index].submissions.append(submission)
        }
    }

    /// 콕 찌르기 — 하루 1회 (TODO: 서버 연결 시 pokes 저장 + 푸시)
    func poke(_ memberID: UUID, groupID: UUID) {
        update(groupID) { group in
            guard let index = group.missions.indices.last else { return }
            group.missions[index].poked.insert(memberID)
        }
    }

    // MARK: - 결과 · 댓글

    /// 10~13 결과 — 내가 올린 미션만 볼 수 있어요 (§5-2, 서버에서는 RLS 로 막아요)
    func result(groupID: UUID, missionID: UUID) -> MissionResult? {
        guard let stored = groups.first(where: { $0.id == groupID }),
              let mission = stored.missions.first(where: { $0.id == missionID }),
              mission.submissions.contains(where: \.isMine) else { return nil }
        let done = Set(mission.submissions.compactMap(\.authorID))
        return MissionResult(
            id: mission.id,
            groupName: stored.detail.name,
            type: mission.type,
            topic: mission.topic,
            date: mission.opensAt,
            memberCount: stored.detail.members.count,
            submissions: mission.submissions,
            pending: stored.detail.members.filter { !done.contains($0.id) }.map(\.displayed)
        )
    }

    func toggleLike(_ submissionID: UUID, missionID: UUID, groupID: UUID) {
        updateSubmission(submissionID, missionID: missionID, groupID: groupID) { submission in
            submission.likedByMe.toggle()
            submission.likeCount += submission.likedByMe ? 1 : -1
        }
    }

    func addComment(_ body: String, to submissionID: UUID, missionID: UUID, groupID: UUID) {
        let me = group(groupID)?.me?.displayed ?? myProfile
        updateSubmission(submissionID, missionID: missionID, groupID: groupID) { submission in
            submission.comments.append(SubmissionComment(id: UUID(), author: me, isMine: true, body: body, createdAt: .now))
        }
    }

    func deleteComment(_ commentID: UUID, from submissionID: UUID, missionID: UUID, groupID: UUID) {
        updateSubmission(submissionID, missionID: missionID, groupID: groupID) { submission in
            submission.comments.removeAll { $0.id == commentID && $0.isMine }
        }
    }

    // MARK: - 기록

    /// 15 캘린더 — 지난 미션 중 누군가 올린 날 + 오늘(내가 올렸으면)
    func records(_ groupID: UUID) -> [DayRecord] {
        guard let stored = groups.first(where: { $0.id == groupID }) else { return [] }
        let today = Self.calendar.startOfDay(for: .now)
        return stored.missions.compactMap { mission in
            let day = Self.calendar.startOfDay(for: mission.opensAt)
            let mine = mission.submissions.contains(where: \.isMine)
            guard !mission.submissions.isEmpty, day < today || mine else { return nil }
            return DayRecord(
                date: mission.opensAt,
                type: mission.type,
                topic: mission.topic,
                doneCount: mission.submissions.count,
                memberCount: stored.detail.members.count,
                didSubmit: mine,
                missionID: mission.id
            )
        }
    }

    // MARK: - 내부

    nonisolated static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TodayMission.seoul
        return calendar
    }()

    private func update(_ groupID: UUID, _ change: (inout StoredGroup) -> Void) {
        guard let index = groups.firstIndex(where: { $0.id == groupID }) else { return }
        change(&groups[index])
    }

    private func updateSubmission(_ id: UUID, missionID: UUID, groupID: UUID, _ change: (inout Submission) -> Void) {
        update(groupID) { group in
            guard let m = group.missions.firstIndex(where: { $0.id == missionID }),
                  let s = group.missions[m].submissions.firstIndex(where: { $0.id == id }) else { return }
            change(&group.missions[m].submissions[s])
        }
    }

    /// 6자리 영문 대문자 + 숫자, 그룹마다 고유 (§5-3). 헷갈리는 0·O·1·I 는 빼요.
    private func makeInviteCode() -> String {
        let alphabet = Array("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        while true {
            let code = String((0..<6).map { _ in alphabet.randomElement()! })
            if !groups.contains(where: { $0.detail.inviteCode == code }) && DemoSeed.group(code: code) == nil {
                return code
            }
        }
    }

    /// 유형은 3일 연속 같지 않게, 주제는 최근 30일 안에 쓴 것 빼고 (§5-1)
    private func makeMission(for group: StoredGroup, opensAt: Date, closesAt: Date) -> StoredMission {
        let recent = group.missions.suffix(2).map(\.type)
        var types = MissionType.allCases
        if recent.count == 2, recent[0] == recent[1] {
            types.removeAll { $0 == recent[0] }
        }
        let type = types.randomElement() ?? .draw
        let usedRecently = Set(group.missions.filter { opensAt.timeIntervalSince($0.opensAt) < 30 * 86_400 }.map(\.topic))
        let pool = TopicPool.topics(for: type)
        let topic = pool.filter { !usedRecently.contains($0) }.randomElement() ?? pool.randomElement() ?? ""
        return StoredMission(
            id: UUID(),
            type: type,
            topic: topic,
            number: group.missions.count + 1,
            opensAt: opensAt,
            closesAt: closesAt
        )
    }

    /// 올린 그림·사진·음성을 기기 캐시 폴더에 저장해요. (서버 연결 시 Storage 업로드로 바뀌어요)
    private static func saveMedia(_ data: Data, fileExtension: String) -> URL? {
        let folder = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("hawe-media")
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let url = folder.appendingPathComponent("\(UUID().uuidString).\(fileExtension)")
        return (try? data.write(to: url)) != nil ? url : nil
    }
}

/// 유형별 주제 풀 (CLAUDE.md §6 topics — 원래는 서버 DB 에 있어요)
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
