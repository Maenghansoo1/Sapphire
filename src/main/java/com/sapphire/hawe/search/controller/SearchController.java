package com.sapphire.hawe.search.controller;

import com.sapphire.hawe.search.dto.MissionSearchItem;
import com.sapphire.hawe.search.service.MissionSearchService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final MissionSearchService service;

    public SearchController(MissionSearchService service) { this.service = service; }

    /** 지난 미션 검색. q 는 2~50자, groupId 는 선택(내 그룹이어야 함), limit 은 1~50(기본 20). */
    @GetMapping("/missions")
    public List<MissionSearchItem> missions(@AuthenticationPrincipal Long userId, @RequestParam String q,
            @RequestParam(required = false) Long groupId, @RequestParam(required = false) Integer limit) {
        return service.search(userId, q, groupId, limit);
    }
}
