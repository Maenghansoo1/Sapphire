package com.sapphire.hawe.character.service;

import com.sapphire.hawe.auth.dao.UserMapper;
import com.sapphire.hawe.character.dao.CharacterMapper;
import com.sapphire.hawe.character.domain.ProfileCharacter;
import com.sapphire.hawe.character.dto.CharacterResponse;
import com.sapphire.hawe.character.dto.CharacterUpdateRequest;
import com.sapphire.hawe.character.dto.MyCharacterResponse;
import com.sapphire.hawe.global.dto.PageResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.security.TokenErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CharacterService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final CharacterMapper characterMapper;
    private final UserMapper userMapper;

    public CharacterService(CharacterMapper characterMapper, UserMapper userMapper) {
        this.characterMapper = characterMapper;
        this.userMapper = userMapper;
    }

    public PageResponse<CharacterResponse> listCharacters(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        List<ProfileCharacter> characters = characterMapper.findAll((long) safePage * safeSize, safeSize);
        long total = characterMapper.count();

        List<CharacterResponse> content = characters.stream()
                .map(character -> new CharacterResponse(character.getId(), character.getName(), character.getSvgKey()))
                .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Transactional
    public MyCharacterResponse changeMyCharacter(Long userId, CharacterUpdateRequest request) {
        ProfileCharacter character = characterMapper.findById(request.characterId());
        if (character == null) {
            throw new BusinessException(CharacterErrorCode.CHARACTER_NOT_FOUND);
        }

        int updated = characterMapper.updateUserCharacterId(userId, character.getId());
        // Some JDBC configurations report zero rows when the selected character was already set.
        if (updated == 0 && userMapper.findById(userId) == null) {
            throw new BusinessException(TokenErrorCode.INVALID_TOKEN);
        }
        return new MyCharacterResponse(character.getId(), character.getName(), character.getSvgKey());
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
