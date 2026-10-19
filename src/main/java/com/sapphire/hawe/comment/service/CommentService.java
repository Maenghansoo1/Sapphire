package com.sapphire.hawe.comment.service;

import com.sapphire.hawe.comment.dao.CommentInsertRow;
import com.sapphire.hawe.comment.dao.CommentMapper;
import com.sapphire.hawe.comment.dao.CommentMentionRow;
import com.sapphire.hawe.comment.dao.CommentRow;
import com.sapphire.hawe.comment.domain.CommentCreatedEvent;
import com.sapphire.hawe.comment.dto.CommentResponse;
import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.global.storage.FileTypeDetector;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.dto.MemberProfileResponse;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import com.sapphire.hawe.submission.service.SubmissionErrorCode;
import com.sapphire.hawe.submission.service.SubmissionService;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CommentService {
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);
    private final CommentMapper comments;
    private final SubmissionService submissions;
    private final GroupMemberMapper members;
    private final FileStorage storage;
    private final ApplicationEventPublisher events;
    public CommentService(CommentMapper comments, SubmissionService submissions,
            GroupMemberMapper members, FileStorage storage, ApplicationEventPublisher events) {
        this.comments = comments; this.submissions = submissions; this.members = members; this.storage = storage;
        this.events = events;
    }

    @Transactional
    public CommentResponse create(Long userId, Long submissionId, String content,
            List<Long> mentionedUserIds, MultipartFile file) {
        SubmissionRow submission = submissions.requireAccessible(userId, submissionId);
        String text = content == null ? "" : content.strip();
        if (text.codePointCount(0, text.length()) > 300) invalid("댓글은 300자 이하로 입력해 주세요");
        if (text.isEmpty() && file == null) invalid("댓글 또는 이미지를 입력해 주세요");
        LinkedHashSet<Long> mentions = new LinkedHashSet<>();
        if (mentionedUserIds != null) mentions.addAll(mentionedUserIds);
        for (Long id : mentions) {
            if (id == null || !members.existsByGroupIdAndUserId(submission.getGroupId(), id))
                invalid("같은 그룹 멤버만 언급할 수 있어요");
        }
        String key = null;
        if (file != null) {
            if (file.isEmpty()) invalid("빈 파일은 첨부할 수 없어요");
            if (file.getSize() > 10L * 1024 * 1024) throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
            byte[] bytes;
            try { bytes = file.getBytes(); }
            catch (IOException e) { throw new IllegalStateException("파일을 읽을 수 없습니다", e); }
            if (bytes.length == 0) invalid("빈 파일은 첨부할 수 없어요");
            if (bytes.length > 10L * 1024 * 1024) throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE);
            FileTypeDetector.Detected detected = FileTypeDetector.detect(bytes);
            if (detected == null || !"IMAGE".equals(detected.mediaType()))
                throw new BusinessException(SubmissionErrorCode.UNSUPPORTED_FILE_TYPE);
            key = storage.store("comments", bytes, detected.extension());
            String storedKey = key;
            if (TransactionSynchronizationManager.isSynchronizationActive())
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) storage.delete(storedKey);
                    }
                });
        }
        CommentInsertRow inserted = new CommentInsertRow(submissionId, userId, text, key, SeoulTime.now().withNano(0));
        comments.insert(inserted);
        for (Long id : mentions) comments.insertMention(inserted.getId(), id);
        events.publishEvent(new CommentCreatedEvent(inserted.getId(), submission.getGroupId(), submission.getMissionId(), submissionId,
                userId, submission.getUserId(), List.copyOf(mentions)));
        return response(comments.findDetailById(inserted.getId()), List.copyOf(mentions), userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(Long userId, Long submissionId, int page, int size) {
        submissions.requireAccessible(userId, submissionId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        long total = comments.countBySubmissionId(submissionId);
        List<CommentRow> rows = comments.findPageBySubmissionId(submissionId, (long) safePage * safeSize, safeSize);
        if (rows.isEmpty()) return PageResponse.of(List.of(), safePage, safeSize, total);
        Map<Long, List<Long>> mentions = new HashMap<>();
        for (CommentMentionRow mention : comments.findMentionsByCommentIds(rows.stream().map(CommentRow::getId).toList()))
            mentions.computeIfAbsent(mention.getCommentId(), ignored -> new ArrayList<>())
                    .add(mention.getMentionedUserId());
        List<CommentResponse> content = rows.stream()
                .map(row -> response(row, mentions.getOrDefault(row.getId(), List.of()), userId)).toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Transactional
    public void delete(Long userId, Long commentId) {
        CommentRow row = comments.findDetailById(commentId);
        if (row == null) throw new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND);
        submissions.requireAccessible(userId, row.getSubmissionId());
        if (!row.getUserId().equals(userId)) throw new BusinessException(CommentErrorCode.NOT_COMMENT_OWNER);
        comments.deleteMentionsByCommentId(commentId);
        comments.deleteById(commentId);
        if (row.getImageUrl() != null) {
            String key = row.getImageUrl();
            Runnable cleanup = () -> {
                try { storage.delete(key); }
                catch (RuntimeException error) { log.warn("Could not delete comment image after commit", error); }
            };
            if (TransactionSynchronizationManager.isSynchronizationActive())
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { cleanup.run(); }
                });
            else cleanup.run();
        }
    }

    private CommentResponse response(CommentRow row, List<Long> mentions, Long userId) {
        return new CommentResponse(row.getId(), new MemberProfileResponse(row.getUserId(), row.getNickname(),
                row.getCharacterKey(), row.getProfileImageUrl() == null ? null : storage.publicUrl(row.getProfileImageUrl())), row.getContent(),
                row.getImageUrl() == null ? null : storage.publicUrl(row.getImageUrl()),
                mentions, SeoulTime.toOffset(row.getCreatedAt()), row.getUserId().equals(userId));
    }

    private static void invalid(String message) {
        throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
    }
}
