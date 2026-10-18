import Foundation

/// 결과 화면 문구 (Android `ResultsFormat.kt` 와 같은 규칙)
enum ResultsFormat {
    static func progressText(done: Int, memberCount: Int) -> String {
        done == memberCount ? "\(done)명 모두 완료" : "\(done) / \(memberCount)명 완료"
    }

    static func hintText(_ type: MissionType) -> String { "\(type.objectForm) 눌러서 자세히 보기" }

    /// 별점 1~5 를 ★/☆ 다섯 칸으로 (임시 표시 — 디자인 확정 시 교체). 범위 밖은 0~5 로 맞춰요.
    static func starsText(_ stars: Int) -> String {
        let filled = min(max(stars, 0), 5)
        return String(repeating: "★", count: filled) + String(repeating: "☆", count: 5 - filled)
    }

    /// 밸런스 A/B 비율(%). 합이 늘 100 이 되게 A 를 반올림하고 B 는 나머지예요. 아무도 없으면 0/0.
    static func balancePercents(countA: Int, countB: Int) -> (a: Int, b: Int) {
        let total = countA + countB
        guard total > 0 else { return (0, 0) }
        let a = Int((Double(countA) * 100 / Double(total)).rounded(.toNearestOrAwayFromZero))
        return (a, 100 - a)
    }

    /// "A · 짜장면"처럼 선택 글자와 서버 선택지. 선택지를 모르면 글자만.
    static func balanceChoiceText(_ choice: String?, spec: MissionSpec) -> String? {
        let option: String?
        switch choice {
        case "A": option = spec.optionA
        case "B": option = spec.optionB
        default: return nil
        }
        return option.nonBlank.map { "\(choice!) · \($0)" } ?? choice
    }

    /// 서버가 소수 첫째 자리로 반올림해 준 값을 그대로 보여요. 62.0 은 "62", 62.5 는 "62.5".
    static func decimalText(_ value: Double) -> String {
        value == value.rounded() ? String(Int(value)) : String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    /// 내 위치와 평균의 차이(내 값 − 평균)를 양 끝 라벨로 풀어 말해요.
    static func diffFromAverageText(_ diff: Double?, spec: MissionSpec) -> String? {
        guard let diff else { return nil }
        if diff == 0 { return "내 위치가 딱 평균이에요" }
        let side = diff > 0 ? spec.right : spec.left
        let direction = side.nonBlank.map { "‘\($0)’ 쪽으로" } ?? (diff > 0 ? "오른쪽으로" : "왼쪽으로")
        return "내 위치는 평균보다 \(direction) \(decimalText(abs(diff)))"
    }

    /// 다수결 예측 순위 (같은 차이는 같은 등수: 1, 1, 3). 서버가 차이 작은 순으로 보내 줘요.
    static func competitionRanks(_ ranking: [ConsensusRank]) -> [Int] {
        ranking.map { entry in 1 + ranking.filter { $0.diff < entry.diff }.count }
    }

    /// 승자 안내. 동률이면 모두, 나는 "나"로 불러요.
    static func consensusWinnerText(_ ranking: [ConsensusRank], winnerUserIDs: Set<Int>, myUserID: Int) -> String? {
        let winners = ranking.filter { winnerUserIDs.contains($0.userID) }
        guard !winners.isEmpty else { return nil }
        let mine = winners.contains { $0.userID == myUserID }
        let others = winners.filter { $0.userID != myUserID }.map { "\($0.nickname)님" }.joined(separator: ", ")
        if winners.count == 1 { return mine ? "내가 가장 가깝게 맞혔어요" : "\(others)이 가장 가깝게 맞혔어요" }
        return mine ? "나와 \(others)이 공동으로 가장 가깝게 맞혔어요" : "\(others)이 공동으로 가장 가깝게 맞혔어요"
    }

    /// AI 심사 배지 이름(B10). 앱이 모르는 배지는 이름 없이 코멘트만 보여요.
    static func aiBadgeLabel(_ badge: String?) -> String? {
        badge == "PERFECT_FIT" ? "오늘의 주제 찰떡상" : nil
    }

    /// 방금(2분 안) 올린 내 사진이 아직 AI 심사 전인지. 심사는 제출 뒤에 따로 끝나서 결과 화면이 한 번 더 불러와요.
    static func awaitsAiReview(_ submissions: [Submission], now: Date) -> Bool {
        submissions.contains { submission in
            guard submission.isMine, submission.type == .photo, submission.aiReview == nil else { return false }
            let age = now.timeIntervalSince(submission.createdAt)
            return age >= 0 && age < 120
        }
    }

    static func commentSendEnabled(_ draft: String) -> Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// 멘션으로 보낼 id: 입력창에 `@닉네임`이 아직 남아 있는 멤버만
    static func mentionedIDs(in text: String, picked: [Int: String]) -> [Int] {
        picked.filter { text.contains("@\($0.value)") }.keys.sorted()
    }

    static func socialErrorMessage(_ error: Error) -> String {
        if error is NetworkError { return "네트워크 연결을 확인해 주세요" }
        switch error.apiCode {
        case "FEED_LOCKED": return "먼저 미션을 올려 주세요"
        case "NOT_GROUP_MEMBER", "SUBMISSION_NOT_FOUND", "COMMENT_NOT_FOUND": return "더 이상 볼 수 없는 글이에요"
        case "NOT_COMMENT_OWNER": return "내 댓글만 지울 수 있어요"
        case "FILE_TOO_LARGE": return "사진이 너무 커요"
        case "UNSUPPORTED_FILE_TYPE": return "지원하지 않는 사진이에요"
        case "VALIDATION_ERROR": return "입력 내용을 확인해 주세요"
        default: return "잠시 후 다시 시도해 주세요"
        }
    }

    /// 새 틀(B5)이라 글 한 덩어리가 아닌 전용 모양(`TemplateAnswer`)으로 그리는 제출물인지
    static func hasTemplateAnswer(_ template: String) -> Bool {
        [MissionTemplates.textOneWord, MissionTemplates.textReview, MissionTemplates.textBalance,
         MissionTemplates.sliderPosition, MissionTemplates.sliderTaste, MissionTemplates.sliderConsensus].contains(template)
    }
}

extension Date {
    /// "오전 9:12" (서울)
    var koreanTime: String {
        let parts = DataStore.calendar.dateComponents([.hour, .minute], from: self)
        let hour24 = parts.hour ?? 0
        let hour = hour24 % 12 == 0 ? 12 : hour24 % 12
        return "\(hour24 < 12 ? "오전" : "오후") \(hour):" + String(format: "%02d", parts.minute ?? 0)
    }

    /// "10월 11일" (서울)
    var koreanMonthDay: String {
        let parts = DataStore.calendar.dateComponents([.month, .day], from: self)
        return "\(parts.month ?? 0)월 \(parts.day ?? 0)일"
    }
}
