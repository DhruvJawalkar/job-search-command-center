package dev.dhruv.jobsearch.review;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, UUID> {
    Optional<WeeklyReview> findByWeekStart(LocalDate weekStart);
    List<WeeklyReview> findTop26ByOrderByWeekStartDesc();
}

interface WeeklyMetricSnapshotRepository extends JpaRepository<WeeklyMetricSnapshot, UUID> {
    Optional<WeeklyMetricSnapshot> findByReviewId(UUID reviewId);
}

interface WeeklyReviewRevisionRepository extends JpaRepository<WeeklyReviewRevision, UUID> {
    List<WeeklyReviewRevision> findByReviewIdOrderByRevisionNumberDesc(UUID reviewId);
    long countByReviewId(UUID reviewId);
}
