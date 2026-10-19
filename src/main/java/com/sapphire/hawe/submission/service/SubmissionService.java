package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.global.config.VisionProperties;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.global.storage.FileTypeDetector;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dto.MemberProfileResponse;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.service.MissionErrorCode;
import com.sapphire.hawe.comment.dao.CommentMapper;
import com.sapphire.hawe.comment.dao.CommentCountRow;
import com.sapphire.hawe.reaction.dao.ReactionMapper;
import com.sapphire.hawe.reaction.dao.ReactionRow;
import com.sapphire.hawe.reaction.service.ReactionService;
import com.sapphire.hawe.submission.dao.SubmissionInsertRow;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import com.sapphire.hawe.submission.dao.AiReviewMapper;
import com.sapphire.hawe.submission.dao.AiReviewRow;
import com.sapphire.hawe.submission.domain.SubmissionCreatedEvent;
import com.sapphire.hawe.submission.dto.AiReviewResponse;
import com.sapphire.hawe.submission.dto.SubmissionResponse;
import com.sapphire.hawe.submission.dto.ReactionSummary;
import java.io.IOException;
import java.text.BreakIterator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
public class SubmissionService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);
    private final MissionMapper missions;
    private final GroupMemberMapper members;
    private final SubmissionMapper submissions;
    private final FileStorage storage;
    private final ObjectMapper json;
    private final ReactionMapper reactions;
    private final CommentMapper comments;
    private final AiReviewMapper aiReviews;
    private final VisionProperties vision;
    private final ApplicationEventPublisher events;

    public SubmissionService(MissionMapper missions, GroupMemberMapper members, SubmissionMapper submissions,
            FileStorage storage, ObjectMapper json, ReactionMapper reactions, CommentMapper comments,
            AiReviewMapper aiReviews, VisionProperties vision, ApplicationEventPublisher events) {
        this.missions = missions; this.members = members; this.submissions = submissions;
        this.storage = storage; this.json = json; this.reactions = reactions; this.comments = comments;
        this.aiReviews = aiReviews; this.vision = vision; this.events = events;
    }

    @Transactional
    public SubmissionResponse submit(Long userId, Long missionId, MultiValueMap<String, String> fields,
            MultiValueMap<String, MultipartFile> files) {
        MissionRow mission = missions.findById(missionId, userId);
        if (mission == null) throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        requireMember(mission.getGroupId(), userId);
        if (!missions.isCurrent(missionId)) throw new BusinessException(MissionErrorCode.MISSION_CLOSED);
        if (submissions.existsByMissionIdAndUserId(missionId, userId))
            throw new BusinessException(SubmissionErrorCode.ALREADY_SUBMITTED);

        String template = Objects.requireNonNullElse(mission.getTemplate(), "");
        Set<String> allowed = allowedFields(template);
        if (allowed == null) invalid("지원하지 않는 미션 틀입니다");
        for (String key : fields.keySet()) if (!allowed.contains(key)) invalid("요구하지 않는 필드: " + key);
        for (String key : files.keySet()) if (!allowed.contains(key) || !"file".equals(key))
            invalid("요구하지 않는 필드: " + key);
        for (List<String> values : fields.values()) if (values.size() != 1) invalid("중복된 필드입니다");
        for (List<MultipartFile> values : files.values()) if (values.size() != 1) invalid("중복된 파일입니다");

        String caption = one(fields, "caption");
        if (caption != null && caption.codePointCount(0, caption.length()) > 200) invalid("설명은 200자 이하로 입력해 주세요");
        String answerData = null;
        String mediaType = null;
        String key = null;
        Integer number = null;
        String choice = null;
        switch (template) {
            case "TEXT_ACROSTIC", "TEXT_FILL_BLANK" -> {
                String normalized = requiredText(fields);
                if ("TEXT_FILL_BLANK".equals(template) && graphemes(normalized) > 100)
                    invalid("글은 100자 이하로 입력해 주세요");
                if ("TEXT_ACROSTIC".equals(template)) validateAcrostic(normalized, mission.getParams());
                answerData = json.writeValueAsString(Map.of("text", normalized));
            }
            case "TEXT_ONE_WORD" -> {
                String word = requiredText(fields);
                if (word.codePoints().anyMatch(Character::isWhitespace)) invalid("띄어쓰기 없이 한 단어로 입력해 주세요");
                if (graphemes(word) > 20) invalid("단어는 20자 이하로 입력해 주세요");
                caption = optionalCaption(caption);
                if (caption != null && caption.codePointCount(0, caption.length()) > 100)
                    invalid("이유는 100자 이하로 입력해 주세요");
                answerData = json.writeValueAsString(Map.of("text", word));
            }
            case "TEXT_REVIEW" -> {
                number = integer(fields, "number", 1, 5, "별점은 1~5 중에서 골라 주세요");
                String review = requiredText(fields);
                if (graphemes(review) > 100) invalid("후기는 100자 이하로 입력해 주세요");
                answerData = json.writeValueAsString(Map.of("text", review));
            }
            case "TEXT_BALANCE" -> {
                choice = one(fields, "choice");
                if (!"A".equals(choice) && !"B".equals(choice)) invalid("A와 B 중 하나를 골라 주세요");
                caption = optionalCaption(caption);
            }
            case "SLIDER_POSITION", "SLIDER_TASTE" -> number = integer(fields, "number", 0, 100, "0~100 사이로 골라 주세요");
            case "SLIDER_CONSENSUS" -> {
                number = integer(fields, "number", 0, 100, "내 위치를 0~100 사이로 골라 주세요");
                int guess = integer(fields, "guess", 0, 100, "예상 평균을 0~100 사이로 골라 주세요");
                answerData = json.writeValueAsString(Map.of("guess", guess));
            }
            case "PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "DRAW_SKETCH", "DRAW_A_TO_B" -> {
                MultipartFile file = files.getFirst("file");
                if (file == null || file.isEmpty()) invalid("파일을 첨부해 주세요");
                long max = 10L * 1024 * 1024;
                if (file.getSize() > max) throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
                byte[] bytes;
                try { bytes = file.getBytes(); }
                catch (IOException e) { throw new IllegalStateException("파일을 읽을 수 없습니다", e); }
                if (bytes.length == 0) invalid("파일을 첨부해 주세요");
                if (bytes.length > max) throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
                FileTypeDetector.Detected detected = FileTypeDetector.detect(bytes);
                mediaType = "IMAGE";
                if (detected == null || !mediaType.equals(detected.mediaType()))
                    throw new BusinessException(SubmissionErrorCode.UNSUPPORTED_FILE_TYPE);
                key = storage.store("submissions", bytes, detected.extension());
                String storedKey = key;
                if (TransactionSynchronizationManager.isSynchronizationActive())
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) storage.delete(storedKey);
                        }
                    });
            }
            default -> throw new IllegalStateException("Unhandled template: " + template);
        }
        LocalDateTime now = SeoulTime.now();
        SubmissionInsertRow row = new SubmissionInsertRow(missionId, userId, key, caption, now,
                now.isAfter(mission.getDeadlineAt()), mediaType, answerData, number, choice);
        try { submissions.insert(row); }
        catch (DuplicateKeyException e) {
            if (key != null) storage.delete(key);
            throw new BusinessException(SubmissionErrorCode.ALREADY_SUBMITTED);
        }
        if (key != null && ("PHOTO_NOW".equals(template) || "PHOTO_COLOR".equals(template))) {
            try { events.publishEvent(new SubmissionCreatedEvent(row.getId(), template)); }
            catch (RuntimeException e) { log.warn("Vision event failed for submission {}", row.getId(), e); }
        }
        return response(submissions.findDetailById(row.getId()), List.of(), 0, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionResponse> feed(Long userId, Long missionId, int page, int size) {
        MissionRow mission = missions.findById(missionId, userId);
        if (mission == null) throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        requireMember(mission.getGroupId(), userId);
        requireSubmitted(missionId, userId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        long total = submissions.countByMissionId(missionId);
        List<SubmissionRow> rows = submissions.findFeedByMissionId(missionId, (long) safePage * safeSize, safeSize);
        List<SubmissionResponse> content = enrich(rows, userId);
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Transactional(readOnly = true)
    public SubmissionResponse detail(Long userId, Long submissionId) {
        SubmissionRow row = requireAccessible(userId, submissionId);
        return enrich(List.of(row), userId).getFirst();
    }

    public SubmissionRow requireAccessible(Long userId, Long submissionId) {
        SubmissionRow row = submissions.findDetailById(submissionId);
        if (row == null) throw new BusinessException(SubmissionErrorCode.SUBMISSION_NOT_FOUND);
        requireMember(row.getGroupId(), userId);
        requireSubmitted(row.getMissionId(), userId);
        return row;
    }

    private List<SubmissionResponse> enrich(List<SubmissionRow> rows, Long userId) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(SubmissionRow::getId).toList();
        Map<Long, List<ReactionRow>> bySubmission = new HashMap<>();
        for (ReactionRow reaction : reactions.findBySubmissionIds(ids))
            bySubmission.computeIfAbsent(reaction.getSubmissionId(), ignored -> new ArrayList<>()).add(reaction);
        Map<Long, Integer> counts = new HashMap<>();
        for (CommentCountRow count : comments.countBySubmissionIds(ids))
            counts.put(count.getSubmissionId(), count.getCount());
        Map<Long, AiReviewRow> reviewBySubmission = new HashMap<>();
        if (vision.enabled()) for (AiReviewRow review : aiReviews.findBySubmissionIds(ids))
            reviewBySubmission.put(review.getSubmissionId(), review);
        return rows.stream().map(row -> response(row,
                ReactionService.summarize(bySubmission.getOrDefault(row.getId(), List.of()), userId),
                counts.getOrDefault(row.getId(), 0), reviewResponse(reviewBySubmission.get(row.getId())))).toList();
    }

    private void requireMember(Long groupId, Long userId) {
        if (!members.existsByGroupIdAndUserId(groupId, userId))
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
    }
    private void requireSubmitted(Long missionId, Long userId) {
        if (!submissions.existsByMissionIdAndUserId(missionId, userId))
            throw new BusinessException(SubmissionErrorCode.FEED_LOCKED);
    }
    private static String one(MultiValueMap<String, String> fields, String key) { return fields.getFirst(key); }
    private static void invalid(String message) { throw new BusinessException(SubmissionErrorCode.INVALID_SUBMISSION, message); }

    /** 틀마다 받는 필드 (MISSIONS_API §3-2). null 이면 제출을 받지 않는 틀(예전 음성 틀·2차 틀). */
    private static Set<String> allowedFields(String template) {
        return switch (template) {
            case "PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "DRAW_SKETCH", "DRAW_A_TO_B" -> Set.of("file", "caption");
            case "TEXT_ACROSTIC", "TEXT_FILL_BLANK" -> Set.of("text");
            case "TEXT_ONE_WORD" -> Set.of("text", "caption");
            case "TEXT_REVIEW" -> Set.of("number", "text");
            case "TEXT_BALANCE" -> Set.of("choice", "caption");
            case "SLIDER_POSITION", "SLIDER_TASTE" -> Set.of("number");
            case "SLIDER_CONSENSUS" -> Set.of("number", "guess");
            default -> null;
        };
    }

    private static String requiredText(MultiValueMap<String, String> fields) {
        String raw = one(fields, "text");
        if (raw == null || raw.length() > 1000) invalid("글을 올바르게 입력해 주세요");
        String normalized = raw.replace("\r\n", "\n").strip();
        if (normalized.isEmpty()) invalid("글을 입력해 주세요");
        return normalized;
    }

    /** 새 틀의 선택 설명: 앞뒤 공백을 지우고, 비었으면 저장하지 않는다. */
    private static String optionalCaption(String caption) {
        if (caption == null) return null;
        String stripped = caption.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** 0 이상의 정수만 받는다(부호·소수·공백 불가). */
    private static int integer(MultiValueMap<String, String> fields, String key, int min, int max, String message) {
        String raw = one(fields, key);
        if (raw == null || !raw.matches("[0-9]{1,3}")) invalid(message);
        int value = Integer.parseInt(raw);
        if (value < min || value > max) invalid(message);
        return value;
    }

    private static int graphemes(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(text);
        int count = 0;
        for (int end = iterator.first(); (end = iterator.next()) != BreakIterator.DONE;) count++;
        return count;
    }

    private void validateAcrostic(String text, String paramsJson) {
        String[] lines = text.split("\n", -1);
        int length = lines.length;
        while (length > 0 && lines[length - 1].isBlank()) length--;
        if (length != 3) invalid("3줄로 써 주세요");
        String word = null;
        try {
            Object value = json.readValue(paramsJson, Map.class).get("word");
            if (value instanceof String s && s.codePointCount(0, s.length()) == 3) word = s;
        } catch (Exception ignored) { }
        for (int i = 0; i < 3; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) invalid("각 줄에 글을 입력해 주세요");
            if (word != null && line.codePointAt(0) != word.codePointAt(word.offsetByCodePoints(0, i)))
                invalid("제시된 글자로 각 줄을 시작해 주세요");
        }
    }

    private static AiReviewResponse reviewResponse(AiReviewRow review) {
        if (review == null || !"DONE".equals(review.getStatus())
                || (review.getBadge() == null && review.getComment() == null)) return null;
        return new AiReviewResponse(review.getBadge(), review.getComment());
    }

    private SubmissionResponse response(SubmissionRow row, List<ReactionSummary> reactions, int commentCount,
            AiReviewResponse aiReview) {
        if (row == null) throw new IllegalStateException("Inserted submission was not found");
        String text = null;
        Integer guess = null;
        if (row.getAnswerData() != null) try {
            Map<?, ?> answer = json.readValue(row.getAnswerData(), Map.class);
            if (answer.get("text") instanceof String value) text = value;
            if (answer.get("guess") instanceof Number value) guess = value.intValue();
        } catch (Exception ignored) { }
        return new SubmissionResponse(row.getId(), row.getMissionId(),
                new MemberProfileResponse(row.getUserId(), row.getNickname(), row.getCharacterKey(),
                        row.getProfileImageUrl() == null ? null : storage.publicUrl(row.getProfileImageUrl())),
                row.getTemplate(), row.getMediaUrl() == null ? null : storage.publicUrl(row.getMediaUrl()),
                row.getMediaType(), row.getCaption(), text, row.getNumberValue(), row.getChoiceValue(), guess,
                reactions, commentCount, aiReview, SeoulTime.toOffset(row.getSubmittedAt()), row.isLate());
    }
}
