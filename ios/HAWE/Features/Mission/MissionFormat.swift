import Foundation

/// 05 미션 화면 문구 (Android `MissionFormat.kt` 와 같은 규칙)
enum MissionFormat {
    /// 서버가 새 틀을 켰는데 이 앱에 그 화면이 없을 때(구형 앱) 05 카드에 보여요.
    static let unsupportedText = "앱을 업데이트하면 이 미션을 할 수 있어요"

    /// "10월 12일" (서울)
    static func dateText(_ opensAt: Date) -> String {
        let parts = DataStore.calendar.dateComponents([.month, .day], from: opensAt)
        return "\(parts.month ?? 0)월 \(parts.day ?? 0)일"
    }

    /// 남은 시간 HH:mm:ss
    static func countdown(to end: Date, now: Date) -> String {
        let seconds = max(0, Int(end.timeIntervalSince(now)))
        return String(format: "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
    }

    /// "마감 오후 11:00" · "마감 내일 오전 9:00" · 마감 뒤에는 지각 안내
    static func deadlineText(deadlineAt: Date, closesAt: Date, now: Date) -> String? {
        guard now < closesAt else { return nil }
        guard now < deadlineAt else { return "마감이 지났어요 · 지금 올리면 지각이에요" }
        let calendar = DataStore.calendar
        let today = calendar.startOfDay(for: now)
        let deadlineDay = calendar.startOfDay(for: deadlineAt)
        let parts = calendar.dateComponents([.month, .day, .hour, .minute], from: deadlineAt)
        let date: String
        if deadlineDay == today {
            date = ""
        } else if deadlineDay == calendar.date(byAdding: .day, value: 1, to: today) {
            date = "내일 "
        } else {
            date = "\(parts.month ?? 0)월 \(parts.day ?? 0)일 "
        }
        let hour24 = parts.hour ?? 0
        let hour = hour24 % 12 == 0 ? 12 : hour24 % 12
        return "마감 \(date)\(hour24 < 12 ? "오전" : "오후") \(hour):" + String(format: "%02d", parts.minute ?? 0)
    }

    static func memberDisplayName(_ member: MissionMember) -> String {
        member.isMe ? "\(member.profile.nickname) (나)" : member.profile.nickname
    }

    static func selectedMission(_ missions: [TodayMission], selectedID: Int?) -> TodayMission? {
        missions.first { $0.id == selectedID } ?? missions.first
    }

    static func submittedToastText(index: Int, total: Int, late: Bool) -> String {
        if total <= 1 { return late ? "지각으로 올렸어요" : "오늘의 미션을 올렸어요" }
        return late ? "\(index + 1)번째 미션을 지각으로 올렸어요" : "\(index + 1)번째 미션을 올렸어요"
    }

    static func lockNoticeText(_ type: MissionType, template: String = "") -> String {
        MissionTemplates.isAggregate(template)
            ? "내 답을 올리면 친구들의 답과 그룹 결과가 열려요."
            : "내 미션을 올리면 친구들의 \(type.subjectForm) 열려요."
    }

    /// 05 카드의 수행 버튼. 밸런스 게임은 글을 쓰는 게 아니라 고르는 거라 따로 불러요.
    static func actionTitle(_ type: MissionType, template: String) -> String {
        template == MissionTemplates.textBalance ? "하나 고르기" : type.actionTitle
    }

    /// 제출한 뒤 05 카드 버튼. 집계 틀은 결과 카드가 먼저 보여서 "결과 보기"예요.
    static func resultsActionTitle(template: String) -> String {
        MissionTemplates.isAggregate(template) ? "결과 보기" : "작품 보기"
    }

    /// 새 틀의 방법 안내(05 카드·수행 화면 제목 아래). 기존 틀은 주제 문장만으로 충분해서 nil.
    static func templateGuideText(_ template: String, spec: MissionSpec) -> String? {
        switch template {
        case MissionTemplates.textOneWord:
            return "띄어쓰기 없이 한 단어로 남겨요"
        case MissionTemplates.textReview:
            return "별점 1~5개와 한 줄 후기를 남겨요"
        case MissionTemplates.textBalance:
            if let a = spec.optionA.nonBlank, let b = spec.optionB.nonBlank { return "‘\(a)’ vs ‘\(b)’ 둘 중 하나를 골라요" }
            return "둘 중 하나를 골라요"
        case MissionTemplates.sliderPosition, MissionTemplates.sliderTaste:
            return sliderEnds(spec).map { "\($0) 사이에서 내 위치를 골라요" } ?? "내 위치를 골라요"
        case MissionTemplates.sliderConsensus:
            return "내 위치와 우리 그룹 평균을 함께 예측해요"
        default:
            return nil
        }
    }

    private static func sliderEnds(_ spec: MissionSpec) -> String? {
        guard let left = spec.left.nonBlank, let right = spec.right.nonBlank else { return nil }
        return "‘\(left)’ ↔ ‘\(right)’"
    }

    static func pokeSentText(_ kind: PokeKind, nickname: String) -> String {
        switch kind {
        case .poke: "\(nickname)님을 콕 찔렀어요"
        }
    }

    static func pokeButtonLabel(_ kind: PokeKind) -> String {
        switch kind {
        case .poke: "콕"
        }
    }

    /// VoiceOver 용. 보낸 뒤에는 보낸 사실을 읽어 줘요.
    static func pokeButtonDescription(_ kind: PokeKind, nickname: String, sent: Bool) -> String {
        if sent { return pokeSentText(kind, nickname: nickname) }
        switch kind {
        case .poke: return "\(nickname)님 콕 찌르기"
        }
    }
}

extension Optional where Wrapped == String {
    /// 비었거나 공백뿐이면 nil
    var nonBlank: String? {
        guard let self, !self.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return self
    }
}
