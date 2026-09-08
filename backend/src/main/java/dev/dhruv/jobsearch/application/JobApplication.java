package dev.dhruv.jobsearch.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.resume.ResumeVariant;
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
@Table(name = "job_application")
public class JobApplication {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false, unique = true)
    private JobOpportunity opportunity;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "resume_variant_id", nullable = false)
    private ResumeVariant resumeVariant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private ApplicationStage stage;

    private LocalDate appliedOn;

    @Column(length = 120)
    private String channel;

    @Column(length = 500)
    private String nextAction;

    private Instant nextActionAt;

    @Column(nullable = false)
    private boolean followUpActive;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected JobApplication() {
    }

    public JobApplication(JobOpportunity opportunity, ResumeVariant resumeVariant, ApplicationStage stage,
            LocalDate appliedOn, String channel, String nextAction, Instant nextActionAt) {
        this.id = UUID.randomUUID();
        this.opportunity = opportunity;
        this.resumeVariant = resumeVariant;
        this.stage = stage;
        this.appliedOn = appliedOn;
        this.channel = channel;
        this.nextAction = nextAction == null || nextAction.isBlank() ? null : nextAction.trim();
        this.nextActionAt = nextActionAt;
        if ((this.nextAction == null) != (this.nextActionAt == null)) {
            throw new IllegalArgumentException("Provide both a next action and date, or leave both blank.");
        }
        this.followUpActive = this.nextAction != null && !stage.isTerminal();
    }

    public ApplicationStage transitionTo(ApplicationStage target, String nextAction, Instant nextActionAt) {
        if (!ApplicationTransitions.canTransition(stage, target)) {
            throw new IllegalStateException("Cannot transition application from " + stage + " to " + target + ".");
        }
        ApplicationStage previous = stage;
        stage = target;
        if (target == ApplicationStage.APPLIED && appliedOn == null) {
            appliedOn = LocalDate.now();
        }
        if (nextAction != null && !nextAction.isBlank()) {
            this.nextAction = nextAction.trim();
        }
        if (nextActionAt != null) {
            this.nextActionAt = nextActionAt;
        }
        if (target.isTerminal()) {
            this.followUpActive = false;
        }
        return previous;
    }

    public void scheduleFollowUp(String nextAction, Instant nextActionAt) {
        if (stage.isTerminal()) {
            throw new IllegalStateException("A terminal application cannot have an active follow-up.");
        }
        if (nextAction == null || nextAction.isBlank() || nextActionAt == null) {
            throw new IllegalArgumentException("A next action and date are required to track a follow-up.");
        }
        this.nextAction = nextAction.trim();
        this.nextActionAt = nextActionAt;
        this.followUpActive = true;
    }

    public void dropFollowUp() {
        this.followUpActive = false;
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
    public ResumeVariant getResumeVariant() { return resumeVariant; }
    public ApplicationStage getStage() { return stage; }
    public LocalDate getAppliedOn() { return appliedOn; }
    public String getChannel() { return channel; }
    public String getNextAction() { return nextAction; }
    public Instant getNextActionAt() { return nextActionAt; }
    public boolean isFollowUpActive() { return followUpActive; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
