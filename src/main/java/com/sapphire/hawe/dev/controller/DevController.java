package com.sapphire.hawe.dev.controller;

import com.sapphire.hawe.auth.dto.TokenResponse;
import com.sapphire.hawe.comment.dto.CommentResponse;
import com.sapphire.hawe.dev.dao.DevSubmissionRow;
import com.sapphire.hawe.dev.dto.DevDtos.*;
import com.sapphire.hawe.dev.service.DevActionService;
import com.sapphire.hawe.dev.service.DevClockService;
import com.sapphire.hawe.dev.service.DevMissionService;
import com.sapphire.hawe.dev.service.DevSeedService;
import com.sapphire.hawe.group.dto.GroupResponse;
import com.sapphire.hawe.group.dto.GroupSettingsUpdateRequest;
import com.sapphire.hawe.poke.dto.PokeResponse;
import com.sapphire.hawe.reaction.dto.ReactionsResponse;
import com.sapphire.hawe.submission.dto.SubmissionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 개발자 도구 — 운영 배포 시 제거/차단 필요. */
@RestController
@RequestMapping("/api/dev")
public class DevController {
    private final DevClockService clock;
    private final DevMissionService missions;
    private final DevActionService actions;
    private final DevSeedService seeds;

    public DevController(DevClockService clock, DevMissionService missions,
            DevActionService actions, DevSeedService seeds) {
        this.clock = clock; this.missions = missions; this.actions = actions; this.seeds = seeds;
    }

    @GetMapping("/status") public ClockStatus status() { return clock.status(); }
    @PutMapping("/clock") public ClockStatus setClock(@RequestBody ClockRequest request) { return clock.set(request); }
    @DeleteMapping("/clock") public ClockStatus resetClock() { return clock.reset(); }

    @PostMapping("/missions/publish") public PublishResponse publish(
            @RequestBody(required = false) PublishRequest request) { return missions.publish(request); }
    @PostMapping("/missions/{missionId}/deadline-now") public DeadlineResponse deadline(
            @PathVariable Long missionId) { return missions.deadlineNow(missionId); }
    @PostMapping("/groups/{groupId}/deadline-now") public UpdatedResponse deadlineGroup(
            @PathVariable Long groupId) { return missions.deadlineCurrent(groupId); }
    @GetMapping("/missions/{missionId}/submissions") public List<DevSubmissionRow> submissions(
            @PathVariable Long missionId) { return actions.listSubmissions(missionId); }
    @PostMapping("/missions/{missionId}/submit") public SubmissionResponse submit(
            @PathVariable Long missionId, @RequestBody UserRequest request) { return actions.submit(missionId, request); }
    @PostMapping("/missions/{missionId}/submit-missing") public List<SubmitResult> submitMissing(
            @PathVariable Long missionId) { return actions.submitMissing(missionId); }
    @PostMapping("/groups/{groupId}/poke") public PokeResponse poke(
            @PathVariable Long groupId, @RequestBody ActorRequest request) { return actions.poke(groupId, request); }
    @PostMapping("/submissions/{submissionId}/react") public ReactionsResponse react(
            @PathVariable Long submissionId, @RequestBody ReactionActorRequest request) {
        return actions.react(submissionId, request);
    }
    @PostMapping("/submissions/{submissionId}/comment") public CommentResponse comment(
            @PathVariable Long submissionId, @RequestBody CommentActorRequest request) {
        return actions.comment(submissionId, request);
    }
    @PostMapping("/groups/{groupId}/insights/regenerate") public RegenerateResponse regenerate(
            @PathVariable Long groupId, @RequestParam String month) { return missions.regenerate(groupId, month); }
    @PatchMapping("/groups/{groupId}/settings") public GroupResponse settings(
            @PathVariable Long groupId, @Valid @RequestBody GroupSettingsUpdateRequest request) {
        return missions.settings(groupId, request);
    }
    @GetMapping("/users") public List<UserInfo> users() { return seeds.ensureUsers(6); }
    @PostMapping("/login-as") public TokenResponse loginAs(@RequestBody UserRequest request) {
        return seeds.loginAs(request == null ? null : request.userId());
    }
    @PostMapping("/seed") public SeedResult seed(@RequestBody(required = false) SeedRequest request) {
        return seeds.seed(request);
    }
    @DeleteMapping("/groups/{groupId}") public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId) {
        seeds.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/reset") public ResetResponse reset() { return seeds.reset(); }
}
