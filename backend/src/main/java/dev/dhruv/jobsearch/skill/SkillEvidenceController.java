package dev.dhruv.jobsearch.skill;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillEvidenceController {

    private final SkillEvidenceService service;
    private final DeterministicSkillExtractionService extractionService;
    private final LiveJobDescriptionExtractionService liveExtractionService;

    public SkillEvidenceController(SkillEvidenceService service,
            DeterministicSkillExtractionService extractionService,
            LiveJobDescriptionExtractionService liveExtractionService) {
        this.service = service;
        this.extractionService = extractionService;
        this.liveExtractionService = liveExtractionService;
    }

    @GetMapping("/overview")
    SkillEvidenceService.Overview overview() {
        return service.overview();
    }

    @PostMapping
    ResponseEntity<SkillEvidenceService.SkillView> createSkill(@Valid @RequestBody SkillRequest request) {
        var skill = service.createSkill(new SkillEvidenceService.NewSkill(request.name(), request.category(),
                request.description(), request.aliases()));
        return ResponseEntity.created(URI.create("/api/v1/skills/" + skill.id())).body(skill);
    }

    @PostMapping("/seed")
    SkillEvidenceService.SeedResult seedReviewedBacklog() {
        return service.seedReviewedBacklog();
    }

    @PostMapping("/extractions")
    DeterministicSkillExtractionService.ExtractionResult extract(
            @RequestBody(required = false) ExtractionRequest request) {
        ExtractionRequest safe = request == null ? new ExtractionRequest(List.of(), false) : request;
        return extractionService.extract(new DeterministicSkillExtractionService.ExtractionCommand(
                safe.opportunityIds(), safe.includeArchived()));
    }

    @PostMapping("/live-extractions")
    LiveJobDescriptionExtractionService.LiveExtractionResult fetchAndExtractLiveDescriptions(
            @RequestBody(required = false) LiveExtractionRequest request) {
        LiveExtractionRequest safe = request == null ? new LiveExtractionRequest(List.of()) : request;
        return liveExtractionService.fetchAndExtract(
                new LiveJobDescriptionExtractionService.LiveExtractionCommand(safe.opportunityIds()));
    }

    @PostMapping("/{id}/aliases")
    SkillEvidenceService.SkillView addAlias(@PathVariable UUID id, @Valid @RequestBody AliasRequest request) {
        return service.addAlias(id, request.alias());
    }

    @PostMapping("/snapshots")
    ResponseEntity<SkillEvidenceService.SnapshotView> captureSnapshot(@Valid @RequestBody SnapshotRequest request) {
        var snapshot = service.captureSnapshot(new SkillEvidenceService.NewSnapshot(request.opportunityId(),
                request.sourceType(), request.sourceLabel(), request.content()));
        return ResponseEntity.created(URI.create("/api/v1/skills/snapshots/" + snapshot.id())).body(snapshot);
    }

    @GetMapping("/observations")
    List<SkillEvidenceService.ObservationView> observations(
            @RequestParam(required = false) UUID opportunityId,
            @RequestParam(required = false) SkillReviewStatus reviewStatus) {
        return service.listObservations(opportunityId, reviewStatus);
    }

    @PostMapping("/observations")
    ResponseEntity<SkillEvidenceService.ObservationView> createObservation(
            @Valid @RequestBody ObservationRequest request) {
        var observation = service.createObservation(new SkillEvidenceService.NewObservation(request.snapshotId(),
                request.skillId(), request.strength(), request.evidenceSnippet(), request.extractionMethod()));
        return ResponseEntity.created(URI.create("/api/v1/skills/observations/" + observation.id())).body(observation);
    }

    @PatchMapping("/observations/{id}/review")
    SkillEvidenceService.ObservationView reviewObservation(@PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request) {
        return service.reviewObservation(id, request.status(), request.note());
    }

    @PatchMapping("/observations/review")
    List<SkillEvidenceService.ObservationView> reviewObservations(@Valid @RequestBody BulkReviewRequest request) {
        return service.reviewObservations(request.observationIds(), request.status(), request.note());
    }

    @PatchMapping("/observations/{id}/correction")
    SkillEvidenceService.ObservationView correctObservation(@PathVariable UUID id,
            @Valid @RequestBody CorrectionRequest request) {
        return service.correctObservation(id, new SkillEvidenceService.Correction(request.skillId(),
                request.strength(), request.evidenceSnippet(), request.note()));
    }

    @PostMapping("/{id}/merge")
    SkillEvidenceService.SkillView mergeSkill(@PathVariable UUID id,
            @Valid @RequestBody MergeRequest request) {
        return service.mergeSkill(id, request.targetSkillId());
    }

    public record SkillRequest(@NotBlank String name, @NotNull SkillCategory category, String description,
            List<String> aliases) {}
    public record AliasRequest(@NotBlank String alias) {}
    public record SnapshotRequest(@NotNull UUID opportunityId, @NotNull SnapshotSourceType sourceType,
            String sourceLabel, @NotBlank String content) {}
    public record ObservationRequest(@NotNull UUID snapshotId, @NotNull UUID skillId,
            @NotNull SkillStrength strength, @NotBlank String evidenceSnippet,
            @NotNull SkillExtractionMethod extractionMethod) {}
    public record ReviewRequest(@NotNull SkillReviewStatus status, String note) {}
    public record BulkReviewRequest(@NotNull List<UUID> observationIds,
            @NotNull SkillReviewStatus status, String note) {}
    public record CorrectionRequest(@NotNull UUID skillId, @NotNull SkillStrength strength,
            @NotBlank String evidenceSnippet, String note) {}
    public record MergeRequest(@NotNull UUID targetSkillId) {}
    public record ExtractionRequest(List<UUID> opportunityIds, boolean includeArchived) {}
    public record LiveExtractionRequest(List<UUID> opportunityIds) {}
}
