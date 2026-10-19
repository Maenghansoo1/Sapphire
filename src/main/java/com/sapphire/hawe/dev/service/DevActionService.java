package com.sapphire.hawe.dev.service;

import com.sapphire.hawe.comment.dto.CommentResponse;
import com.sapphire.hawe.comment.service.CommentService;
import com.sapphire.hawe.dev.dao.DevMapper;
import com.sapphire.hawe.dev.dao.DevSubmissionRow;
import com.sapphire.hawe.dev.dto.DevDtos.ActorRequest;
import com.sapphire.hawe.dev.dto.DevDtos.CommentActorRequest;
import com.sapphire.hawe.dev.dto.DevDtos.ReactionActorRequest;
import com.sapphire.hawe.dev.dto.DevDtos.SubmitResult;
import com.sapphire.hawe.dev.dto.DevDtos.UserRequest;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.service.MissionErrorCode;
import com.sapphire.hawe.poke.dto.PokeRequest;
import com.sapphire.hawe.poke.dto.PokeResponse;
import com.sapphire.hawe.poke.service.PokeService;
import com.sapphire.hawe.reaction.dto.ReactionRequest;
import com.sapphire.hawe.reaction.dto.ReactionsResponse;
import com.sapphire.hawe.reaction.service.ReactionService;
import com.sapphire.hawe.submission.dto.SubmissionResponse;
import com.sapphire.hawe.submission.service.SubmissionService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
public class DevActionService {
    private final MissionMapper missions;
    private final GroupMemberMapper members;
    private final DevMapper dev;
    private final SubmissionService submissions;
    private final PokeService pokes;
    private final ReactionService reactions;
    private final CommentService comments;
    private final ObjectMapper json;

    public DevActionService(MissionMapper missions, GroupMemberMapper members, DevMapper dev,
            SubmissionService submissions, PokeService pokes, ReactionService reactions,
            CommentService comments, ObjectMapper json) {
        this.missions = missions; this.members = members; this.dev = dev; this.submissions = submissions;
        this.pokes = pokes; this.reactions = reactions; this.comments = comments; this.json = json;
    }

    public List<DevSubmissionRow> listSubmissions(Long missionId) {
        if (dev.findMission(missionId) == null) throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        return dev.findSubmissions(missionId);
    }

    public SubmissionResponse submit(Long missionId, UserRequest request) {
        if (request == null || request.userId() == null)
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        MissionRow mission = missions.findById(missionId, request.userId());
        if (mission == null) throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        Sample sample = sample(mission);
        return submissions.submit(request.userId(), missionId, sample.fields(), sample.files());
    }

    public List<SubmitResult> submitMissing(Long missionId) {
        MissionRow mission = missions.findById(missionId, 0L);
        if (mission == null) throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        List<SubmitResult> results = new ArrayList<>();
        for (Long userId : members.findUserIdsByGroupId(mission.getGroupId())) {
            if (dev.findSubmissions(missionId).stream().anyMatch(row -> userId.equals(row.getUserId()))) continue;
            try {
                SubmissionResponse submitted = submit(missionId, new UserRequest(userId));
                results.add(new SubmitResult(userId, true, submitted.id(), null));
            } catch (BusinessException error) {
                results.add(new SubmitResult(userId, false, null, error.errorCode().code()));
            } catch (RuntimeException error) {
                results.add(new SubmitResult(userId, false, null, CommonErrorCode.INTERNAL_ERROR.code()));
            }
        }
        return results;
    }

    public PokeResponse poke(Long groupId, ActorRequest request) {
        if (request == null || request.fromUserId() == null || request.toUserId() == null)
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        return pokes.sendPoke(request.fromUserId(), groupId,
                new PokeRequest(request.toUserId(), request.type(), request.missionId()));
    }

    public ReactionsResponse react(Long submissionId, ReactionActorRequest request) {
        if (request == null || request.userId() == null)
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        return reactions.toggle(request.userId(), submissionId, new ReactionRequest(request.stickerType()));
    }

    public CommentResponse comment(Long submissionId, CommentActorRequest request) {
        if (request == null || request.userId() == null)
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        return comments.create(request.userId(), submissionId, request.content(), List.of(), null);
    }

    record Sample(MultiValueMap<String, String> fields, MultiValueMap<String, MultipartFile> files) { }

    Sample sample(MissionRow mission) {
        MultiValueMap<String, String> fields = new LinkedMultiValueMap<>();
        MultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<>();
        String template = mission.getTemplate();
        if (template == null) throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        switch (template) {
            case "TEXT_ACROSTIC" -> {
                String word = "테스트";
                try {
                    Object value = json.readValue(mission.getParams(), Map.class).get("word");
                    if (value instanceof String text && text.codePointCount(0, text.length()) == 3) word = text;
                } catch (RuntimeException ignored) { }
                StringBuilder body = new StringBuilder();
                for (int i = 0; i < 3; i++) {
                    if (i > 0) body.append('\n');
                    body.appendCodePoint(word.codePointAt(word.offsetByCodePoints(0, i))).append(" 테스트 문장");
                }
                fields.add("text", body.toString());
            }
            case "TEXT_ONE_WORD" -> fields.add("text", "테스트");
            case "TEXT_REVIEW" -> { fields.add("number", "4"); fields.add("text", "재미있었어요"); }
            case "TEXT_BALANCE" -> fields.add("choice", "A");
            case "TEXT_FILL_BLANK" -> fields.add("text", "함께한 하루였어요");
            case "PHOTO_NOW", "PHOTO_GALLERY", "PHOTO_COLOR", "DRAW_SKETCH", "DRAW_A_TO_B" ->
                    files.add("file", new ImageFile(samplePng()));
            default -> throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        return new Sample(fields, files);
    }

    private static byte[] samplePng() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.CYAN);
        graphics.fillRect(0, 0, 32, 32);
        graphics.dispose();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    private record ImageFile(byte[] data) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return "dev-sample.png"; }
        public String getContentType() { return "image/png"; }
        public boolean isEmpty() { return data.length == 0; }
        public long getSize() { return data.length; }
        public byte[] getBytes() { return data.clone(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        public void transferTo(java.io.File destination) throws IOException {
            java.nio.file.Files.write(destination.toPath(), data);
        }
    }
}
