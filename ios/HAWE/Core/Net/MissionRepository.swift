import Foundation

/// 05 화면에 그릴 지금 열린 미션들과 다음 발행 시각
struct CurrentMissionState {
    /// 하루 여러 미션(A6) — 슬롯 순서
    var missions: [ServerMission]
    var nextPublishAt: Date?

    var mission: ServerMission? { missions.first }
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
    /// userId → 이 미션에서 내가 보낸 재촉
    var pokedByMe: [Int: Set<PokeKind>] = [:]
}

extension MissionType {
    /// 서버 `media` (PHOTO · TEXT · DRAW · SLIDER)
    init?(media: String) {
        switch media {
        case "PHOTO": self = .photo
        case "TEXT": self = .write
        case "DRAW": self = .draw
        case "SLIDER": self = .slider
        default: return nil
        }
    }

    /// 서버 `template` 접두어 (PHOTO_* · DRAW_* · TEXT_* · SLIDER_*)
    init?(template: String) {
        if template.hasPrefix("PHOTO_") { self = .photo }
        else if template.hasPrefix("DRAW_") { self = .draw }
        else if template.hasPrefix("TEXT_") { self = .write }
        else if template.hasPrefix("SLIDER_") { self = .slider }
        else { return nil }
    }
}

/// 오늘의 미션 (`/api/groups/{id}/missions/today`, `/api/missions/{id}`)
final class MissionRepository: Sendable {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func current(groupID: Int) async throws -> CurrentMissionState {
        let response: TodayMissionsDTO = try await api.get("/api/groups/\(groupID)/missions/today")
        return CurrentMissionState(
            missions: response.missions.map(\.model),
            nextPublishAt: response.nextPublishAt.flatMap(ServerDate.instant)
        )
    }

    /// 미션 한 개(지난 미션 포함). 달력에서 연 지난 미션의 틀·빈칸을 알아낼 때 써요.
    func mission(missionID: Int) async throws -> ServerMission {
        let row: MissionDTO = try await api.get("/api/missions/\(missionID)")
        return row.model
    }
}

private extension MissionDTO {
    var model: ServerMission {
        ServerMission(
            id: missionId,
            number: number,
            topic: content,
            type: media.flatMap(MissionType.init(media:)),
            template: template,
            spec: MissionSpec(params: params ?? [:]),
            publishedAt: ServerDate.instant(publishedAt) ?? .now,
            deadlineAt: ServerDate.instant(deadlineAt) ?? .now,
            closesAt: ServerDate.instant(closesAt) ?? .now,
            submittedByMe: submittedByMe,
            members: Dictionary(members.map { ($0.userId, $0.submitted) }, uniquingKeysWith: { a, b in a || b }),
            pokedByMe: Dictionary(
                members.compactMap { member -> (Int, Set<PokeKind>)? in
                    let kinds = Set((member.pokedByMe ?? []).compactMap(PokeKind.init(rawValue:)))
                    return kinds.isEmpty ? nil : (member.userId, kinds)
                },
                uniquingKeysWith: { a, b in a.union(b) }
            )
        )
    }
}

extension MissionSpec {
    init(params: [String: JSONValue]) {
        self.init(
            seconds: params["seconds"]?.int,
            word: params["word"]?.string,
            drawFrom: params["a"]?.string,
            drawTo: params["b"]?.string,
            optionA: params["optionA"]?.string,
            optionB: params["optionB"]?.string,
            question: params["question"]?.string,
            topic: params["topic"]?.string,
            left: params["left"]?.string,
            right: params["right"]?.string,
            subject: params["subject"]?.string,
            hasTarget: params["target"] != nil || params["targetUserId"] != nil
        )
    }
}
