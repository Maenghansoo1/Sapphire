package com.sapphire.hawe.reaction.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.reaction.dao.ReactionMapper;
import com.sapphire.hawe.reaction.dao.ReactionRow;
import com.sapphire.hawe.reaction.domain.StickerType;
import com.sapphire.hawe.reaction.dto.ReactionRequest;
import com.sapphire.hawe.reaction.dto.ReactionsResponse;
import com.sapphire.hawe.submission.dto.ReactionSummary;
import com.sapphire.hawe.submission.service.SubmissionService;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReactionService {
    private final ReactionMapper reactions;
    private final SubmissionService submissions;
    public ReactionService(ReactionMapper reactions, SubmissionService submissions) {
        this.reactions = reactions; this.submissions = submissions;
    }

    @Transactional
    public ReactionsResponse toggle(Long userId, Long submissionId, ReactionRequest request) {
        StickerType type = StickerType.fromValue(request.stickerType());
        if (type == null) throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "지원하지 않는 스티커입니다");
        submissions.requireAccessible(userId, submissionId);
        if (reactions.delete(submissionId, userId, type.value()) == 0) {
            try { reactions.insert(submissionId, userId, type.value()); }
            catch (DuplicateKeyException ignored) { }
        }
        return new ReactionsResponse(summarize(reactions.findBySubmissionId(submissionId), userId));
    }

    public static List<ReactionSummary> summarize(List<ReactionRow> rows, Long viewerId) {
        return java.util.Arrays.stream(StickerType.values()).map(type -> {
            List<ReactionRow> matching = rows.stream().filter(row -> type.value().equals(row.getStickerType())).toList();
            List<Long> userIds = matching.stream().filter(row -> !row.isAi()).map(ReactionRow::getUserId).toList();
            return matching.isEmpty() ? null : new ReactionSummary(type.value(), matching.size(),
                    userIds.contains(viewerId), userIds, matching.stream().anyMatch(ReactionRow::isAi));
        }).filter(summary -> summary != null).toList();
    }
}
