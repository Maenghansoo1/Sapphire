import Foundation

/// 알림 목록 문구·묶음·이동 규칙 (Android `NotificationFormat.kt` 와 같아요)
enum NotificationFormat {
    /// 서버 문장(`body`)이 없을 때 앱이 만드는 문장
    static func text(_ notification: AppNotification) -> String {
        if let body = notification.body { return body }
        let name = notification.from.map { "\($0.nickname)님이" } ?? "누군가"
        switch notification.type {
        case .topic: return "오늘의 미션이 열렸어요"
        case .poke: return "\(name) 콕 찔렀어요"
        case .comment: return "\(name) 내 작품에 댓글을 남겼어요"
        case .mention: return "\(name) 댓글에서 나를 불렀어요"
        case .reaction: return "\(name) 내 작품에 반응을 남겼어요"
        }
    }

    /// 아바타 오른쪽 아래에 붙는 종류 표시
    static func badgeIcon(_ type: NotificationType) -> HIcon {
        switch type {
        case .topic: .bell
        case .poke: .bell
        case .comment, .mention: .comment
        case .reaction: .heartFill
        }
    }

    enum Section: String {
        case today = "오늘"
        case earlier = "이전"
    }

    /// 서울 날짜 기준으로 오늘 받은 알림과 그 전 알림을 나눠요. 순서는 서버가 준 최신순 그대로예요.
    static func sections(_ items: [AppNotification], now: Date) -> [(Section, [AppNotification])] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TodayMission.seoul
        let today = items.filter { calendar.isDate($0.createdAt, inSameDayAs: now) }
        let earlier = items.filter { !calendar.isDate($0.createdAt, inSameDayAs: now) }
        return [(Section.today, today), (Section.earlier, earlier)].filter { !$0.1.isEmpty }
    }

    enum Target: Equatable {
        case mission(groupID: Int)
        case results(groupID: Int, missionID: Int)
        /// 나간 그룹 등 열 수 없는 알림
        case missing
    }

    /// 누르면 갈 곳 (CONTRACT §8-3): 미션 도착·콕은 그 그룹 05 화면,
    /// 댓글·멘션·반응은 내가 그 미션을 냈으면 결과 화면, 아니면 05 화면.
    static func target(_ notification: AppNotification, groupExists: Bool, canOpenResults: Bool) -> Target {
        guard groupExists else { return .missing }
        let aboutMyWork: Set<NotificationType> = [.comment, .mention, .reaction]
        if aboutMyWork.contains(notification.type), let missionID = notification.missionID, canOpenResults {
            return .results(groupID: notification.groupID, missionID: missionID)
        }
        return .mission(groupID: notification.groupID)
    }

    /// "방금" · "5분 전" · "3시간 전" · "2일 전" · "1주 전" · "3개월 전" (Android `koreanRelativeText`)
    static func relativeText(_ date: Date, now: Date) -> String {
        let seconds = max(0, Int(now.timeIntervalSince(date)))
        switch seconds {
        case ..<60: return "방금"
        case ..<3600: return "\(seconds / 60)분 전"
        case ..<86400: return "\(seconds / 3600)시간 전"
        case ..<604_800: return "\(seconds / 86400)일 전"
        case ..<2_419_200: return "\(seconds / 604_800)주 전"
        case ..<31_536_000: return "\(max(1, seconds / 2_592_000))개월 전"
        default: return "\(seconds / 31_536_000)년 전"
        }
    }
}
