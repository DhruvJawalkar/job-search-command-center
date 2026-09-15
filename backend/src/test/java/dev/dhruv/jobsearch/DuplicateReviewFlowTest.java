package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.ingestion.DuplicateMatchType;
import dev.dhruv.jobsearch.ingestion.DuplicateResolution;
import dev.dhruv.jobsearch.ingestion.DuplicateReviewService;
import dev.dhruv.jobsearch.ingestion.GenericInboxCandidateStatus;
import dev.dhruv.jobsearch.ingestion.GenericInboxService;
import dev.dhruv.jobsearch.ingestion.GenericInboxSourceType;
import dev.dhruv.jobsearch.opportunity.OpportunityMergeField;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;

@SpringBootTest
@Transactional
class DuplicateReviewFlowTest {

    @Autowired GenericInboxService inbox;
    @Autowired DuplicateReviewService duplicates;
    @Autowired OpportunityService opportunities;

    @Test
    void detectsAnExactCanonicalUrlAndLinksWithoutCreatingAnotherOpening() {
        var existing = opportunities.create(new OpportunityService.CreateOpportunity(
                "Exact Match Co", "Senior Backend Engineer", "Hyderabad", WorkMode.HYBRID,
                "Careers", "https://exact-match.example/jobs/42?tracking=abc", "Existing description", Instant.now()));
        long before = opportunities.list().size();
        var item = inbox.receive(new GenericInboxService.CreateCommand(GenericInboxSourceType.PASTED_JSON,
                "Research", null, "application/json", """
                {"company":"Exact Match Co","title":"Senior Backend Engineer","url":"https://exact-match.example/jobs/42"}
                """)).item();

        var report = duplicates.scanItem(item.id());
        var reviewed = inbox.get(item.id()).candidates().getFirst();

        assertThat(report.candidatesFlagged()).isEqualTo(1);
        assertThat(reviewed.status()).isEqualTo(GenericInboxCandidateStatus.DUPLICATE_REVIEW);
        assertThat(reviewed.duplicateMatches()).singleElement()
                .satisfies(match -> {
                    assertThat(match.opportunityId()).isEqualTo(existing.getId());
                    assertThat(match.matchType()).isEqualTo(DuplicateMatchType.EXACT_URL);
                    assertThat(match.confidence()).isEqualTo(100);
                });

        var match = reviewed.duplicateMatches().getFirst();
        var result = duplicates.resolve(reviewed.id(), new DuplicateReviewService.ResolveCommand(
                DuplicateResolution.LINK_EXISTING, match.id(), Set.of()));

        assertThat(result.status()).isEqualTo(GenericInboxCandidateStatus.LINKED);
        assertThat(result.opportunityId()).isEqualTo(existing.getId());
        assertThat(opportunities.list()).hasSize((int) before);
    }

    @Test
    void detectsExternalIdsAndMergesOnlyTheSelectedFields() {
        var existing = opportunities.create(new OpportunityService.CreateOpportunity(
                "External Match Co", "Staff Platform Engineer", "Bengaluru", WorkMode.ONSITE,
                "Greenhouse", "https://external-match.example/jobs/original", "Original description", Instant.now()));
        opportunities.recordSourceExternalId(existing.getId(), "REQ-9001");
        var item = inbox.receive(new GenericInboxService.CreateCommand(GenericInboxSourceType.PASTED_JSON,
                "Greenhouse", null, "application/json", """
                {"company":"External Match Co","title":"Staff Platform Engineer","location":"Remote",
                 "source":"Greenhouse","jobId":"REQ-9001","url":"https://external-match.example/jobs/new",
                 "description":"Reviewed, more complete description"}
                """)).item();
        duplicates.scanItem(item.id());
        var candidate = inbox.get(item.id()).candidates().getFirst();
        var match = candidate.duplicateMatches().getFirst();

        assertThat(match.matchType()).isEqualTo(DuplicateMatchType.EXACT_EXTERNAL_ID);

        var result = duplicates.resolve(candidate.id(), new DuplicateReviewService.ResolveCommand(
                DuplicateResolution.MERGE_SELECTED_FIELDS, match.id(),
                Set.of(OpportunityMergeField.LOCATION, OpportunityMergeField.DESCRIPTION)));
        var merged = opportunities.get(result.opportunityId());

        assertThat(result.status()).isEqualTo(GenericInboxCandidateStatus.MERGED);
        assertThat(merged.getLocation()).isEqualTo("Remote");
        assertThat(merged.getDescription()).isEqualTo("Reviewed, more complete description");
        assertThat(merged.getSourceUrl()).isEqualTo("https://external-match.example/jobs/original");
    }

    @Test
    void explainsLikelySimilarityAndAllowsAReviewedSeparateOpening() {
        opportunities.create(new OpportunityService.CreateOpportunity(
                "Similarity Labs Ltd", "Senior Software Engineer Data Platform", "Bengaluru", WorkMode.HYBRID,
                "Existing", "https://similarity.example/jobs/one", "First team", Instant.now()));
        long before = opportunities.list().size();
        var item = inbox.receive(new GenericInboxService.CreateCommand(GenericInboxSourceType.PASTED_JSON,
                "Research", null, "application/json", """
                {"company":"Similarity Labs","title":"Senior Software Engineer - Data Platform","location":"Bengaluru",
                 "url":"https://similarity.example/jobs/two","description":"A distinct team opening"}
                """)).item();
        duplicates.scanItem(item.id());
        var candidate = inbox.get(item.id()).candidates().getFirst();

        assertThat(candidate.duplicateMatches()).singleElement()
                .satisfies(match -> {
                    assertThat(match.matchType()).isEqualTo(DuplicateMatchType.LIKELY_SIMILAR);
                    assertThat(match.explanation()).contains("Company similarity", "title similarity", "location similarity");
                });

        var result = duplicates.resolve(candidate.id(), new DuplicateReviewService.ResolveCommand(
                DuplicateResolution.CREATE_SEPARATE, null, Set.of()));

        assertThat(result.status()).isEqualTo(GenericInboxCandidateStatus.IMPORTED);
        assertThat(opportunities.list()).hasSize((int) before + 1);
    }

    @Test
    void allowsASeparateOpeningWhenTheIncomingExternalIdIsAlreadyOwned() {
        var existing = opportunities.create(new OpportunityService.CreateOpportunity(
                "Shared Requisition Co", "Senior Engineer Platform", "Hyderabad", WorkMode.HYBRID,
                "Workday", "https://shared-requisition.example/original", "Original role", Instant.now()));
        opportunities.recordSourceExternalId(existing.getId(), "REQ-SHARED");
        long before = opportunities.list().size();
        var item = inbox.receive(new GenericInboxService.CreateCommand(GenericInboxSourceType.PASTED_JSON,
                "Workday", null, "application/json", """
                {"company":"Shared Requisition Co","title":"Senior Engineer Platform - New Team",
                 "source":"Workday","jobId":"REQ-SHARED","url":"https://shared-requisition.example/new"}
                """)).item();
        duplicates.scanItem(item.id());
        var candidate = inbox.get(item.id()).candidates().getFirst();

        var result = duplicates.resolve(candidate.id(), new DuplicateReviewService.ResolveCommand(
                DuplicateResolution.CREATE_SEPARATE, null, Set.of()));

        assertThat(result.status()).isEqualTo(GenericInboxCandidateStatus.IMPORTED);
        assertThat(opportunities.list()).hasSize((int) before + 1);
        assertThat(opportunities.get(result.opportunityId()).getSourceExternalId()).isNull();
    }
}
