package dev.dhruv.jobsearch.assistance;

import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/assistance")
public class AssistanceController {

    private final AssistanceService service;

    public AssistanceController(AssistanceService service) { this.service = service; }

    @GetMapping("/configuration")
    AssistanceService.ConfigurationView configuration() { return service.configuration(); }

    @GetMapping("/inbox/{candidateId}")
    AssistanceService.InboxAssistanceOverview inbox(@PathVariable UUID candidateId) {
        return service.inboxOverview(candidateId);
    }

    @PostMapping("/inbox/{candidateId}")
    AssistanceService.GenerateInboxResult generateInbox(@PathVariable UUID candidateId,
            @Valid @RequestBody ConfirmTransmission request) {
        return service.generateInbox(candidateId, request.confirmedTransmission());
    }

    @PostMapping("/runs/{runId}/apply")
    AssistanceService.InboxAssistanceView apply(@PathVariable UUID runId,
            @Valid @RequestBody ApplyFields request) {
        return service.applyFields(runId, request.fields());
    }

    @PostMapping("/runs/{runId}/skills")
    AssistanceService.PublishSkillsResult publishSkills(@PathVariable UUID runId) {
        return service.publishSkills(runId);
    }

    @PostMapping("/runs/{runId}/dismiss")
    AssistanceService.InboxAssistanceView dismiss(@PathVariable UUID runId) { return service.dismiss(runId); }

    @GetMapping("/weekly/{reviewId}")
    AssistanceService.WeeklyAssistanceOverview weekly(@PathVariable UUID reviewId) {
        return service.weeklyOverview(reviewId);
    }

    @PostMapping("/weekly/{reviewId}")
    AssistanceService.GenerateWeeklyResult generateWeekly(@PathVariable UUID reviewId,
            @Valid @RequestBody ConfirmTransmission request) {
        return service.generateWeekly(reviewId, request.confirmedTransmission());
    }

    public record ConfirmTransmission(boolean confirmedTransmission) {}
    public record ApplyFields(@NotNull Set<String> fields) {}
}
