package dev.dhruv.jobsearch.review;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "weekly_metric_snapshot")
public class WeeklyMetricSnapshot {

    @Id private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, unique = true)
    private WeeklyReview review;
    @Column(nullable = false) private int openingsDiscovered;
    @Column(nullable = false) private int applicationsSubmitted;
    @Column(nullable = false) private int highFitApplications;
    @Column(nullable = false) private int applicationProgressions;
    @Column(nullable = false) private int outreachSent;
    @Column(nullable = false) private int outreachResponses;
    @Column(nullable = false) private int referralsSecured;
    @Column(nullable = false) private int preparationSessions;
    @Column(nullable = false) private int preparationMinutes;
    @Column(nullable = false) private int preparationTasksPlanned;
    @Column(nullable = false) private int preparationTasksCompleted;
    @Column(nullable = false) private int commitmentsPlanned;
    @Column(nullable = false) private int commitmentsCompleted;
    @Column(nullable = false) private int dailyActionsPlanned;
    @Column(nullable = false) private int dailyActionsCompleted;
    @Column(nullable = false) private int activePipeline;
    @Column(nullable = false) private int overdueApplicationActions;
    @Column(nullable = false) private int overdueOutreachFollowUps;
    @Column(nullable = false) private int stageApplied;
    @Column(nullable = false) private int stageRecruiterScreen;
    @Column(nullable = false) private int stageInterviewing;
    @Column(nullable = false) private int stageOffer;
    @Column(nullable = false) private Instant capturedAt;

    protected WeeklyMetricSnapshot() {}

    public WeeklyMetricSnapshot(WeeklyReview review, WeeklyReviewService.MetricValues values, Instant capturedAt) {
        this.id = UUID.randomUUID();
        this.review = review;
        this.openingsDiscovered = values.openingsDiscovered();
        this.applicationsSubmitted = values.applicationsSubmitted();
        this.highFitApplications = values.highFitApplications();
        this.applicationProgressions = values.applicationProgressions();
        this.outreachSent = values.outreachSent();
        this.outreachResponses = values.outreachResponses();
        this.referralsSecured = values.referralsSecured();
        this.preparationSessions = values.preparationSessions();
        this.preparationMinutes = values.preparationMinutes();
        this.preparationTasksPlanned = values.preparationTasksPlanned();
        this.preparationTasksCompleted = values.preparationTasksCompleted();
        this.commitmentsPlanned = values.commitmentsPlanned();
        this.commitmentsCompleted = values.commitmentsCompleted();
        this.dailyActionsPlanned = values.dailyActionsPlanned();
        this.dailyActionsCompleted = values.dailyActionsCompleted();
        this.activePipeline = values.activePipeline();
        this.overdueApplicationActions = values.overdueApplicationActions();
        this.overdueOutreachFollowUps = values.overdueOutreachFollowUps();
        this.stageApplied = values.stageApplied();
        this.stageRecruiterScreen = values.stageRecruiterScreen();
        this.stageInterviewing = values.stageInterviewing();
        this.stageOffer = values.stageOffer();
        this.capturedAt = capturedAt;
    }

    @PrePersist void prePersist() { if (capturedAt == null) capturedAt = Instant.now(); }

    public WeeklyReview getReview() { return review; }
    public int getOpeningsDiscovered() { return openingsDiscovered; }
    public int getApplicationsSubmitted() { return applicationsSubmitted; }
    public int getHighFitApplications() { return highFitApplications; }
    public int getApplicationProgressions() { return applicationProgressions; }
    public int getOutreachSent() { return outreachSent; }
    public int getOutreachResponses() { return outreachResponses; }
    public int getReferralsSecured() { return referralsSecured; }
    public int getPreparationSessions() { return preparationSessions; }
    public int getPreparationMinutes() { return preparationMinutes; }
    public int getPreparationTasksPlanned() { return preparationTasksPlanned; }
    public int getPreparationTasksCompleted() { return preparationTasksCompleted; }
    public int getCommitmentsPlanned() { return commitmentsPlanned; }
    public int getCommitmentsCompleted() { return commitmentsCompleted; }
    public int getDailyActionsPlanned() { return dailyActionsPlanned; }
    public int getDailyActionsCompleted() { return dailyActionsCompleted; }
    public int getActivePipeline() { return activePipeline; }
    public int getOverdueApplicationActions() { return overdueApplicationActions; }
    public int getOverdueOutreachFollowUps() { return overdueOutreachFollowUps; }
    public int getStageApplied() { return stageApplied; }
    public int getStageRecruiterScreen() { return stageRecruiterScreen; }
    public int getStageInterviewing() { return stageInterviewing; }
    public int getStageOffer() { return stageOffer; }
    public Instant getCapturedAt() { return capturedAt; }
}
