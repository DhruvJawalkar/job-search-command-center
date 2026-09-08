package dev.dhruv.jobsearch.skill;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/skills/backlog")
public class PersonalSkillBacklogController {
    private final PersonalSkillBacklogService service;
    public PersonalSkillBacklogController(PersonalSkillBacklogService service) { this.service = service; }

    @GetMapping PersonalSkillBacklogService.BacklogOverview overview() { return service.overview(); }

    @PostMapping("/sync-preparation")
    PersonalSkillBacklogService.SyncResult syncPreparation() { return service.synchronizeAiPreparationBacklog(); }

    @PutMapping("/skills/{skillId}")
    PersonalSkillBacklogService.BacklogItemView update(@PathVariable UUID skillId,
            @Valid @RequestBody UpdateRequest request) {
        return service.update(skillId, new PersonalSkillBacklogService.UpdateBacklog(request.currentLevel(),
                request.targetLevel(), request.priority(), request.rationale(), request.status(), request.nextReviewOn()));
    }

    @PostMapping("/{backlogId}/preparation-links")
    ResponseEntity<PersonalSkillBacklogService.PreparationLinkView> linkPreparation(@PathVariable UUID backlogId,
            @Valid @RequestBody PreparationLinkRequest request) {
        var result = service.linkPreparation(backlogId, request.prepItemId(), request.note());
        return ResponseEntity.created(URI.create("/api/v1/skills/backlog/" + backlogId + "/preparation-links/" + result.id())).body(result);
    }

    @PostMapping("/{backlogId}/resources")
    ResponseEntity<PersonalSkillBacklogService.ResourceView> addResource(@PathVariable UUID backlogId,
            @Valid @RequestBody ResourceRequest request) {
        var result = service.addResource(backlogId, new PersonalSkillBacklogService.NewResource(request.title(),
                request.url(), request.resourceType(), request.status(), request.notes()));
        return ResponseEntity.created(URI.create("/api/v1/skills/backlog/" + backlogId + "/resources/" + result.id())).body(result);
    }

    @PostMapping("/{backlogId}/project-evidence")
    ResponseEntity<PersonalSkillBacklogService.ProjectEvidenceView> addProjectEvidence(@PathVariable UUID backlogId,
            @Valid @RequestBody ProjectEvidenceRequest request) {
        var result = service.addProjectEvidence(backlogId, new PersonalSkillBacklogService.NewProjectEvidence(
                request.title(), request.url(), request.evidenceType(), request.description(), request.outcome(), request.completedOn()));
        return ResponseEntity.created(URI.create("/api/v1/skills/backlog/" + backlogId + "/project-evidence/" + result.id())).body(result);
    }

    public record UpdateRequest(@NotNull SkillProficiencyLevel currentLevel,
            @NotNull SkillProficiencyLevel targetLevel, @Min(1) @Max(5) int priority, String rationale,
            @NotNull PersonalSkillStatus status, LocalDate nextReviewOn) {}
    public record PreparationLinkRequest(@NotNull UUID prepItemId, String note) {}
    public record ResourceRequest(@NotBlank String title, String url, LearningResourceType resourceType,
            LearningResourceStatus status, String notes) {}
    public record ProjectEvidenceRequest(@NotBlank String title, String url, ProjectEvidenceType evidenceType,
            String description, String outcome, LocalDate completedOn) {}
}
