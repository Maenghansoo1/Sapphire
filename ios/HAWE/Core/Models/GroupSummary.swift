import Foundation

/// 04 홈 그룹 카드에 필요한 정보
struct GroupSummary: Identifiable, Hashable {
    let id: Int
    var name: String
    var relationship: Relationship
    var members: [AvatarContent]
    /// 연인 그룹의 '우리의 1일'
    var startDate: Date?
    /// 아직 오늘 미션이 없으면 nil
    var missionType: MissionType?
    var topic: String?
    var submittedCount: Int
    /// 오늘 열린 미션을 모두 냈는지
    var didSubmit: Bool
    /// 오늘 열린 미션 수와 그중 내가 낸 수 (하루 여러 미션, A6)
    var missionCount = 0
    var mySubmittedCount = 0

    /// 우리의 1일을 1일째로 세요 (서울 날짜 기준).
    func daysTogether(on today: Date = AppClock.now(), calendar: Calendar = DataStore.calendar) -> Int? {
        guard let startDate else { return nil }
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: startDate), to: calendar.startOfDay(for: today)).day ?? 0
        return days + 1
    }
}

extension GroupSummary {
    /// Figma 04 홈 화면의 목업 데이터
    static let mock: [GroupSummary] = [
        GroupSummary(
            id: LocalID.next(),
            name: "컴공 동기",
            relationship: .friend,
            members: [.character(.gamja), .character(.kkotnim), .character(.mulbangul), .character(.pilleum), .character(.gomdol)],
            missionType: .photo,
            topic: "지금 창밖으로 보이는 풍경",
            submittedCount: 3,
            didSubmit: false
        ),
        GroupSummary(
            id: LocalID.next(),
            name: "우리 둘",
            relationship: .lover,
            members: [.character(.mongsil), .character(.ipsagwi)],
            startDate: Calendar.current.date(byAdding: .day, value: -127, to: .now),
            missionType: .draw,
            topic: "요즘 제일 웃겼던 순간",
            submittedCount: 2,
            didSubmit: true
        ),
        GroupSummary(
            id: LocalID.next(),
            name: "조별과제 B팀",
            relationship: .rival,
            members: [.character(.pilleum), .character(.gomdol), .character(.ipsagwi), .character(.mongsil)],
            missionType: .write,
            topic: "오늘 하루를 세 단어로",
            submittedCount: 1,
            didSubmit: false
        ),
    ]
}
