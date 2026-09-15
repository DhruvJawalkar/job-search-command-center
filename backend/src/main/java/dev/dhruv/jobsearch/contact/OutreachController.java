package dev.dhruv.jobsearch.contact;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
@RequestMapping("/api/v1")
public class OutreachController {

    private final OutreachService service;

    public OutreachController(OutreachService service) {
        this.service = service;
    }

    @GetMapping("/contacts")
    List<ContactResponse> contacts() {
        return service.contacts().stream().map(ContactResponse::from).toList();
    }

    @PostMapping("/contacts")
    ResponseEntity<ContactResponse> createContact(@Valid @RequestBody NewContactRequest request) {
        NetworkContact contact = service.createContact(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/contacts/" + contact.getId()))
                .body(ContactResponse.from(contact));
    }

    @GetMapping("/outreach")
    List<OutreachResponse> outreach(@RequestParam(required = false) UUID opportunityId,
            @RequestParam(defaultValue = "false") boolean dueOnly) {
        return service.list(opportunityId, dueOnly).stream().map(OutreachResponse::from).toList();
    }

    @PostMapping("/outreach")
    ResponseEntity<OutreachResponse> createOutreach(@Valid @RequestBody CreateOutreachRequest request) {
        OutreachActivity activity = service.create(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/outreach/" + activity.getId()))
                .body(OutreachResponse.from(activity));
    }

    @PatchMapping("/outreach/{id}")
    OutreachResponse updateOutreach(@PathVariable UUID id, @Valid @RequestBody UpdateOutreachRequest request) {
        return OutreachResponse.from(service.transition(id,
                new OutreachService.UpdateOutreach(request.status(), request.followUpAt(),
                        request.outcome(), request.notes())));
    }

    @PostMapping("/outreach/{id}/follow-ups")
    OutreachResponse recordFollowUp(@PathVariable UUID id, @Valid @RequestBody RecordFollowUpRequest request) {
        return OutreachResponse.from(service.recordFollowUp(id,
                new OutreachService.RecordFollowUp(request.nextFollowUpAt(), request.notes())));
    }

    public record NewContactRequest(@NotBlank String fullName, String companyName, String roleTitle,
            String profileUrl, @Email String email, RelationshipStrength relationshipStrength, String notes) {
        OutreachService.NewContact toCommand() {
            return new OutreachService.NewContact(fullName, companyName, roleTitle, profileUrl, email,
                    relationshipStrength, notes);
        }
    }

    public record CreateOutreachRequest(@NotNull UUID opportunityId, UUID contactId,
            @Valid NewContactRequest newContact, OutreachType outreachType, OutreachStatus status,
            String channel, String messageSummary, Instant followUpAt, String notes) {
        OutreachService.CreateOutreach toCommand() {
            return new OutreachService.CreateOutreach(opportunityId, contactId,
                    newContact == null ? null : newContact.toCommand(), outreachType, status, channel,
                    messageSummary, followUpAt, notes);
        }
    }

    public record UpdateOutreachRequest(@NotNull OutreachStatus status, Instant followUpAt,
            String outcome, String notes) {
    }

    public record RecordFollowUpRequest(@NotNull Instant nextFollowUpAt, String notes) {
    }

    public record ContactResponse(UUID id, String fullName, String companyName, String roleTitle,
            String profileUrl, String email, RelationshipStrength relationshipStrength, String notes,
            Instant createdAt, Instant updatedAt) {
        static ContactResponse from(NetworkContact contact) {
            return new ContactResponse(contact.getId(), contact.getFullName(), contact.getCompanyName(),
                    contact.getRoleTitle(), contact.getProfileUrl(), contact.getEmail(),
                    contact.getRelationshipStrength(), contact.getNotes(), contact.getCreatedAt(),
                    contact.getUpdatedAt());
        }
    }

    public record OutreachResponse(UUID id, UUID opportunityId, String companyName, String roleTitle,
            UUID applicationId, UUID contactId, String contactName, String contactCompany,
            RelationshipStrength relationshipStrength, OutreachType outreachType, OutreachStatus status,
            String channel, String messageSummary, Instant requestedAt, Instant followUpAt,
            int followUpCount, Instant respondedAt, String outcome, String notes, boolean overdue, Instant updatedAt) {
        static OutreachResponse from(OutreachActivity activity) {
            boolean overdue = activity.getFollowUpAt() != null && !activity.getFollowUpAt().isAfter(Instant.now())
                    && activity.getStatus() != OutreachStatus.CLOSED
                    && activity.getStatus() != OutreachStatus.DECLINED;
            return new OutreachResponse(activity.getId(), activity.getOpportunity().getId(),
                    activity.getOpportunity().getCompanyName(), activity.getOpportunity().getRoleTitle(),
                    activity.getApplication() == null ? null : activity.getApplication().getId(),
                    activity.getContact().getId(), activity.getContact().getFullName(),
                    activity.getContact().getCompanyName(), activity.getContact().getRelationshipStrength(),
                    activity.getOutreachType(), activity.getStatus(), activity.getChannel(),
                    activity.getMessageSummary(), activity.getRequestedAt(), activity.getFollowUpAt(),
                    activity.getFollowUpCount(), activity.getRespondedAt(), activity.getOutcome(), activity.getNotes(), overdue,
                    activity.getUpdatedAt());
        }
    }
}
