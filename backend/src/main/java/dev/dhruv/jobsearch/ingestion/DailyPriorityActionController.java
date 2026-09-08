package dev.dhruv.jobsearch.ingestion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.dhruv.jobsearch.opportunity.OpportunityStatus;
import dev.dhruv.jobsearch.shared.NotFoundException;

@RestController
@RequestMapping("/api/v1/daily-actions")
public class DailyPriorityActionController {

    private final DailyPriorityActionRepository repository;

    public DailyPriorityActionController(DailyPriorityActionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    DailyActionDay list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selectedDate = date == null ? repository.findLatestActionDate() : date;
        List<DailyActionResponse> actions = selectedDate == null ? List.of() : repository
                .findByActionDateOrderByPriorityRankAsc(selectedDate).stream()
                .filter(action -> action.getOpportunity() == null
                        || action.getOpportunity().getStatus() != OpportunityStatus.ARCHIVED)
                .map(DailyActionResponse::from).toList();
        return new DailyActionDay(selectedDate, actions);
    }

    @PatchMapping("/{id}")
    @Transactional
    DailyActionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDailyAction request) {
        DailyPriorityAction action = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Daily action " + id + " was not found."));
        action.changeStatus(request.status());
        return DailyActionResponse.from(action);
    }

    public record UpdateDailyAction(@NotNull DailyActionStatus status) {
    }

    public record DailyActionDay(LocalDate date, List<DailyActionResponse> actions) {
    }

    public record DailyActionResponse(UUID id, LocalDate actionDate, int priorityRank, String actionText,
            DailyActionStatus status, UUID opportunityId, String companyName, String roleTitle, Instant updatedAt) {
        static DailyActionResponse from(DailyPriorityAction action) {
            return new DailyActionResponse(action.getId(), action.getActionDate(), action.getPriorityRank(),
                    action.getActionText(), action.getStatus(),
                    action.getOpportunity() == null ? null : action.getOpportunity().getId(),
                    action.getOpportunity() == null ? null : action.getOpportunity().getCompanyName(),
                    action.getOpportunity() == null ? null : action.getOpportunity().getRoleTitle(),
                    action.getUpdatedAt());
        }
    }
}
