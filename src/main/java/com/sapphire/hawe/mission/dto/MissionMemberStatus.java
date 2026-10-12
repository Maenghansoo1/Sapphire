package com.sapphire.hawe.mission.dto;

import java.util.List;

public record MissionMemberStatus(Long userId, boolean submitted, List<String> pokedByMe) {
}
