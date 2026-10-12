package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.dto.PageResponse;
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
import com.sapphire.hawe.submission.dto.SubmissionResponse;
import com.sapphire.hawe.submission.dto.ReactionSummary;
import java.io.IOException;
import java.text.BreakIterator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
public class SubmissionService {
    private final MissionMapper missions;
    private final GroupMemberMapper members;
    private final SubmissionMapper submissions;
    private final FileStorage storage;
    private final ObjectMapper json;
    private final ReactionMapper reactions;
    private final CommentMapper comments;

    public SubmissionService(MissionMapper missions, GroupMemberMapper members, SubmissionMapper submissions,
            FileStorage storage, ObjectMapper json, ReactionMapper reactions, CommentMapper comments) {
        this.missions = missions; this.members = members; this.submissions = submissions;
        this.storage = storage; this.json = json; this.reactions = reactions; this.comments = comments;
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

        String template = mission.getTemplate();
        boolean image = Set.of("PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "DRAW_SKETCH", "DRAW_A_TO_B")
                .contains(template);
        boolean audio = "VOICE_LINE".equals(template);
        boolean text = "TEXT_FILL_BLANK".equals(template) || "TEXT_ACROSTIC".equals(template);
        if (!image && !audio && !text) invalid("지원하지 않는 미션 틀입니다");
        Set<String> allowed = text ? Set.of("text") : audio
                ? Set.of("file", "duration", "waveform", "caption") : Set.of("file", "caption");
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
        if (text) {
            String raw = one(fields, "text");
            if (raw == null || raw.length() > 1000) invalid("글을 올바르게 입력해 주세요");
            String normalized = raw.replace("\r\n", "\n").strip();
            if (normalized.isEmpty()) invalid("글을 입력해 주세요");
            if ("TEXT_FILL_BLANK".equals(template) && graphemes(normalized) > 100)
                invalid("글은 100자 이하로 입력해 주세요");
            if ("TEXT_ACROSTIC".equals(template)) validateAcrostic(normalized, mission.getParams());
            answerData = json.writeValueAsString(Map.of("text", normalized));
        } else {
            MultipartFile file = files.getFirst("file");
            if (file == null || file.isEmpty()) invalid("파일을 첨부해 주세요");
            long max = image ? 10L * 1024 * 1024 : 5L * 1024 * 1024;
            if (file.getSize() > max) throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
            byte[] bytes;
            try { bytes = file.getBytes(); }
            catch (IOException e) { throw new IllegalStateException("파일을 읽을 수 없습니다", e); }
            if (bytes.length == 0) invalid("파일을 첨부해 주세요");
            if (bytes.length > max) throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
            FileTypeDetector.Detected detected = FileTypeDetector.detect(bytes);
            mediaType = image ? "IMAGE" : "AUDIO";
            if (detected == null || !mediaType.equals(detected.mediaType()))
                throw new BusinessException(SubmissionErrorCode.UNSUPPORTED_FILE_TYPE);
            if (audio) {
                String durationText = one(fields, "duration");
                double duration;
                try { duration = Double.parseDouble(durationText); }
                catch (Exception e) { throw new BusinessException(SubmissionErrorCode.INVALID_SUBMISSION, "음성 길이를 입력해 주세요"); }
                if (!Double.isFinite(duration) || duration <= 0 || duration > 30)
                    invalid("음성 길이는 30초 이하로 입력해 주세요");
                List<Double> waveform = parseWaveform(one(fields, "waveform"));
                answerData = json.writeValueAsString(Map.of("duration", duration, "waveform", waveform));
            }
            key = storage.store("submissions", bytes, detected.extension());
            String storedKey = key;
            if (TransactionSynchronizationManager.isSynchronizationActive())
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) storage.delete(storedKey);
                    }
                });
        }
        LocalDateTime now = SeoulTime.now();
        SubmissionInsertRow row = new SubmissionInsertRow(missionId, userId, key, caption, now,
                now.isAfter(mission.getDeadlineAt()), mediaType, answerData);
        try { submissions.insert(row); }
        catch (DuplicateKeyException e) {
            if (key != null) storage.delete(key);
            throw new BusinessException(SubmissionErrorCode.ALREADY_SUBMITTED);
        }
        return response(submissions.findDetailById(row.getId()), List.of(), 0);
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
        return rows.stream().map(row -> response(row,
                ReactionService.summarize(bySubmission.getOrDefault(row.getId(), List.of()), userId),
                counts.getOrDefault(row.getId(), 0))).toList();
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

    private List<Double> parseWaveform(String value) {
        try {
            Object parsed = json.readValue(value, Object.class);
            if (!(parsed instanceof List<?> values) || values.size() > 64)
                throw new BusinessException(SubmissionErrorCode.INVALID_SUBMISSION, "파형이 올바르지 않습니다");
            List<Double> result = new ArrayList<>();
            for (Object item : values) {
                if (!(item instanceof Number number))
                    throw new BusinessException(SubmissionErrorCode.INVALID_SUBMISSION, "파형이 올바르지 않습니다");
                double v = number.doubleValue();
                if (!Double.isFinite(v) || v < 0 || v > 1) invalid("파형이 올바르지 않습니다");
                result.add(v);
            }
            return result;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(SubmissionErrorCode.INVALID_SUBMISSION, "파형이 올바르지 않습니다"); }
    }

    private SubmissionResponse response(SubmissionRow row, List<ReactionSummary> reactions, int commentCount) {
        if (row == null) throw new IllegalStateException("Inserted submission was not found");
        String text = null;
        Double duration = null;
        List<Double> waveform = null;
        if (row.getAnswerData() != null) try {
            Map<?, ?> answer = json.readValue(row.getAnswerData(), Map.class);
            if (answer.get("text") instanceof String value) text = value;
            if (answer.get("duration") instanceof Number value && Double.isFinite(value.doubleValue()))
                duration = value.doubleValue();
            if (answer.get("waveform") instanceof List<?> values) {
                List<Double> parsed = new ArrayList<>();
                for (Object item : values) {
                    if (!(item instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                        parsed = null; break;
                    }
                    parsed.add(number.doubleValue());
                }
                waveform = parsed;
            }
        } catch (Exception ignored) { }
        return new SubmissionResponse(row.getId(), row.getMissionId(),
                new MemberProfileResponse(row.getUserId(), row.getNickname(), row.getCharacterKey(),
                        row.getProfileImageUrl() == null ? null : storage.publicUrl(row.getProfileImageUrl())),
                row.getTemplate(), row.getMediaUrl() == null ? null : storage.publicUrl(row.getMediaUrl()),
                row.getMediaType(), row.getCaption(), text, duration, waveform, reactions, commentCount,
                SeoulTime.toOffset(row.getSubmittedAt()), row.isLate());
    }
}
