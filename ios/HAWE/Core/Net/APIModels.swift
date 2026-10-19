import Foundation

// 서버 요청·응답 모양 (docs/BACKEND/API_DESIGN.md · MISSIONS_API.md). 모르는 키는 무시해요.
// 서버가 빼먹을 수 있는 값은 옵셔널로 받아요 (Android 의 기본값과 같은 뜻).

struct ErrorBodyDTO: Decodable {
    let code: String
    let message: String
}

struct PageDTO<T: Decodable>: Decodable {
    let content: [T]
}

/// 본문이 없는 POST
struct EmptyBodyDTO: Encodable {}

/// 모양을 따지지 않는 응답 (개발자 API 결과를 글자로 보여 줄 때)
struct AnyResponseDTO: Decodable {
    let text: String

    init(from decoder: Decoder) throws {
        let value = try? JSONAny(from: decoder)
        text = value?.description ?? ""
    }
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

struct ProfileUpdateDTO: Encodable {
    let nickname: String
}

struct ProfileImageResponseDTO: Decodable {
    let profileImageUrl: String
}

struct PasswordChangeRequestDTO: Encodable {
    let currentPassword: String
    let newPassword: String
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
    /// 하루 여러 미션(B7) — 없으면 `todayMission` 하나만 써요.
    let todayMissions: [TodayMissionSummaryDTO]?
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
    /// 이 그룹에서만 쓰는 프로필이 있으면 위 닉네임·캐릭터가 그 프로필이에요.
    let hasGroupProfile: Bool?
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

struct GroupProfileRequestDTO: Encodable {
    let nickname: String
    let characterId: Int
}

struct InviteCodeDTO: Decodable {
    let inviteCode: String
    let inviteUrl: String
}

struct MissionSlotRequestDTO: Encodable {
    let slotNo: Int
    let publishTime: String?
    let deadlineTime: String?
}

struct GroupSettingsRequestDTO: Encodable {
    let missionSlots: [MissionSlotRequestDTO]
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
    let category: String?
    let template: String
    let media: String?
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
    /// 이 미션에서 내가 이 멤버에게 보낸 재촉 (poke)
    let pokedByMe: [String]?
}

struct TodayMissionSummaryDTO: Decodable {
    let missionId: Int
    let media: String
    let content: String
    let submittedCount: Int
    let submittedByMe: Bool
}

// MARK: - 콕 찌르기

struct PokeRequestDTO: Encodable {
    let toUserId: Int
    let type: String
    let missionId: Int?
}

struct PokeResponseDTO: Decodable {
    let id: Int
    let toUserId: Int
    let type: String
    let missionId: Int
    let sentAt: String?
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
    let caption: String?
    let text: String?
    let number: Int?
    let choice: String?
    let guess: Int?
    let submittedAt: String
    let isLate: Bool?
    let reactions: [ReactionDTO]?
    let commentCount: Int?
    let aiReview: AiReviewDTO?
}

struct AiReviewDTO: Decodable {
    let badge: String?
    let comment: String?
}

struct ReactionDTO: Decodable {
    let stickerType: String
    let count: Int?
    let reactedByMe: Bool?
    let userIds: [Int]?
}

struct ReactionsDTO: Decodable {
    let reactions: [ReactionDTO]?
}

struct ReactionRequestDTO: Encodable {
    let stickerType: String
}

struct CommentDTO: Decodable {
    let id: Int
    let author: SubmissionAuthorDTO
    let content: String?
    let imageUrl: String?
    let mentionedUserIds: [Int]?
    let createdAt: String
    let mine: Bool?
}

struct SubmissionPageDTO: Decodable {
    let content: [SubmissionDTO]
}

struct MissionResultDTO: Decodable {
    let template: String
    let totalCount: Int?
    let choices: [ChoiceCountDTO]?
    let myChoice: String?
    let average: Double?
    let distribution: [NumberEntryDTO]?
    let myNumber: Int?
    let diffFromAverage: Double?
    let ranking: [RankingEntryDTO]?
    let winnerUserIds: [Int]?
}

struct ChoiceCountDTO: Decodable {
    let choice: String
    let count: Int?
}

struct NumberEntryDTO: Decodable {
    let userId: Int
    let nickname: String?
    let number: Int
}

struct RankingEntryDTO: Decodable {
    let userId: Int
    let nickname: String?
    let guess: Int
    let diff: Double
}

// MARK: - 달력 · 활동 요약

struct CalendarDTO: Decodable {
    let month: String
    let days: [CalendarDayDTO]
}

struct CalendarDayDTO: Decodable {
    let date: String
    let missions: [CalendarMissionDTO]
}

struct CalendarMissionDTO: Decodable {
    let missionId: Int
    let media: String?
    let content: String
    let submittedCount: Int
    let memberCount: Int
    /// GOOD · LATE · PENALTY · null
    let stamp: String?
}

struct MyStatsDTO: Decodable {
    let completedMissions: Int
    let streakDays: Int
    let groupCount: Int
    let byMedia: [String: Int]?
}

// MARK: - 인사이트

struct InsightDTO: Decodable {
    let groupId: Int
    let month: String
    let status: String
    let retryAfterSeconds: Int?
    let stale: Bool?
    let empty: Bool?
    let activityIndex: InsightActivityDTO?
    let fastest: InsightFastestDTO?
    let topWords: [InsightWordDTO]?
    let mostReacted: InsightReactedDTO?
    let highlights: [String]?
}

struct InsightActivityDTO: Decodable {
    let score: Int
    let deltaFromPreviousMonth: Int?
    let participationRate: Double
    let onTimeRate: Double
    let interactionRate: Double
}

struct InsightFastestDTO: Decodable {
    let userId: Int
    let nickname: String
    let characterKey: String?
    let profileImageUrl: String?
    let firstCount: Int
    let averageResponseSeconds: Int
    let fastestSingleSeconds: Int
}

struct InsightWordDTO: Decodable {
    let word: String
    let count: Int
    let memberCount: Int
}

struct InsightReactedDTO: Decodable {
    let submissionId: Int
    let missionId: Int
    let missionContent: String
    let authorNickname: String
    let reactionCount: Int
    let commentCount: Int
    let topSticker: String?
}

// MARK: - 개발자 API (`/api/dev`, 개발 서버에서만 열려요)

struct DevClockStatusDTO: Decodable {
    let realNow: String?
    let serverNow: String?
    let offsetSeconds: Int?
    let offsetActive: Bool?
    let warning: String?
}

struct DevClockRequestDTO: Encodable {
    let now: String?
    let plusSeconds: Int?
    let publish: Bool
}

struct DevUserDTO: Decodable, Hashable {
    let userId: Int
    let email: String?
    let nickname: String?
    let characterKey: String?
}

struct DevUserRequestDTO: Encodable {
    let userId: Int
}

struct DevPublishRequestDTO: Encodable {
    let groupId: Int?
    let template: String?
}

struct DevRerollRequestDTO: Encodable {
    let template: String?
}

struct DevPublishedMissionDTO: Decodable {
    let missionId: Int
    let groupId: Int
    let slotNo: Int
    let template: String
    let content: String
}

struct DevPublishResponseDTO: Decodable {
    let created: [DevPublishedMissionDTO]?
    let message: String?
}

struct DevSubmissionDTO: Decodable, Hashable {
    let submissionId: Int
    let userId: Int
    let nickname: String?
    let template: String?
    let submittedAt: String?
    let late: Bool?
}

struct DevSubmitResultDTO: Decodable {
    let userId: Int
    let ok: Bool
    let submissionId: Int?
    let errorCode: String?
}

struct DevActorRequestDTO: Encodable {
    let fromUserId: Int
    let toUserId: Int
    let type: String
    let missionId: Int?
}

struct DevReactionRequestDTO: Encodable {
    let userId: Int
    let stickerType: String
}

struct DevCommentRequestDTO: Encodable {
    let userId: Int
    let content: String
}

struct DevSeedRequestDTO: Encodable {
    let days: Int
    let memberCount: Int
    let dailyMissionCount: Int
    let relationType: String
}

struct DevSeedResultDTO: Decodable {
    let groupId: Int
    let name: String?
    let inviteCode: String?
    let ownerUserId: Int
    let memberUserIds: [Int]?
    let days: Int?
    let missionsCreated: Int?
    let submissionsCreated: Int?
}

struct DevResetResultDTO: Decodable {
    let deletedGroups: Int?
}

struct DevSlotRequestDTO: Encodable {
    let slotNo: Int
    let publishTime: String
}

struct DevSettingsRequestDTO: Encodable {
    let dailyMissionCount: Int
    let missionSlots: [DevSlotRequestDTO]
}

// MARK: - 모양이 정해지지 않은 값

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

/// 아무 JSON 이나 읽어서 글자로 보여 줘요 (개발자 도구 결과 표시용).
private indirect enum JSONAny: Decodable, CustomStringConvertible {
    case object([String: JSONAny]), array([JSONAny]), string(String), number(Double), bool(Bool), null

    init(from decoder: Decoder) throws {
        if let keyed = try? decoder.container(keyedBy: AnyKey.self) {
            var object: [String: JSONAny] = [:]
            for key in keyed.allKeys { object[key.stringValue] = try keyed.decode(JSONAny.self, forKey: key) }
            self = .object(object)
        } else if var unkeyed = try? decoder.unkeyedContainer() {
            var array: [JSONAny] = []
            while !unkeyed.isAtEnd { array.append(try unkeyed.decode(JSONAny.self)) }
            self = .array(array)
        } else {
            let single = try decoder.singleValueContainer()
            if single.decodeNil() { self = .null }
            else if let value = try? single.decode(Bool.self) { self = .bool(value) }
            else if let value = try? single.decode(Double.self) { self = .number(value) }
            else { self = .string(try single.decode(String.self)) }
        }
    }

    var description: String {
        switch self {
        case .object(let object):
            "{" + object.keys.sorted().map { "\($0)=\(object[$0]!.description)" }.joined(separator: ", ") + "}"
        case .array(let array): "[" + array.map(\.description).joined(separator: ", ") + "]"
        case .string(let value): value
        case .number(let value): value.rounded() == value ? String(Int(value)) : String(value)
        case .bool(let value): String(value)
        case .null: "null"
        }
    }

    private struct AnyKey: CodingKey {
        var stringValue: String
        var intValue: Int?
        init?(stringValue: String) { self.stringValue = stringValue }
        init?(intValue: Int) { self.stringValue = String(intValue); self.intValue = intValue }
    }
}
