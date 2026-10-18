import Foundation

/// 개발자 API (`/api/dev`, 개발 서버에서만 열려요) — 개발자 모드의 개발자 도구에서만 써요.
final class DevRepository: Sendable {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func status() async throws -> DevClockStatusDTO { try await api.get("/api/dev/status", auth: false) }

    func setClock(now: String? = nil, plusSeconds: Int? = nil, publish: Bool = true) async throws -> DevClockStatusDTO {
        try await api.put("/api/dev/clock", body: DevClockRequestDTO(now: now, plusSeconds: plusSeconds, publish: publish), auth: false)
    }

    func resetClock() async throws -> DevClockStatusDTO { try await api.deleteReturning("/api/dev/clock", auth: false) }

    func publish(groupID: Int?, template: String? = nil) async throws -> DevPublishResponseDTO {
        try await api.post("/api/dev/missions/publish", body: DevPublishRequestDTO(groupId: groupID, template: template), auth: false)
    }

    func reroll(missionID: Int, template: String? = nil) async throws -> DevPublishedMissionDTO {
        try await api.post("/api/dev/missions/\(missionID)/reroll", body: DevRerollRequestDTO(template: template), auth: false)
    }

    func deadlineNow(missionID: Int) async throws -> AnyResponseDTO {
        try await api.post("/api/dev/missions/\(missionID)/deadline-now", body: EmptyBodyDTO(), auth: false)
    }

    func groupDeadlineNow(groupID: Int) async throws -> AnyResponseDTO {
        try await api.post("/api/dev/groups/\(groupID)/deadline-now", body: EmptyBodyDTO(), auth: false)
    }

    func submissions(missionID: Int) async throws -> [DevSubmissionDTO] {
        try await api.get("/api/dev/missions/\(missionID)/submissions", auth: false)
    }

    func submitAs(missionID: Int, userID: Int) async throws -> AnyResponseDTO {
        try await api.post("/api/dev/missions/\(missionID)/submit", body: DevUserRequestDTO(userId: userID), auth: false)
    }

    func submitMissing(missionID: Int) async throws -> [DevSubmitResultDTO] {
        try await api.post("/api/dev/missions/\(missionID)/submit-missing", body: EmptyBodyDTO(), auth: false)
    }

    func poke(groupID: Int, fromUserID: Int, toUserID: Int, type: String, missionID: Int?) async throws -> AnyResponseDTO {
        try await api.post("/api/dev/groups/\(groupID)/poke",
                           body: DevActorRequestDTO(fromUserId: fromUserID, toUserId: toUserID, type: type, missionId: missionID),
                           auth: false)
    }

    func react(submissionID: Int, userID: Int, stickerType: String) async throws -> AnyResponseDTO {
        try await api.post("/api/dev/submissions/\(submissionID)/react",
                           body: DevReactionRequestDTO(userId: userID, stickerType: stickerType), auth: false)
    }

    func comment(submissionID: Int, userID: Int, content: String) async throws -> AnyResponseDTO {
        try await api.post("/api/dev/submissions/\(submissionID)/comment",
                           body: DevCommentRequestDTO(userId: userID, content: content), auth: false)
    }

    func regenerateInsight(groupID: Int, month: String) async throws -> AnyResponseDTO {
        try await api.post("/api/dev/groups/\(groupID)/insights/regenerate?month=\(month)", body: EmptyBodyDTO(), auth: false)
    }

    func updateSettings(groupID: Int, dailyMissionCount: Int, slotPublishTimes: [String]) async throws -> AnyResponseDTO {
        let slots = slotPublishTimes.prefix(dailyMissionCount).enumerated().map { DevSlotRequestDTO(slotNo: $0.offset + 1, publishTime: $0.element) }
        return try await api.patch("/api/dev/groups/\(groupID)/settings",
                                   body: DevSettingsRequestDTO(dailyMissionCount: dailyMissionCount, missionSlots: Array(slots)), auth: false)
    }

    func users() async throws -> [DevUserDTO] { try await api.get("/api/dev/users", auth: false) }

    func loginAs(userID: Int) async throws -> TokenResponseDTO {
        try await api.post("/api/dev/login-as", body: DevUserRequestDTO(userId: userID), auth: false)
    }

    func seed(days: Int, memberCount: Int, dailyMissionCount: Int, relationType: String = "FRIEND") async throws -> DevSeedResultDTO {
        try await api.post("/api/dev/seed", body: DevSeedRequestDTO(days: days, memberCount: memberCount,
                                                                      dailyMissionCount: dailyMissionCount, relationType: relationType), auth: false)
    }

    func deleteGroup(groupID: Int) async throws { try await api.delete("/api/dev/groups/\(groupID)", auth: false) }

    func reset() async throws -> DevResetResultDTO { try await api.post("/api/dev/reset", body: EmptyBodyDTO(), auth: false) }
}
