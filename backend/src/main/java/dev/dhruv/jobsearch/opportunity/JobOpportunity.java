package dev.dhruv.jobsearch.opportunity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "job_opportunity")
public class JobOpportunity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 240)
    private String companyName;

    @Column(nullable = false, length = 240)
    private String roleTitle;

    @Column(length = 240)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private WorkMode workMode;

    @Column(length = 120)
    private String sourceName;

    @Column(length = 240)
    private String sourceExternalId;

    @Column(length = 1500)
    private String sourceUrl;

    @Column(length = 1500)
    private String canonicalUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OpportunityStatus status;

    private Integer fitScore;

    @Column(columnDefinition = "text")
    private String fitSummary;

    @Column(columnDefinition = "text")
    private String archiveReason;

    private Instant archivedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private OpportunityStatus archivedFromStatus;

    @Column(nullable = false)
    private Instant discoveredAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Version
    private long version;

    protected JobOpportunity() {
    }

    public JobOpportunity(String companyName, String roleTitle, String location, WorkMode workMode,
            String sourceName, String sourceUrl, String canonicalUrl, String description, Instant discoveredAt) {
        this.id = UUID.randomUUID();
        this.companyName = companyName;
        this.roleTitle = roleTitle;
        this.location = location;
        this.workMode = workMode == null ? WorkMode.UNSPECIFIED : workMode;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.canonicalUrl = canonicalUrl;
        this.description = description;
        this.status = OpportunityStatus.NEW;
        this.demo = false;
        this.discoveredAt = discoveredAt == null ? Instant.now() : discoveredAt;
    }

    public void refreshFromImport(String companyName, String roleTitle, String location, WorkMode workMode,
            String sourceName, String sourceUrl, String description, Integer fitScore, String fitSummary,
            OpportunityStatus suggestedStatus) {
        this.companyName = companyName;
        this.roleTitle = roleTitle;
        this.location = location;
        this.workMode = workMode == null ? WorkMode.UNSPECIFIED : workMode;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.description = description;
        this.fitScore = fitScore;
        this.fitSummary = fitSummary;
        if (status != OpportunityStatus.APPLIED && status != OpportunityStatus.EXPIRED
                && status != OpportunityStatus.ARCHIVED) {
            this.status = suggestedStatus;
        }
    }

    public void recordSourceExternalId(String sourceExternalId) {
        this.sourceExternalId = sourceExternalId;
    }

    public void mergeReviewedFields(Set<OpportunityMergeField> fields, String companyName, String roleTitle,
            String location, WorkMode workMode, String sourceName, String sourceUrl, String canonicalUrl,
            String sourceExternalId, String description) {
        if (fields.contains(OpportunityMergeField.COMPANY_NAME)) this.companyName = companyName;
        if (fields.contains(OpportunityMergeField.ROLE_TITLE)) this.roleTitle = roleTitle;
        if (fields.contains(OpportunityMergeField.LOCATION)) this.location = location;
        if (fields.contains(OpportunityMergeField.WORK_MODE)) this.workMode = workMode == null ? WorkMode.UNSPECIFIED : workMode;
        if (fields.contains(OpportunityMergeField.SOURCE_NAME)) this.sourceName = sourceName;
        if (fields.contains(OpportunityMergeField.SOURCE_URL)) {
            this.sourceUrl = sourceUrl;
            this.canonicalUrl = canonicalUrl;
        }
        if (fields.contains(OpportunityMergeField.SOURCE_EXTERNAL_ID)) this.sourceExternalId = sourceExternalId;
        if (fields.contains(OpportunityMergeField.DESCRIPTION)) this.description = description;
    }

    public void markDemo() {
        this.demo = true;
    }

    public void recordDecision(OpportunityStatus status, Integer fitScore, String fitSummary) {
        if (this.status == OpportunityStatus.ARCHIVED) {
            throw new IllegalStateException("Restore this opening before changing its review decision.");
        }
        if (status != OpportunityStatus.REVIEWING
                && status != OpportunityStatus.SHORTLISTED
                && status != OpportunityStatus.SKIPPED) {
            throw new IllegalArgumentException("A review decision must be REVIEWING, SHORTLISTED, or SKIPPED.");
        }
        if (fitScore != null && (fitScore < 0 || fitScore > 100)) {
            throw new IllegalArgumentException("Fit score must be between 0 and 100.");
        }
        this.status = status;
        this.fitScore = fitScore;
        this.fitSummary = fitSummary;
    }

    public void markApplied() {
        this.status = OpportunityStatus.APPLIED;
    }

    public void archive(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("An archive reason is required.");
        }
        if (status == OpportunityStatus.ARCHIVED) {
            throw new IllegalStateException("This opening is already archived.");
        }
        if (status == OpportunityStatus.APPLIED) {
            throw new IllegalStateException("An opening with a submitted application cannot be archived.");
        }
        archivedFromStatus = status;
        archiveReason = reason.trim();
        archivedAt = Instant.now();
        status = OpportunityStatus.ARCHIVED;
    }

    public void restore() {
        if (status != OpportunityStatus.ARCHIVED) {
            throw new IllegalStateException("Only an archived opening can be restored.");
        }
        status = archivedFromStatus == null ? OpportunityStatus.REVIEWING : archivedFromStatus;
        archiveReason = null;
        archivedAt = null;
        archivedFromStatus = null;
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
    public String getCompanyName() { return companyName; }
    public String getRoleTitle() { return roleTitle; }
    public String getLocation() { return location; }
    public WorkMode getWorkMode() { return workMode; }
    public String getSourceName() { return sourceName; }
    public String getSourceExternalId() { return sourceExternalId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public String getDescription() { return description; }
    public OpportunityStatus getStatus() { return status; }
    public Integer getFitScore() { return fitScore; }
    public String getFitSummary() { return fitSummary; }
    public String getArchiveReason() { return archiveReason; }
    public Instant getArchivedAt() { return archivedAt; }
    public OpportunityStatus getArchivedFromStatus() { return archivedFromStatus; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isDemo() { return demo; }
}
