package dev.dhruv.jobsearch.opportunity;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {

    private final OpportunityService service;

    public OpportunityController(OpportunityService service) {
        this.service = service;
    }

    @GetMapping
    List<OpportunityResponse> list() {
        return service.list().stream().map(OpportunityResponse::from).toList();
    }

    @GetMapping("/{id}")
    OpportunityResponse get(@PathVariable UUID id) {
        return OpportunityResponse.from(service.get(id));
    }

    @PostMapping
    ResponseEntity<OpportunityResponse> create(@Valid @RequestBody CreateOpportunityRequest request) {
        JobOpportunity created = service.create(new OpportunityService.CreateOpportunity(
                request.companyName(), request.roleTitle(), request.location(), request.workMode(),
                request.sourceName(), request.sourceUrl(), request.description(), request.discoveredAt()));
        if (request.sourceExternalId() != null && !request.sourceExternalId().isBlank()) {
            created = service.recordSourceExternalId(created.getId(), request.sourceExternalId());
        }
        return ResponseEntity.created(URI.create("/api/v1/opportunities/" + created.getId()))
                .body(OpportunityResponse.from(created));
    }

    @PostMapping("/{id}/decision")
    OpportunityResponse decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return OpportunityResponse.from(service.decide(id, request.status(), request.fitScore(), request.fitSummary()));
    }

    @PostMapping("/{id}/archive")
    OpportunityResponse archive(@PathVariable UUID id, @Valid @RequestBody ArchiveRequest request) {
        return OpportunityResponse.from(service.archive(id, request.reason()));
    }

    @PostMapping("/{id}/restore")
    OpportunityResponse restore(@PathVariable UUID id) {
        return OpportunityResponse.from(service.restore(id));
    }

    public record CreateOpportunityRequest(
            @NotBlank String companyName,
            @NotBlank String roleTitle,
            String location,
            WorkMode workMode,
            String sourceName,
            String sourceExternalId,
            String sourceUrl,
            String description,
            Instant discoveredAt) {
    }

    public record DecisionRequest(
            @NotNull OpportunityStatus status,
            @Min(0) @Max(100) Integer fitScore,
            String fitSummary) {
    }

    public record ArchiveRequest(@NotBlank String reason) {
    }

    public record OpportunityResponse(
            UUID id,
            String companyName,
            String roleTitle,
            String location,
            WorkMode workMode,
            String sourceName,
            String sourceExternalId,
            String sourceUrl,
            String description,
            OpportunityStatus status,
            Integer fitScore,
            String fitSummary,
            String archiveReason,
            Instant archivedAt,
            OpportunityStatus archivedFromStatus,
            Instant discoveredAt,
            Instant createdAt,
            Instant updatedAt) {

        static OpportunityResponse from(JobOpportunity opportunity) {
            return new OpportunityResponse(
                    opportunity.getId(), opportunity.getCompanyName(), opportunity.getRoleTitle(),
                    opportunity.getLocation(), opportunity.getWorkMode(), opportunity.getSourceName(),
                    opportunity.getSourceExternalId(),
                    opportunity.getSourceUrl(), opportunity.getDescription(), opportunity.getStatus(),
                    opportunity.getFitScore(), opportunity.getFitSummary(), opportunity.getArchiveReason(),
                    opportunity.getArchivedAt(), opportunity.getArchivedFromStatus(), opportunity.getDiscoveredAt(),
                    opportunity.getCreatedAt(), opportunity.getUpdatedAt());
        }
    }
}
