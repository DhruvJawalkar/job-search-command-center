package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dev.dhruv.jobsearch.assistance.AssistanceRunStatus;
import dev.dhruv.jobsearch.assistance.AssistanceService;
import dev.dhruv.jobsearch.ingestion.GenericInboxService;
import dev.dhruv.jobsearch.ingestion.GenericInboxSourceType;
import dev.dhruv.jobsearch.privacy.AssistanceContextMode;
import dev.dhruv.jobsearch.privacy.PrivacyPolicyService;
import dev.dhruv.jobsearch.review.WeeklyReviewService;
import dev.dhruv.jobsearch.skill.SkillEvidenceService;

@SpringBootTest
class AssistanceFlowTest {

    private static final HttpServer PROVIDER = provider();

    @DynamicPropertySource
    static void assistanceProperties(DynamicPropertyRegistry registry) {
        registry.add("app.connected.enabled", () -> "true");
        registry.add("app.connected.broker-base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
        registry.add("app.connected.broker-token", () -> "test-broker-token-that-is-at-least-32-characters");
        registry.add("app.assistance.openai.model", () -> "test-model");
    }

    @Autowired AssistanceService assistance;
    @Autowired GenericInboxService inbox;
    @Autowired SkillEvidenceService skills;
    @Autowired WeeklyReviewService weeklyReviews;
    @Autowired PrivacyPolicyService privacyPolicy;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void enableConnectedAssistance() {
        privacyPolicy.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.TIME_BOUND, 7,
                true, "v1", true));
    }

    @AfterAll
    static void stopProvider() { PROVIDER.stop(0); }

    @Test
    void structuresOnlyAfterConfirmationAndKeepsFieldsSkillsAndWeeklyNarrativeReviewControlled() {
        skills.seedReviewedBacklog();
        var received = inbox.receive(new GenericInboxService.CreateCommand(GenericInboxSourceType.PASTED_TEXT,
                "Test source", null, "text/plain",
                "Acme is hiring a Senior Backend Engineer in Hyderabad. Hybrid. Requires Java and Spring Boot."));
        var candidate = received.item().candidates().getFirst();

        var generated = assistance.generateInbox(candidate.id(), inboxToken(candidate.id()));
        assertThat(generated.run().status()).isEqualTo(AssistanceRunStatus.COMPLETED);
        assertThat(generated.run().suggestion().fields().companyName()).isEqualTo("Acme");
        assertThat(assistance.generateInbox(candidate.id(), "replay-does-not-transmit").replayed()).isTrue();

        assistance.applyFields(generated.run().id(), Set.of("companyName", "roleTitle", "location", "workMode", "description"));
        var updated = inbox.get(received.item().id()).candidates().getFirst();
        assertThat(updated.companyName()).isEqualTo("Acme");
        assertThat(updated.roleTitle()).isEqualTo("Senior Backend Engineer");

        inbox.importCandidate(candidate.id());
        var published = assistance.publishSkills(generated.run().id());
        assertThat(published.publishedCount()).isEqualTo(2);
        assertThat(published.unmatchedSkills()).isEmpty();
        assertThat(skills.overview().proposedCount()).isGreaterThanOrEqualTo(2);

        var weekly = weeklyReviews.generate(LocalDate.now().minusWeeks(5)).review();
        var draft = assistance.generateWeekly(weekly.id(), weeklyToken(weekly.id()));
        assertThat(draft.run().status()).isEqualTo(AssistanceRunStatus.COMPLETED);
        assertThat(draft.run().draft().nextWeekFocus()).contains("follow-ups");
        assertThat(weeklyReviews.get(weekly.id()).revisions()).isEmpty();
    }

    @Test
    void statelessResultsAreResponseOnlyAndNeverWrittenToTheDatabase() {
        privacyPolicy.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.STATELESS, null,
                true, "v1", true));
        var received = inbox.receive(new GenericInboxService.CreateCommand(GenericInboxSourceType.PASTED_TEXT,
                "Stateless source", null, "text/plain",
                "Acme is hiring a Senior Backend Engineer in Hyderabad. Hybrid. Requires Java and Spring Boot. Stateless case."));
        var candidate = received.item().candidates().getFirst();
        int before = jdbc.queryForObject("select count(*) from assistance_run", Integer.class);
        int beforeDecisions = jdbc.queryForObject("select count(*) from assistance_decision", Integer.class);

        var generated = assistance.generateInbox(candidate.id(), inboxToken(candidate.id()));

        assertThat(generated.replayed()).isFalse();
        assertThat(generated.run().status()).isEqualTo(AssistanceRunStatus.COMPLETED);
        assertThat(generated.run().statelessSaveArtifact()).isNotBlank();
        assertThat(jdbc.queryForObject("select count(*) from assistance_run", Integer.class)).isEqualTo(before);
        assertThat(assistance.inboxOverview(candidate.id()).runs()).isEmpty();
        var saved = assistance.applyStatelessFields(candidate.id(), generated.run().statelessSaveArtifact(),
                Set.of("companyName", "roleTitle"));
        assertThat(saved.appliedFields()).containsExactly("companyName", "roleTitle");
        assertThat(jdbc.queryForObject("select count(*) from assistance_decision", Integer.class))
                .isEqualTo(beforeDecisions);
        assertThatThrownBy(() -> assistance.applyFields(generated.run().id(), Set.of("companyName")))
                .hasMessageContaining("response-only");
    }

    @Test
    void statelessSaveArtifactRejectsAChangedStructuredCandidateContext() {
        privacyPolicy.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.STATELESS, null,
                true, "v1", true));
        var received = inbox.receive(new GenericInboxService.CreateCommand(GenericInboxSourceType.PASTED_TEXT,
                "Stateless binding source", null, "text/plain",
                "Acme is hiring a Senior Backend Engineer in Hyderabad. Hybrid. Requires Java and Spring Boot. Stateless binding case."));
        var candidate = received.item().candidates().getFirst();
        var generated = assistance.generateInbox(candidate.id(), inboxToken(candidate.id()));

        inbox.revise(candidate.id(), new GenericInboxService.ReviseCandidate(
                "Changed Company", candidate.roleTitle(), candidate.location(), candidate.workMode(),
                candidate.sourceName(), candidate.sourceExternalId(), candidate.sourceUrl(), candidate.description()));

        assertThatThrownBy(() -> assistance.applyStatelessFields(candidate.id(),
                generated.run().statelessSaveArtifact(), Set.of("roleTitle")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("artifact is invalid");
    }

    @Test
    void sessionResultsReplayAndSupportExplicitSaveWithoutDurableAssistantContext() {
        privacyPolicy.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.SESSION_ONLY, null,
                true, "v1", true));
        var received = inbox.receive(new GenericInboxService.CreateCommand(GenericInboxSourceType.PASTED_TEXT,
                "Session source", null, "text/plain",
                "Acme is hiring a Senior Backend Engineer in Hyderabad. Hybrid. Requires Java and Spring Boot. Session case."));
        var candidate = received.item().candidates().getFirst();
        int beforeRuns = jdbc.queryForObject("select count(*) from assistance_run", Integer.class);
        int beforeDecisions = jdbc.queryForObject("select count(*) from assistance_decision", Integer.class);

        String token = inboxToken(candidate.id());
        var generated = assistance.generateInbox(candidate.id(), token);
        assertThat(assistance.generateInbox(candidate.id(), "replay-does-not-transmit").replayed()).isTrue();
        assistance.applyFields(generated.run().id(), Set.of("companyName", "roleTitle"));

        assertThat(inbox.get(received.item().id()).candidates().getFirst().companyName()).isEqualTo("Acme");
        assertThat(assistance.inboxOverview(candidate.id()).runs()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from assistance_run", Integer.class)).isEqualTo(beforeRuns);
        assertThat(jdbc.queryForObject("select count(*) from assistance_decision", Integer.class))
                .isEqualTo(beforeDecisions);

        privacyPolicy.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.STATELESS, null,
                true, "v1", true));
        privacyPolicy.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.SESSION_ONLY, null,
                true, "v1", true));
        assertThat(assistance.inboxOverview(candidate.id()).runs()).isEmpty();
    }

    private String inboxToken(java.util.UUID candidateId) {
        return assistance.previewInboxTransmission(candidateId).confirmationToken();
    }

    private String weeklyToken(java.util.UUID reviewId) {
        return assistance.previewWeeklyTransmission(reviewId).confirmationToken();
    }

    private static HttpServer provider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/openai/v1/responses", AssistanceFlowTest::respond);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String output = request.contains("weekly_reflection_draft") ? WEEKLY_OUTPUT : INBOX_OUTPUT;
        String response = "{\"id\":\"resp-test\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":"
                + quote(output) + "}]}],\"usage\":{\"input_tokens\":120,\"output_tokens\":80}}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }

    private static final String INBOX_OUTPUT = """
            {"fields":{"companyName":"Acme","roleTitle":"Senior Backend Engineer","location":"Hyderabad","workMode":"HYBRID","sourceName":"Test source","sourceExternalId":null,"sourceUrl":null,"description":"Acme is hiring a Senior Backend Engineer in Hyderabad. Hybrid. Requires Java and Spring Boot."},"responsibilities":[],"requiredQualifications":["Java","Spring Boot"],"preferredQualifications":[],"skills":[{"name":"Java","strength":"REQUIRED","evidenceSnippet":"Requires Java"},{"name":"Spring Boot","strength":"REQUIRED","evidenceSnippet":"Spring Boot"}],"warnings":[],"reviewQuestions":[]}
            """;

    private static final String WEEKLY_OUTPUT = """
            {"wins":"Applications and outreach were recorded.","challenges":"Some follow-ups are overdue.","reflection":"The preserved metrics show consistent application activity.","nextWeekAdjustments":"Prioritize overdue actions before adding lower-fit work.","nextWeekFocus":"Close overdue application and outreach follow-ups.","evidence":["Immutable application count","Overdue follow-up counts"]}
            """;
}
