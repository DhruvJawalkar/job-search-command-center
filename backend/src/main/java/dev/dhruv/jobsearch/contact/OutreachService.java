package dev.dhruv.jobsearch.contact;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.application.JobApplication;
import dev.dhruv.jobsearch.application.JobApplicationRepository;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class OutreachService {

    private final NetworkContactRepository contactRepository;
    private final OutreachActivityRepository outreachRepository;
    private final JobOpportunityRepository opportunityRepository;
    private final JobApplicationRepository applicationRepository;

    public OutreachService(NetworkContactRepository contactRepository, OutreachActivityRepository outreachRepository,
            JobOpportunityRepository opportunityRepository, JobApplicationRepository applicationRepository) {
        this.contactRepository = contactRepository;
        this.outreachRepository = outreachRepository;
        this.opportunityRepository = opportunityRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public List<NetworkContact> contacts() {
        return contactRepository.findAllByOrderByFullNameAsc();
    }

    @Transactional
    public NetworkContact createContact(NewContact command) {
        return contactRepository.save(new NetworkContact(command.fullName(), command.companyName(), command.roleTitle(),
                command.profileUrl(), command.email(), command.relationshipStrength(), command.notes()));
    }

    @Transactional(readOnly = true)
    public List<OutreachActivity> list(UUID opportunityId, boolean dueOnly) {
        List<OutreachActivity> source = opportunityId == null
                ? outreachRepository.findAllByOrderByFollowUpAtAscCreatedAtDesc()
                : outreachRepository.findByOpportunityIdOrderByCreatedAtDesc(opportunityId);
        if (!dueOnly) return source;
        Instant now = Instant.now();
        return source.stream().filter(activity -> activity.getFollowUpAt() != null)
                .filter(activity -> !activity.getFollowUpAt().isAfter(now))
                .filter(activity -> activity.getStatus() != OutreachStatus.CLOSED
                        && activity.getStatus() != OutreachStatus.DECLINED)
                .toList();
    }

    @Transactional
    public OutreachActivity create(CreateOutreach command) {
        NetworkContact contact;
        if (command.contactId() != null && command.newContact() != null) {
            throw new IllegalArgumentException("Choose an existing contact or create a new one, not both.");
        } else if (command.contactId() != null) {
            contact = contactRepository.findById(command.contactId())
                    .orElseThrow(() -> new NotFoundException("Contact " + command.contactId() + " was not found."));
        } else if (command.newContact() != null) {
            contact = createContact(command.newContact());
        } else {
            throw new IllegalArgumentException("A contact is required for outreach.");
        }

        JobOpportunity opportunity = opportunityRepository.findById(command.opportunityId())
                .orElseThrow(() -> new NotFoundException("Opportunity " + command.opportunityId() + " was not found."));
        JobApplication application = applicationRepository.findByOpportunityId(opportunity.getId()).orElse(null);
        return outreachRepository.save(new OutreachActivity(contact, opportunity, application,
                command.outreachType(), command.status(), command.channel(), command.messageSummary(),
                command.followUpAt(), command.notes()));
    }

    @Transactional
    public OutreachActivity transition(UUID id, UpdateOutreach command) {
        OutreachActivity activity = outreachRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Outreach " + id + " was not found."));
        activity.transitionTo(command.status(), command.followUpAt(), command.outcome(), command.notes());
        return activity;
    }

    @Transactional
    public OutreachActivity recordFollowUp(UUID id, RecordFollowUp command) {
        OutreachActivity activity = outreachRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Outreach " + id + " was not found."));
        activity.recordFollowUp(command.nextFollowUpAt(), command.notes());
        return activity;
    }

    public record NewContact(String fullName, String companyName, String roleTitle, String profileUrl,
            String email, RelationshipStrength relationshipStrength, String notes) {
    }

    public record CreateOutreach(UUID opportunityId, UUID contactId, NewContact newContact,
            OutreachType outreachType, OutreachStatus status, String channel, String messageSummary,
            Instant followUpAt, String notes) {
    }

    public record UpdateOutreach(OutreachStatus status, Instant followUpAt, String outcome, String notes) {
    }

    public record RecordFollowUp(Instant nextFollowUpAt, String notes) {
    }
}
