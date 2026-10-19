import Foundation

struct SentPoke: Equatable {
    var toUserID: Int
    var kind: PokeKind
    var missionID: Int
}

/// 재촉에 실패한 뒤 화면이 할 일
enum PokeFollowUp: Equatable {
    /// 이미 보냈어요 — 보낸 것으로 표시해요.
    case markSent
    /// 상대가 이미 냈거나 미션이 없어요 — 다시 받아요.
    case refresh
    /// 내가 그룹에서 빠졌어요 — 화면을 닫아요.
    case leave
    case none
}

/// `message` 가 nil 이면 안내 없이 조용히 처리해요 (CONTRACT §4: 이미 보냄·이미 제출함).
struct PokeFailure: Equatable {
    var followUp: PokeFollowUp
    var message: String?

    init(followUp: PokeFollowUp, message: String?) {
        self.followUp = followUp
        self.message = message
    }

    init(error: Error) {
        switch error.apiCode {
        case "ALREADY_POKED": self.init(followUp: .markSent, message: nil)
        case "TARGET_ALREADY_SUBMITTED", "NO_OPEN_MISSION": self.init(followUp: .refresh, message: nil)
        case "TARGET_NOT_GROUP_MEMBER": self.init(followUp: .refresh, message: error.userMessage)
        case "NOT_GROUP_MEMBER", "GROUP_NOT_FOUND": self.init(followUp: .leave, message: error.userMessage)
        default: self.init(followUp: .none, message: error.userMessage)
        }
    }
}

/// 콕 찌르기·똥뿌리기 (`POST /api/groups/{id}/pokes`)
final class PokeRepository: Sendable {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func send(groupID: Int, toUserID: Int, kind: PokeKind, missionID: Int?) async throws -> SentPoke {
        let dto: PokeResponseDTO = try await api.post(
            "/api/groups/\(groupID)/pokes",
            body: PokeRequestDTO(toUserId: toUserID, type: kind.rawValue, missionId: missionID)
        )
        return SentPoke(toUserID: dto.toUserId, kind: PokeKind(rawValue: dto.type) ?? kind, missionID: dto.missionId)
    }
}
