import Foundation

/// 그룹 활동 인사이트 (`/api/groups/{id}/insights`, B11) — 서버가 계산한 값을 그대로 보여요.
struct GroupInsight: Hashable {
    /// 그 달 1일 (서울)
    var month: Date
    /// 지난 계산 결과예요 — 서버가 다시 계산하고 있어요.
    var stale: Bool
    /// 이 달에는 기록이 없어요.
    var empty: Bool
    var activityIndex: ActivityIndex?
    var fastest: FastestMember?
    var topWords: [TopWord]
    var mostReacted: MostReacted?
    var highlights: [String]
}

struct ActivityIndex: Hashable {
    var score: Int
    var delta: Int?
    var participationRate: Double
    var onTimeRate: Double
    var interactionRate: Double
}

struct FastestMember: Hashable {
    var userID: Int
    var profile: Profile
    var firstCount: Int
    var averageResponseSeconds: Int
    var fastestSingleSeconds: Int
}

struct TopWord: Hashable {
    var word: String
    var count: Int
    var memberCount: Int
}

struct MostReacted: Hashable {
    var submissionID: Int
    var missionID: Int
    var missionContent: String
    var authorNickname: String
    var reactionCount: Int
    var commentCount: Int
    var topSticker: Sticker?
}
