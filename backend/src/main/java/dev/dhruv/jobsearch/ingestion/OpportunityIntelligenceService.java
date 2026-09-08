package dev.dhruv.jobsearch.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.application.JobApplicationRepository;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.OpportunityStatus;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class OpportunityIntelligenceService {

    private final OpportunityObservationRepository observationRepository;
    private final JobApplicationRepository applicationRepository;

    public OpportunityIntelligenceService(OpportunityObservationRepository observationRepository,
            JobApplicationRepository applicationRepository) {
        this.observationRepository = observationRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public OpportunityFeed feed(String query, String recommendation, String location, String resume,
            BigDecimal minScore, LocalDate observedOn, boolean archived) {
        List<OpportunityObservation> source;
        if (observedOn != null) {
            source = observationRepository.findByObservedOnOrderBySourceRankAsc(observedOn);
        } else {
            Map<UUID, OpportunityObservation> latestByOpportunity = new LinkedHashMap<>();
            observationRepository.findAllByOrderByObservedOnDescSourceRankAsc()
                    .forEach(observation -> latestByOpportunity.putIfAbsent(
                            observation.getOpportunity().getId(), observation));
            source = new ArrayList<>(latestByOpportunity.values());
        }

        List<OpportunityObservation> filtered = source.stream()
                .filter(observation -> archived
                        ? observation.getOpportunity().getStatus() == OpportunityStatus.ARCHIVED
                        : observation.getOpportunity().getStatus() != OpportunityStatus.ARCHIVED)
                .filter(observation -> matchesQuery(observation, query))
                .filter(observation -> contains(observation.getRecommendation(), recommendation))
                .filter(observation -> contains(observation.getOpportunity().getLocation(), location))
                .filter(observation -> contains(observation.getRecommendedResumeName(), resume))
                .filter(observation -> minScore == null || observation.getWeightedTotal().compareTo(minScore) >= 0)
                .sorted(Comparator.comparing(OpportunityObservation::getObservedOn).reversed()
                        .thenComparing(OpportunityObservation::getSourceRank))
                .toList();

        Map<UUID, JobApplicationRepository.OpeningApplicationLink> applicationLinks = new LinkedHashMap<>();
        applicationRepository.findOpeningLinks().forEach(link -> applicationLinks.put(link.getOpportunityId(), link));
        List<OpeningView> openings = filtered.stream().map(observation -> {
            var link = applicationLinks.get(observation.getOpportunity().getId());
            return toView(observation, link == null ? null : link.getId(), link == null ? null : link.getStage().name());
        }).toList();
        List<LocalDate> dates = observationRepository.findObservationDates();
        List<String> recommendations = source.stream().map(OpportunityObservation::getRecommendation)
                .distinct().sorted().toList();
        List<String> resumes = source.stream().map(OpportunityObservation::getRecommendedResumeName)
                .filter(value -> value != null && !value.isBlank()).distinct().sorted().toList();

        return new OpportunityFeed(observationRepository.findLatestObservationDate(), openings.size(),
                openings, dates, recommendations, resumes);
    }

    @Transactional(readOnly = true)
    public OpeningDetail detail(UUID opportunityId) {
        List<OpportunityObservation> history = observationRepository
                .findByOpportunityIdOrderByObservedOnDesc(opportunityId);
        if (history.isEmpty()) {
            throw new NotFoundException("No imported intelligence was found for opportunity " + opportunityId + ".");
        }
        OpportunityObservation latest = history.getFirst();
        return new OpeningDetail(toView(latest), latest.getRoleSummary(), latest.getFitRationale(),
                latest.getKeyRisks(), latest.getAuthorizationEligibility(), latest.getPostingDate(),
                latest.getVerifiedDate(), history.stream().map(ObservationHistory::from).toList());
    }

    private OpeningView toView(OpportunityObservation observation) {
        JobOpportunity opportunity = observation.getOpportunity();
        var application = applicationRepository.findByOpportunityId(opportunity.getId()).orElse(null);
        UUID applicationId = application == null ? null : application.getId();
        String applicationStage = application == null ? null : application.getStage().name();
        return toView(observation, applicationId, applicationStage);
    }

    private OpeningView toView(OpportunityObservation observation, UUID applicationId, String applicationStage) {
        JobOpportunity opportunity = observation.getOpportunity();
        return new OpeningView(opportunity.getId(), opportunity.getCompanyName(), opportunity.getRoleTitle(),
                opportunity.getLocation(), opportunity.getWorkMode().name(), opportunity.getStatus().name(),
                opportunity.getSourceUrl(), observation.getObservedOn(), observation.getSourceRank(),
                observation.getOverallFit(), observation.getRecruiterScreenStrength(),
                observation.getTechnicalScope(), observation.getGrowthPotential(), observation.getWeightedTotal(),
                observation.getRecommendation(), observation.getRoleSummary(), observation.getFitRationale(),
                observation.getKeyRisks(), observation.getRecommendedResumeName(), applicationId, applicationStage,
                opportunity.getArchiveReason(), opportunity.getArchivedAt(),
                opportunity.getArchivedFromStatus() == null ? null : opportunity.getArchivedFromStatus().name());
    }

    private boolean matchesQuery(OpportunityObservation observation, String query) {
        if (query == null || query.isBlank()) return true;
        String needle = query.toLowerCase(Locale.ROOT).trim();
        JobOpportunity opportunity = observation.getOpportunity();
        return contains(opportunity.getCompanyName(), needle)
                || contains(opportunity.getRoleTitle(), needle)
                || contains(observation.getRoleSummary(), needle)
                || contains(observation.getFitRationale(), needle)
                || contains(observation.getKeyRisks(), needle);
    }

    private boolean contains(String value, String filter) {
        return filter == null || filter.isBlank()
                || value != null && value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT).trim());
    }

    public record OpportunityFeed(LocalDate latestObservationDate, int resultCount, List<OpeningView> openings,
            List<LocalDate> availableDates, List<String> recommendations, List<String> resumeVariants) {
    }

    public record OpeningView(
            UUID opportunityId,
            String companyName,
            String roleTitle,
            String location,
            String workMode,
            String status,
            String sourceUrl,
            LocalDate observedOn,
            int rank,
            BigDecimal overallFit,
            BigDecimal recruiterScreenStrength,
            BigDecimal technicalScope,
            BigDecimal growthPotential,
            BigDecimal weightedTotal,
            String recommendation,
            String roleSummary,
            String fitRationale,
            String keyRisks,
            String recommendedResumeVariant,
            UUID applicationId,
            String applicationStage,
            String archiveReason,
            java.time.Instant archivedAt,
            String archivedFromStatus) {
    }

    public record OpeningDetail(OpeningView opening, String roleSummary, String fitRationale, String keyRisks,
            String authorizationEligibility, LocalDate postingDate, LocalDate verifiedDate,
            List<ObservationHistory> observationHistory) {
    }

    public record ObservationHistory(LocalDate observedOn, int rank, BigDecimal weightedTotal,
            String recommendation, String recommendedResumeVariant) {
        static ObservationHistory from(OpportunityObservation observation) {
            return new ObservationHistory(observation.getObservedOn(), observation.getSourceRank(),
                    observation.getWeightedTotal(), observation.getRecommendation(),
                    observation.getRecommendedResumeName());
        }
    }
}
