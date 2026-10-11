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

/// 오늘의 미션 참여 현황 한 줄
struct MissionMember: Identifiable, Hashable {
    let id: UUID
    var profile: Profile
    var isMe = false
    var isDone = false
    /// 오늘 이 멤버를 이미 콕 찔렀는지 (하루 1회)
    var pokedToday = false
}

/// 05 그룹 · 오늘의 미션
struct TodayMission: Identifiable, Hashable {
    let id: UUID
    var group: GroupDetail
    var type: MissionType
    var topic: String
    /// 그룹의 몇 번째 미션인지 (#142)
    var number: Int
    var opensAt: Date
    /// 다음 미션이 열리는 시각 — 이때까지 제출할 수 있어요.
    var closesAt: Date
    var members: [MissionMember]

    var doneCount: Int { members.filter(\.isDone).count }
    var didSubmit: Bool { members.first(where: \.isMe)?.isDone ?? false }
}

extension TodayMission {
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
                id: UUID(),
                profile: Profile(avatar: avatar, nickname: names[index % names.count]),
                isMe: isMe,
                isDone: isDone
            )
        }
        let window = window(topicTime: group.topicTime)
        return TodayMission(
            id: UUID(),
            group: group,
            type: summary.missionType,
            topic: summary.topic,
            number: group.missionCount,
            opensAt: window.opens,
            closesAt: window.closes,
            members: members
        )
    }
}
