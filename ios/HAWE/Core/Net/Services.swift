import Foundation
import Observation

/// 서버 저장소 묶음 — 화면에서는 `@Environment(Services.self)` 로 꺼내 써요.
@Observable
final class Services {
    @ObservationIgnored let auth: AuthRepository
    @ObservationIgnored let groups: GroupRepository
    @ObservationIgnored let missions: MissionRepository
    @ObservationIgnored let pokes: PokeRepository
    @ObservationIgnored let submissions: SubmissionRepository
    @ObservationIgnored let calendar: CalendarRepository
    @ObservationIgnored let insights: InsightRepository
    @ObservationIgnored let notifications: NotificationRepository
    @ObservationIgnored let dev: DevRepository
    @ObservationIgnored let tokens: TokenStore
    @ObservationIgnored let baseURL: URL

    init(baseURL: URL = AppConfig.apiBaseURL, tokens: TokenStore, onSessionExpired: @escaping @Sendable () -> Void = {}) {
        let api = APIClient(baseURL: baseURL, tokens: tokens, onSessionExpired: onSessionExpired)
        self.tokens = tokens
        self.baseURL = baseURL
        auth = AuthRepository(api: api, tokens: tokens)
        groups = GroupRepository(api: api)
        missions = MissionRepository(api: api)
        pokes = PokeRepository(api: api)
        submissions = SubmissionRepository(api: api)
        calendar = CalendarRepository(api: api)
        insights = InsightRepository(api: api)
        notifications = NotificationRepository(api: api)
        dev = DevRepository(api: api)
    }

    /// #Preview 용 — 서버에 닿지 않아요.
    static var preview: Services { Services(tokens: InMemoryTokenStore()) }
}

/// 아직 서버가 없는 기능을 눌렀을 때
enum ComingSoon {
    static let message = "아직 준비 중이에요"
}
