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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "job_skill_observation")
public class JobSkillObservation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private JobOpportunity opportunity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private JobDescriptionSnapshot snapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private CanonicalSkill skill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillStrength strength;

    @Column(nullable = false, columnDefinition = "text")
    private String evidenceSnippet;

    @Column(nullable = false, length = 64)
    private String evidenceFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private SkillExtractionMethod extractionMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SkillReviewStatus reviewStatus;

    @Column(columnDefinition = "text")
    private String reviewNote;

    @Column(nullable = false)
    private Instant observedAt;

    private Instant reviewedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected JobSkillObservation() {
    }

    JobSkillObservation(JobDescriptionSnapshot snapshot, CanonicalSkill skill, SkillStrength strength,
            String evidenceSnippet, String evidenceFingerprint, SkillExtractionMethod extractionMethod) {
        this.id = UUID.randomUUID();
        this.opportunity = snapshot.getOpportunity();
        this.snapshot = snapshot;
        this.skill = skill;
        this.strength = strength == null ? SkillStrength.MENTIONED : strength;
        this.evidenceSnippet = evidenceSnippet.trim();
        this.evidenceFingerprint = evidenceFingerprint;
        this.extractionMethod = extractionMethod == null ? SkillExtractionMethod.MANUAL : extractionMethod;
        this.reviewStatus = SkillReviewStatus.PROPOSED;
        this.observedAt = Instant.now();
    }

    public void review(SkillReviewStatus status, String note) {
        if (status == null || status == SkillReviewStatus.PROPOSED) {
            throw new IllegalArgumentException("Review status must be ACCEPTED or REJECTED.");
        }
        reviewStatus = status;
        reviewNote = note == null || note.isBlank() ? null : note.trim();
        reviewedAt = Instant.now();
    }

    public void correct(CanonicalSkill correctedSkill, SkillStrength correctedStrength,
            String correctedEvidence, String correctedFingerprint, String note) {
        this.skill = correctedSkill;
        this.strength = correctedStrength == null ? SkillStrength.MENTIONED : correctedStrength;
        this.evidenceSnippet = correctedEvidence.trim();
        this.evidenceFingerprint = correctedFingerprint;
        this.extractionMethod = SkillExtractionMethod.MANUAL;
        review(SkillReviewStatus.ACCEPTED, note);
    }

    void reassignTo(CanonicalSkill target) {
        this.skill = target;
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
    public JobOpportunity getOpportunity() { return opportunity; }
    public JobDescriptionSnapshot getSnapshot() { return snapshot; }
    public CanonicalSkill getSkill() { return skill; }
    public SkillStrength getStrength() { return strength; }
    public String getEvidenceSnippet() { return evidenceSnippet; }
    public String getEvidenceFingerprint() { return evidenceFingerprint; }
    public SkillExtractionMethod getExtractionMethod() { return extractionMethod; }
    public SkillReviewStatus getReviewStatus() { return reviewStatus; }
    public String getReviewNote() { return reviewNote; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
