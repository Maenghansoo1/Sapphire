import Foundation

extension MissionType {
    /// 05 미션 카드의 CTA
    var actionTitle: String {
        switch self {
        case .draw: "그림 그리기"
        case .photo: "사진 찍기"
        case .write: "글 쓰기"
        case .voice: "음성 녹음"
        }
    }

    /// "친구들의 ○○ 열려요" 의 주어 (그림이·사진이·글이·음성이)
    var subjectForm: String { "\(displayName)이" }
}

/// 서버 미션 `params` — 30초 스케치 시간, 삼행시 제시어, 'a 를 b 로' 그리기
struct MissionSpec: Hashable {
    var seconds: Int?
    var word: String?
    var drawFrom: String?
    var drawTo: String?
}

/// 오늘의 미션 참여 현황 한 줄
struct MissionMember: Identifiable, Hashable {
    /// 서버 userId
    let id: Int
    var profile: Profile
    var isMe = false
    var isDone = false
    /// 오늘 이 멤버를 이미 콕 찔렀는지 (하루 1회)
    var pokedToday = false
}

/// 05 그룹 · 오늘의 미션
struct TodayMission: Identifiable, Hashable {
    /// 서버 missionId
    let id: Int
    var group: GroupDetail
    /// 앱에서 아직 열 수 없는 유형이면 nil
    var type: MissionType?
    var topic: String
    /// 그룹의 몇 번째 미션인지 (#142)
    var number: Int
    var opensAt: Date
    /// 다음 미션이 열리는 시각 — 이때까지 제출할 수 있어요.
    var closesAt: Date
    var members: [MissionMember]
    /// 마감 — 지나도 다음 미션이 열리기 전까지는 '지각'으로 올릴 수 있어요.
    var deadlineAt: Date? = nil
    /// 서버 템플릿 (TEXT_FILL_BLANK, TEXT_ACROSTIC, DRAW_SKETCH_30S …)
    var template: String = ""
    var spec = MissionSpec()

    var doneCount: Int { members.filter(\.isDone).count }
    var didSubmit: Bool { members.first(where: \.isMe)?.isDone ?? false }
}

extension TodayMission {
    /// 그룹이 없을 때 그릴 빈 값
    static func empty(groupID: Int) -> TodayMission {
        TodayMission(
            id: LocalID.next(),
            group: GroupDetail(
                id: groupID, name: "", relationship: .friend, createdAt: .now, missionCount: 0,
                inviteCode: "", topicTime: DateComponents(hour: 9, minute: 0), notify: false, members: []
            ),
            type: .draw, topic: "", number: 0, opensAt: .now, closesAt: .now, members: []
        )
    }

    static let seoul = TimeZone(identifier: "Asia/Seoul") ?? .current

    /// 주제 도착 시간 기준으로 지금 열려 있는 미션의 시작·끝 (CLAUDE.md §5-1)
    static func window(topicTime: DateComponents, now: Date = .now) -> (opens: Date, closes: Date) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = seoul
        let todayOpen = calendar.date(
            bySettingHour: topicTime.hour ?? 9, minute: topicTime.minute ?? 0, second: 0, of: now
        ) ?? now
        let opens = now >= todayOpen ? todayOpen : calendar.date(byAdding: .day, value: -1, to: todayOpen) ?? todayOpen
        let closes = calendar.date(byAdding: .day, value: 1, to: opens) ?? opens
        return (opens, closes)
    }

    /// 홈 카드 목업으로 05 화면 목업을 만들어요. 멤버 이름은 Figma 05 화면 값.
    static func mock(for summary: GroupSummary) -> TodayMission {
        let names = ["과대표 하늘", "민준", "지우", "서연", "현우", "도윤"]
        var group = GroupDetail.mock
        group.name = summary.name
        group.relationship = summary.relationship

        var othersDone = summary.submittedCount - (summary.didSubmit ? 1 : 0)
        // 홈 카드의 마지막 캐릭터를 '나'로 맨 위에 둬요 (Figma 05: 과대표 하늘 = 곰돌이).
        let avatars = summary.members.suffix(1) + summary.members.dropLast()
        let members = avatars.enumerated().map { index, avatar in
            let isMe = index == 0
            let isDone: Bool
            if isMe {
                isDone = summary.didSubmit
            } else {
                isDone = othersDone > 0
                if isDone { othersDone -= 1 }
            }
            return MissionMember(
                id: LocalID.next(),
                profile: Profile(avatar: avatar, nickname: names[index % names.count]),
                isMe: isMe,
                isDone: isDone
            )
        }
        let window = window(topicTime: group.topicTime)
        return TodayMission(
            id: LocalID.next(),
            group: group,
            type: summary.missionType,
            topic: summary.topic ?? "",
            number: group.missionCount,
            opensAt: window.opens,
            closesAt: window.closes,
            members: members
        )
    }
}
