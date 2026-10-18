import UIKit

/// 홈 카드에 쓰는 오늘 미션 요약 (그룹 목록 응답의 `todayMissions`)
struct MissionSummary: Hashable {
    var missionID: Int
    var type: MissionType?
    var topic: String
    var submittedCount: Int
    var submittedByMe: Bool
}

struct LoadedGroup {
    var detail: GroupDetail
    /// 오늘 열린 미션들 (슬롯 순서)
    var missions: [MissionSummary]

    var mission: MissionSummary? { missions.first }
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
                        let members = summary.members.map { $0.member(api: api, myUserID: myUserID, ownerID: detail.ownerId) }
                        let today = (summary.todayMissions ?? []).isEmpty
                            ? [summary.todayMission].compactMap { $0 }
                            : summary.todayMissions ?? []
                        let missions = today.map {
                            MissionSummary(missionID: $0.missionId, type: MissionType(media: $0.media), topic: $0.content,
                                           submittedCount: $0.submittedCount, submittedByMe: $0.submittedByMe)
                        }
                        return (index, LoadedGroup(detail: detail.model(members: members), missions: missions))
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
        return info.model(members: try await members.content.map { $0.member(api: api, myUserID: myUserID, ownerID: info.ownerId) })
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
            members: response.members.map { api.avatar(characterKey: $0.characterKey, profileImageURL: $0.profileImageUrl) },
            memberCount: response.memberCount,
            maxMembers: response.maxMembers
        )
    }

    func join(code: String) async throws -> Int {
        let response: JoinResponseDTO = try await api.post("/api/groups/join", body: JoinRequestDTO(code: code))
        return response.groupId
    }

    /// 그룹 나가기. 다른 멤버가 있는 그룹장은 그룹장을 넘길 멤버(`successorUserID`)를 같이 보내요.
    func leave(groupID: Int, successorUserID: Int? = nil) async throws {
        try await api.delete("/api/groups/\(groupID)/members/me" + (successorUserID.map { "?successorUserId=\($0)" } ?? ""))
    }

    /// 그룹 내 프로필을 지우고 기본 프로필로 돌아가요.
    func clearGroupProfile(groupID: Int) async throws {
        try await api.delete("/api/groups/\(groupID)/members/me/profile")
    }

    /// 그룹 내 프로필 저장 — 캐릭터면 닉네임과 캐릭터 번호, 새로 고른 사진이면 닉네임과 사진을 같이 올려요.
    /// 이미 올린 사진(`.photo`)이면 바꿀 게 없어요.
    func saveGroupProfile(groupID: Int, avatar: AvatarContent, nickname: String) async throws {
        let path = "/api/groups/\(groupID)/members/me"
        let trimmed = nickname.trimmingCharacters(in: .whitespaces)
        switch avatar {
        case .character(let character):
            let page: PageDTO<CharacterDTO> = try await api.get("/api/characters?page=0&size=100", auth: false)
            guard let match = page.content.first(where: { $0.svgKey == character.rawValue }) else {
                throw APIError(status: 400, code: "VALIDATION_ERROR", message: "캐릭터를 찾을 수 없어요")
            }
            let _: GroupMemberProfileDTO = try await api.put(
                "\(path)/profile", body: GroupProfileRequestDTO(nickname: trimmed, characterId: match.id)
            )
        case .image(let image):
            let data = try await AvatarPhoto.uploadData(image)
            let _: GroupMemberProfileDTO = try await api.putMultipart("\(path)/profile-image", parts: [
                .text(name: "nickname", value: trimmed),
                .file(name: "file", filename: "profile.jpg", mime: "image/jpeg", data: data),
            ])
        case .photo:
            break
        }
    }

    /// 초대 코드 다시 만들기 (그룹장만) — 이전 코드는 바로 쓸 수 없게 돼요.
    func reissueInviteCode(groupID: Int) async throws -> InviteCodeDTO {
        try await api.post("/api/groups/\(groupID)/invite-code", body: EmptyBodyDTO())
    }

    /// 주제 도착 시간(1번 슬롯 발행 시각) 바꾸기 (그룹장만). 다른 슬롯과 직접 정한 마감은 그대로 보내요.
    func updateTopicTime(groupID: Int, time: DateComponents) async throws -> DateComponents {
        let detail: GroupDetailDTO = try await api.get("/api/groups/\(groupID)")
        let slotOne = detail.missionSlots.first { $0.slotNo == 1 }
        let publish = String(format: "%02d:%02d", time.hour ?? 9, time.minute ?? 0)
        let slots = [MissionSlotRequestDTO(slotNo: 1, publishTime: publish,
                                           deadlineTime: slotOne?.custom == true ? slotOne?.deadlineTime : nil)]
            + detail.missionSlots.filter { $0.slotNo != 1 && $0.custom }
                .map { MissionSlotRequestDTO(slotNo: $0.slotNo, publishTime: $0.publishTime, deadlineTime: $0.deadlineTime) }
        let updated: GroupDetailDTO = try await api.patch("/api/groups/\(groupID)/settings", body: GroupSettingsRequestDTO(missionSlots: slots))
        guard let saved = updated.missionSlots.first(where: { $0.slotNo == 1 }).flatMap({ ServerDate.time($0.publishTime) }) else {
            throw APIError(status: 200, code: "INVALID_RESPONSE", message: "서버 응답을 읽을 수 없습니다")
        }
        return saved
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
    func member(api: APIClient, myUserID: Int, ownerID: Int) -> GroupMember {
        GroupMember(
            id: userId,
            defaultProfile: Profile(avatar: api.avatar(characterKey: characterKey, profileImageURL: profileImageUrl), nickname: nickname),
            isMe: userId == myUserID,
            isOwner: userId == ownerID
        )
    }
}

private extension GroupMemberDTO {
    func member(api: APIClient, myUserID: Int, ownerID: Int) -> GroupMember {
        let profile = Profile(avatar: api.avatar(characterKey: characterKey, profileImageURL: profileImageUrl), nickname: nickname)
        return GroupMember(
            id: userId,
            defaultProfile: profile,
            groupProfile: hasGroupProfile == true ? profile : nil,
            isMe: userId == myUserID,
            isOwner: userId == ownerID || role == "owner"
        )
    }
}
