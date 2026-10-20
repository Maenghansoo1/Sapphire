import Foundation

/// 프로필 한 벌 (캐릭터 또는 사진 + 닉네임)
struct Profile: Hashable {
    var avatar: AvatarContent
    var nickname: String
}

/// 그룹 멤버. 화면에 보일 때는 `그룹 내 값 ?? 기본 값` (CLAUDE.md §5-4)
struct GroupMember: Identifiable, Hashable {
    /// 서버 userId (앱 안 체험 데이터는 음수)
    let id: Int
    var defaultProfile: Profile
    /// 그룹 내 프로필 — nil 이면 기본 프로필을 써요.
    var groupProfile: Profile?
    var isMe = false
    var isOwner = false
    var monthlySubmissionCount: Int? = nil
    var stampedToday: Bool? = nil

    var displayed: Profile { groupProfile ?? defaultProfile }
}

/// 16 그룹 정보 · 설정에 필요한 정보
struct GroupDetail: Identifiable, Hashable {
    /// 서버 groupId (앱 안 체험 데이터는 음수)
    let id: Int
    var name: String
    var relationship: Relationship
    var createdAt: Date
    var missionCount: Int
    var inviteCode: String
    /// 주제 도착 시간 (Asia/Seoul, 기본 오전 9:00)
    var topicTime: DateComponents
    /// 이 그룹 알림 (group_members.notify)
    var notify: Bool
    var members: [GroupMember]
    /// 연인 그룹의 '우리의 1일'
    var startDate: Date? = nil

    var me: GroupMember? { members.first(where: \.isMe) }
    var amIOwner: Bool { me?.isOwner ?? false }
}

/// 서버에 없는 체험·미리보기 데이터의 id — 서버 id 와 겹치지 않게 음수로 줘요.
enum LocalID {
    nonisolated(unsafe) private static var last = 0

    static func next() -> Int {
        last -= 1
        return last
    }
}

extension Profile {
    /// 마이페이지 기본 프로필 목업 (Figma 16-1: 몽실이 · 하늘)
    static let mockMine = Profile(avatar: .character(.mongsil), nickname: "하늘")
}

extension GroupDetail {
    /// Figma 16 화면의 목업 데이터
    static let mock = GroupDetail(
        id: LocalID.next(),
        name: "컴공 동기",
        relationship: .friend,
        createdAt: Calendar.current.date(from: DateComponents(year: 2026, month: 6, day: 2)) ?? .now,
        missionCount: 142,
        inviteCode: "K7Q2PX",
        topicTime: DateComponents(hour: 9, minute: 0),
        notify: true,
        members: [
            GroupMember(
                id: LocalID.next(),
                defaultProfile: .mockMine,
                groupProfile: Profile(avatar: .character(.gomdol), nickname: "과대표 하늘"),
                isMe: true,
                isOwner: true
            ),
            GroupMember(id: LocalID.next(), defaultProfile: Profile(avatar: .character(.gamja), nickname: "민준")),
            GroupMember(id: LocalID.next(), defaultProfile: Profile(avatar: .character(.kkotnim), nickname: "지우")),
            GroupMember(id: LocalID.next(), defaultProfile: Profile(avatar: .character(.mulbangul), nickname: "서연")),
            GroupMember(id: LocalID.next(), defaultProfile: Profile(avatar: .character(.pilleum), nickname: "현우")),
        ]
    )

    /// 방금 만든 그룹 — 나 혼자, 방장
    static func newlyCreated(name: String, relationship: Relationship) -> GroupDetail {
        GroupDetail(
            id: LocalID.next(),
            name: name,
            relationship: relationship,
            createdAt: .now,
            missionCount: 0,
            // TODO: 서버가 고유한 6자리 코드를 만들어 줘요. 지금은 목업.
            inviteCode: "H4W3K2",
            topicTime: DateComponents(hour: 9, minute: 0),
            notify: true,
            members: [GroupMember(id: LocalID.next(), defaultProfile: .mockMine, isMe: true, isOwner: true)]
        )
    }
}
