package dev.dhruv.jobsearch.review;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "weekly_review_revision")
public class WeeklyReviewRevision {

    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private WeeklyReview review;
    @Column(nullable = false) private int revisionNumber;
    @Column(columnDefinition = "text") private String wins;
    @Column(columnDefinition = "text") private String challenges;
    @Column(columnDefinition = "text") private String reflection;
    @Column(columnDefinition = "text") private String nextWeekAdjustments;
    @Column(columnDefinition = "text") private String nextWeekFocus;
    @Column(nullable = false) private Instant createdAt;

    protected WeeklyReviewRevision() {}

    public WeeklyReviewRevision(WeeklyReview review, int revisionNumber, String wins, String challenges,
            String reflection, String nextWeekAdjustments, String nextWeekFocus) {
        this.id = UUID.randomUUID();
        this.review = review;
        this.revisionNumber = revisionNumber;
        this.wins = optional(wins);
        this.challenges = optional(challenges);
        this.reflection = optional(reflection);
        this.nextWeekAdjustments = optional(nextWeekAdjustments);
        this.nextWeekFocus = optional(nextWeekFocus);
    }

    @PrePersist void prePersist() { createdAt = Instant.now(); }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public int getRevisionNumber() { return revisionNumber; }
    public String getWins() { return wins; }
    public String getChallenges() { return challenges; }
    public String getReflection() { return reflection; }
    public String getNextWeekAdjustments() { return nextWeekAdjustments; }
    public String getNextWeekFocus() { return nextWeekFocus; }
    public Instant getCreatedAt() { return createdAt; }
}
