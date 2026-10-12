import Foundation

/// 홈 카드에 쓰는 오늘 미션 요약 (그룹 목록 응답의 `todayMission`)
struct MissionSummary: Hashable {
    var missionID: Int
    var type: MissionType?
    var topic: String
    var submittedCount: Int
    var submittedByMe: Bool
}

struct LoadedGroup {
    var detail: GroupDetail
    var mission: MissionSummary?
}

/// 그룹·초대 (`/api/groups`, `/api/invites`)
final class GroupRepository: Sendable {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    /// 내 그룹 전부 — 목록을 받은 뒤 그룹마다 상세를 같이 받아요.
    func loadAll(myUserID: Int) async throws -> [LoadedGroup] {
        let page: PageDTO<GroupSummaryDTO> = try await api.get("/api/groups?page=0&size=100")
        let results = try await withThrowingTaskGroup(of: (Int, LoadedGroup?).self) { tasks in
            for (index, summary) in page.content.enumerated() {
                tasks.addTask { [api] in
                    do {
                        let detail: GroupDetailDTO = try await api.get("/api/groups/\(summary.id)")
                        let members = summary.members.map { $0.member(myUserID: myUserID, ownerID: detail.ownerId) }
                        let mission = summary.todayMission.map {
                            MissionSummary(missionID: $0.missionId, type: MissionType(media: $0.media), topic: $0.content,
                                           submittedCount: $0.submittedCount, submittedByMe: $0.submittedByMe)
                        }
                        return (index, LoadedGroup(detail: detail.model(members: members), mission: mission))
                    } catch let error as APIError where error.status == 403 || error.status == 404 {
                        return (index, nil)
                    }
                }
            }
            var rows: [(Int, LoadedGroup?)] = []
            for try await row in tasks { rows.append(row) }
            return rows.sorted { $0.0 < $1.0 }.compactMap(\.1)
        }
        if !page.content.isEmpty && results.isEmpty {
            throw APIError(status: 404, code: "GROUP_NOT_FOUND", message: "No group details available")
        }
        return results
    }

    func detail(groupID: Int, myUserID: Int) async throws -> GroupDetail {
        async let detail: GroupDetailDTO = api.get("/api/groups/\(groupID)")
        async let members: PageDTO<GroupMemberDTO> = api.get("/api/groups/\(groupID)/members?page=0&size=100")
        let info = try await detail
        return info.model(members: try await members.content.map { $0.member(myUserID: myUserID, ownerID: info.ownerId) })
    }

    func create(name: String, relationship: Relationship, startDate: Date?, myUserID: Int, myProfile: Profile) async throws -> GroupDetail {
        let response: GroupDetailDTO = try await api.post("/api/groups", body: CreateGroupRequestDTO(
            name: name.trimmingCharacters(in: .whitespaces),
            relationType: relationship.serverValue,
            startDate: relationship == .lover ? startDate.map(ServerDate.dayString) : nil
        ))
        return response.model(members: [
            GroupMember(id: myUserID, defaultProfile: myProfile, isMe: true, isOwner: myUserID == response.ownerId),
        ])
    }

    func preview(code: String) async throws -> GroupInvitePreview {
        let response: InvitePreviewDTO = try await api.get("/api/invites/\(code)")
        return GroupInvitePreview(
            name: response.name,
            relationship: Relationship(server: response.relationType),
            members: response.members.map { .character(ProfileCharacter.fromKey($0.characterKey)) },
            memberCount: response.memberCount,
            maxMembers: response.maxMembers
        )
    }

    func join(code: String) async throws -> Int {
        let response: JoinResponseDTO = try await api.post("/api/groups/join", body: JoinRequestDTO(code: code))
        return response.groupId
    }

    func leave(groupID: Int, successorUserID: Int? = nil) async throws {
        try await api.delete("/api/groups/\(groupID)/members/me" + (successorUserID.map { "?successorUserId=\($0)" } ?? ""))
    }
}

private extension GroupDetailDTO {
    func model(members: [GroupMember]) -> GroupDetail {
        GroupDetail(
            id: id,
            name: name,
            relationship: Relationship(server: relationType),
            createdAt: ServerDate.instant(createdAt) ?? .now,
            missionCount: missionCount,
            inviteCode: inviteCode,
            topicTime: missionSlots.first(where: { $0.slotNo == 1 }).flatMap { ServerDate.time($0.publishTime) }
                ?? DateComponents(hour: 9, minute: 0),
            notify: true,
            members: members,
            startDate: startDate.flatMap(ServerDate.day)
        )
    }
}

private extension GroupMemberProfileDTO {
    func member(myUserID: Int, ownerID: Int) -> GroupMember {
        GroupMember(
            id: userId,
            defaultProfile: Profile(avatar: .character(ProfileCharacter.fromKey(characterKey)), nickname: nickname),
            isMe: userId == myUserID,
            isOwner: userId == ownerID
        )
    }
}

private extension GroupMemberDTO {
    func member(myUserID: Int, ownerID: Int) -> GroupMember {
        GroupMember(
            id: userId,
            defaultProfile: Profile(avatar: .character(ProfileCharacter.fromKey(characterKey)), nickname: nickname),
            isMe: userId == myUserID,
            isOwner: userId == ownerID || role == "owner"
        )
    }
}
