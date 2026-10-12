import Foundation

/// 05 화면에 그릴 지금 열린 미션과 다음 발행 시각
struct CurrentMissionState {
    var mission: ServerMission?
    var nextPublishAt: Date?
}

struct ServerMission {
    var id: Int
    var number: Int
    var topic: String
    var type: MissionType?
    var template: String
    var spec: MissionSpec
    var publishedAt: Date
    var deadlineAt: Date
    var closesAt: Date
    var submittedByMe: Bool
    /// userId → 제출 여부
    var members: [Int: Bool]
}

extension MissionType {
    /// 서버 `media` (PHOTO · VOICE · TEXT · DRAW)
    init?(media: String) {
        switch media {
        case "PHOTO": self = .photo
        case "VOICE": self = .voice
        case "TEXT": self = .write
        case "DRAW": self = .draw
        default: return nil
        }
    }

    /// 서버 `template` 접두어 (PHOTO_* · DRAW_* · VOICE_* · TEXT_*)
    init?(template: String) {
        if template.hasPrefix("PHOTO_") { self = .photo }
        else if template.hasPrefix("DRAW_") { self = .draw }
        else if template.hasPrefix("VOICE_") { self = .voice }
        else if template.hasPrefix("TEXT_") { self = .write }
        else { return nil }
    }
}

/// 오늘의 미션 (`/api/groups/{id}/missions/today`)
final class MissionRepository: Sendable {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func current(groupID: Int) async throws -> CurrentMissionState {
        let response: TodayMissionsDTO = try await api.get("/api/groups/\(groupID)/missions/today")
        let mission = response.missions.first.map { row in
            ServerMission(
                id: row.missionId,
                number: row.number,
                topic: row.content,
                type: MissionType(media: row.media),
                template: row.template,
                spec: MissionSpec(params: row.params ?? [:]),
                publishedAt: ServerDate.instant(row.publishedAt) ?? .now,
                deadlineAt: ServerDate.instant(row.deadlineAt) ?? .now,
                closesAt: ServerDate.instant(row.closesAt) ?? .now,
                submittedByMe: row.submittedByMe,
                members: Dictionary(row.members.map { ($0.userId, $0.submitted) }, uniquingKeysWith: { a, b in a || b })
            )
        }
        return CurrentMissionState(mission: mission, nextPublishAt: response.nextPublishAt.flatMap(ServerDate.instant))
    }
}

extension MissionSpec {
    init(params: [String: JSONValue]) {
        self.init(
            seconds: params["seconds"]?.int,
            word: params["word"]?.string,
            drawFrom: params["a"]?.string,
            drawTo: params["b"]?.string
        )
    }
}
