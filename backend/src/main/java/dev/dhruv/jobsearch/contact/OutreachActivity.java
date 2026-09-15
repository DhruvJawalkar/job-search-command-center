package dev.dhruv.jobsearch.contact;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.dhruv.jobsearch.application.JobApplication;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "outreach_activity")
public class OutreachActivity {

    private static final Map<OutreachStatus, Set<OutreachStatus>> TRANSITIONS = Map.of(
            OutreachStatus.PLANNED, EnumSet.of(OutreachStatus.SENT, OutreachStatus.CLOSED),
            OutreachStatus.SENT, EnumSet.of(OutreachStatus.RESPONDED, OutreachStatus.REFERRED,
                    OutreachStatus.DECLINED, OutreachStatus.CLOSED),
            OutreachStatus.RESPONDED, EnumSet.of(OutreachStatus.REFERRED, OutreachStatus.DECLINED,
                    OutreachStatus.CLOSED),
            OutreachStatus.REFERRED, EnumSet.of(OutreachStatus.CLOSED),
            OutreachStatus.DECLINED, EnumSet.of(OutreachStatus.CLOSED),
            OutreachStatus.CLOSED, EnumSet.noneOf(OutreachStatus.class));

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private NetworkContact contact;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private JobOpportunity opportunity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id")
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private OutreachType outreachType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OutreachStatus status;

    @Column(length = 80)
    private String channel;

    @Column(columnDefinition = "text")
    private String messageSummary;

    private Instant requestedAt;
    private Instant followUpAt;
    private Instant respondedAt;

    @Column(nullable = false)
    private int followUpCount;

    @Column(length = 500)
    private String outcome;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected OutreachActivity() {
    }

    public OutreachActivity(NetworkContact contact, JobOpportunity opportunity, JobApplication application,
            OutreachType outreachType, OutreachStatus status, String channel, String messageSummary,
            Instant followUpAt, String notes) {
        this.id = UUID.randomUUID();
        this.contact = contact;
        this.opportunity = opportunity;
        this.application = application;
        this.outreachType = outreachType == null ? OutreachType.REFERRAL_REQUEST : outreachType;
        this.status = status == null ? OutreachStatus.PLANNED : status;
        this.channel = optional(channel);
        this.messageSummary = optional(messageSummary);
        this.followUpAt = followUpAt;
        this.notes = optional(notes);
        applyStatusTimestamps(this.status);
    }

    public void transitionTo(OutreachStatus target, Instant nextFollowUpAt, String outcome, String notes) {
        if (target == null) throw new IllegalArgumentException("Outreach status is required.");
        if (target != status && !TRANSITIONS.get(status).contains(target)) {
            throw new IllegalStateException("Cannot transition outreach from " + status + " to " + target + ".");
        }
        status = target;
        if (nextFollowUpAt != null || target == OutreachStatus.RESPONDED || target == OutreachStatus.REFERRED
                || target == OutreachStatus.CLOSED || target == OutreachStatus.DECLINED) {
            followUpAt = nextFollowUpAt;
        }
        if (outcome != null && !outcome.isBlank()) this.outcome = outcome.trim();
        if (notes != null && !notes.isBlank()) this.notes = notes.trim();
        applyStatusTimestamps(target);
    }

    public void recordFollowUp(Instant nextFollowUpAt, String notes) {
        if (status != OutreachStatus.SENT) {
            throw new IllegalStateException("Follow-ups can only be recorded for sent outreach.");
        }
        if (nextFollowUpAt == null) {
            throw new IllegalArgumentException("The next follow-up date is required.");
        }
        followUpCount++;
        followUpAt = nextFollowUpAt;
        if (notes != null && !notes.isBlank()) this.notes = notes.trim();
    }

    private void applyStatusTimestamps(OutreachStatus target) {
        Instant now = Instant.now();
        // Closing tracking is not evidence that a message was sent or answered.
        if (target != OutreachStatus.PLANNED && target != OutreachStatus.CLOSED && requestedAt == null) requestedAt = now;
        if (EnumSet.of(OutreachStatus.RESPONDED, OutreachStatus.REFERRED,
                OutreachStatus.DECLINED).contains(target) && respondedAt == null) {
            respondedAt = now;
        }
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public NetworkContact getContact() { return contact; }
    public JobOpportunity getOpportunity() { return opportunity; }
    public JobApplication getApplication() { return application; }
    public OutreachType getOutreachType() { return outreachType; }
    public OutreachStatus getStatus() { return status; }
    public String getChannel() { return channel; }
    public String getMessageSummary() { return messageSummary; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getFollowUpAt() { return followUpAt; }
    public int getFollowUpCount() { return followUpCount; }
    public Instant getRespondedAt() { return respondedAt; }
    public String getOutcome() { return outcome; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
