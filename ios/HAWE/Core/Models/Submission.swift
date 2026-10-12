import Foundation

/// 한 멤버가 올린 작품 (서버 `SubmissionResponse`)
/// 좋아요·댓글은 서버에 생기면(7단계) 다시 붙여요.
struct Submission: Identifiable, Hashable {
    /// 서버 submissionId (앱 안 체험 데이터는 음수)
    let id: Int
    var author: Profile
    var isMine: Bool
    var type: MissionType
    var createdAt: Date
    /// 사진·그림·음성 파일 주소 (서버 `/files/...` 를 절대 URL 로 바꾼 값)
    var mediaURL: URL?
    var text: String?
    var duration: TimeInterval?
    /// 음성 파형 막대 높이 0...1
    var waveform: [CGFloat] = []
    /// 마감이 지난 뒤 올렸어요.
    var isLate = false
    /// 올린 멤버(userId) — 참여 현황·'아직' 표시에 써요.
    var authorID: Int? = nil
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
}
