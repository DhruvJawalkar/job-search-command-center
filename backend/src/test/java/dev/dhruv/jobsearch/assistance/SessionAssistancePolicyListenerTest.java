package dev.dhruv.jobsearch.assistance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.dhruv.jobsearch.privacy.AssistanceContextMode;
import dev.dhruv.jobsearch.privacy.PrivacyPolicyService;

class SessionAssistancePolicyListenerTest {

    @Test
    void leavingSessionOnlyClearsAllProcessLocalAssistanceContext() {
        SessionAssistanceStore store = new SessionAssistanceStore(Duration.ofHours(8), 4,
                Clock.fixed(Instant.parse("2026-09-18T08:00:00Z"), ZoneOffset.UTC));
        AssistanceRun run = new AssistanceRun(AssistanceUseCase.INBOX_STRUCTURING, UUID.randomUUID(), "input-hash",
                "provider", "model", "prompt", "schema");
        store.put(run);

        new SessionAssistancePolicyListener(store).policyModeChanged(
                new PrivacyPolicyService.AssistanceContextModeChanged(
                        AssistanceContextMode.SESSION_ONLY, AssistanceContextMode.STATELESS));

        assertThat(store.size()).isZero();
    }
}
