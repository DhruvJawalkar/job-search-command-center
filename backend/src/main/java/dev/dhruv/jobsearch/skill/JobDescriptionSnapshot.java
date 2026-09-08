package dev.dhruv.jobsearch.skill;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_description_snapshot")
public class JobDescriptionSnapshot {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private JobOpportunity opportunity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private SnapshotSourceType sourceType;

    @Column(length = 500)
    private String sourceLabel;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private Instant capturedAt;

    protected JobDescriptionSnapshot() {
    }

    JobDescriptionSnapshot(JobOpportunity opportunity, SnapshotSourceType sourceType, String sourceLabel,
            String content, String contentHash) {
        this.id = UUID.randomUUID();
        this.opportunity = opportunity;
        this.sourceType = sourceType == null ? SnapshotSourceType.PASTED_DESCRIPTION : sourceType;
        String normalizedSourceLabel = sourceLabel == null || sourceLabel.isBlank() ? null : sourceLabel.trim();
        this.sourceLabel = normalizedSourceLabel != null && normalizedSourceLabel.length() > 500
                ? normalizedSourceLabel.substring(0, 500) : normalizedSourceLabel;
        this.content = content.trim();
        this.contentHash = contentHash;
    }

    @PrePersist
    void prePersist() {
        capturedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public JobOpportunity getOpportunity() { return opportunity; }
    public SnapshotSourceType getSourceType() { return sourceType; }
    public String getSourceLabel() { return sourceLabel; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public Instant getCapturedAt() { return capturedAt; }
}
