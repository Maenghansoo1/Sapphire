package com.sapphire.hawe.dev.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

public final class DevDtos {
    private DevDtos() { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClockStatus(OffsetDateTime realNow, OffsetDateTime serverNow,
            long offsetSeconds, boolean offsetActive, String warning) { }
    public record ClockRequest(String now, Long plusSeconds, Boolean publish) { }
    public record PublishRequest(Long groupId, String template) { }
    public record RerollRequest(String template) { }
    public record PublishedMission(Long missionId, Long groupId, int slotNo, String template, String content) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublishResponse(List<PublishedMission> created, String message) { }
    public record DeadlineResponse(Long missionId, OffsetDateTime deadlineAt) { }
    public record UpdatedResponse(int updated) { }
    public record UserRequest(Long userId) { }
    public record ActorRequest(Long fromUserId, Long toUserId, String type, Long missionId) { }
    public record ReactionActorRequest(Long userId, String stickerType) { }
    public record CommentActorRequest(Long userId, String content) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubmitResult(Long userId, boolean ok, Long submissionId, String errorCode) { }
    public record UserInfo(Long userId, String email, String nickname, String characterKey) { }
    public record SeedRequest(Integer days, Integer memberCount, Integer dailyMissionCount,
            String relationType, String name) { }
    public record SeedResult(Long groupId, String name, String inviteCode, Long ownerUserId,
            List<Long> memberUserIds, int days, int missionsCreated, int submissionsCreated) { }
    public record RegenerateResponse(String status, String month) { }
    public record ResetResponse(int deletedGroups) { }
}
