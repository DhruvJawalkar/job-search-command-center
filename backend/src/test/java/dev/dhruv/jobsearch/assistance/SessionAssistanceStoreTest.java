package dev.dhruv.jobsearch.assistance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SessionAssistanceStoreTest {

    @Test
    void expiresAtTheExactBoundaryAndAReplacementStoreStartsEmpty() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T08:00:00Z"));
        SessionAssistanceStore store = new SessionAssistanceStore(Duration.ofHours(8), 4, clock);
        AssistanceRun run = run("first");
        store.put(run);

        clock.at(Instant.parse("2026-09-18T15:59:59.999Z"));
        assertThat(store.find(run.getId())).isPresent();

        clock.at(Instant.parse("2026-09-18T16:00:00Z"));
        assertThat(store.find(run.getId())).isEmpty();
        assertThat(new SessionAssistanceStore(Duration.ofHours(8), 4, clock).size()).isZero();
    }

    @Test
    void evictsTheOldestEntryWhenTheBoundIsReachedAndClearIsIdempotent() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T08:00:00Z"));
        SessionAssistanceStore store = new SessionAssistanceStore(Duration.ofHours(8), 2, clock);
        AssistanceRun first = run("first");
        AssistanceRun second = run("second");
        AssistanceRun third = run("third");

        store.put(first);
        clock.at(clock.instant().plusSeconds(1));
        store.put(second);
        clock.at(clock.instant().plusSeconds(1));
        store.put(third);

        assertThat(store.find(first.getId())).isEmpty();
        assertThat(store.find(second.getId())).isPresent();
        assertThat(store.find(third.getId())).isPresent();
        store.clear();
        store.clear();
        assertThat(store.size()).isZero();
    }

    private AssistanceRun run(String input) {
        return new AssistanceRun(AssistanceUseCase.INBOX_STRUCTURING, UUID.randomUUID(), input,
                "provider", "model", "prompt", "schema");
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
