package dev.dhruv.jobsearch.ingestion;

import java.time.Instant;
import java.util.UUID;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.WorkMode;
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
@Table(name = "generic_inbox_candidate")
public class GenericInboxCandidate {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inbox_item_id", nullable = false)
    private GenericInboxItem inboxItem;

    @Column(nullable = false)
    private int rowNumber;

    @Column(length = 240)
    private String companyName;

    @Column(length = 240)
    private String roleTitle;

    @Column(length = 240)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkMode workMode;

    @Column(length = 120)
    private String sourceName;

    @Column(length = 240)
    private String sourceExternalId;

    @Column(length = 1500)
    private String sourceUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String parseWarnings;

    @Column(nullable = false, columnDefinition = "text")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GenericInboxCandidateStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opportunity_id")
    private JobOpportunity opportunity;

    private Instant reviewedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected GenericInboxCandidate() {
    }

    public GenericInboxCandidate(GenericInboxItem inboxItem, int rowNumber, String companyName, String roleTitle,
            String location, WorkMode workMode, String sourceName, String sourceExternalId, String sourceUrl, String description,
            String parseWarnings, String rawPayload) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.inboxItem = inboxItem;
        this.rowNumber = rowNumber;
        this.companyName = companyName;
        this.roleTitle = roleTitle;
        this.location = location;
        this.workMode = workMode == null ? WorkMode.UNSPECIFIED : workMode;
        this.sourceName = sourceName;
        this.sourceExternalId = sourceExternalId;
        this.sourceUrl = sourceUrl;
        this.description = description;
        this.parseWarnings = parseWarnings;
        this.rawPayload = rawPayload;
        this.status = GenericInboxCandidateStatus.NEEDS_REVIEW;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void revise(String companyName, String roleTitle, String location, WorkMode workMode,
            String sourceName, String sourceExternalId, String sourceUrl, String description) {
        requireReviewable();
        this.companyName = companyName;
        this.roleTitle = roleTitle;
        this.location = location;
        this.workMode = workMode == null ? WorkMode.UNSPECIFIED : workMode;
        this.sourceName = sourceName;
        this.sourceExternalId = sourceExternalId;
        this.sourceUrl = sourceUrl;
        this.description = description;
        this.parseWarnings = missingFieldWarnings(companyName, roleTitle);
        this.updatedAt = Instant.now();
    }

    public void importAs(JobOpportunity opportunity) {
        requireReviewable();
        this.opportunity = opportunity;
        this.status = GenericInboxCandidateStatus.IMPORTED;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public void markDuplicateReview() {
        if (status == GenericInboxCandidateStatus.NEEDS_REVIEW) {
            status = GenericInboxCandidateStatus.DUPLICATE_REVIEW;
            updatedAt = Instant.now();
        }
    }

    public void clearDuplicateReview() {
        if (status == GenericInboxCandidateStatus.DUPLICATE_REVIEW) {
            status = GenericInboxCandidateStatus.NEEDS_REVIEW;
            updatedAt = Instant.now();
        }
    }

    public void linkTo(JobOpportunity opportunity) {
        requireReviewable();
        this.opportunity = opportunity;
        this.status = GenericInboxCandidateStatus.LINKED;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public void mergedInto(JobOpportunity opportunity) {
        requireReviewable();
        this.opportunity = opportunity;
        this.status = GenericInboxCandidateStatus.MERGED;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    public void reject() {
        requireReviewable();
        this.status = GenericInboxCandidateStatus.REJECTED;
        this.reviewedAt = Instant.now();
        this.updatedAt = this.reviewedAt;
    }

    private void requireReviewable() {
        if (status != GenericInboxCandidateStatus.NEEDS_REVIEW
                && status != GenericInboxCandidateStatus.DUPLICATE_REVIEW) {
            throw new IllegalStateException("This inbox candidate has already been reviewed.");
        }
    }

    static String missingFieldWarnings(String companyName, String roleTitle) {
        if ((companyName == null || companyName.isBlank()) && (roleTitle == null || roleTitle.isBlank())) {
            return "Company and role title need review before import.";
        }
        if (companyName == null || companyName.isBlank()) return "Company needs review before import.";
        if (roleTitle == null || roleTitle.isBlank()) return "Role title needs review before import.";
        return null;
    }

    public UUID getId() { return id; }
    public GenericInboxItem getInboxItem() { return inboxItem; }
    public int getRowNumber() { return rowNumber; }
    public String getCompanyName() { return companyName; }
    public String getRoleTitle() { return roleTitle; }
    public String getLocation() { return location; }
    public WorkMode getWorkMode() { return workMode; }
    public String getSourceName() { return sourceName; }
    public String getSourceExternalId() { return sourceExternalId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getDescription() { return description; }
    public String getParseWarnings() { return parseWarnings; }
    public String getRawPayload() { return rawPayload; }
    public GenericInboxCandidateStatus getStatus() { return status; }
    public JobOpportunity getOpportunity() { return opportunity; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
