package dev.dhruv.jobsearch.skill;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketSignalService {

    private final JobSkillObservationRepository observations;
    private final OpportunityCohortClassifier classifier;
    private final CompanySegmentCatalog companySegments;

    public MarketSignalService(JobSkillObservationRepository observations,
            OpportunityCohortClassifier classifier, CompanySegmentCatalog companySegments) {
        this.observations = observations;
        this.classifier = classifier;
        this.companySegments = companySegments;
    }

    @Transactional(readOnly = true)
    public MarketSignalOverview overview(MarketSignalQuery requested) {
        ResolvedQuery query = resolve(requested);
        List<JobSkillObservation> accepted = observations
                .findByReviewStatusOrderByUpdatedAtDesc(SkillReviewStatus.ACCEPTED).stream()
                .filter(item -> DeterministicSkillExtractionService.isFullDescriptionSource(
                        item.getSnapshot().getSourceType()))
                .toList();

        List<JobSkillObservation> currentCohort = accepted.stream()
                .filter(item -> inCohort(item, query.from(), query.to(), query)).toList();
        List<JobSkillObservation> previousCohort = accepted.stream()
                .filter(item -> inCohort(item, query.previousFrom(), query.previousTo(), query)).toList();
        int currentSampleSize = distinctOpportunities(currentCohort);
        int previousSampleSize = distinctOpportunities(previousCohort);

        List<JobSkillObservation> currentSignals = strengthFilter(currentCohort, query.strength());
        List<JobSkillObservation> previousSignals = strengthFilter(previousCohort, query.strength());
        Map<UUID, Aggregate> current = aggregate(currentSignals);
        Map<UUID, Aggregate> previous = aggregate(previousSignals);
        Set<UUID> skillIds = new LinkedHashSet<>();
        skillIds.addAll(current.keySet());
        skillIds.addAll(previous.keySet());

        List<SkillSignal> signals = skillIds.stream().map(skillId -> {
            Aggregate currentValue = current.get(skillId);
            Aggregate previousValue = previous.get(skillId);
            Aggregate identity = currentValue == null ? previousValue : currentValue;
            int currentOpenings = currentValue == null ? 0 : currentValue.openingCount();
            int previousOpenings = previousValue == null ? 0 : previousValue.openingCount();
            double currentFrequency = percentage(currentOpenings, currentSampleSize);
            double previousFrequency = percentage(previousOpenings, previousSampleSize);
            Double trend = previousSampleSize == 0 ? null : round(currentFrequency - previousFrequency);
            return new SkillSignal(skillId, identity.skillName(), identity.category(), currentOpenings,
                    currentFrequency, previousOpenings, previousFrequency, trend,
                    currentValue == null ? 0 : currentValue.requiredCount(),
                    currentValue == null ? 0 : currentValue.preferredCount(),
                    currentValue == null ? 0 : currentValue.mentionedCount(),
                    currentValue == null ? 0 : currentValue.evidenceCount());
        }).sorted(Comparator.comparingInt(SkillSignal::currentOpeningCount).reversed()
                .thenComparing((SkillSignal item) -> item.trendDeltaPercentagePoints() == null
                        ? Double.NEGATIVE_INFINITY : item.trendDeltaPercentagePoints(), Comparator.reverseOrder())
                .thenComparing(SkillSignal::skillName)).toList();

        CohortOptions options = options(accepted);
        return new MarketSignalOverview(query.view(), currentSampleSize, previousSampleSize,
                currentSignals.size(), signals, options);
    }

    @Transactional(readOnly = true)
    public EvidenceDrilldown evidence(UUID skillId, MarketSignalQuery requested) {
        ResolvedQuery query = resolve(requested);
        List<EvidenceItem> items = strengthFilter(observations
                .findByReviewStatusOrderByUpdatedAtDesc(SkillReviewStatus.ACCEPTED).stream()
                .filter(item -> item.getSkill().getId().equals(skillId))
                .filter(item -> inCohort(item, query.from(), query.to(), query)).toList(), query.strength())
                .stream().map(this::evidenceItem)
                .sorted(Comparator.comparing(EvidenceItem::discoveredOn).reversed()
                        .thenComparing(EvidenceItem::companyName)
                        .thenComparing(EvidenceItem::roleTitle)).toList();
        return new EvidenceDrilldown(skillId, items.isEmpty() ? null : items.getFirst().skillName(),
                query.view(), items);
    }

    private CohortOptions options(List<JobSkillObservation> accepted) {
        Set<RoleFamily> roleFamilies = EnumSet.noneOf(RoleFamily.class);
        Set<SeniorityBand> seniorities = EnumSet.noneOf(SeniorityBand.class);
        Set<String> segments = new HashSet<>();
        for (JobSkillObservation item : accepted) {
            roleFamilies.add(classifier.roleFamily(item.getOpportunity().getRoleTitle()));
            seniorities.add(classifier.seniority(item.getOpportunity().getRoleTitle()));
            segments.add(companySegments.segmentFor(item.getOpportunity().getCompanyName()));
        }
        return new CohortOptions(roleFamilies.stream().toList(), seniorities.stream().toList(),
                segments.stream().sorted().toList(), List.copyOf(EnumSet.allOf(SkillStrength.class)));
    }

    private boolean inCohort(JobSkillObservation item, LocalDate from, LocalDate to, ResolvedQuery query) {
        LocalDate discoveredOn = discoveredOn(item);
        if (discoveredOn.isBefore(from) || discoveredOn.isAfter(to)) return false;
        if (query.roleFamily() != null
                && classifier.roleFamily(item.getOpportunity().getRoleTitle()) != query.roleFamily()) return false;
        if (query.seniority() != null
                && classifier.seniority(item.getOpportunity().getRoleTitle()) != query.seniority()) return false;
        return query.companySegment() == null || query.companySegment().isBlank()
                || companySegments.segmentFor(item.getOpportunity().getCompanyName())
                        .equalsIgnoreCase(query.companySegment());
    }

    private EvidenceItem evidenceItem(JobSkillObservation item) {
        return new EvidenceItem(item.getId(), item.getSkill().getId(), item.getSkill().getName(),
                item.getSkill().getCategory(), item.getOpportunity().getId(), item.getOpportunity().getCompanyName(),
                item.getOpportunity().getRoleTitle(), classifier.roleFamily(item.getOpportunity().getRoleTitle()),
                classifier.seniority(item.getOpportunity().getRoleTitle()),
                companySegments.segmentFor(item.getOpportunity().getCompanyName()), item.getStrength(),
                item.getEvidenceSnippet(), item.getSnapshot().getSourceType(), item.getSnapshot().getSourceLabel(),
                item.getOpportunity().getSourceUrl(), discoveredOn(item), item.getReviewedAt());
    }

    private static List<JobSkillObservation> strengthFilter(List<JobSkillObservation> items, SkillStrength strength) {
        return strength == null ? items : items.stream().filter(item -> item.getStrength() == strength).toList();
    }

    private static Map<UUID, Aggregate> aggregate(List<JobSkillObservation> items) {
        Map<UUID, AggregateBuilder> builders = new HashMap<>();
        for (JobSkillObservation item : items) {
            builders.computeIfAbsent(item.getSkill().getId(), ignored -> new AggregateBuilder(
                    item.getSkill().getName(), item.getSkill().getCategory())).add(item);
        }
        Map<UUID, Aggregate> result = new HashMap<>();
        builders.forEach((skillId, builder) -> result.put(skillId, builder.build()));
        return result;
    }

    private static int distinctOpportunities(List<JobSkillObservation> items) {
        return (int) items.stream().map(item -> item.getOpportunity().getId()).distinct().count();
    }

    private static LocalDate discoveredOn(JobSkillObservation item) {
        return item.getOpportunity().getDiscoveredAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static double percentage(int count, int sampleSize) {
        return sampleSize == 0 ? 0.0 : round(count * 100.0 / sampleSize);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static ResolvedQuery resolve(MarketSignalQuery requested) {
        MarketSignalQuery query = requested == null ? new MarketSignalQuery(null, null, null, null, null, null) : requested;
        LocalDate to = query.to() == null ? LocalDate.now(ZoneOffset.UTC) : query.to();
        LocalDate from = query.from() == null ? to.minusDays(89) : query.from();
        if (from.isAfter(to)) throw new IllegalArgumentException("Market-signal start date must not be after the end date.");
        long windowDays = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(windowDays - 1);
        return new ResolvedQuery(from, to, previousFrom, previousTo, query.roleFamily(), query.seniority(),
                blankToNull(query.companySegment()), query.strength());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class AggregateBuilder {
        private final String skillName;
        private final SkillCategory category;
        private final Map<UUID, SkillStrength> strongestByOpportunity = new HashMap<>();
        private int evidenceCount;

        private AggregateBuilder(String skillName, SkillCategory category) {
            this.skillName = skillName;
            this.category = category;
        }

        private void add(JobSkillObservation observation) {
            strongestByOpportunity.merge(observation.getOpportunity().getId(), observation.getStrength(),
                    (left, right) -> rank(left) >= rank(right) ? left : right);
            evidenceCount++;
        }

        private Aggregate build() {
            int required = 0;
            int preferred = 0;
            int mentioned = 0;
            for (SkillStrength strength : strongestByOpportunity.values()) {
                if (strength == SkillStrength.REQUIRED) required++;
                else if (strength == SkillStrength.PREFERRED) preferred++;
                else mentioned++;
            }
            return new Aggregate(skillName, category, strongestByOpportunity.size(), required, preferred,
                    mentioned, evidenceCount);
        }

        private static int rank(SkillStrength strength) {
            return switch (strength) {
                case REQUIRED -> 3;
                case PREFERRED -> 2;
                case MENTIONED -> 1;
            };
        }
    }

    private record Aggregate(String skillName, SkillCategory category, int openingCount,
            int requiredCount, int preferredCount, int mentionedCount, int evidenceCount) {}

    private record ResolvedQuery(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo,
            RoleFamily roleFamily, SeniorityBand seniority, String companySegment, SkillStrength strength) {
        QueryView view() {
            return new QueryView(from, to, previousFrom, previousTo, roleFamily, seniority, companySegment, strength);
        }
    }

    public record MarketSignalQuery(LocalDate from, LocalDate to, RoleFamily roleFamily,
            SeniorityBand seniority, String companySegment, SkillStrength strength) {}
    public record QueryView(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo,
            RoleFamily roleFamily, SeniorityBand seniority, String companySegment, SkillStrength strength) {}
    public record CohortOptions(List<RoleFamily> roleFamilies, List<SeniorityBand> seniorities,
            List<String> companySegments, List<SkillStrength> strengths) {}
    public record MarketSignalOverview(QueryView query, int currentSampleSize, int previousSampleSize,
            int acceptedEvidenceCount, List<SkillSignal> signals, CohortOptions options) {}
    public record SkillSignal(UUID skillId, String skillName, SkillCategory category,
            int currentOpeningCount, double currentFrequencyPercent, int previousOpeningCount,
            double previousFrequencyPercent, Double trendDeltaPercentagePoints,
            int requiredCount, int preferredCount, int mentionedCount, int evidenceCount) {}
    public record EvidenceDrilldown(UUID skillId, String skillName, QueryView query, List<EvidenceItem> evidence) {}
    public record EvidenceItem(UUID observationId, UUID skillId, String skillName, SkillCategory category,
            UUID opportunityId, String companyName, String roleTitle, RoleFamily roleFamily,
            SeniorityBand seniority, String companySegment, SkillStrength strength, String evidenceSnippet,
            SnapshotSourceType sourceType, String sourceLabel, String sourceUrl, LocalDate discoveredOn,
            java.time.Instant reviewedAt) {}
}
