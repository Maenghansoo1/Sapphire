import Foundation

/// 작품에 달린 댓글 (DB `comments`)
struct SubmissionComment: Identifiable, Hashable {
    let id: UUID
    var author: Profile
    var isMine: Bool
    var body: String
    var createdAt: Date
}

/// 한 멤버가 올린 작품 (DB `submissions`)
struct Submission: Identifiable, Hashable {
    let id: UUID
    var author: Profile
    var isMine: Bool
    var type: MissionType
    var createdAt: Date
    /// 사진·그림 — 서버 연결 전에는 nil 이라 자리 표시를 보여요.
    var mediaURL: URL?
    var text: String?
    var duration: TimeInterval?
    /// 음성 파형 막대 높이 0...1
    var waveform: [CGFloat] = []
    var likeCount: Int
    var likedByMe = false
    /// 좋아요 누른 다른 멤버 이름 (최근 순)
    var likerNames: [String] = []
    var comments: [SubmissionComment] = []
}

/// 하루치 미션 결과 (10~13)
struct MissionResult: Identifiable, Hashable {
    let id: UUID
    var groupName: String
    var type: MissionType
    var topic: String
    var date: Date
    var memberCount: Int
    var submissions: [Submission]
    /// 아직 안 올린 멤버 — '아직' 상태로 보여요 (CLAUDE.md §5-2)
    var pending: [Profile]
}

// MARK: - 목업

extension MissionResult {
    /// 05 미션에서 '친구들 작품 보기'를 눌렀을 때 — 완료한 멤버의 작품을 목업으로 만들어요.
    static func mock(for mission: TodayMission) -> MissionResult {
        let done = mission.members.filter(\.isDone)
        let pending = mission.members.filter { !$0.isDone }.map(\.profile)
        return mock(
            groupName: mission.group.name,
            type: mission.type,
            topic: mission.topic,
            date: mission.opensAt,
            authors: done.map { ($0.profile, $0.isMe) },
            pending: pending
        )
    }

    /// Figma 10~14 화면 값으로 채운 목업
    static func mock(
        groupName: String = "컴공 동기",
        type: MissionType,
        topic: String,
        date: Date,
        authors: [(Profile, Bool)]? = nil,
        pending: [Profile] = []
    ) -> MissionResult {
        let members = GroupDetail.mock.members
        let authors = authors ?? [
            (members[1].displayed, false),
            (members[2].displayed, false),
            (members[3].displayed, false),
            (members[4].displayed, false),
            (members[0].displayed, true),
        ]
        let start = Calendar.current.startOfDay(for: date)
        let times: [TimeInterval] = [9 * 3600 + 12 * 60, 11 * 3600 + 40 * 60, 14 * 3600 + 5 * 60, 18 * 3600 + 2 * 60, 22 * 3600 + 30 * 60, 23 * 3600]
        let texts = [
            "편의점 사장님이 \"오늘도 수고 많았어요\" 하면서 영수증을 건네주셨다. 별말 아닌데 하루가 풀렸음.",
            "엄마가 전화로 밥은 잘 먹고 다니냐고 물어봤다.",
            "조별과제 팀원이 네 덕분에 끝났다고 말해줬다. 사실 나도 덕분이었는데.",
            "교수님이 질문 좋다고 하셨다.",
            "오늘은 아무 말도 안 들었지만 그래도 괜찮았다.",
        ]
        let likes = [3, 4, 2, 5, 2, 1]
        let durations: [TimeInterval] = [9, 12, 10, 7, 11, 8]
        let names = authors.map(\.0.nickname)

        let submissions = authors.enumerated().map { index, author in
            Submission(
                id: UUID(),
                author: author.0,
                isMine: author.1,
                type: type,
                createdAt: start.addingTimeInterval(times[index % times.count]),
                text: type == .write ? texts[index % texts.count] : nil,
                duration: type == .voice ? durations[index % durations.count] : nil,
                waveform: type == .voice ? mockWaveform(seed: index) : [],
                likeCount: likes[index % likes.count],
                likerNames: names.filter { $0 != author.0.nickname }.prefix(likes[index % likes.count]).map { $0 },
                comments: index == 0 ? SubmissionComment.mockList(on: date) : []
            )
        }
        return MissionResult(
            id: UUID(),
            groupName: groupName,
            type: type,
            topic: topic,
            date: date,
            memberCount: authors.count + pending.count,
            submissions: submissions,
            pending: pending
        )
    }

    private static func mockWaveform(seed: Int) -> [CGFloat] {
        let base: [CGFloat] = [6, 10, 16, 22, 14, 20, 24, 12, 8, 18, 22, 16, 10, 6, 12, 20, 24, 18, 12, 8, 14, 20, 16, 10, 6, 10, 14, 8]
        let shift = (seed * 5) % base.count
        return (base[shift...] + base[..<shift]).map { $0 / 24 }
    }
}

extension SubmissionComment {
    /// Figma 14 화면의 댓글 3개
    static func mockList(on date: Date) -> [SubmissionComment] {
        let members = GroupDetail.mock.members
        let now = Date.now
        return [
            SubmissionComment(id: UUID(), author: members[3].displayed, isMine: false, body: "이거 그때 과방에서 있었던 일 맞지 ㅋㅋㅋ", createdAt: now.addingTimeInterval(-2 * 3600)),
            SubmissionComment(id: UUID(), author: members[2].displayed, isMine: false, body: "표정이 너무 정확함", createdAt: now.addingTimeInterval(-3600)),
            SubmissionComment(id: UUID(), author: members[4].displayed, isMine: false, body: "나도 내일 이걸로 그릴까", createdAt: now.addingTimeInterval(-30 * 60)),
        ]
    }
}
