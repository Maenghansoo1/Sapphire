import Foundation

extension MissionType {
    /// 05 미션 카드의 CTA
    var actionTitle: String {
        switch self {
        case .draw: "그림 그리기"
        case .photo: "사진 찍기"
        case .write: "글 쓰기"
        case .slider: "슬라이더 정하기"
        }
    }
}

/// 서버 미션 `params` — 30초 스케치 시간, 삼행시 제시어, 'a 를 b 로' 그리기, 새 틀(B5)의 선택지·라벨
struct MissionSpec: Hashable {
    var seconds: Int?
    var word: String?
    var drawFrom: String?
    var drawTo: String?
    /// 밸런스 게임(TEXT_BALANCE)의 두 선택지
    var optionA: String?
    var optionB: String?
    /// 슬라이더: 질문(SLIDER_POSITION·SLIDER_CONSENSUS) 또는 주제(SLIDER_TASTE)와 양 끝 라벨
    var question: String?
    var topic: String?
    var left: String?
    var right: String?
    /// 그림·사진 틀이 그릴(찍을) 대상
    var subject: String?
    /// 사람을 지목한 미션인지 (서버가 닉네임으로 풀어 줘요)
    var hasTarget = false

    var sliderQuestion: String? { question ?? topic }
}

/// 서버 틀 코드(`MISSIONS_API.md` §1-2) 중 이 앱이 쓰는 것
enum MissionTemplates {
    static let photoNow = "PHOTO_NOW"
    static let photoGallery = "PHOTO_GALLERY"
    static let photoColor = "PHOTO_COLOR"
    static let textAcrostic = "TEXT_ACROSTIC"
    static let textFillBlank = "TEXT_FILL_BLANK"
    static let textOneWord = "TEXT_ONE_WORD"
    static let textReview = "TEXT_REVIEW"
    static let textBalance = "TEXT_BALANCE"
    static let drawSketch = "DRAW_SKETCH"
    static let drawAToB = "DRAW_A_TO_B"
    static let sliderPosition = "SLIDER_POSITION"
    static let sliderTaste = "SLIDER_TASTE"
    static let sliderConsensus = "SLIDER_CONSENSUS"

    static let supported: Set<String> = [
        photoNow, photoGallery, photoColor, textAcrostic, textFillBlank, textOneWord, textReview, textBalance,
        drawSketch, drawAToB, sliderPosition, sliderTaste, sliderConsensus,
    ]
    static let aggregate: Set<String> = [textBalance, sliderPosition, sliderTaste, sliderConsensus]

    /// 이 앱에 수행 화면이 있는 틀인지. 서버가 나중에 새 틀을 켜면 05 화면은 "앱 업데이트" 안내만 보여요.
    static func isSupported(_ template: String) -> Bool { supported.contains(template) }

    /// 그룹 결과(`GET /missions/{id}/result`)가 있는 집계 틀(밸런스·슬라이더 3종)인지
    static func isAggregate(_ template: String) -> Bool { aggregate.contains(template) }
}

/// 재촉 종류(콕 찌르기·똥뿌리기). rawValue 는 서버 `type` 값.
enum PokeKind: String, CaseIterable, Hashable {
    case poke, fling
}

/// 오늘의 미션 참여 현황 한 줄
struct MissionMember: Identifiable, Hashable {
    /// 서버 userId
    let id: Int
    var profile: Profile
    var isMe = false
    var isDone = false
    /// 이 미션에서 이 멤버에게 이미 보낸 재촉 (종류마다 한 번)
    var poked: Set<PokeKind> = []
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
    /// 서버 템플릿 (TEXT_FILL_BLANK, TEXT_ACROSTIC, DRAW_SKETCH …)
    var template: String = ""
    var spec = MissionSpec()

    var doneCount: Int { members.filter(\.isDone).count }
    var didSubmit: Bool { members.contains { $0.isMe && $0.isDone } }
}

extension TodayMission {
    static let seoul = TimeZone(identifier: "Asia/Seoul") ?? .current

    /// 주제 도착 시간 기준으로 지금 열려 있는 미션의 시작·끝 (CLAUDE.md §5-1)
    static func window(topicTime: DateComponents, now: Date = AppClock.now()) -> (opens: Date, closes: Date) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = seoul
        let todayOpen = calendar.date(
            bySettingHour: topicTime.hour ?? 9, minute: topicTime.minute ?? 0, second: 0, of: now
        ) ?? now
        let opens = now >= todayOpen ? todayOpen : calendar.date(byAdding: .day, value: -1, to: todayOpen) ?? todayOpen
        let closes = calendar.date(byAdding: .day, value: 1, to: opens) ?? opens
        return (opens, closes)
    }
}
