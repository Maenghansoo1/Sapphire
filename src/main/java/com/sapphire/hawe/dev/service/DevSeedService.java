package com.sapphire.hawe.dev.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.auth.dto.TokenResponse;
import com.sapphire.hawe.character.dao.CharacterMapper;
import com.sapphire.hawe.character.domain.ProfileCharacter;
import com.sapphire.hawe.comment.dao.CommentInsertRow;
import com.sapphire.hawe.comment.dao.CommentMapper;
import com.sapphire.hawe.dev.dao.DevMapper;
import com.sapphire.hawe.dev.dto.DevDtos.ResetResponse;
import com.sapphire.hawe.dev.dto.DevDtos.SeedRequest;
import com.sapphire.hawe.dev.dto.DevDtos.SeedResult;
import com.sapphire.hawe.dev.dto.DevDtos.UserInfo;
import com.sapphire.hawe.global.config.MissionProperties;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.security.JwtTokenProvider;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.domain.GroupMember;
import com.sapphire.hawe.group.dto.GroupCreateRequest;
import com.sapphire.hawe.group.dto.GroupResponse;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.group.service.GroupService;
import com.sapphire.hawe.mission.dao.MissionInsertRow;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.domain.GenerationSource;
import com.sapphire.hawe.mission.domain.MissionTemplate;
import com.sapphire.hawe.mission.domain.MissionTiming;
import com.sapphire.hawe.reaction.dao.ReactionMapper;
import com.sapphire.hawe.submission.dao.SubmissionInsertRow;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DevSeedService {
    private static final MissionTemplate[] TEMPLATES = {
            MissionTemplate.TEXT_ACROSTIC, MissionTemplate.TEXT_ONE_WORD, MissionTemplate.TEXT_REVIEW,
            MissionTemplate.TEXT_BALANCE, MissionTemplate.TEXT_FILL_BLANK, MissionTemplate.SLIDER_POSITION,
            MissionTemplate.SLIDER_TASTE, MissionTemplate.SLIDER_CONSENSUS };
    private static final String[] STICKERS = { "heart", "laugh", "wow", "sad", "fire" };
    private final UserMapper users;
    private final CharacterMapper characters;
    private final PasswordEncoder passwords;
    private final JwtTokenProvider tokens;
    private final GroupService groupService;
    private final GroupMapper groups;
    private final GroupMemberMapper members;
    private final MissionMapper missions;
    private final SubmissionMapper submissions;
    private final ReactionMapper reactions;
    private final CommentMapper comments;
    private final DevMapper dev;
    private final MissionProperties missionProperties;
    private final ObjectMapper json;

    public DevSeedService(UserMapper users, CharacterMapper characters, PasswordEncoder passwords,
            JwtTokenProvider tokens, GroupService groupService, GroupMapper groups, GroupMemberMapper members,
            MissionMapper missions, SubmissionMapper submissions, ReactionMapper reactions, CommentMapper comments,
            DevMapper dev, MissionProperties missionProperties, ObjectMapper json) {
        this.users = users; this.characters = characters; this.passwords = passwords; this.tokens = tokens;
        this.groupService = groupService; this.groups = groups; this.members = members; this.missions = missions;
        this.submissions = submissions; this.reactions = reactions; this.comments = comments; this.dev = dev;
        this.missionProperties = missionProperties; this.json = json;
    }

    @Transactional
    public List<UserInfo> ensureUsers(int count) {
        List<ProfileCharacter> available = characters.findAll(0, 100);
        if (available.isEmpty()) throw new IllegalStateException("No profile characters configured");
        List<UserInfo> result = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            String email = "dev" + number + "@hawe.dev";
            User user = users.findByEmail(email);
            if (user == null) {
                user = new User();
                user.setEmail(email);
                user.setNickname("개발자" + number);
                user.setPasswordHash(passwords.encode("dev12345"));
                user.setTermsAgreedAt(SeoulTime.now());
                user.setPrivacyAgreedAt(SeoulTime.now());
                users.insert(user);
                characters.updateUserCharacterId(user.getId(), available.get((number - 1) % available.size()).getId());
                user = users.findById(user.getId());
            }
            Long characterId = user.getCharacterId();
            ProfileCharacter character = available.stream().filter(c -> c.getId().equals(characterId))
                    .findFirst().orElse(null);
            result.add(new UserInfo(user.getId(), user.getEmail(), user.getNickname(),
                    character == null ? null : character.getSvgKey()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public TokenResponse loginAs(Long userId) {
        User user = userId == null ? null : users.findById(userId);
        if (user == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return new TokenResponse(tokens.createAccessToken(user.getId(), user.getNickname(), user.getTokenVersion()),
                tokens.createRefreshToken(user.getId(), user.getTokenVersion()));
    }

    @Transactional
    public SeedResult seed(SeedRequest request) {
        int days = request == null || request.days() == null ? 30 : request.days();
        int count = request == null || request.memberCount() == null ? 5 : request.memberCount();
        int daily = request == null || request.dailyMissionCount() == null ? 1 : request.dailyMissionCount();
        String relation = request == null || request.relationType() == null ? "FRIEND" : request.relationType();
        if (days < 1 || days > 90 || count < 2 || count > ("COUPLE".equals(relation) ? 2 : 6)
                || daily < 1 || daily > missionProperties.maxDailyCount())
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        List<UserInfo> seedUsers = ensureUsers(Math.max(count, 6));
        String name = request == null ? null : request.name();
        if (name == null || name.isBlank()) {
            int suffix = dev.findDevGroupIds().size() + 1;
            do { name = "[DEV] 테스트 그룹 " + suffix++; } while (dev.countGroupsNamed(name) > 0);
        }
        GroupResponse created = groupService.createGroup(seedUsers.getFirst().userId(),
                new GroupCreateRequest(name, relation, null, daily, null));
        Long groupId = created.id();
        List<Long> memberIds = seedUsers.stream().limit(count).map(UserInfo::userId).toList();
        for (Long userId : memberIds.subList(1, memberIds.size())) {
            GroupMember member = new GroupMember();
            member.setGroupId(groupId); member.setUserId(userId); member.setRole(GroupMember.ROLE_MEMBER);
            members.insert(member);
        }
        LocalDate today = SeoulTime.today();
        LocalDateTime joinedAt = today.minusDays(days).atStartOfDay();
        dev.backdateGroup(groupId, joinedAt);
        dev.backdateMembers(groupId, joinedAt);
        Random random = new Random(groupId);
        int missionCount = 0, submissionCount = 0;
        for (int age = days; age >= 1; age--) {
            LocalDate date = today.minusDays(age);
            for (int slot = 1; slot <= daily; slot++) {
                MissionTemplate template = TEMPLATES[(missionCount) % TEMPLATES.length];
                LocalDateTime publishedAt = date.atTime(missionProperties.defaultPublishTime());
                LocalDateTime deadlineAt = MissionTiming.deadlineAfter(publishedAt,
                        missionProperties.defaultDeadlineTime());
                MissionInsertRow mission = new MissionInsertRow();
                mission.setGroupId(groupId); mission.setContent("[DEV] " + template.displayName() + " " + date);
                mission.setCategory("일상"); mission.setMissionDate(date); mission.setSlotNo(slot);
                mission.setTemplate(template.name()); mission.setParams(json.writeValueAsString(params(template, memberIds)));
                mission.setPublishedAt(publishedAt); mission.setDeadlineAt(deadlineAt);
                mission.setGenerationSource(GenerationSource.PLACEHOLDER.name());
                missions.insert(mission);
                missionCount++;
                for (int memberIndex = 0; memberIndex < memberIds.size(); memberIndex++) {
                    Long userId = memberIds.get(memberIndex);
                    if (random.nextDouble() >= 0.70) continue;
                    boolean late = random.nextDouble() < 0.20;
                    LocalDateTime submittedAt = late ? deadlineAt.plusMinutes(5 + random.nextInt(30))
                            : publishedAt.plusMinutes(10 + random.nextInt(120));
                    if (!late && submittedAt.isAfter(deadlineAt)) submittedAt = deadlineAt.minusSeconds(1);
                    SubmissionInsertRow submission = historicalSubmission(mission.getId(), userId,
                            template, submittedAt, late);
                    submissions.insert(submission);
                    submissionCount++;
                    if (random.nextInt(3) == 0) reactions.insert(submission.getId(),
                            memberIds.get((memberIndex + 1) % memberIds.size()), STICKERS[random.nextInt(STICKERS.length)]);
                    if (random.nextInt(4) == 0) comments.insert(new CommentInsertRow(submission.getId(),
                            memberIds.get((memberIndex + 1) % memberIds.size()), "좋았어요!", null,
                            submittedAt.plusMinutes(3)));
                }
            }
        }
        return new SeedResult(groupId, name, created.inviteCode(), memberIds.getFirst(), memberIds,
                days, missionCount, submissionCount);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        Group group = groups.findById(groupId);
        if (group == null) throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        for (Long userId : members.findUserIdsByGroupId(groupId))
            if (!userId.equals(group.getOwnerId())) groupService.leaveGroup(userId, groupId, null);
        groupService.leaveGroup(group.getOwnerId(), groupId, null);
    }

    @Transactional
    public ResetResponse reset() {
        List<Long> groupIds = dev.findDevGroupIds();
        for (Long id : groupIds) deleteGroup(id);
        SeoulTime.reset();
        return new ResetResponse(groupIds.size());
    }

    private Map<String, Object> params(MissionTemplate template, List<Long> members) {
        return switch (template) {
            case TEXT_ACROSTIC -> Map.of("word", "테스트");
            case TEXT_BALANCE -> Map.of("optionA", "아침", "optionB", "저녁");
            case TEXT_FILL_BLANK -> Map.of("sentence", "오늘은 ___ 함께했어요", "targetUserId", members.getFirst());
            case SLIDER_POSITION, SLIDER_CONSENSUS -> Map.of("question", "오늘의 기분은?", "left", "차분", "right", "활기");
            case SLIDER_TASTE -> Map.of("topic", "오늘의 취향", "left", "별로", "right", "최고");
            default -> Map.of();
        };
    }

    private SubmissionInsertRow historicalSubmission(Long missionId, Long userId, MissionTemplate template,
            LocalDateTime at, boolean late) {
        String answer = null, choice = null;
        Integer number = null;
        switch (template) {
            case TEXT_ACROSTIC -> answer = json.writeValueAsString(Map.of("text", "테 오늘도\n스 스마일\n트 트로피"));
            case TEXT_ONE_WORD -> answer = json.writeValueAsString(Map.of("text", "즐거움"));
            case TEXT_REVIEW -> { answer = json.writeValueAsString(Map.of("text", "좋은 하루였어요")); number = 4; }
            case TEXT_BALANCE -> choice = "A";
            case TEXT_FILL_BLANK -> answer = json.writeValueAsString(Map.of("text", "함께해서 좋았어요"));
            case SLIDER_POSITION, SLIDER_TASTE -> number = 55;
            case SLIDER_CONSENSUS -> { number = 55; answer = json.writeValueAsString(Map.of("guess", 50)); }
            default -> throw new IllegalArgumentException("Seed template needs a file: " + template);
        }
        return new SubmissionInsertRow(missionId, userId, null, null, at, late, null, answer, number, choice);
    }
}
