package dev.dhruv.jobsearch.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.opportunity.OpportunityStatus;

@Service
public class LiveJobDescriptionExtractionService {

    private static final int MAX_CONCURRENT_FETCHES = 6;

    private final JobOpportunityRepository opportunities;
    private final JobDescriptionSnapshotRepository snapshots;
    private final CanonicalSkillRepository skills;
    private final LiveJobPageFetcher fetcher;
    private final DeterministicSkillExtractionService extraction;

    public LiveJobDescriptionExtractionService(JobOpportunityRepository opportunities,
            JobDescriptionSnapshotRepository snapshots, CanonicalSkillRepository skills, LiveJobPageFetcher fetcher,
            DeterministicSkillExtractionService extraction) {
        this.opportunities = opportunities;
        this.snapshots = snapshots;
        this.skills = skills;
        this.fetcher = fetcher;
        this.extraction = extraction;
    }

    public LiveExtractionResult fetchAndExtract(LiveExtractionCommand command) {
        Set<UUID> selected = command.opportunityIds() == null ? Set.of() : Set.copyOf(command.opportunityIds());
        List<JobOpportunity> eligible = opportunities.findByDemoFalseOrderByDiscoveredAtDesc().stream()
                .filter(opportunity -> selected.isEmpty() || selected.contains(opportunity.getId()))
                .filter(opportunity -> isActive(opportunity.getStatus()))
                .toList();

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(1, Math.min(MAX_CONCURRENT_FETCHES, eligible.size())));
        List<FetchAttempt> attempts;
        try {
            List<CompletableFuture<FetchAttempt>> futures = eligible.stream()
                    .map(opportunity -> CompletableFuture.supplyAsync(() -> fetch(opportunity), executor))
                    .toList();
            attempts = futures.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdownNow();
        }

        int pagesFetched = 0;
        int snapshotsCreated = 0;
        List<UUID> readyForExtraction = new ArrayList<>();
        List<LiveOpportunityResult> details = new ArrayList<>();
        for (FetchAttempt attempt : attempts) {
            JobOpportunity opportunity = attempt.opportunity();
            if (attempt.description() == null) {
                details.add(new LiveOpportunityResult(opportunity.getId(), opportunity.getCompanyName(),
                        opportunity.getRoleTitle(), false, false, 0, attempt.message()));
                continue;
            }
            pagesFetched++;
            String hash = sha256(attempt.description().description());
            boolean created = snapshots.findByOpportunityIdAndContentHash(opportunity.getId(), hash).isEmpty();
            if (created) {
                snapshots.save(new JobDescriptionSnapshot(opportunity, SnapshotSourceType.LIVE_JOB_PAGE,
                        attempt.description().finalUrl(), attempt.description().description(), hash));
                snapshotsCreated++;
            }
            readyForExtraction.add(opportunity.getId());
            details.add(new LiveOpportunityResult(opportunity.getId(), opportunity.getCompanyName(),
                    opportunity.getRoleTitle(), true, created, attempt.description().description().length(),
                    attempt.description().extractionMethod()));
        }

        DeterministicSkillExtractionService.ExtractionResult extracted = readyForExtraction.isEmpty()
                ? new DeterministicSkillExtractionService.ExtractionResult(
                        0, (int) skills.count(), 0, 0, 0, List.of())
                : extraction.extract(new DeterministicSkillExtractionService.ExtractionCommand(
                        readyForExtraction, false));
        return new LiveExtractionResult(eligible.size(), pagesFetched, snapshotsCreated,
                eligible.size() - pagesFetched, extracted.catalogSize(), extracted.matchesFound(),
                extracted.observationsCreated(), details);
    }

    private FetchAttempt fetch(JobOpportunity opportunity) {
        if (opportunity.getSourceUrl() == null || opportunity.getSourceUrl().isBlank()) {
            return new FetchAttempt(opportunity, null, "No direct job link is stored.");
        }
        try {
            return new FetchAttempt(opportunity, fetcher.fetch(opportunity.getSourceUrl()), null);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "The live job description could not be fetched." : exception.getMessage();
            return new FetchAttempt(opportunity, null, message);
        }
    }

    private boolean isActive(OpportunityStatus status) {
        return status != OpportunityStatus.ARCHIVED && status != OpportunityStatus.SKIPPED
                && status != OpportunityStatus.EXPIRED;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record FetchAttempt(JobOpportunity opportunity,
            LiveJobPageFetcher.FetchedDescription description, String message) {}

    public record LiveExtractionCommand(List<UUID> opportunityIds) {}
    public record LiveOpportunityResult(UUID opportunityId, String companyName, String roleTitle,
            boolean fetched, boolean snapshotCreated, int descriptionCharacters, String detail) {}
    public record LiveExtractionResult(int opportunitiesEligible, int pagesFetched, int snapshotsCreated,
            int fetchFailures, int catalogSize, int matchesFound, int observationsCreated,
            List<LiveOpportunityResult> opportunities) {}
}
