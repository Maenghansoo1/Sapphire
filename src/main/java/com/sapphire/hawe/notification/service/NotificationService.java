package com.sapphire.hawe.notification.service;

import com.sapphire.hawe.comment.domain.CommentCreatedEvent;
import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.storage.FileStorage;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.mission.domain.MissionsPublishedEvent;
import com.sapphire.hawe.notification.dao.NotificationMapper;
import com.sapphire.hawe.notification.dao.NotificationRow;
import com.sapphire.hawe.notification.domain.NotificationType;
import com.sapphire.hawe.notification.dto.NotificationResponse;
import com.sapphire.hawe.notification.dto.UnreadCountResponse;
import com.sapphire.hawe.poke.domain.PokeSentEvent;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationMapper notifications;
    private final FileStorage storage;

    public NotificationService(NotificationMapper notifications, FileStorage storage) {
        this.notifications = notifications;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        long total = notifications.count(userId);
        List<NotificationResponse> content = notifications.findPage(userId, (long) safePage * safeSize, safeSize)
                .stream().map(this::response).toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(notifications.countUnread(userId));
    }

    @Transactional
    public void read(Long userId, Long id) {
        if (notifications.markRead(id, userId) == 0 && !notifications.existsOwned(id, userId))
            throw new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Transactional
    public void readAll(Long userId) {
        notifications.markAllRead(userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createPoke(PokeSentEvent event) {
        if (!event.fromUserId().equals(event.toUserId()))
            insert(event.fromUserId(), event.toUserId(), NotificationType.poke,
                    event.groupId(), event.missionId(), null, SeoulTime.now().withNano(0));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createComment(CommentCreatedEvent event) {
        LocalDateTime now = SeoulTime.now().withNano(0);
        LinkedHashSet<Long> mentions = new LinkedHashSet<>(event.mentionedUserIds());
        mentions.remove(event.fromUserId());
        if (!event.fromUserId().equals(event.submissionUserId()) && !mentions.contains(event.submissionUserId()))
            insert(event.fromUserId(), event.submissionUserId(), NotificationType.comment,
                    event.groupId(), event.missionId(), event.submissionId(), now);
        for (Long mentioned : mentions)
            insert(event.fromUserId(), mentioned, NotificationType.mention,
                    event.groupId(), event.missionId(), event.submissionId(), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createTopics(MissionsPublishedEvent event) {
        LocalDateTime now = SeoulTime.now().withNano(0);
        for (Long missionId : event.missionIds())
            for (Long userId : event.recipientUserIds())
                insert(null, userId, NotificationType.topic, event.groupId(), missionId, null, now);
    }

    private void insert(Long fromUserId, Long toUserId, NotificationType type, Long groupId,
            Long missionId, Long submissionId, LocalDateTime now) {
        notifications.insert(fromUserId, toUserId, type.name(), groupId, missionId, submissionId, now);
    }

    private NotificationResponse response(NotificationRow row) {
        NotificationResponse.FromUser fromUser = row.getFromUserId() == null ? null
                : new NotificationResponse.FromUser(row.getFromUserId(), row.getNickname(), row.getCharacterKey(),
                        row.getProfileImageUrl() == null ? null : storage.publicUrl(row.getProfileImageUrl()));
        String nickname = row.getNickname();
        String body = switch (NotificationType.valueOf(row.getType())) {
            case topic -> "오늘의 주제가 도착했어요";
            case poke -> nickname + "님이 콕 찔렀어요";
            case comment -> nickname + "님이 내 작품에 댓글을 남겼어요";
            case mention -> nickname + "님이 댓글에서 나를 언급했어요";
        };
        return new NotificationResponse(row.getId(), row.getType(), row.getGroupId(), row.getGroupName(),
                fromUser, row.getMissionId(), row.getSubmissionId(), body, row.isRead(),
                SeoulTime.toOffset(row.getCreatedAt()));
    }
}
