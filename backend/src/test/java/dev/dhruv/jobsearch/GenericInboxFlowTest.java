package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.ingestion.GenericInboxCandidateStatus;
import dev.dhruv.jobsearch.ingestion.GenericInboxItemStatus;
import dev.dhruv.jobsearch.ingestion.GenericInboxService;
import dev.dhruv.jobsearch.ingestion.GenericInboxSourceType;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;

@SpringBootTest
@Transactional
class GenericInboxFlowTest {

    @Autowired GenericInboxService inbox;
    @Autowired OpportunityService opportunities;

    @Test
    void parsesReviewsAndImportsJsonWithoutPublishingBeforeApproval() {
        String json = """
                {"jobs":[{"company":"Inbox Test Co","title":"Senior Platform Engineer","location":"Hyderabad",
                "workMode":"Hybrid","url":"https://inbox-test.example/jobs/one","description":"Build reliable services"}]}
                """;

        var received = inbox.receive(new GenericInboxService.CreateCommand(
                GenericInboxSourceType.PASTED_JSON, "Manual research", null, "application/json", json));

        assertThat(received.replayed()).isFalse();
        assertThat(received.item().status()).isEqualTo(GenericInboxItemStatus.NEEDS_REVIEW);
        assertThat(received.item().candidates()).hasSize(1);
        assertThat(opportunities.list()).noneMatch(value -> value.getCompanyName().equals("Inbox Test Co"));

        var candidate = received.item().candidates().getFirst();
        assertThat(candidate.workMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(candidate.parseWarnings()).isNull();

        var imported = inbox.importCandidate(candidate.id());
        assertThat(imported.status()).isEqualTo(GenericInboxCandidateStatus.IMPORTED);
        assertThat(imported.opportunityId()).isNotNull();
        assertThat(opportunities.get(imported.opportunityId()).getDescription()).isEqualTo("Build reliable services");
        assertThat(inbox.list().getFirst().status()).isEqualTo(GenericInboxItemStatus.IMPORTED);
    }

    @Test
    void replaysIdenticalContentAndParsesQuotedCsvRows() {
        String csv = "company,title,location,work_mode,url,description\n"
                + "\"Replay Test Co\",\"Senior Engineer, Data\",Remote,remote,https://inbox-test.example/jobs/two,\"Spark, Kafka\"\n";

        var first = inbox.receive(new GenericInboxService.CreateCommand(
                GenericInboxSourceType.UPLOADED_CSV, "CSV export", "jobs.csv", "text/csv", csv));
        var replay = inbox.receive(new GenericInboxService.CreateCommand(
                GenericInboxSourceType.UPLOADED_CSV, "CSV export again", "jobs-copy.csv", "text/csv", csv));

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.item().id()).isEqualTo(first.item().id());
        assertThat(first.item().candidates().getFirst().roleTitle()).isEqualTo("Senior Engineer, Data");
        assertThat(first.item().candidates().getFirst().description()).isEqualTo("Spark, Kafka");
    }

    @Test
    void keepsUnstructuredTextReviewableUntilRequiredFieldsAreSupplied() {
        String text = "A promising platform role copied from a conversation without structured labels.";
        var received = inbox.receive(new GenericInboxService.CreateCommand(
                GenericInboxSourceType.PASTED_TEXT, "Conversation notes", null, "text/plain", text));
        var candidate = received.item().candidates().getFirst();

        assertThat(candidate.parseWarnings()).contains("Company and role title");

        var revised = inbox.revise(candidate.id(), new GenericInboxService.ReviseCandidate(
                "Reviewed Co", "Backend Engineer", "Remote", WorkMode.REMOTE, "Conversation", null, null, text));
        assertThat(revised.parseWarnings()).isNull();

        var rejected = inbox.rejectCandidate(candidate.id());
        assertThat(rejected.status()).isEqualTo(GenericInboxCandidateStatus.REJECTED);
        assertThat(inbox.list().getFirst().status()).isEqualTo(GenericInboxItemStatus.REJECTED);
    }
}
