package dev.dhruv.jobsearch.ingestion;

import java.time.Instant;
import java.util.UUID;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inbox_duplicate_match")
public class InboxDuplicateMatch {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private GenericInboxCandidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private JobOpportunity opportunity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DuplicateMatchType matchType;

    @Column(nullable = false)
    private int confidence;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DuplicateMatchStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant resolvedAt;

    @Version
    private long version;

    protected InboxDuplicateMatch() {
    }

    public InboxDuplicateMatch(GenericInboxCandidate candidate, JobOpportunity opportunity, DuplicateMatchType matchType,
            int confidence, String explanation) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.candidate = candidate;
        this.opportunity = opportunity;
        this.matchType = matchType;
        this.confidence = confidence;
        this.explanation = explanation;
        this.status = DuplicateMatchStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void refresh(DuplicateMatchType matchType, int confidence, String explanation) {
        if (status != DuplicateMatchStatus.OPEN) return;
        this.matchType = matchType;
        this.confidence = confidence;
        this.explanation = explanation;
        this.updatedAt = Instant.now();
    }

    public void resolve() {
        status = DuplicateMatchStatus.RESOLVED;
        resolvedAt = Instant.now();
        updatedAt = resolvedAt;
    }

    public void dismiss() {
        if (status == DuplicateMatchStatus.OPEN) {
            status = DuplicateMatchStatus.DISMISSED;
            resolvedAt = Instant.now();
            updatedAt = resolvedAt;
        }
    }

    public UUID getId() { return id; }
    public GenericInboxCandidate getCandidate() { return candidate; }
    public JobOpportunity getOpportunity() { return opportunity; }
    public DuplicateMatchType getMatchType() { return matchType; }
    public int getConfidence() { return confidence; }
    public String getExplanation() { return explanation; }
    public DuplicateMatchStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
