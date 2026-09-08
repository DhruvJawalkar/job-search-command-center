package dev.dhruv.jobsearch.application;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping("/applications")
    List<ApplicationResponse> list() {
        return service.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/applications/{id}")
    ApplicationResponse get(@PathVariable UUID id) {
        return toResponse(service.get(id));
    }

    @PostMapping(value = "/opportunities/{opportunityId}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApplicationResponse> create(@PathVariable UUID opportunityId,
            @Valid @RequestPart("request") CreateApplicationRequest request,
            @RequestPart("resumeFile") MultipartFile resumeFile,
            @RequestPart(value = "jobDescription", required = false) String jobDescription) {
        JobApplication application = service.createWithArtifacts(opportunityId, new ApplicationService.CreateApplication(
                request.resumeVariantId(), request.stage(), request.appliedOn(), request.channel(),
                request.nextAction(), request.nextActionAt(), request.note()), resumeFile, jobDescription);
        return ResponseEntity.created(URI.create("/api/v1/applications/" + application.getId()))
                .body(toResponse(application));
    }

    @PostMapping("/applications/{id}/transitions")
    ApplicationResponse transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return toResponse(service.transition(id, new ApplicationService.TransitionApplication(
                request.toStage(), request.nextAction(), request.nextActionAt(), request.note())));
    }

    @PatchMapping("/applications/{id}/follow-up")
    ApplicationResponse updateFollowUp(@PathVariable UUID id, @Valid @RequestBody FollowUpRequest request) {
        JobApplication application = request.drop()
                ? service.dropFollowUp(id, request.note())
                : service.updateFollowUp(id, new ApplicationService.UpdateApplicationFollowUp(
                        request.stage(), request.nextAction(), request.nextActionAt(), request.note()));
        return toResponse(application);
    }

    private ApplicationResponse toResponse(JobApplication application) {
        List<EventResponse> events = service.events(application.getId()).stream()
                .map(event -> new EventResponse(event.getId(), event.getFromStage(), event.getToStage(),
                        event.getNote(), event.getOccurredAt()))
                .toList();
        List<ArtifactResponse> artifacts = service.artifacts(application.getId()).stream()
                .map(artifact -> new ArtifactResponse(artifact.getId(), artifact.getArtifactType(),
                        artifact.getOriginalFilename(), "application-resumes/" + artifact.getStoredRelativePath(),
                        artifact.getContentHash(), artifact.getMediaType(), artifact.getSizeBytes(),
                        artifact.getCreatedAt()))
                .toList();
        return new ApplicationResponse(
                application.getId(), application.getOpportunity().getId(),
                application.getOpportunity().getCompanyName(), application.getOpportunity().getRoleTitle(),
                application.getResumeVariant().getId(), application.getResumeVariant().getName(),
                application.getResumeVariant().getVersionLabel(), application.getStage(), application.getAppliedOn(),
                application.getChannel(), application.getNextAction(), application.getNextActionAt(),
                application.isFollowUpActive(), application.getCreatedAt(), application.getUpdatedAt(), artifacts, events);
    }

    public record CreateApplicationRequest(
            @NotNull UUID resumeVariantId,
            ApplicationStage stage,
            LocalDate appliedOn,
            String channel,
            String nextAction,
            Instant nextActionAt,
            String note) {
    }

    public record TransitionRequest(
            @NotNull ApplicationStage toStage,
            String nextAction,
            Instant nextActionAt,
            String note) {
    }

    public record FollowUpRequest(
            boolean drop,
            ApplicationStage stage,
            String nextAction,
            Instant nextActionAt,
            String note) {
    }

    public record ApplicationResponse(
            UUID id,
            UUID opportunityId,
            String companyName,
            String roleTitle,
            UUID resumeVariantId,
            String resumeName,
            String resumeVersion,
            ApplicationStage stage,
            LocalDate appliedOn,
            String channel,
            String nextAction,
            Instant nextActionAt,
            boolean followUpActive,
            Instant createdAt,
            Instant updatedAt,
            List<ArtifactResponse> artifacts,
            List<EventResponse> events) {
    }

    public record ArtifactResponse(
            UUID id,
            ApplicationArtifactType artifactType,
            String originalFilename,
            String storedPath,
            String contentHash,
            String mediaType,
            long sizeBytes,
            Instant createdAt) {
    }

    public record EventResponse(
            UUID id,
            ApplicationStage fromStage,
            ApplicationStage toStage,
            String note,
            Instant occurredAt) {
    }
}
