package com.sapphire.hawe.poke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** {@code missionId}: 선택. 없으면 첫 슬롯(현재 미션 중 가장 작은 slotNo)의 미션, 있으면 그 그룹의 현재 미션이어야 한다. */
public record PokeRequest(
        @NotNull Long toUserId,
        @NotBlank @Pattern(regexp = "poke", message = "poke만 보낼 수 있습니다") String type,
        Long missionId) {
}
