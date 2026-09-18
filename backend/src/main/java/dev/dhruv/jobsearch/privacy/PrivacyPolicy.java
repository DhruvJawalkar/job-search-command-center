package dev.dhruv.jobsearch.privacy;

import java.time.Instant;

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
@Table(name = "privacy_policy")
public class PrivacyPolicy {

    static final short SINGLETON_ID = 1;

    @Id private short id;
    @Column(nullable = false) private long revision;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private AssistanceContextMode assistanceContextMode;
    private Integer derivedContextRetentionDays;
    @Column(nullable = false) private int transientIngestionRetentionDays;
    @Column(nullable = false) private boolean connectedAssistanceEnabled;
    @Column(length = 80) private String consentTextVersion;
    private Instant consentAcceptedAt;
    private Instant lastSuccessfulCleanupAt;
    private Instant nextScheduledCleanupAt;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version @Column(name = "lock_version", nullable = false) private long lockVersion;

    protected PrivacyPolicy() {
        id = SINGLETON_ID;
        revision = 1;
        assistanceContextMode = AssistanceContextMode.STATELESS;
        transientIngestionRetentionDays = 7;
    }

    void update(AssistanceContextMode mode, Integer retentionDays, int transientRetentionDays,
            boolean connectedAssistanceEnabled,
            String consentTextVersion, Instant requestedConsentAcceptedAt) {
        validate(mode, retentionDays);
        validateTransient(transientRetentionDays);
        boolean previouslyEligible = isAutomaticCleanupEligible();
        Instant previousNextScheduledCleanupAt = this.nextScheduledCleanupAt;
        String cleanedConsentTextVersion = clean(consentTextVersion);
        Instant effectiveConsentAcceptedAt = requestedConsentAcceptedAt;
        if (cleanedConsentTextVersion != null && cleanedConsentTextVersion.equals(this.consentTextVersion)
                && this.consentAcceptedAt != null && requestedConsentAcceptedAt != null) {
            effectiveConsentAcceptedAt = this.consentAcceptedAt;
        }
        if ((cleanedConsentTextVersion == null) != (effectiveConsentAcceptedAt == null)) {
            throw new IllegalArgumentException("Consent text version and acceptance must be recorded together.");
        }
        this.assistanceContextMode = mode;
        this.derivedContextRetentionDays = retentionDays;
        this.transientIngestionRetentionDays = transientRetentionDays;
        this.connectedAssistanceEnabled = connectedAssistanceEnabled;
        this.consentTextVersion = cleanedConsentTextVersion;
        this.consentAcceptedAt = effectiveConsentAcceptedAt;
        this.nextScheduledCleanupAt = isAutomaticCleanupEligible()
                ? previouslyEligible && previousNextScheduledCleanupAt != null
                    ? previousNextScheduledCleanupAt
                    : effectiveConsentAcceptedAt.plusSeconds(24 * 60 * 60L)
                : null;
        this.revision++;
    }

    void recordSuccessfulCleanup(Instant completedAt, Instant nextScheduledCleanupAt) {
        this.lastSuccessfulCleanupAt = completedAt;
        this.nextScheduledCleanupAt = nextScheduledCleanupAt;
    }

    boolean isAutomaticCleanupEligible() {
        return consentTextVersion != null && consentAcceptedAt != null;
    }

    static void validate(AssistanceContextMode mode, Integer retentionDays) {
        if (mode == null) throw new IllegalArgumentException("Choose an assistance-context mode.");
        if (mode == AssistanceContextMode.TIME_BOUND) {
            if (retentionDays == null || (retentionDays != 7 && retentionDays != 30 && retentionDays != 90)) {
                throw new IllegalArgumentException("Time-bound personalization must retain derived context for 7, 30, or 90 days.");
            }
        } else if (retentionDays != null) {
            throw new IllegalArgumentException("Derived retention days apply only to time-bound personalization.");
        }
    }

    static void validateTransient(Integer retentionDays) {
        if (retentionDays == null || (retentionDays != 7 && retentionDays != 30)) {
            throw new IllegalArgumentException("Transient import data must be retained for 7 or 30 days.");
        }
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public long getRevision() { return revision; }
    public AssistanceContextMode getAssistanceContextMode() { return assistanceContextMode; }
    public Integer getDerivedContextRetentionDays() { return derivedContextRetentionDays; }
    public int getTransientIngestionRetentionDays() { return transientIngestionRetentionDays; }
    public boolean isConnectedAssistanceEnabled() { return connectedAssistanceEnabled; }
    public String getConsentTextVersion() { return consentTextVersion; }
    public Instant getConsentAcceptedAt() { return consentAcceptedAt; }
    public Instant getLastSuccessfulCleanupAt() { return lastSuccessfulCleanupAt; }
    public Instant getNextScheduledCleanupAt() { return nextScheduledCleanupAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
