package dev.dhruv.jobsearch.connected;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import dev.dhruv.jobsearch.privacy.AssistanceContextMode;
import dev.dhruv.jobsearch.privacy.PrivacyPolicyService;

@SpringBootTest
@TestPropertySource(properties = {
        "app.connected.enabled=true",
        "app.connected.broker-base-url=http://127.0.0.1:8787",
        "app.connected.broker-token=test-broker-token-that-is-at-least-32-characters"
})
class TransmissionServiceTest {
    @Autowired TransmissionService transmissions;
    @Autowired PrivacyPolicyService privacy;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.update("delete from transmission_receipt");
        jdbc.update("delete from transmission_preview");
        jdbc.update("delete from privacy_cleanup_receipt");
        jdbc.update("delete from privacy_policy");
        privacy.update(new PrivacyPolicyService.UpdatePolicy(AssistanceContextMode.TIME_BOUND, 7,
                true, "v1", true));
    }

    @Test
    void bindsOneTimeConfirmationToDestinationOperationAndPayload() {
        String payload = "minimized-payload";
        var preview = transmissions.issue(TransmissionOperation.OPENAI_INBOX_STRUCTURING,
                "https://api.openai.com/v1/responses", "Structure one reviewed inbox candidate",
                List.of("companyName", "roleTitle", "description"), payload);

        assertThat(preview.confirmationToken()).isNotBlank();
        assertThat(preview.minimizedFields()).containsExactly("companyName", "description", "roleTitle");
        assertThatThrownBy(() -> transmissions.consume(preview.confirmationToken(),
                TransmissionOperation.OPENAI_INBOX_STRUCTURING, "https://api.openai.com/v1/responses", "changed"))
                .hasMessageContaining("changed after preview");

        var consumption = transmissions.consume(preview.confirmationToken(),
                TransmissionOperation.OPENAI_INBOX_STRUCTURING, "https://api.openai.com/v1/responses", payload);
        var receipt = transmissions.record(consumption, TransmissionOutcome.SUCCESS);
        assertThat(receipt.destination()).isEqualTo("https://api.openai.com/v1/responses");
        assertThat(receipt.payloadHash()).isEqualTo(TransmissionService.sha256(payload));
        assertThat(receipt.outcome()).isEqualTo("SUCCESS");
        assertThat(transmissions.recentReceipts()).hasSize(1);

        assertThatThrownBy(() -> transmissions.consume(preview.confirmationToken(),
                TransmissionOperation.OPENAI_INBOX_STRUCTURING, "https://api.openai.com/v1/responses", payload))
                .hasMessageContaining("already been used");
    }

    @Test
    void acceptedConnectedPolicyCanPreviewInEveryRetentionMode() {
        for (AssistanceContextMode mode : AssistanceContextMode.values()) {
            privacy.update(new PrivacyPolicyService.UpdatePolicy(mode,
                    mode == AssistanceContextMode.TIME_BOUND ? 7 : null, true, "v1", true));
            var preview = transmissions.issue(TransmissionOperation.OPENAI_WEEKLY_REFLECTION,
                    "https://api.openai.com/v1/responses", "Draft one reviewed reflection",
                    List.of("metrics"), "payload-" + mode);
            assertThat(preview.confirmationToken()).isNotBlank();
        }
    }

    @Test
    void expiredUnconsumedPreviewRemainsAsAuditMetadataWhenAnotherPreviewIsIssued() {
        String payload = "first-payload";
        var expired = transmissions.issue(TransmissionOperation.OPENAI_INBOX_STRUCTURING,
                "https://api.openai.com/v1/responses", "Structure one reviewed inbox candidate",
                List.of("description"), payload);
        jdbc.update("update transmission_preview set expires_at=? where id=?",
                Timestamp.from(Instant.now().minusSeconds(1)), expired.id());

        transmissions.issue(TransmissionOperation.OPENAI_WEEKLY_REFLECTION,
                "https://api.openai.com/v1/responses", "Draft one reviewed reflection",
                List.of("metrics"), "second-payload");

        assertThat(jdbc.queryForObject("select count(*) from transmission_preview where id=?",
                Integer.class, expired.id())).isOne();
        assertThatThrownBy(() -> transmissions.consume(expired.confirmationToken(),
                TransmissionOperation.OPENAI_INBOX_STRUCTURING,
                "https://api.openai.com/v1/responses", payload)).hasMessageContaining("expired");
        assertThat(jdbc.queryForObject("select count(*) from transmission_preview where id=?",
                Integer.class, expired.id())).isOne();
    }
}
