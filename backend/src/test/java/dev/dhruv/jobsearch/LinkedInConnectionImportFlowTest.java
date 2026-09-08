package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.contact.LinkedInConnectionImportService;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;

@SpringBootTest
@Transactional
class LinkedInConnectionImportFlowTest {
    private static final Path EXPORT = createExport();

    @DynamicPropertySource
    static void linkedinExport(DynamicPropertyRegistry registry) {
        registry.add("app.imports.linkedin-connections.file", () -> EXPORT.toString());
    }

    @Autowired LinkedInConnectionImportService importer;
    @Autowired OpportunityService opportunities;

    @Test
    void importsOfficialConnectionsIdempotentlyAndConvertsCompanyMatchesToCandidates() {
        var first = importer.importConnections();
        assertThat(first.rowsSeen()).isEqualTo(2);
        assertThat(first.connectionsCreated()).isEqualTo(2);
        assertThat(first.replayed()).isFalse();

        var replay = importer.importConnections();
        assertThat(replay.replayed()).isTrue();
        assertThat(importer.overview().totalConnections()).isEqualTo(2);

        var opportunity = opportunities.create(new OpportunityService.CreateOpportunity(
                "Databricks", "Senior Software Engineer", "Bengaluru", WorkMode.HYBRID,
                "Test", "https://example.com/databricks", "Build cloud infrastructure.", Instant.now()));
        var matches = importer.matches(opportunity.getId());
        assertThat(matches).extracting(LinkedInConnectionImportService.ConnectionView::fullName)
                .containsExactly("Ada Lovelace");

        var candidate = importer.saveAsCandidate(matches.getFirst().id(), opportunity.getId());
        assertThat(candidate.getConnectionDegree().name()).isEqualTo("FIRST");
        assertThat(candidate.isCurrentCompanyMatch()).isTrue();
        assertThat(importer.saveAsCandidate(matches.getFirst().id(), opportunity.getId()).getId())
                .isEqualTo(candidate.getId());
    }

    private static Path createExport() {
        try {
            Path file = Files.createTempFile("linkedin-connections-", ".csv");
            Files.writeString(file, "Notes:\r\n\"LinkedIn export note\"\r\n\r\n"
                    + "First Name,Last Name,URL,Email Address,Company,Position,Connected On\r\n"
                    + "Ada,Lovelace,https://www.linkedin.com/in/ada,,Databricks,Staff Software Engineer,25 Aug 2026\r\n"
                    + "Grace,Hopper,https://www.linkedin.com/in/grace,,Microsoft,Engineering Leader,24 Aug 2026\r\n");
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
