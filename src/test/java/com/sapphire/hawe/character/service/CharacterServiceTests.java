package com.sapphire.hawe.character.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.auth.domain.User;
import com.sapphire.hawe.character.dao.CharacterMapper;
import com.sapphire.hawe.character.domain.ProfileCharacter;
import com.sapphire.hawe.character.dto.CharacterUpdateRequest;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.TokenErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTests {

    @Mock CharacterMapper characterMapper;
    @Mock UserMapper userMapper;

    private CharacterService service;

    @BeforeEach
    void setUp() {
        service = new CharacterService(characterMapper, userMapper);
    }

    @Test
    void missingUserDoesNotReturnSuccess() {
        when(characterMapper.findById(3L)).thenReturn(character());
        when(characterMapper.updateUserCharacterId(42L, 3L)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeMyCharacter(42L, new CharacterUpdateRequest(3L)));

        assertEquals(TokenErrorCode.INVALID_TOKEN, error.errorCode());
        verify(userMapper).findById(42L);
    }

    @Test
    void selectingCurrentCharacterStillSucceedsWhenUpdateCountIsZero() {
        when(characterMapper.findById(3L)).thenReturn(character());
        when(characterMapper.updateUserCharacterId(42L, 3L)).thenReturn(0);
        when(userMapper.findById(42L)).thenReturn(new User());

        var response = service.changeMyCharacter(42L, new CharacterUpdateRequest(3L));

        assertEquals(3L, response.characterId());
        assertEquals("bear", response.svgKey());
    }

    @Test
    void changedCharacterReturnsSelectedCharacter() {
        when(characterMapper.findById(3L)).thenReturn(character());
        when(characterMapper.updateUserCharacterId(42L, 3L)).thenReturn(1);

        var response = service.changeMyCharacter(42L, new CharacterUpdateRequest(3L));

        assertEquals(3L, response.characterId());
        verifyNoInteractions(userMapper);
    }

    private ProfileCharacter character() {
        ProfileCharacter character = new ProfileCharacter();
        character.setId(3L);
        character.setName("곰돌이");
        character.setSvgKey("bear");
        return character;
    }
}
