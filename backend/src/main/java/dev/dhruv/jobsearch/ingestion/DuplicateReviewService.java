package dev.dhruv.jobsearch.ingestion;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.OpportunityMergeField;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class DuplicateReviewService {

    private static final int LIKELY_THRESHOLD = 78;

    private final GenericInboxItemRepository items;
    private final GenericInboxCandidateRepository candidates;
    private final InboxDuplicateMatchRepository matches;
    private final OpportunityService opportunities;

    public DuplicateReviewService(GenericInboxItemRepository items, GenericInboxCandidateRepository candidates,
            InboxDuplicateMatchRepository matches, OpportunityService opportunities) {
        this.items = items;
        this.candidates = candidates;
        this.matches = matches;
        this.opportunities = opportunities;
    }

    @Transactional
    public ScanReport scanAll() {
        int itemsScanned = 0;
        int candidatesScanned = 0;
        int matchesCreated = 0;
        int matchesRefreshed = 0;
        int candidatesFlagged = 0;
        for (GenericInboxItem item : items.findTop30ByOrderByCreatedAtDesc()) {
            ScanReport result = scanItemInternal(item);
            itemsScanned++;
            candidatesScanned += result.candidatesScanned();
            matchesCreated += result.matchesCreated();
            matchesRefreshed += result.matchesRefreshed();
            candidatesFlagged += result.candidatesFlagged();
        }
        return new ScanReport(itemsScanned, candidatesScanned, matchesCreated, matchesRefreshed, candidatesFlagged);
    }

    @Transactional
    public ScanReport scanItem(UUID itemId) {
        GenericInboxItem item = items.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Inbox item " + itemId + " was not found."));
        return scanItemInternal(item);
    }

    private ScanReport scanItemInternal(GenericInboxItem item) {
        int scanned = 0;
        int created = 0;
        int refreshed = 0;
        int flagged = 0;
        List<JobOpportunity> existingOpportunities = opportunities.list();
        for (GenericInboxCandidate candidate : candidates.findByInboxItemIdOrderByRowNumber(item.getId())) {
            if (candidate.getStatus() != GenericInboxCandidateStatus.NEEDS_REVIEW
                    && candidate.getStatus() != GenericInboxCandidateStatus.DUPLICATE_REVIEW) continue;
            scanned++;
            Map<UUID, DetectedMatch> detected = detect(candidate, existingOpportunities);
            Set<UUID> detectedOpportunityIds = detected.keySet();
            for (InboxDuplicateMatch stale : matches.findByCandidateIdAndStatusOrderByConfidenceDesc(
                    candidate.getId(), DuplicateMatchStatus.OPEN)) {
                if (!detectedOpportunityIds.contains(stale.getOpportunity().getId())) stale.dismiss();
            }
            for (DetectedMatch value : detected.values()) {
                var existing = matches.findByCandidateIdAndOpportunityId(candidate.getId(), value.opportunity().getId());
                if (existing.isPresent()) {
                    existing.get().refresh(value.type(), value.confidence(), value.explanation());
                    refreshed++;
                } else {
                    matches.save(new InboxDuplicateMatch(candidate, value.opportunity(), value.type(),
                            value.confidence(), value.explanation()));
                    created++;
                }
            }
            if (detected.isEmpty()) candidate.clearDuplicateReview();
            else {
                candidate.markDuplicateReview();
                flagged++;
            }
        }
        return new ScanReport(1, scanned, created, refreshed, flagged);
    }

    @Transactional
    public ResolutionResult resolve(UUID candidateId, ResolveCommand command) {
        GenericInboxCandidate candidate = candidates.findById(candidateId)
                .orElseThrow(() -> new NotFoundException("Inbox candidate " + candidateId + " was not found."));
        if (candidate.getStatus() != GenericInboxCandidateStatus.DUPLICATE_REVIEW) {
            throw new IllegalStateException("This candidate is not awaiting duplicate resolution.");
        }
        List<InboxDuplicateMatch> openMatches = matches.findByCandidateIdAndStatusOrderByConfidenceDesc(
                candidateId, DuplicateMatchStatus.OPEN);
        if (openMatches.isEmpty()) throw new IllegalStateException("No open duplicate evidence remains for this candidate.");

        JobOpportunity target;
        switch (command.resolution()) {
            case LINK_EXISTING -> {
                InboxDuplicateMatch selected = selectedMatch(command.matchId(), openMatches);
                target = selected.getOpportunity();
                candidate.linkTo(target);
                resolveMatches(openMatches, selected);
            }
            case MERGE_SELECTED_FIELDS -> {
                InboxDuplicateMatch selected = selectedMatch(command.matchId(), openMatches);
                Set<OpportunityMergeField> fields = command.fields() == null ? Set.of() : Set.copyOf(command.fields());
                target = opportunities.mergeReviewed(selected.getOpportunity().getId(),
                        new OpportunityService.MergeOpportunity(fields, candidate.getCompanyName(), candidate.getRoleTitle(),
                                candidate.getLocation(), candidate.getWorkMode(), candidate.getSourceName(),
                                candidate.getSourceUrl(), candidate.getSourceExternalId(), candidate.getDescription()));
                candidate.mergedInto(target);
                resolveMatches(openMatches, selected);
            }
            case CREATE_SEPARATE -> {
                target = opportunities.create(new OpportunityService.CreateOpportunity(
                        candidate.getCompanyName(), candidate.getRoleTitle(), candidate.getLocation(), candidate.getWorkMode(),
                        firstNonBlank(candidate.getSourceName(), candidate.getInboxItem().getSourceLabel(), "Generic inbox"),
                        candidate.getSourceUrl(), candidate.getDescription(), Instant.now()));
                if (notBlank(candidate.getSourceExternalId()) && opportunities.findBySourceExternalId(
                        candidate.getSourceName(), candidate.getSourceExternalId()) == null) {
                    target = opportunities.recordSourceExternalId(target.getId(), candidate.getSourceExternalId());
                }
                candidate.importAs(target);
                openMatches.forEach(InboxDuplicateMatch::dismiss);
            }
            default -> throw new IllegalArgumentException("Unsupported duplicate resolution.");
        }
        refreshItemStatus(candidate.getInboxItem());
        return new ResolutionResult(candidate.getInboxItem().getId(), candidate.getId(), candidate.getStatus(), target.getId());
    }

    private Map<UUID, DetectedMatch> detect(GenericInboxCandidate candidate, List<JobOpportunity> existing) {
        Map<UUID, DetectedMatch> detected = new LinkedHashMap<>();
        JobOpportunity exactUrl = opportunities.findByCanonicalUrl(candidate.getSourceUrl());
        if (exactUrl != null) {
            detected.put(exactUrl.getId(), new DetectedMatch(exactUrl, DuplicateMatchType.EXACT_URL, 100,
                    "Exact canonical source URL match."));
        }
        JobOpportunity exactExternal = opportunities.findBySourceExternalId(candidate.getSourceName(),
                candidate.getSourceExternalId());
        if (exactExternal != null) {
            detected.putIfAbsent(exactExternal.getId(), new DetectedMatch(exactExternal,
                    DuplicateMatchType.EXACT_EXTERNAL_ID, 100,
                    "Exact source and external job ID match."));
        }
        for (JobOpportunity opportunity : existing) {
            if (detected.containsKey(opportunity.getId())) continue;
            double company = tokenSimilarity(candidate.getCompanyName(), opportunity.getCompanyName(), true);
            double title = tokenSimilarity(candidate.getRoleTitle(), opportunity.getRoleTitle(), false);
            double location = locationSimilarity(candidate.getLocation(), opportunity.getLocation());
            int confidence = (int) Math.round(company * 45 + title * 45 + location * 10);
            if (company >= .65 && title >= .65 && confidence >= LIKELY_THRESHOLD) {
                String explanation = "Company similarity " + percent(company) + " (45% weight); title similarity "
                        + percent(title) + " (45%); location similarity " + percent(location) + " (10%).";
                detected.put(opportunity.getId(), new DetectedMatch(opportunity, DuplicateMatchType.LIKELY_SIMILAR,
                        confidence, explanation));
            }
        }
        return detected;
    }

    private InboxDuplicateMatch selectedMatch(UUID matchId, List<InboxDuplicateMatch> openMatches) {
        if (matchId == null) throw new IllegalArgumentException("Choose the existing opening to resolve against.");
        return openMatches.stream().filter(match -> match.getId().equals(matchId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The selected duplicate match is not open for this candidate."));
    }

    private void resolveMatches(List<InboxDuplicateMatch> openMatches, InboxDuplicateMatch selected) {
        for (InboxDuplicateMatch match : openMatches) {
            if (match.getId().equals(selected.getId())) match.resolve();
            else match.dismiss();
        }
    }

    private void refreshItemStatus(GenericInboxItem item) {
        List<GenericInboxCandidate> values = candidates.findByInboxItemIdOrderByRowNumber(item.getId());
        long imported = values.stream().filter(value -> value.getStatus() == GenericInboxCandidateStatus.IMPORTED
                || value.getStatus() == GenericInboxCandidateStatus.LINKED
                || value.getStatus() == GenericInboxCandidateStatus.MERGED).count();
        long rejected = values.stream().filter(value -> value.getStatus() == GenericInboxCandidateStatus.REJECTED).count();
        if (imported == values.size()) item.updateReviewStatus(GenericInboxItemStatus.IMPORTED);
        else if (rejected == values.size()) item.updateReviewStatus(GenericInboxItemStatus.REJECTED);
        else if (imported + rejected > 0) item.updateReviewStatus(GenericInboxItemStatus.PARTIALLY_REVIEWED);
        else item.updateReviewStatus(GenericInboxItemStatus.NEEDS_REVIEW);
    }

    private double tokenSimilarity(String left, String right, boolean company) {
        Set<String> leftTokens = tokens(left, company);
        Set<String> rightTokens = tokens(right, company);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0;
        if (leftTokens.equals(rightTokens)) return 1;
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return (double) intersection.size() / union.size();
    }

    private double locationSimilarity(String left, String right) {
        if (!notBlank(left) || !notBlank(right)) return .5;
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft)) return 1;
        return tokenSimilarity(left, right, false);
    }

    private Set<String> tokens(String value, boolean company) {
        if (!notBlank(value)) return Set.of();
        Set<String> ignored = company ? Set.of("inc", "llc", "ltd", "limited", "technologies", "technology",
                "solutions", "software", "india", "pvt", "private", "corp", "corporation") : Set.of();
        return Arrays.stream(normalize(value).split("\\s+"))
                .filter(token -> token.length() > 1 && !ignored.contains(token))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        return Arrays.stream(values).filter(this::notBlank).map(String::trim).findFirst().orElse(null);
    }

    private record DetectedMatch(JobOpportunity opportunity, DuplicateMatchType type, int confidence,
            String explanation) {}

    public record ScanReport(int itemsScanned, int candidatesScanned, int matchesCreated, int matchesRefreshed,
            int candidatesFlagged) {}

    public record ResolveCommand(DuplicateResolution resolution, UUID matchId, Set<OpportunityMergeField> fields) {}

    public record ResolutionResult(UUID itemId, UUID candidateId, GenericInboxCandidateStatus status,
            UUID opportunityId) {}
}
