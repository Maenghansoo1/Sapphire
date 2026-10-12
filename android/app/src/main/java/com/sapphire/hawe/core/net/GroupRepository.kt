package com.sapphire.hawe.core.net

import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.core.model.GroupDetail
import com.sapphire.hawe.core.model.GroupInvitePreview
import com.sapphire.hawe.core.model.GroupMember
import com.sapphire.hawe.core.model.Profile
import com.sapphire.hawe.core.model.MissionType
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.assets.Relationship
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class MissionSummary(val missionId: Long, val type: MissionType?, val topic: String,
    val submittedCount: Int, val submittedByMe: Boolean)
data class LoadedGroup(val detail: GroupDetail, val mission: MissionSummary?)

class GroupRepository(private val api: ApiClient) {
    suspend fun loadAll(myUserId: Long): List<LoadedGroup> = coroutineScope {
        val page: PageDto<GroupSummaryDto> = api.get("/api/groups?page=0&size=100")
        val results = page.content.map { summary -> async {
            try {
                val detail: GroupDetailDto = api.get("/api/groups/${summary.id}")
                LoadedGroup(detail.toModel(summary.members.map { it.toMember(myUserId, detail.ownerId) }),
                    summary.todayMission?.let { MissionSummary(it.missionId, mediaToType(it.media),
                        it.content, it.submittedCount, it.submittedByMe) })
            } catch (e: ApiException) {
                if (e.status == 403 || e.status == 404) null else throw e
            }
        } }.awaitAll().filterNotNull()
        if (page.content.isNotEmpty() && results.isEmpty()) throw ApiException(404, "GROUP_NOT_FOUND", "No group details available")
        results
    }

    suspend fun detail(groupId: Long, myUserId: Long): GroupDetail = coroutineScope {
        val detail = async { api.get<GroupDetailDto>("/api/groups/$groupId") }
        val members = async { api.get<PageDto<GroupMemberDto>>("/api/groups/$groupId/members?page=0&size=100") }
        val info = detail.await()
        info.toModel(members.await().content.map { it.toMember(myUserId, info.ownerId) })
    }

    suspend fun create(name: String, relationship: Relationship, startDate: LocalDate?,
        myUserId: Long, myProfile: Profile): GroupDetail {
        val response: GroupDetailDto = api.post("/api/groups", CreateGroupRequestDto(name.trim(), relationship.serverValue,
            startDate?.toString().takeIf { relationship == Relationship.LOVER }))
        return response.toModel(listOf(GroupMember(myUserId, myProfile,
            isMe = true, isOwner = myUserId == response.ownerId)))
    }

    suspend fun preview(code: String): GroupInvitePreview {
        val response: InvitePreviewDto = api.get("/api/invites/$code")
        return GroupInvitePreview(response.name, Relationship.fromServer(response.relationType),
            response.members.map { AvatarContent.Character(ProfileCharacter.fromKey(it.characterKey)) },
            response.memberCount, response.maxMembers)
    }

    suspend fun join(code: String): Long {
        val response: JoinResponseDto = api.post("/api/groups/join", JoinRequestDto(code))
        return response.groupId
    }

    suspend fun leave(groupId: Long, successorUserId: Long? = null) {
        api.delete("/api/groups/$groupId/members/me" + (successorUserId?.let { "?successorUserId=$it" } ?: ""))
    }

    private fun GroupDetailDto.toModel(members: List<GroupMember>): GroupDetail = GroupDetail(
        id, name, Relationship.fromServer(relationType), OffsetDateTime.parse(createdAt).toInstant(),
        missionCount.toInt(), inviteCode,
        missionSlots.firstOrNull { it.slotNo == 1 }?.publishTime?.let(LocalTime::parse) ?: LocalTime.of(9, 0),
        members = members, startDate = startDate?.let(LocalDate::parse))

    private fun GroupMemberProfileDto.toMember(myUserId: Long, ownerId: Long): GroupMember =
        GroupMember(userId, Profile(AvatarContent.Character(ProfileCharacter.fromKey(characterKey)), nickname),
            isMe = userId == myUserId, isOwner = userId == ownerId)

    private fun GroupMemberDto.toMember(myUserId: Long, ownerId: Long): GroupMember =
        GroupMember(userId, Profile(AvatarContent.Character(ProfileCharacter.fromKey(characterKey)), nickname),
            isMe = userId == myUserId, isOwner = userId == ownerId || role == "owner")
}
