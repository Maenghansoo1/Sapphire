import Foundation

/// 앱 안 알림 종류. rawValue 는 서버 `type` 값 (CONTRACT_DECISIONS §8-3, `reaction` 은 추가 요청).
enum NotificationType: String, CaseIterable, Hashable {
    case topic, poke, comment, mention, reaction
}

/// 알림 목록 한 줄. `from` 은 그 그룹의 표시 프로필이고, 미션 도착(`topic`)처럼 보낸 사람이 없으면 nil.
struct AppNotification: Identifiable, Hashable {
    var id: Int
    var type: NotificationType
    var groupID: Int
    var groupName: String
    var from: Profile?
    var missionID: Int?
    var submissionID: Int?
    var body: String?
    var isRead: Bool
    var createdAt: Date
}

struct NotificationFromDTO: Decodable {
    let userId: Int
    let nickname: String
    let characterKey: String?
    let profileImageUrl: String?
}

struct NotificationDTO: Decodable {
    let id: Int
    let type: String
    let groupId: Int
    let groupName: String?
    let fromUser: NotificationFromDTO?
    let missionId: Int?
    let submissionId: Int?
    let body: String?
    let isRead: Bool?
    let createdAt: String
}

struct NotificationPageDTO: Decodable {
    let content: [NotificationDTO]
}

/// 앱 안 알림 목록 (`GET /api/notifications`) — 앱이 모르는 종류는 빼요.
final class NotificationRepository: Sendable {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func list(page: Int = 0, size: Int = 50) async throws -> [AppNotification] {
        let dto: NotificationPageDTO = try await api.get("/api/notifications?page=\(page)&size=\(size)")
        return dto.content.compactMap { item in
            guard let type = NotificationType(rawValue: item.type), let createdAt = ServerDate.instant(item.createdAt) else { return nil }
            let from = item.fromUser.map {
                Profile(avatar: api.avatar(characterKey: $0.characterKey, profileImageURL: $0.profileImageUrl), nickname: $0.nickname)
            }
            let body = item.body?.trimmingCharacters(in: .whitespaces).isEmpty == false ? item.body : nil
            return AppNotification(
                id: item.id, type: type, groupID: item.groupId, groupName: item.groupName ?? "", from: from,
                missionID: item.missionId, submissionID: item.submissionId, body: body,
                isRead: item.isRead ?? false, createdAt: createdAt
            )
        }
    }

    func markRead(id: Int) async throws {
        try await api.patchNoContent("/api/notifications/\(id)/read")
    }

    func markAllRead() async throws {
        try await api.patchNoContent("/api/notifications/read-all")
    }
}
