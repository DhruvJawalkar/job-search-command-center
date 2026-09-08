package dev.dhruv.jobsearch.review;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "weekly_review")
public class WeeklyReview {

    @Id private UUID id;
    @Column(nullable = false, unique = true) private LocalDate weekStart;
    @Column(nullable = false) private LocalDate weekEnd;
    @Column(nullable = false) private LocalDate effectiveThrough;
    @Column(nullable = false) private boolean completeWeek;
    @Column(nullable = false) private Instant generatedAt;
    @Column(nullable = false) private Instant createdAt;

    protected WeeklyReview() {}

    public WeeklyReview(LocalDate weekStart, LocalDate weekEnd, LocalDate effectiveThrough, Instant generatedAt) {
        this.id = UUID.randomUUID();
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
        this.effectiveThrough = effectiveThrough;
        this.completeWeek = effectiveThrough.equals(weekEnd);
        this.generatedAt = generatedAt;
    }

    @PrePersist void prePersist() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public LocalDate getWeekStart() { return weekStart; }
    public LocalDate getWeekEnd() { return weekEnd; }
    public LocalDate getEffectiveThrough() { return effectiveThrough; }
    public boolean isCompleteWeek() { return completeWeek; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
