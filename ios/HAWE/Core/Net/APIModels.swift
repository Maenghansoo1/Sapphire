import Foundation

// 서버 요청·응답 모양 (docs/BACKEND/API_DESIGN.md · MISSIONS_API.md). 모르는 키는 무시해요.

struct ErrorBodyDTO: Decodable {
    let code: String
    let message: String
}

struct PageDTO<T: Decodable>: Decodable {
    let content: [T]
}

// MARK: - 인증

struct RegisterRequestDTO: Encodable {
    let email: String
    let password: String
    let nickname: String
    let agreeTerms: Bool
    let agreePrivacy: Bool
}

struct RegisterResponseDTO: Decodable {
    let id: Int
    let email: String
    let nickname: String
}

struct LoginRequestDTO: Encodable {
    let email: String
    let password: String
}

struct RefreshRequestDTO: Encodable {
    let refreshToken: String
}

struct TokenResponseDTO: Decodable {
    let accessToken: String
    let refreshToken: String
}

struct MeDTO: Decodable {
    let id: Int
    let email: String
    let nickname: String
    let characterKey: String?
    let profileImageUrl: String?
    let createdAt: String
}

struct CharacterDTO: Decodable {
    let id: Int
    let name: String
    let svgKey: String
}

struct CharacterUpdateDTO: Encodable {
    let characterId: Int
}

struct CharacterUpdateResponseDTO: Decodable {
    let characterId: Int
    let name: String
    let svgKey: String
}

// MARK: - 그룹

struct GroupMemberProfileDTO: Decodable {
    let userId: Int
    let nickname: String
    let characterKey: String?
    let profileImageUrl: String?
}

struct GroupSummaryDTO: Decodable {
    let id: Int
    let name: String
    let relationType: String
    let memberCount: Int
    let maxMembers: Int
    let members: [GroupMemberProfileDTO]
    let startDate: String?
    let todayMission: TodayMissionSummaryDTO?
}

struct MissionSlotDTO: Decodable {
    let slotNo: Int
    let publishTime: String
    let deadlineTime: String
    let custom: Bool
}

struct GroupDetailDTO: Decodable {
    let id: Int
    let name: String
    let ownerId: Int
    let relationType: String
    let createdAt: String
    let memberCount: Int
    let maxMembers: Int
    let inviteCode: String
    let inviteUrl: String
    let startDate: String?
    let dailyMissionCount: Int
    let missionSlots: [MissionSlotDTO]
    let missionCount: Int
}

struct GroupMemberDTO: Decodable {
    let userId: Int
    let nickname: String
    let characterKey: String?
    let profileImageUrl: String?
    let role: String
}

struct CreateGroupRequestDTO: Encodable {
    let name: String
    let relationType: String
    let startDate: String?
}

struct InvitePreviewDTO: Decodable {
    let groupId: Int
    let name: String
    let relationType: String
    let memberCount: Int
    let maxMembers: Int
    let members: [GroupMemberProfileDTO]
}

struct JoinRequestDTO: Encodable {
    let code: String
}

struct JoinResponseDTO: Decodable {
    let groupId: Int
    let name: String
}

// MARK: - 미션

struct TodayMissionsDTO: Decodable {
    let date: String
    let dailyMissionCount: Int
    let nextPublishAt: String?
    let missions: [MissionDTO]
}

struct MissionDTO: Decodable {
    let missionId: Int
    let groupId: Int
    let slotNo: Int
    let number: Int
    let content: String
    let category: String
    let template: String
    let media: String
    let params: [String: JSONValue]?
    let publishedAt: String
    let deadlineAt: String
    let closesAt: String
    let submittedByMe: Bool
    let members: [MissionMemberDTO]
}

struct MissionMemberDTO: Decodable {
    let userId: Int
    let submitted: Bool
}

struct TodayMissionSummaryDTO: Decodable {
    let missionId: Int
    let media: String
    let content: String
    let submittedCount: Int
    let submittedByMe: Bool
}

// MARK: - 제출

struct SubmissionAuthorDTO: Decodable {
    let userId: Int
    let nickname: String
    let characterKey: String?
    let profileImageUrl: String?
}

struct SubmissionDTO: Decodable {
    let id: Int
    let missionId: Int
    let author: SubmissionAuthorDTO
    let template: String
    let mediaUrl: String?
    let mediaType: String?
    let duration: Double?
    let waveform: [Double]?
    let caption: String?
    let text: String?
    let submittedAt: String
    let isLate: Bool?
}

struct SubmissionPageDTO: Decodable {
    let content: [SubmissionDTO]
}

/// 미션 `params` 처럼 모양이 정해지지 않은 값
enum JSONValue: Decodable {
    case string(String), number(Double), bool(Bool), null, other

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .null }
        else if let value = try? container.decode(Bool.self) { self = .bool(value) }
        else if let value = try? container.decode(Double.self) { self = .number(value) }
        else if let value = try? container.decode(String.self) { self = .string(value) }
        else { self = .other }
    }

    var string: String? {
        switch self {
        case .string(let value): value
        case .number(let value): value.rounded() == value ? String(Int(value)) : String(value)
        case .bool(let value): String(value)
        case .null, .other: nil
        }
    }

    var int: Int? {
        switch self {
        case .number(let value): Int(value)
        case .string(let value): Int(value)
        default: nil
        }
    }
}
