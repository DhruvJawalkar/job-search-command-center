package dev.dhruv.jobsearch.review;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class WeeklyReviewService {

    private final WeeklyReviewRepository reviews;
    private final WeeklyMetricSnapshotRepository snapshots;
    private final WeeklyReviewRevisionRepository revisions;
    private final JdbcTemplate jdbc;
    private final ZoneId zone = ZoneId.systemDefault();

    public WeeklyReviewService(WeeklyReviewRepository reviews, WeeklyMetricSnapshotRepository snapshots,
            WeeklyReviewRevisionRepository revisions, JdbcTemplate jdbc) {
        this.reviews = reviews;
        this.snapshots = snapshots;
        this.revisions = revisions;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public WeeklyOverview overview(LocalDate requestedWeekStart) {
        LocalDate today = LocalDate.now(zone);
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart == null ? today : requestedWeekStart);
        MetricView preview = livePreview(weekStart, today);
        List<ReviewView> history = historyViews();
        return new WeeklyOverview(today, preview, history);
    }

    @Transactional(readOnly = true)
    public MonthlyProgress monthly(LocalDate requestedMonth) {
        LocalDate today = LocalDate.now(zone);
        LocalDate monthStart = (requestedMonth == null ? today : requestedMonth).withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        List<MonthlyDay> days = new ArrayList<>();
        int applications = 0, outreachSent = 0, outreachResponses = 0, referrals = 0;
        int tasksPlanned = 0, tasksCompleted = 0, sessions = 0, minutes = 0;
        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            Instant from = date.atStartOfDay(zone).toInstant();
            Instant until = date.plusDays(1).atStartOfDay(zone).toInstant();
            int dayApplications = count("select count(*) from job_application where applied_on = ?", date);
            int dayOutreach = count("select count(*) from outreach_activity where requested_at >= ? and requested_at < ?", from, until);
            int dayResponses = count("select count(*) from outreach_activity where responded_at >= ? and responded_at < ?", from, until);
            int dayReferrals = count("select count(*) from outreach_activity where status = 'REFERRED' and responded_at >= ? and responded_at < ?", from, until);
            int dayTasksPlanned = count("select count(*) from preparation_item where scheduled_for = ? or due_date = ?", date, date);
            int dayTasksCompleted = count("select count(*) from preparation_item where completed_at >= ? and completed_at < ?", from, until);
            int daySessions = count("select count(*) from practice_session where practiced_at >= ? and practiced_at < ?", from, until);
            int dayMinutes = count("select coalesce(sum(duration_minutes), 0) from practice_session where practiced_at >= ? and practiced_at < ?", from, until);
            applications += dayApplications; outreachSent += dayOutreach; outreachResponses += dayResponses;
            referrals += dayReferrals; tasksPlanned += dayTasksPlanned; tasksCompleted += dayTasksCompleted;
            sessions += daySessions; minutes += dayMinutes;
            days.add(new MonthlyDay(date, dayApplications, dayOutreach, dayResponses, dayReferrals,
                    dayTasksPlanned, dayTasksCompleted, daySessions, dayMinutes));
        }
        return new MonthlyProgress(monthStart, monthEnd, today,
                new MonthlyTotals(applications, outreachSent, outreachResponses, referrals, tasksPlanned,
                        tasksCompleted, sessions, minutes), days);
    }

    @Transactional
    public CreateResult generate(LocalDate requestedWeekStart) {
        LocalDate today = LocalDate.now(zone);
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart == null ? today : requestedWeekStart);
        if (weekStart.isAfter(today)) throw new IllegalArgumentException("A weekly review cannot start in the future.");
        var existing = reviews.findByWeekStart(weekStart);
        if (existing.isPresent()) return new CreateResult(true, view(existing.get(), previousSnapshot(weekStart)));

        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate effectiveThrough = weekEnd.isBefore(today) ? weekEnd : today;
        Instant generatedAt = Instant.now();
        WeeklyReview review = reviews.save(new WeeklyReview(weekStart, weekEnd, effectiveThrough, generatedAt));
        MetricValues values = calculate(weekStart, effectiveThrough, generatedAt);
        snapshots.save(new WeeklyMetricSnapshot(review, values, generatedAt));
        return new CreateResult(false, view(review, previousSnapshot(weekStart)));
    }

    @Transactional
    public ReviewView addRevision(UUID reviewId, RevisionCommand command) {
        WeeklyReview review = reviews.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Weekly review " + reviewId + " was not found."));
        if (allBlank(command.wins(), command.challenges(), command.reflection(), command.nextWeekAdjustments(),
                command.nextWeekFocus())) {
            throw new IllegalArgumentException("Add at least one reflection or next-week adjustment.");
        }
        int revisionNumber = Math.toIntExact(revisions.countByReviewId(reviewId) + 1);
        revisions.save(new WeeklyReviewRevision(review, revisionNumber, command.wins(), command.challenges(),
                command.reflection(), command.nextWeekAdjustments(), command.nextWeekFocus()));
        return view(review, previousSnapshot(review.getWeekStart()));
    }

    @Transactional(readOnly = true)
    public ReviewView get(UUID reviewId) {
        WeeklyReview review = reviews.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Weekly review " + reviewId + " was not found."));
        return view(review, previousSnapshot(review.getWeekStart()));
    }

    private List<ReviewView> historyViews() {
        List<ReviewView> result = new ArrayList<>();
        for (WeeklyReview review : reviews.findTop26ByOrderByWeekStartDesc()) {
            result.add(view(review, previousSnapshot(review.getWeekStart())));
        }
        return result;
    }

    private ReviewView view(WeeklyReview review, WeeklyMetricSnapshot previous) {
        WeeklyMetricSnapshot snapshot = snapshots.findByReviewId(review.getId())
                .orElseThrow(() -> new IllegalStateException("Weekly review snapshot is missing."));
        List<RevisionView> revisionViews = revisions.findByReviewIdOrderByRevisionNumberDesc(review.getId()).stream()
                .map(RevisionView::from).toList();
        return new ReviewView(review.getId(), review.getWeekStart(), review.getWeekEnd(), review.getEffectiveThrough(),
                review.isCompleteWeek(), review.getGeneratedAt(), MetricView.from(snapshot),
                previous == null ? MetricDelta.zero() : MetricDelta.between(snapshot, previous), revisionViews);
    }

    private MetricView livePreview(LocalDate weekStart, LocalDate today) {
        if (weekStart.isAfter(today)) throw new IllegalArgumentException("A weekly preview cannot start in the future.");
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate effectiveThrough = weekEnd.isBefore(today) ? weekEnd : today;
        Instant generatedAt = Instant.now();
        return MetricView.from(weekStart, weekEnd, effectiveThrough, effectiveThrough.equals(weekEnd), generatedAt,
                calculate(weekStart, effectiveThrough, generatedAt));
    }

    private WeeklyMetricSnapshot previousSnapshot(LocalDate weekStart) {
        return reviews.findByWeekStart(weekStart.minusWeeks(1))
                .flatMap(review -> snapshots.findByReviewId(review.getId())).orElse(null);
    }

    private MetricValues calculate(LocalDate weekStart, LocalDate effectiveThrough, Instant capturedAt) {
        Instant from = weekStart.atStartOfDay(zone).toInstant();
        Instant until = effectiveThrough.plusDays(1).atStartOfDay(zone).toInstant();
        int openings = count("select count(*) from job_opportunity where is_demo = false and discovered_at >= ? and discovered_at < ?", from, until);
        int applications = count("select count(*) from job_application where applied_on between ? and ?", weekStart, effectiveThrough);
        int highFit = count("""
                select count(*) from job_application a join job_opportunity o on o.id = a.opportunity_id
                where a.applied_on between ? and ? and (o.fit_score >= 80 or exists (
                  select 1 from opportunity_observation oo where oo.opportunity_id = o.id and oo.weighted_total >= 8.0))
                """, weekStart, effectiveThrough);
        int progressions = count("select count(*) from application_event where from_stage is not null and occurred_at >= ? and occurred_at < ?", from, until);
        int outreachSent = count("select count(*) from outreach_activity where requested_at >= ? and requested_at < ?", from, until);
        int outreachResponses = count("select count(*) from outreach_activity where responded_at >= ? and responded_at < ?", from, until);
        int referrals = count("select count(*) from outreach_activity where status = 'REFERRED' and responded_at >= ? and responded_at < ?", from, until);
        int sessions = count("select count(*) from practice_session where practiced_at >= ? and practiced_at < ?", from, until);
        int minutes = count("select coalesce(sum(duration_minutes), 0) from practice_session where practiced_at >= ? and practiced_at < ?", from, until);
        int preparationTasksPlanned = count("select count(*) from preparation_item where (scheduled_for between ? and ?) or (due_date between ? and ?)",
                weekStart, effectiveThrough, weekStart, effectiveThrough);
        int preparationTasksCompleted = count("select count(*) from preparation_item where completed_at >= ? and completed_at < ?", from, until);
        int commitmentsPlanned = count("select count(*) from daily_prep_commitment where commitment_date between ? and ?", weekStart, effectiveThrough);
        int commitmentsCompleted = count("select count(*) from daily_prep_commitment where commitment_date between ? and ? and status = 'COMPLETED'", weekStart, effectiveThrough);
        int actionsPlanned = count("select count(*) from daily_priority_action where action_date between ? and ?", weekStart, effectiveThrough);
        int actionsCompleted = count("select count(*) from daily_priority_action where action_date between ? and ? and status = 'DONE'", weekStart, effectiveThrough);
        String activeStages = "('DRAFT','APPLIED','RECRUITER_SCREEN','INTERVIEWING','OFFER')";
        int activePipeline = count("select count(*) from job_application where stage in " + activeStages);
        int overdueApplications = count("select count(*) from job_application where stage in " + activeStages + " and next_action_at < ?", capturedAt);
        int overdueOutreach = count("select count(*) from outreach_activity where status in ('PLANNED','SENT','RESPONDED','REFERRED') and follow_up_at is not null and follow_up_at < ?", capturedAt);
        int stageApplied = count("select count(*) from job_application where stage = 'APPLIED'");
        int stageRecruiter = count("select count(*) from job_application where stage = 'RECRUITER_SCREEN'");
        int stageInterviewing = count("select count(*) from job_application where stage = 'INTERVIEWING'");
        int stageOffer = count("select count(*) from job_application where stage = 'OFFER'");
        return new MetricValues(openings, applications, highFit, progressions, outreachSent, outreachResponses,
                referrals, sessions, minutes, preparationTasksPlanned, preparationTasksCompleted, commitmentsPlanned, commitmentsCompleted, actionsPlanned,
                actionsCompleted, activePipeline, overdueApplications, overdueOutreach, stageApplied,
                stageRecruiter, stageInterviewing, stageOffer);
    }

    private int count(String sql, Object... args) {
        Object[] jdbcArgs = Arrays.stream(args)
                .map(value -> value instanceof Instant instant ? java.sql.Timestamp.from(instant) : value)
                .toArray();
        Number value = jdbc.queryForObject(sql, Number.class, jdbcArgs);
        return value == null ? 0 : value.intValue();
    }

    private LocalDate normalizeWeekStart(LocalDate value) {
        return value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private boolean allBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return false;
        return true;
    }

    public record WeeklyOverview(LocalDate today, MetricView livePreview, List<ReviewView> reviews) {}
    public record MonthlyProgress(LocalDate monthStart, LocalDate monthEnd, LocalDate today,
            MonthlyTotals totals, List<MonthlyDay> days) {}
    public record MonthlyTotals(int applicationsSubmitted, int outreachSent, int outreachResponses,
            int referralsSecured, int preparationTasksPlanned, int preparationTasksCompleted,
            int preparationSessions, int preparationMinutes) {}
    public record MonthlyDay(LocalDate date, int applicationsSubmitted, int outreachSent, int outreachResponses,
            int referralsSecured, int preparationTasksPlanned, int preparationTasksCompleted,
            int preparationSessions, int preparationMinutes) {}
    public record CreateResult(boolean replayed, ReviewView review) {}
    public record RevisionCommand(String wins, String challenges, String reflection, String nextWeekAdjustments,
            String nextWeekFocus) {}

    public record MetricValues(int openingsDiscovered, int applicationsSubmitted, int highFitApplications,
            int applicationProgressions, int outreachSent, int outreachResponses, int referralsSecured,
            int preparationSessions, int preparationMinutes, int preparationTasksPlanned, int preparationTasksCompleted,
            int commitmentsPlanned, int commitmentsCompleted,
            int dailyActionsPlanned, int dailyActionsCompleted, int activePipeline, int overdueApplicationActions,
            int overdueOutreachFollowUps, int stageApplied, int stageRecruiterScreen, int stageInterviewing,
            int stageOffer) {}

    public record MetricView(LocalDate weekStart, LocalDate weekEnd, LocalDate effectiveThrough,
            boolean completeWeek, Instant capturedAt, int openingsDiscovered, int applicationsSubmitted,
            int highFitApplications, int applicationProgressions, int outreachSent, int outreachResponses,
            int referralsSecured, int preparationSessions, int preparationMinutes, int preparationTasksPlanned,
            int preparationTasksCompleted, int commitmentsPlanned,
            int commitmentsCompleted, int dailyActionsPlanned, int dailyActionsCompleted, int activePipeline,
            int overdueApplicationActions, int overdueOutreachFollowUps, int stageApplied,
            int stageRecruiterScreen, int stageInterviewing, int stageOffer) {
        static MetricView from(WeeklyReview review, WeeklyMetricSnapshot snapshot) {
            return new MetricView(review.getWeekStart(), review.getWeekEnd(), review.getEffectiveThrough(),
                    review.isCompleteWeek(), snapshot.getCapturedAt(), snapshot.getOpeningsDiscovered(),
                    snapshot.getApplicationsSubmitted(), snapshot.getHighFitApplications(),
                    snapshot.getApplicationProgressions(), snapshot.getOutreachSent(), snapshot.getOutreachResponses(),
                    snapshot.getReferralsSecured(), snapshot.getPreparationSessions(), snapshot.getPreparationMinutes(),
                    snapshot.getPreparationTasksPlanned(), snapshot.getPreparationTasksCompleted(),
                    snapshot.getCommitmentsPlanned(), snapshot.getCommitmentsCompleted(), snapshot.getDailyActionsPlanned(),
                    snapshot.getDailyActionsCompleted(), snapshot.getActivePipeline(), snapshot.getOverdueApplicationActions(),
                    snapshot.getOverdueOutreachFollowUps(), snapshot.getStageApplied(), snapshot.getStageRecruiterScreen(),
                    snapshot.getStageInterviewing(), snapshot.getStageOffer());
        }
        static MetricView from(WeeklyMetricSnapshot snapshot) { return from(snapshot.getReview(), snapshot); }
        static MetricView from(LocalDate weekStart, LocalDate weekEnd, LocalDate effectiveThrough, boolean completeWeek,
                Instant capturedAt, MetricValues values) {
            return new MetricView(weekStart, weekEnd, effectiveThrough, completeWeek, capturedAt,
                    values.openingsDiscovered(), values.applicationsSubmitted(), values.highFitApplications(),
                    values.applicationProgressions(), values.outreachSent(), values.outreachResponses(),
                    values.referralsSecured(), values.preparationSessions(), values.preparationMinutes(),
                    values.preparationTasksPlanned(), values.preparationTasksCompleted(),
                    values.commitmentsPlanned(), values.commitmentsCompleted(), values.dailyActionsPlanned(),
                    values.dailyActionsCompleted(), values.activePipeline(), values.overdueApplicationActions(),
                    values.overdueOutreachFollowUps(), values.stageApplied(), values.stageRecruiterScreen(),
                    values.stageInterviewing(), values.stageOffer());
        }
    }

    public record MetricDelta(int applicationsSubmitted, int outreachSent, int outreachResponses,
            int preparationSessions, int preparationMinutes, int preparationTasksCompleted,
            int commitmentsCompleted, int dailyActionsCompleted) {
        static MetricDelta zero() { return new MetricDelta(0, 0, 0, 0, 0, 0, 0, 0); }
        static MetricDelta between(WeeklyMetricSnapshot current, WeeklyMetricSnapshot previous) {
            return new MetricDelta(current.getApplicationsSubmitted() - previous.getApplicationsSubmitted(),
                    current.getOutreachSent() - previous.getOutreachSent(),
                    current.getOutreachResponses() - previous.getOutreachResponses(),
                    current.getPreparationSessions() - previous.getPreparationSessions(),
                    current.getPreparationMinutes() - previous.getPreparationMinutes(),
                    current.getPreparationTasksCompleted() - previous.getPreparationTasksCompleted(),
                    current.getCommitmentsCompleted() - previous.getCommitmentsCompleted(),
                    current.getDailyActionsCompleted() - previous.getDailyActionsCompleted());
        }
    }

    public record ReviewView(UUID id, LocalDate weekStart, LocalDate weekEnd, LocalDate effectiveThrough,
            boolean completeWeek, Instant generatedAt, MetricView metrics, MetricDelta delta,
            List<RevisionView> revisions) {}

    public record RevisionView(UUID id, int revisionNumber, String wins, String challenges, String reflection,
            String nextWeekAdjustments, String nextWeekFocus, Instant createdAt) {
        static RevisionView from(WeeklyReviewRevision value) {
            return new RevisionView(value.getId(), value.getRevisionNumber(), value.getWins(), value.getChallenges(),
                    value.getReflection(), value.getNextWeekAdjustments(), value.getNextWeekFocus(), value.getCreatedAt());
        }
    }
}
