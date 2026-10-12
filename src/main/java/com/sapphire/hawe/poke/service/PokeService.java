package com.sapphire.hawe.poke.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.poke.dao.PokeMapper;
import com.sapphire.hawe.poke.domain.Poke;
import com.sapphire.hawe.poke.dto.PokeRequest;
import com.sapphire.hawe.poke.dto.PokeResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class PokeService {

    private final PokeMapper pokeMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;

    public PokeService(PokeMapper pokeMapper, GroupMapper groupMapper, GroupMemberMapper groupMemberMapper) {
        this.pokeMapper = pokeMapper;
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
    }

    @Transactional
    public PokeResponse sendPoke(Long userId, Long groupId, PokeRequest request) {
        if (groupMapper.findById(groupId) == null) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupMemberMapper.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(GroupErrorCode.NOT_GROUP_MEMBER);
        }

        Long targetId = request.toUserId();
        if (targetId.equals(userId)) {
            throw new BusinessException(PokeErrorCode.CANNOT_POKE_SELF);
        }
        if (!groupMemberMapper.existsByGroupIdAndUserId(groupId, targetId)) {
            throw new BusinessException(PokeErrorCode.TARGET_NOT_GROUP_MEMBER);
        }
        Long missionId = pokeMapper.findCurrentMissionId(groupId);
        if (missionId == null) {
            throw new BusinessException(PokeErrorCode.NO_OPEN_MISSION);
        }
        if (pokeMapper.existsSubmission(missionId, targetId)) {
            throw new BusinessException(PokeErrorCode.TARGET_ALREADY_SUBMITTED);
        }
        if (pokeMapper.existsPoke(userId, targetId, missionId, request.type())) {
            throw new BusinessException(PokeErrorCode.ALREADY_POKED);
        }

        Poke poke = new Poke();
        poke.setFromUserId(userId);
        poke.setToUserId(targetId);
        poke.setGroupId(groupId);
        poke.setMissionId(missionId);
        poke.setType(request.type());
        poke.setSentAt(SeoulTime.now().withNano(0));
        try {
            pokeMapper.insert(poke);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(PokeErrorCode.ALREADY_POKED);
        }

        return new PokeResponse(poke.getId(), poke.getToUserId(), poke.getType(), poke.getMissionId(),
                SeoulTime.toOffset(poke.getSentAt()));
    }
}
