import Foundation

enum InsightResult {
    case ready(GroupInsight)
    /// 서버가 아직 계산 중이에요 (202) — `retryAfterSeconds` 뒤에 다시 물어봐요.
    case generating(retryAfterSeconds: Int)
}

enum InsightOutcome: Equatable {
    /// `updating` 이면 지난 값을 보여 주는 중이고 새 값을 받는 중이에요.
    case ready(GroupInsight, updating: Bool)
    case timedOut
}

/// 그룹 활동 인사이트 (`GET /api/groups/{id}/insights?month=2026-10`)
final class InsightRepository: Sendable {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func load(groupID: Int, month: Date) async throws -> InsightResult {
        let monthText = CalendarRepository.monthString(month)
        let dto: InsightDTO = try await api.get("/api/groups/\(groupID)/insights?month=\(monthText)")
        guard dto.groupId == groupID, dto.month == monthText else {
            throw APIError(status: 200, code: "INVALID_RESPONSE", message: "요청한 달의 응답이 아닙니다")
        }
        switch dto.status {
        case "GENERATING":
            return .generating(retryAfterSeconds: dto.retryAfterSeconds ?? 3)
        case "READY":
            return .ready(GroupInsight(
                month: month,
                stale: dto.stale ?? false,
                empty: dto.empty ?? false,
                activityIndex: dto.activityIndex.map {
                    ActivityIndex(score: $0.score, delta: $0.deltaFromPreviousMonth, participationRate: $0.participationRate,
                                  onTimeRate: $0.onTimeRate, interactionRate: $0.interactionRate)
                },
                fastest: dto.fastest.map {
                    FastestMember(
                        userID: $0.userId,
                        profile: Profile(avatar: api.avatar(characterKey: $0.characterKey, profileImageURL: $0.profileImageUrl), nickname: $0.nickname),
                        firstCount: $0.firstCount, averageResponseSeconds: $0.averageResponseSeconds,
                        fastestSingleSeconds: $0.fastestSingleSeconds
                    )
                },
                topWords: (dto.topWords ?? []).map { TopWord(word: $0.word, count: $0.count, memberCount: $0.memberCount) },
                mostReacted: dto.mostReacted.map {
                    MostReacted(submissionID: $0.submissionId, missionID: $0.missionId, missionContent: $0.missionContent,
                                authorNickname: $0.authorNickname, reactionCount: $0.reactionCount,
                                commentCount: $0.commentCount, topSticker: $0.topSticker.flatMap(Sticker.init(rawValue:)))
                },
                highlights: dto.highlights ?? []
            ))
        default:
            throw APIError(status: 200, code: "INVALID_RESPONSE", message: "알 수 없는 인사이트 상태입니다")
        }
    }
}

/// 계산 중(202)이면 2~6초 간격으로 최대 8번 물어보고, 지난 값(stale)이면 먼저 보여 준 뒤 한 번 더 받아요.
/// (Android `loadInsight` 와 같은 순서)
func loadInsight(
    _ load: () async throws -> InsightResult,
    sleep: (Int) async throws -> Void = { try await Task.sleep(for: .seconds($0)) },
    onProgress: (InsightOutcome) -> Void
) async throws {
    for attempt in 0..<8 {
        switch try await load() {
        case .generating(let retryAfter):
            if attempt == 7 {
                onProgress(.timedOut)
                return
            }
            try await sleep(min(max(retryAfter, 2), 6))
        case .ready(let first):
            onProgress(.ready(first, updating: first.stale))
            if first.stale {
                do {
                    if case .ready(let refreshed) = try await load(), !refreshed.stale {
                        onProgress(.ready(refreshed, updating: false))
                    } else {
                        onProgress(.ready(first, updating: false))
                    }
                } catch is CancellationError {
                    throw CancellationError()
                } catch let error where error.apiCode == "NOT_GROUP_MEMBER" || error.apiCode == "GROUP_NOT_FOUND" {
                    throw error
                } catch {
                    onProgress(.ready(first, updating: false))
                }
            }
            return
        }
    }
}
