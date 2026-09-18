package dev.dhruv.jobsearch.assistance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class StatelessAssistanceArtifactServiceTest {

    @Test
    void signedArtifactIsBoundToTargetAndInputAndExpiresAtTheBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T08:00:00Z"));
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        var service = new StatelessAssistanceArtifactService(new ObjectMapper(), Duration.ofMinutes(5), clock, key);
        UUID target = UUID.randomUUID();
        String token = service.issue(AssistanceUseCase.INBOX_STRUCTURING, target, "input-hash", "{\"value\":1}");

        var verified = service.verify(token, AssistanceUseCase.INBOX_STRUCTURING, target, "input-hash");
        assertThat(verified.resultPayload()).isEqualTo("{\"value\":1}");
        assertThatThrownBy(() -> service.verify(token, AssistanceUseCase.INBOX_STRUCTURING, UUID.randomUUID(),
                "input-hash")).hasMessageContaining("invalid");
        assertThatThrownBy(() -> service.verify(token + "x", AssistanceUseCase.INBOX_STRUCTURING, target,
                "input-hash")).hasMessageContaining("invalid");

        clock.at(Instant.parse("2026-09-18T08:05:00Z"));
        assertThatThrownBy(() -> service.verify(token, AssistanceUseCase.INBOX_STRUCTURING, target, "input-hash"))
                .hasMessageContaining("expired");
    }

    @Test
    void artifactCannotBeUsedAfterAServiceRestartChangesTheProcessKey() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T08:00:00Z"));
        UUID target = UUID.randomUUID();
        var first = new StatelessAssistanceArtifactService(new ObjectMapper(), Duration.ofMinutes(5), clock,
                new byte[32]);
        byte[] replacementKey = new byte[32];
        Arrays.fill(replacementKey, (byte) 1);
        var restarted = new StatelessAssistanceArtifactService(new ObjectMapper(), Duration.ofMinutes(5), clock,
                replacementKey);
        String token = first.issue(AssistanceUseCase.INBOX_STRUCTURING, target, "hash", "{}");

        assertThatThrownBy(() -> restarted.verify(token, AssistanceUseCase.INBOX_STRUCTURING, target, "hash"))
                .hasMessageContaining("invalid");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void at(Instant value) { instant = value; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
