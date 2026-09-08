package dev.dhruv.jobsearch.ingestion;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.opportunity.OpportunityMergeField;

@RestController
@RequestMapping("/api/v1/inbox")
public class GenericInboxController {

    private final GenericInboxService service;
    private final DuplicateReviewService duplicateReview;

    public GenericInboxController(GenericInboxService service, DuplicateReviewService duplicateReview) {
        this.service = service;
        this.duplicateReview = duplicateReview;
    }

    @GetMapping("/items")
    List<GenericInboxService.InboxItemView> items() {
        return service.list();
    }

    @PostMapping("/items")
    ResponseEntity<GenericInboxService.CreateResult> receive(@Valid @RequestBody CreateInboxItemRequest request) {
        var result = service.receive(new GenericInboxService.CreateCommand(request.sourceType(), request.sourceLabel(),
                request.sourceFilename(), request.mediaType(), request.content()));
        if (result.item().status() != GenericInboxItemStatus.FAILED) duplicateReview.scanItem(result.item().id());
        var reviewed = new GenericInboxService.CreateResult(result.replayed(), service.get(result.item().id()));
        if (result.replayed()) return ResponseEntity.ok(reviewed);
        return ResponseEntity.created(URI.create("/api/v1/inbox/items/" + result.item().id())).body(reviewed);
    }

    @PatchMapping("/candidates/{id}")
    GenericInboxService.InboxCandidateView revise(@PathVariable UUID id,
            @Valid @RequestBody ReviseCandidateRequest request) {
        var revised = service.revise(id, new GenericInboxService.ReviseCandidate(request.companyName(), request.roleTitle(),
                request.location(), request.workMode(), request.sourceName(), request.sourceExternalId(),
                request.sourceUrl(), request.description()));
        duplicateReview.scanItem(revised.inboxItemId());
        return service.get(revised.inboxItemId()).candidates().stream().filter(candidate -> candidate.id().equals(id))
                .findFirst().orElseThrow();
    }

    @PostMapping("/candidates/{id}/import")
    GenericInboxService.InboxCandidateView importCandidate(@PathVariable UUID id) {
        return service.importCandidate(id);
    }

    @PostMapping("/candidates/{id}/reject")
    GenericInboxService.InboxCandidateView rejectCandidate(@PathVariable UUID id) {
        return service.rejectCandidate(id);
    }

    @PostMapping("/duplicates/scan")
    DuplicateReviewService.ScanReport scanDuplicates() {
        return duplicateReview.scanAll();
    }

    @PostMapping("/candidates/{id}/duplicate-resolution")
    GenericInboxService.InboxCandidateView resolveDuplicate(@PathVariable UUID id,
            @Valid @RequestBody ResolveDuplicateRequest request) {
        var result = duplicateReview.resolve(id, new DuplicateReviewService.ResolveCommand(
                request.resolution(), request.matchId(), request.fields()));
        return service.get(result.itemId()).candidates().stream().filter(candidate -> candidate.id().equals(id))
                .findFirst().orElseThrow();
    }

    public record CreateInboxItemRequest(
            @NotNull GenericInboxSourceType sourceType,
            @Size(max = 160) String sourceLabel,
            @Size(max = 500) String sourceFilename,
            @Size(max = 120) String mediaType,
            @NotBlank @Size(max = 2_000_000) String content) {}

    public record ReviseCandidateRequest(
            @Size(max = 240) String companyName,
            @Size(max = 240) String roleTitle,
            @Size(max = 240) String location,
            WorkMode workMode,
            @Size(max = 120) String sourceName,
            @Size(max = 240) String sourceExternalId,
            @Size(max = 1500) String sourceUrl,
            @Size(max = 1_000_000) String description) {}

    public record ResolveDuplicateRequest(
            @NotNull DuplicateResolution resolution,
            UUID matchId,
            Set<OpportunityMergeField> fields) {}
}
