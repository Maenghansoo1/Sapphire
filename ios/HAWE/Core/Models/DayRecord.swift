import Foundation

/// 달력에 찍히는 내 도장 — 서버가 제출 기록으로 계산해서 내려줘요.
enum Stamp: String, Hashable {
    case good = "GOOD"
    case late = "LATE"
    case penalty = "PENALTY"

    var displayName: String {
        switch self {
        case .good: "참잘했어요"
        case .late: "지각"
        case .penalty: "벌점"
        }
    }

    /// Assets.xcassets/Stamps (임시 이미지 — 디자인이 나오면 같은 이름으로 바꿔요)
    var imageName: String {
        switch self {
        case .good: "stamp-good"
        case .late: "stamp-late"
        case .penalty: "stamp-penalty"
        }
    }
}

/// 하루치 기록 (15 캘린더 칸)
struct DayRecord: Hashable {
    var date: Date
    var type: MissionType
    var topic: String
    var doneCount: Int
    var memberCount: Int
    /// 내가 그날 올렸는지 — 안 올린 날은 결과를 볼 수 없어요 (CLAUDE.md §5-2, 사용자 결정 2026-10-12)
    var didSubmit: Bool
    /// '모아보기'로 열 미션
    var missionID: Int? = nil
    /// 도장이 없으면 nil (아직 낼 수 있는 미션, 들어오기 전 미션)
    var stamp: Stamp? = nil
}
