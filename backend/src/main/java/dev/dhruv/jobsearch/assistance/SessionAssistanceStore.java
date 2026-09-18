package dev.dhruv.jobsearch.assistance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Process-local assistance context for SESSION_ONLY mode. Nothing in this store
 * is serialised, and a service restart necessarily clears it.
 */
@Component
class SessionAssistanceStore {

    private final Duration ttl;
    private final int maximumEntries;
    private final Clock clock;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    @Autowired
    SessionAssistanceStore(@Value("${app.privacy.session-context.ttl:PT8H}") Duration ttl,
            @Value("${app.privacy.session-context.maximum-entries:128}") int maximumEntries) {
        this(ttl, maximumEntries, Clock.systemUTC());
    }

    SessionAssistanceStore(Duration ttl, int maximumEntries, Clock clock) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Session assistance TTL must be positive.");
        }
        if (maximumEntries < 1 || maximumEntries > 1024) {
            throw new IllegalArgumentException("Session assistance capacity must be between 1 and 1024.");
        }
        this.ttl = ttl;
        this.maximumEntries = maximumEntries;
        this.clock = clock;
    }

    synchronized void put(AssistanceRun run) {
        purgeExpired();
        entries.put(run.getId(), new Entry(run, clock.instant(), new ArrayList<>()));
        while (entries.size() > maximumEntries) {
            entries.remove(entries.keySet().iterator().next());
        }
    }

    synchronized Optional<AssistanceRun> find(UUID id) {
        purgeExpired();
        Entry entry = entries.get(id);
        return entry == null ? Optional.empty() : Optional.of(entry.run());
    }

    synchronized Optional<AssistanceRun> findReplay(AssistanceUseCase useCase, UUID targetId, String inputHash,
            String provider, String model, String promptVersion, String schemaVersion) {
        purgeExpired();
        return entries.values().stream().map(Entry::run)
                .filter(run -> run.getUseCase() == useCase && run.getTargetId().equals(targetId)
                        && run.getInputHash().equals(inputHash) && run.getProvider().equals(provider)
                        && run.getModel().equals(model) && run.getPromptVersion().equals(promptVersion)
                        && run.getSchemaVersion().equals(schemaVersion))
                .findFirst();
    }

    synchronized List<AssistanceRun> find(AssistanceUseCase useCase, UUID targetId) {
        purgeExpired();
        return entries.values().stream().map(Entry::run)
                .filter(run -> run.getUseCase() == useCase && run.getTargetId().equals(targetId))
                .sorted(Comparator.comparing(AssistanceRun::getCreatedAt).reversed()).toList();
    }

    synchronized void addDecision(UUID runId, AssistanceService.DecisionView decision) {
        purgeExpired();
        Entry entry = entries.get(runId);
        if (entry == null) throw new IllegalStateException("Session assistance result has expired.");
        entry.decisions().add(0, decision);
    }

    synchronized List<AssistanceService.DecisionView> decisions(UUID runId) {
        purgeExpired();
        Entry entry = entries.get(runId);
        return entry == null ? List.of() : List.copyOf(entry.decisions());
    }

    synchronized int size() {
        purgeExpired();
        return entries.size();
    }

    synchronized void clear() { entries.clear(); }

    private void purgeExpired() {
        Instant cutoff = clock.instant().minus(ttl);
        entries.entrySet().removeIf(entry -> !entry.getValue().storedAt().isAfter(cutoff));
    }

    private record Entry(AssistanceRun run, Instant storedAt,
            List<AssistanceService.DecisionView> decisions) {}
}
