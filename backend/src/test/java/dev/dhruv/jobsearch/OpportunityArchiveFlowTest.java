package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.OpportunityStatus;
import dev.dhruv.jobsearch.opportunity.WorkMode;

@SpringBootTest
@Transactional
class OpportunityArchiveFlowTest {

    @Autowired
    private OpportunityService opportunityService;

    @Test
    void archivesWithAnExplanationAndRestoresThePriorDecision() {
        JobOpportunity opportunity = opportunityService.create(new OpportunityService.CreateOpportunity(
                "Archive Test", "Platform Engineer", "Hyderabad", WorkMode.HYBRID,
                "Test", "https://example.com/jobs/archive-test", "Build reliable systems", Instant.now()));
        opportunityService.decide(opportunity.getId(), OpportunityStatus.SHORTLISTED, 91, "Strong fit");

        JobOpportunity archived = opportunityService.archive(
                opportunity.getId(), "  Role scope is too narrow right now.  ");

        assertThat(archived.getStatus()).isEqualTo(OpportunityStatus.ARCHIVED);
        assertThat(archived.getArchivedFromStatus()).isEqualTo(OpportunityStatus.SHORTLISTED);
        assertThat(archived.getArchiveReason()).isEqualTo("Role scope is too narrow right now.");
        assertThat(archived.getArchivedAt()).isNotNull();

        archived.refreshFromImport("Archive Test", "Platform Engineer", "Hyderabad", WorkMode.HYBRID,
                "Test", "https://example.com/jobs/archive-test", "Updated description", 88, "Updated fit",
                OpportunityStatus.REVIEWING);
        assertThat(archived.getStatus()).isEqualTo(OpportunityStatus.ARCHIVED);

        JobOpportunity restored = opportunityService.restore(opportunity.getId());
        assertThat(restored.getStatus()).isEqualTo(OpportunityStatus.SHORTLISTED);
        assertThat(restored.getArchiveReason()).isNull();
        assertThat(restored.getArchivedAt()).isNull();
        assertThat(restored.getArchivedFromStatus()).isNull();
    }

    @Test
    void requiresAnArchiveExplanation() {
        JobOpportunity opportunity = opportunityService.create(new OpportunityService.CreateOpportunity(
                "Reason Test", "Security Engineer", "Remote", WorkMode.REMOTE,
                "Test", "https://example.com/jobs/archive-reason", null, Instant.now()));

        assertThatThrownBy(() -> opportunityService.archive(opportunity.getId(), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An archive reason is required.");
    }
}
