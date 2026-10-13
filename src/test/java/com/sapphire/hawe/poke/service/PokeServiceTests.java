package com.sapphire.hawe.poke.service;

import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.ErrorCode;
import com.sapphire.hawe.group.dao.GroupMapper;
import com.sapphire.hawe.group.dao.GroupMemberMapper;
import com.sapphire.hawe.group.domain.Group;
import com.sapphire.hawe.group.service.GroupErrorCode;
import com.sapphire.hawe.poke.dao.PokeMapper;
import com.sapphire.hawe.poke.domain.Poke;
import com.sapphire.hawe.poke.dto.PokeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PokeServiceTests {

    @Mock PokeMapper pokeMapper;
    @Mock GroupMapper groupMapper;
    @Mock GroupMemberMapper groupMemberMapper;

    private PokeService service;

    @BeforeEach
    void setUp() {
        service = new PokeService(pokeMapper, groupMapper, groupMemberMapper);
    }

    @Test
    void sendPokeSavesPokeAndReturnsIt() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 8L)).thenReturn(true);
        when(pokeMapper.findCurrentMissionId(11L)).thenReturn(10L);
        doAnswer(invocation -> {
            Poke poke = invocation.getArgument(0);
            poke.setId(1L);
            return null;
        }).when(pokeMapper).insert(any(Poke.class));

        var response = service.sendPoke(7L, 11L, new PokeRequest(8L, "fling", null));

        assertEquals(1L, response.id());
        assertEquals(8L, response.toUserId());
        assertEquals("fling", response.type());
        assertEquals(10L, response.missionId());
        assertNotNull(response.sentAt());
        ArgumentCaptor<Poke> saved = ArgumentCaptor.forClass(Poke.class);
        verify(pokeMapper).insert(saved.capture());
        assertEquals(7L, saved.getValue().getFromUserId());
        assertEquals(11L, saved.getValue().getGroupId());
        assertEquals(10L, saved.getValue().getMissionId());
    }

    @Test
    void missingGroupReturnsNotFound() {
        assertFailsWith(GroupErrorCode.GROUP_NOT_FOUND, new PokeRequest(8L, "poke", null));
    }

    @Test
    void senderOutsideGroupCannotPoke() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));

        assertFailsWith(GroupErrorCode.NOT_GROUP_MEMBER, new PokeRequest(8L, "poke", null));
    }

    @Test
    void cannotPokeSelf() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);

        assertFailsWith(PokeErrorCode.CANNOT_POKE_SELF, new PokeRequest(7L, "poke", null));
    }

    @Test
    void targetOutsideGroupReturnsNotFound() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);

        assertFailsWith(PokeErrorCode.TARGET_NOT_GROUP_MEMBER, new PokeRequest(8L, "poke", null));
    }

    @Test
    void targetWhoSubmittedCurrentMissionCannotBePoked() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 8L)).thenReturn(true);
        when(pokeMapper.findCurrentMissionId(11L)).thenReturn(10L);
        when(pokeMapper.existsSubmission(10L, 8L)).thenReturn(true);

        assertFailsWith(PokeErrorCode.TARGET_ALREADY_SUBMITTED, new PokeRequest(8L, "poke", null));
    }

    @Test
    void noCurrentMissionReturnsConflict() {
        allowMembers();
        when(pokeMapper.findCurrentMissionId(11L)).thenReturn(null);
        assertFailsWith(PokeErrorCode.NO_OPEN_MISSION, new PokeRequest(8L, "poke", null));
    }

    @Test
    void chosenMissionMustBeCurrentInTheGroup() {
        allowMembers();
        when(pokeMapper.isCurrentMissionOfGroup(12L, 11L)).thenReturn(true);

        var response = service.sendPoke(7L, 11L, new PokeRequest(8L, "poke", 12L));

        assertEquals(12L, response.missionId());
        verify(pokeMapper).existsSubmission(12L, 8L);
        verify(pokeMapper).existsPoke(7L, 8L, 12L, "poke");
        verify(pokeMapper, never()).findCurrentMissionId(11L);
        ArgumentCaptor<Poke> saved = ArgumentCaptor.forClass(Poke.class);
        verify(pokeMapper).insert(saved.capture());
        assertEquals(12L, saved.getValue().getMissionId());
    }

    @Test
    void closedOrOtherGroupsMissionReturnsConflict() {
        allowMembers();
        when(pokeMapper.isCurrentMissionOfGroup(9L, 11L)).thenReturn(false);
        assertFailsWith(PokeErrorCode.NO_OPEN_MISSION, new PokeRequest(8L, "poke", 9L));
        verify(pokeMapper, never()).findCurrentMissionId(11L);
    }

    @Test
    void sameTypeCannotBeSentTwice() {
        allowMembers();
        when(pokeMapper.findCurrentMissionId(11L)).thenReturn(10L);
        when(pokeMapper.existsPoke(7L, 8L, 10L, "poke")).thenReturn(true);
        assertFailsWith(PokeErrorCode.ALREADY_POKED, new PokeRequest(8L, "poke", null));
    }

    @Test
    void concurrentDuplicateBecomesAlreadyPoked() {
        allowMembers();
        when(pokeMapper.findCurrentMissionId(11L)).thenReturn(10L);
        doThrow(new DuplicateKeyException("duplicate")).when(pokeMapper).insert(any(Poke.class));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendPoke(7L, 11L, new PokeRequest(8L, "poke", null)));
        assertEquals(PokeErrorCode.ALREADY_POKED, error.errorCode());
    }

    private void allowMembers() {
        when(groupMapper.findById(11L)).thenReturn(group(11L));
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 7L)).thenReturn(true);
        when(groupMemberMapper.existsByGroupIdAndUserId(11L, 8L)).thenReturn(true);
    }

    private void assertFailsWith(ErrorCode expected, PokeRequest request) {
        BusinessException error = assertThrows(BusinessException.class, () -> service.sendPoke(7L, 11L, request));
        assertEquals(expected, error.errorCode());
        verify(pokeMapper, never()).insert(any(Poke.class));
    }

    private static Group group(Long id) {
        Group group = new Group();
        group.setId(id);
        group.setOwnerId(7L);
        return group;
    }
}
