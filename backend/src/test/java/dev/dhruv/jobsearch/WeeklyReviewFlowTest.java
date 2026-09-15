package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.review.WeeklyReviewService;

@SpringBootTest
@Transactional
class WeeklyReviewFlowTest {

    @Autowired WeeklyReviewService reviews;
    @Autowired OpportunityService opportunities;

    @Test
    void generatesOneImmutableSnapshotPerNormalizedWeek() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        var created = reviews.generate(today);
        int openingsAtSnapshot = created.review().metrics().openingsDiscovered();

        opportunities.create(new OpportunityService.CreateOpportunity("Snapshot Proof Co", "Platform Engineer",
                "Hyderabad", WorkMode.HYBRID, "Test", "https://weekly-review.example/immutable",
                "Created after the weekly snapshot.", Instant.now()));

        var replayed = reviews.generate(today);

        assertThat(created.replayed()).isFalse();
        assertThat(created.review().weekStart()).isEqualTo(monday);
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.review().id()).isEqualTo(created.review().id());
        assertThat(replayed.review().metrics().openingsDiscovered()).isEqualTo(openingsAtSnapshot);
    }

    @Test
    void keepsNarrativeChangesAsAppendOnlyRevisions() {
        var generated = reviews.generate(LocalDate.now().minusWeeks(2));
        var first = reviews.addRevision(generated.review().id(), new WeeklyReviewService.RevisionCommand(
                "Completed focused applications", null, "Quality stayed high", "Increase referral follow-up", "Referrals"));
        var second = reviews.addRevision(generated.review().id(), new WeeklyReviewService.RevisionCommand(
                null, "Preparation slipped", "A second considered reflection", "Time-box resume tailoring", "Preparation"));

        assertThat(first.revisions()).hasSize(1);
        assertThat(second.revisions()).hasSize(2);
        assertThat(second.revisions().getFirst().revisionNumber()).isEqualTo(2);
        assertThat(second.revisions().get(1).wins()).isEqualTo("Completed focused applications");
        assertThat(second.metrics()).isEqualTo(first.metrics());
    }
}
