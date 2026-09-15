package dev.dhruv.jobsearch.ingestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.resume.ResumeVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "opportunity_observation")
public class OpportunityObservation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private JobOpportunity opportunity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_resume_variant_id")
    private ResumeVariant recommendedResumeVariant;

    @Column(nullable = false)
    private LocalDate observedOn;

    @Column(nullable = false)
    private int sourceRank;

    private LocalDate postingDate;

    @Column(nullable = false)
    private LocalDate verifiedDate;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal overallFit;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal recruiterScreenStrength;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal technicalScope;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal growthPotential;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal weightedTotal;

    @Column(nullable = false, length = 80)
    private String recommendation;

    @Column(nullable = false, columnDefinition = "text")
    private String roleSummary;

    @Column(nullable = false, columnDefinition = "text")
    private String fitRationale;

    @Column(columnDefinition = "text")
    private String keyRisks;

    @Column(columnDefinition = "text")
    private String authorizationEligibility;

    @Column(length = 200)
    private String recommendedResumeName;

    @Column(nullable = false, length = 64)
    private String rowFingerprint;

    @Column(nullable = false, columnDefinition = "text")
    private String rawPayload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected OpportunityObservation() {
    }

    public OpportunityObservation(ImportBatch importBatch, JobOpportunity opportunity, LocalDate observedOn) {
        this.id = UUID.randomUUID();
        this.importBatch = importBatch;
        this.opportunity = opportunity;
        this.observedOn = observedOn;
    }

    public void refresh(ImportBatch batch, ResumeVariant resumeVariant, ImportedObservation data) {
        this.importBatch = batch;
        this.recommendedResumeVariant = resumeVariant;
        this.sourceRank = data.sourceRank();
        this.postingDate = data.postingDate();
        this.verifiedDate = data.verifiedDate();
        this.overallFit = data.overallFit();
        this.recruiterScreenStrength = data.recruiterScreenStrength();
        this.technicalScope = data.technicalScope();
        this.growthPotential = data.growthPotential();
        this.weightedTotal = data.weightedTotal();
        this.recommendation = data.recommendation();
        this.roleSummary = data.roleSummary();
        this.fitRationale = data.fitRationale();
        this.keyRisks = data.keyRisks();
        this.authorizationEligibility = data.authorizationEligibility();
        this.recommendedResumeName = data.recommendedResumeName();
        this.rowFingerprint = data.rowFingerprint();
        this.rawPayload = data.rawPayload();
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
    public ImportBatch getImportBatch() { return importBatch; }
    public JobOpportunity getOpportunity() { return opportunity; }
    public ResumeVariant getRecommendedResumeVariant() { return recommendedResumeVariant; }
    public LocalDate getObservedOn() { return observedOn; }
    public int getSourceRank() { return sourceRank; }
    public LocalDate getPostingDate() { return postingDate; }
    public LocalDate getVerifiedDate() { return verifiedDate; }
    public BigDecimal getOverallFit() { return overallFit; }
    public BigDecimal getRecruiterScreenStrength() { return recruiterScreenStrength; }
    public BigDecimal getTechnicalScope() { return technicalScope; }
    public BigDecimal getGrowthPotential() { return growthPotential; }
    public BigDecimal getWeightedTotal() { return weightedTotal; }
    public String getRecommendation() { return recommendation; }
    public String getRoleSummary() { return roleSummary; }
    public String getFitRationale() { return fitRationale; }
    public String getKeyRisks() { return keyRisks; }
    public String getAuthorizationEligibility() { return authorizationEligibility; }
    public String getRecommendedResumeName() { return recommendedResumeName; }
    public String getRowFingerprint() { return rowFingerprint; }
    public String getRawPayload() { return rawPayload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public record ImportedObservation(
            int sourceRank,
            LocalDate postingDate,
            LocalDate verifiedDate,
            BigDecimal overallFit,
            BigDecimal recruiterScreenStrength,
            BigDecimal technicalScope,
            BigDecimal growthPotential,
            BigDecimal weightedTotal,
            String recommendation,
            String roleSummary,
            String fitRationale,
            String keyRisks,
            String authorizationEligibility,
            String recommendedResumeName,
            String rowFingerprint,
            String rawPayload) {
    }
}
