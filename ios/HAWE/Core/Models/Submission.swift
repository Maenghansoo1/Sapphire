import Foundation

/// 한 멤버가 올린 작품 (서버 `SubmissionResponse`)
struct Submission: Identifiable, Hashable {
    /// 서버 submissionId (앱 안 체험 데이터는 음수)
    let id: Int
    var author: Profile
    var isMine: Bool
    var type: MissionType
    var createdAt: Date
    /// 사진·그림 파일 주소 (서버 `/files/...` 를 절대 URL 로 바꾼 값)
    var mediaURL: URL?
    var text: String?
    /// 마감이 지난 뒤 올렸어요.
    var isLate = false
    /// 올린 멤버(userId) — 참여 현황·'아직' 표시에 써요.
    var authorID: Int? = nil
    var reactions: [Reaction] = []
    var commentCount = 0
    // 새 틀(B5)에서만 채워져요: 별점·슬라이더 위치(number), 밸런스 선택(choice), 평균 예측(guess), 이유(caption)
    var template = ""
    var number: Int? = nil
    var choice: String? = nil
    var guess: Int? = nil
    var caption: String? = nil
    /// 사진-주제 적합도 AI 심사(B10). 심사가 끝나고 보여 줄 게 있을 때만 서버가 보내요(점수는 오지 않음).
    var aiReview: AiReview? = nil
}

/// AI 심사 결과. `badge` 는 "PERFECT_FIT" 또는 nil, `comment` 는 칭찬 한 줄 또는 nil.
struct AiReview: Hashable {
    var badge: String?
    var comment: String?
}

/// 서버 스티커 종류(`stickerType`). 디자인이 나오기 전까지 이모지로 임시 표시해요.
enum Sticker: String, CaseIterable, Hashable {
    case heart, laugh, wow, sad, fire

    var emoji: String {
        switch self {
        case .heart: "❤️"
        case .laugh: "😂"
        case .wow: "😮"
        case .sad: "😢"
        case .fire: "🔥"
        }
    }

    var label: String {
        switch self {
        case .heart: "하트"
        case .laugh: "웃김"
        case .wow: "놀람"
        case .sad: "슬픔"
        case .fire: "최고"
        }
    }
}

struct Reaction: Hashable {
    var sticker: Sticker
    var count: Int
    var reactedByMe: Bool
    var userIDs: [Int]
}

struct SubmissionComment: Identifiable, Hashable {
    let id: Int
    var author: Profile
    var authorID: Int
    var content: String
    var imageURL: URL?
    var mentionedUserIDs: [Int]
    var createdAt: Date
    var isMine: Bool
}

/// 하루치 미션 결과 (10~13)
struct MissionResult: Identifiable, Hashable {
    let id: Int
    var groupName: String
    var type: MissionType
    var topic: String
    var date: Date
    var memberCount: Int
    var submissions: [Submission]
    /// 아직 안 올린 멤버 — '아직' 상태로 보여요 (CLAUDE.md §5-2)
    var pending: [Profile]
    var template = ""
    var spec = MissionSpec()
}

/// 집계 틀(밸런스·슬라이더)의 그룹 결과. 서버가 계산한 값을 그대로 담아요(`MISSIONS_API.md` §5).
enum MissionAggregate: Hashable {
    case balance(Balance)
    /// SLIDER_POSITION·SLIDER_TASTE. `diffFromAverage` = 내 값 − 평균.
    case slider(Slider)
    /// SLIDER_CONSENSUS. `ranking` 은 서버 순서(차이 작은 순), 동률 승자는 여러 명.
    case consensus(Consensus)

    struct Balance: Hashable {
        var totalCount: Int
        var countA: Int
        var countB: Int
        var myChoice: String?
    }

    struct Slider: Hashable {
        var totalCount: Int
        var average: Double?
        var distribution: [SliderPoint]
        var myNumber: Int?
        var diffFromAverage: Double?
    }

    struct Consensus: Hashable {
        var totalCount: Int
        var average: Double?
        var ranking: [ConsensusRank]
        var winnerUserIDs: Set<Int>
    }

    var totalCount: Int {
        switch self {
        case .balance(let value): value.totalCount
        case .slider(let value): value.totalCount
        case .consensus(let value): value.totalCount
        }
    }
}

struct SliderPoint: Hashable {
    var userID: Int
    var nickname: String
    var number: Int
}

struct ConsensusRank: Hashable {
    var userID: Int
    var nickname: String
    var guess: Int
    var diff: Double
}
