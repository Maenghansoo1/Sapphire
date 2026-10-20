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

    static func commentSendEnabled(_ draft: String) -> Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// 멘션으로 보낼 id: 입력창에 `@닉네임`이 아직 남아 있는 멤버만
    static func mentionedIDs(in text: String, picked: [Int: String]) -> [Int] {
        picked.filter { text.contains("@\($0.value)") }.keys.sorted()
    }

    static func stickerToggleSequence(mine: [Sticker], pressed: Sticker) -> [Sticker] {
        var selected: [Sticker] = []
        for sticker in mine where !selected.contains(sticker) { selected.append(sticker) }
        if !selected.contains(pressed) { selected.append(pressed) }
        return selected
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
        [MissionTemplates.textOneWord, MissionTemplates.textReview, MissionTemplates.textBalance].contains(template)
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
