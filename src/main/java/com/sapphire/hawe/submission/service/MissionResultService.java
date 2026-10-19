package com.sapphire.hawe.submission.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.mission.dao.MissionMapper;
import com.sapphire.hawe.mission.dao.MissionRow;
import com.sapphire.hawe.mission.service.MissionErrorCode;
import com.sapphire.hawe.submission.dao.SubmissionMapper;
import com.sapphire.hawe.submission.dao.SubmissionRow;
import com.sapphire.hawe.submission.dto.MissionResultResponse;
import com.sapphire.hawe.submission.dto.MissionResultResponse.ChoiceCount;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 집계 틀(밸런스 게임)의 그룹 결과. 내가 제출해야 볼 수 있고, 지금까지 들어온 제출로 계산한다(늦은 제출 포함). */
@Service
public class MissionResultService {
    private final MissionMapper missions;
    private final GroupMemberMapper members;
    private final SubmissionMapper submissions;

    public MissionResultService(MissionMapper missions, GroupMemberMapper members, SubmissionMapper submissions) {
        this.missions = missions; this.members = members; this.submissions = submissions;
    }

    @Transactional(readOnly = true)
    public MissionResultResponse result(Long userId, Long missionId) {
        MissionRow mission = missions.findById(missionId, userId);
        if (mission == null) throw new BusinessException(MissionErrorCode.MISSION_NOT_FOUND);
        if (!members.existsByGroupIdAndUserId(mission.getGroupId(), userId))
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        if (!submissions.existsByMissionIdAndUserId(missionId, userId))
            throw new BusinessException(SubmissionErrorCode.FEED_LOCKED);
        if (!"TEXT_BALANCE".equals(mission.getTemplate()))
            throw new BusinessException(MissionErrorCode.RESULT_NOT_SUPPORTED);
        return balance(submissions.findResultRowsByMissionId(missionId), userId);
    }

    private static MissionResultResponse balance(List<SubmissionRow> rows, Long userId) {
        int a = 0, b = 0;
        String mine = null;
        for (SubmissionRow row : rows) {
            if ("A".equals(row.getChoiceValue())) a++;
            else if ("B".equals(row.getChoiceValue())) b++;
            if (row.getUserId().equals(userId)) mine = row.getChoiceValue();
        }
        return MissionResultResponse.balance(rows.size(), List.of(new ChoiceCount("A", a), new ChoiceCount("B", b)),
                mine);
    }
}
