package dev.dhruv.jobsearch.contact;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/linkedin-connections")
public class LinkedInConnectionController {
    private final LinkedInConnectionImportService service;

    public LinkedInConnectionController(LinkedInConnectionImportService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    LinkedInConnectionImportService.Overview overview() {
        return service.overview();
    }

    @PostMapping("/imports")
    LinkedInConnectionImportService.ImportView importConnections() {
        return service.importConnections();
    }

    @GetMapping("/matches")
    List<LinkedInConnectionImportService.ConnectionView> matches(@RequestParam UUID opportunityId) {
        return service.matches(opportunityId);
    }

    @PostMapping("/{id}/candidates")
    ReferralDiscoveryController.CandidateResponse saveAsCandidate(@PathVariable UUID id,
            @Valid @RequestBody CandidateRequest request) {
        return ReferralDiscoveryController.CandidateResponse.from(
                service.saveAsCandidate(id, request.opportunityId()));
    }

    public record CandidateRequest(@NotNull UUID opportunityId) {}
}
