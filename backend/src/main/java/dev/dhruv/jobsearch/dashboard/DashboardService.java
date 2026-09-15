package dev.dhruv.jobsearch.dashboard;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import dev.dhruv.jobsearch.application.ApplicationStage;
import dev.dhruv.jobsearch.application.JobApplication;
import dev.dhruv.jobsearch.application.JobApplicationRepository;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.opportunity.OpportunityStatus;

@Service
public class DashboardService {

    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Kolkata");

    private final JobOpportunityRepository opportunityRepository;
    private final JobApplicationRepository applicationRepository;
    private final boolean demoMode;

    public DashboardService(JobOpportunityRepository opportunityRepository,
            JobApplicationRepository applicationRepository,
            @Value("${app.demo-mode:false}") boolean demoMode) {
        this.opportunityRepository = opportunityRepository;
        this.applicationRepository = applicationRepository;
        this.demoMode = demoMode;
    }

    @Transactional(readOnly = true)
    public MorningDashboard morning() {
        List<JobOpportunity> opportunities = demoMode ? opportunityRepository.findAllByOrderByDiscoveredAtDesc()
                : opportunityRepository.findByDemoFalseOrderByDiscoveredAtDesc();
        List<JobOpportunity> activeOpportunities = opportunities.stream()
                .filter(opportunity -> opportunity.getStatus() != OpportunityStatus.ARCHIVED)
                .toList();
        List<JobApplication> applications = applicationRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(application -> demoMode || !application.getOpportunity().isDemo())
                .toList();
        Instant actionWindow = Instant.now().plusSeconds(48 * 60 * 60);

        List<ActionItem> actions = applications.stream()
                .filter(application -> !application.getStage().isTerminal())
                .filter(JobApplication::isFollowUpActive)
                .filter(application -> application.getNextActionAt() != null)
                .filter(application -> !application.getNextActionAt().isAfter(actionWindow))
                .sorted((left, right) -> left.getNextActionAt().compareTo(right.getNextActionAt()))
                .map(application -> new ActionItem(
                        application.getId(), application.getOpportunity().getCompanyName(),
                        application.getOpportunity().getRoleTitle(), application.getStage(),
                        application.getNextAction(), application.getNextActionAt()))
                .toList();

        Map<ApplicationStage, Long> pipeline = new LinkedHashMap<>();
        for (ApplicationStage stage : ApplicationStage.values()) {
            long count = applications.stream().filter(application -> application.getStage() == stage).count();
            if (count > 0 || !stage.isTerminal()) {
                pipeline.put(stage, count);
            }
        }

        List<OpportunityCard> recent = activeOpportunities.stream()
                .filter(opportunity -> opportunity.getStatus() != OpportunityStatus.SKIPPED
                        && opportunity.getStatus() != OpportunityStatus.EXPIRED)
                .limit(6)
                .map(opportunity -> new OpportunityCard(
                        opportunity.getId(), opportunity.getCompanyName(), opportunity.getRoleTitle(),
                        opportunity.getLocation(), opportunity.getWorkMode().name(), opportunity.getStatus(),
                        opportunity.getFitScore(), opportunity.getFitSummary(), opportunity.getDiscoveredAt(),
                        applicationRepository.findByOpportunityId(opportunity.getId())
                                .map(JobApplication::getId).orElse(null)))
                .toList();

        long activeApplications = applications.stream().filter(application -> !application.getStage().isTerminal()).count();
        long shortlisted = activeOpportunities.stream()
                .filter(opportunity -> opportunity.getStatus() == OpportunityStatus.SHORTLISTED).count();
        LocalDate weekStart = LocalDate.now(USER_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long appliedThisWeek = applications.stream()
                .filter(application -> application.getAppliedOn() != null)
                .filter(application -> !application.getAppliedOn().isBefore(weekStart))
                .count();

        return new MorningDashboard(
                LocalDate.now(USER_ZONE), activeOpportunities.size(), shortlisted, activeApplications, appliedThisWeek,
                actions.size(), actions, recent, pipeline);
    }

    public record MorningDashboard(
            LocalDate date,
            long totalOpportunities,
            long shortlistedOpportunities,
            long activeApplications,
            long appliedThisWeek,
            long actionsDue,
            List<ActionItem> actions,
            List<OpportunityCard> opportunities,
            Map<ApplicationStage, Long> pipeline) {
    }

    public record ActionItem(
            UUID applicationId,
            String companyName,
            String roleTitle,
            ApplicationStage stage,
            String nextAction,
            Instant nextActionAt) {
    }

    public record OpportunityCard(
            UUID id,
            String companyName,
            String roleTitle,
            String location,
            String workMode,
            OpportunityStatus status,
            Integer fitScore,
            String fitSummary,
            Instant discoveredAt,
            UUID applicationId) {
    }
}
