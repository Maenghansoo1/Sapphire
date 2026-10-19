package com.sapphire.hawe.poke.domain;

public record PokeSentEvent(Long groupId, Long missionId, Long fromUserId, Long toUserId) { }
