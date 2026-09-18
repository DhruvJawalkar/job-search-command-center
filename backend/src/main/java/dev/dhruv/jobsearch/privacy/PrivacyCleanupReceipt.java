package dev.dhruv.jobsearch.privacy;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "privacy_cleanup_receipt")
public class PrivacyCleanupReceipt {

    public static final String DERIVED_ASSISTANCE_CONTEXT = "DERIVED_ASSISTANCE_CONTEXT";
    public static final String RETENTION_ENFORCEMENT = "RETENTION_ENFORCEMENT";
    public static final String SUCCESS = "SUCCESS";
    public static final String PARTIAL = "PARTIAL";

    @Id private UUID id;
    @Column(nullable = false) private long policyRevision;
    @Column(nullable = false, length = 64) private String category;
    @Column(nullable = false) private Instant cutoff;
    @Column(nullable = false) private int assistanceRunCount;
    @Column(nullable = false) private int assistanceDecisionCount;
    @Column(nullable = false) private int transientDatabaseRecordCount;
    @Column(nullable = false) private int auditMetadataRecordCount;
    @Column(nullable = false) private int transientFileCount;
    @Column(nullable = false) private int skippedUnsafeFileCount;
    private Instant transientCutoff;
    @Column(nullable = false, length = 24) private String outcome;
    @Column(nullable = false) private Instant createdAt;

    protected PrivacyCleanupReceipt() {}

    PrivacyCleanupReceipt(long policyRevision, Instant cutoff, int runCount, int decisionCount, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.policyRevision = policyRevision;
        this.category = DERIVED_ASSISTANCE_CONTEXT;
        this.cutoff = cutoff;
        this.assistanceRunCount = runCount;
        this.assistanceDecisionCount = decisionCount;
        this.outcome = SUCCESS;
        this.createdAt = createdAt;
    }

    PrivacyCleanupReceipt(long policyRevision, Instant derivedCutoff, int runCount, int decisionCount,
            Instant transientCutoff, int transientDatabaseRecordCount, int auditMetadataRecordCount,
            int transientFileCount,
            int skippedUnsafeFileCount, String outcome, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.policyRevision = policyRevision;
        this.category = RETENTION_ENFORCEMENT;
        this.cutoff = derivedCutoff;
        this.assistanceRunCount = runCount;
        this.assistanceDecisionCount = decisionCount;
        this.transientCutoff = transientCutoff;
        this.transientDatabaseRecordCount = transientDatabaseRecordCount;
        this.auditMetadataRecordCount = auditMetadataRecordCount;
        this.transientFileCount = transientFileCount;
        this.skippedUnsafeFileCount = skippedUnsafeFileCount;
        this.outcome = outcome;
        this.createdAt = createdAt;
    }

    void finishFileDeletion(int transientFileCount, int skippedUnsafeFileCount) {
        this.transientFileCount = transientFileCount;
        this.skippedUnsafeFileCount = skippedUnsafeFileCount;
        this.outcome = skippedUnsafeFileCount == 0 ? SUCCESS : PARTIAL;
    }

    public UUID getId() { return id; }
    public long getPolicyRevision() { return policyRevision; }
    public String getCategory() { return category; }
    public Instant getCutoff() { return cutoff; }
    public int getAssistanceRunCount() { return assistanceRunCount; }
    public int getAssistanceDecisionCount() { return assistanceDecisionCount; }
    public int getTransientDatabaseRecordCount() { return transientDatabaseRecordCount; }
    public int getAuditMetadataRecordCount() { return auditMetadataRecordCount; }
    public int getTransientFileCount() { return transientFileCount; }
    public int getSkippedUnsafeFileCount() { return skippedUnsafeFileCount; }
    public Instant getTransientCutoff() { return transientCutoff; }
    public String getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }
}
