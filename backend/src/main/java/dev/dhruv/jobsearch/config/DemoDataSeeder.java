package dev.dhruv.jobsearch.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.application.ApplicationService;
import dev.dhruv.jobsearch.application.ApplicationStage;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.OpportunityStatus;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.resume.ResumeVariant;
import dev.dhruv.jobsearch.resume.ResumeVariantRepository;

@Component
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private final JobOpportunityRepository opportunityRepository;
    private final ResumeVariantRepository resumeRepository;
    private final OpportunityService opportunityService;
    private final ApplicationService applicationService;

    public DemoDataSeeder(JobOpportunityRepository opportunityRepository,
            ResumeVariantRepository resumeRepository,
            OpportunityService opportunityService,
            ApplicationService applicationService) {
        this.opportunityRepository = opportunityRepository;
        this.resumeRepository = resumeRepository;
        this.opportunityService = opportunityService;
        this.applicationService = applicationService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (opportunityRepository.count() > 0 || resumeRepository.count() > 0) {
            return;
        }

        ResumeVariant backendResume = resumeRepository.save(new ResumeVariant(
                "Backend & Platform", "Senior Backend Engineer", "2026.08-a", null, null));
        resumeRepository.save(new ResumeVariant(
                "Distributed Systems", "Platform / Infrastructure Engineer", "2026.08-a", null, null));

        JobOpportunity applied = opportunityService.create(new OpportunityService.CreateOpportunity(
                "Northstar Systems", "Senior Backend Engineer", "Hyderabad", WorkMode.HYBRID,
                "Daily job brief", "https://example.com/jobs/northstar-senior-backend", demoDescription(),
                Instant.now().minus(1, ChronoUnit.DAYS)));
        applied.markDemo();
        opportunityService.decide(applied.getId(), OpportunityStatus.SHORTLISTED, 91,
                "Strong Java, distributed systems, and platform ownership match.");
        applicationService.create(applied.getId(), new ApplicationService.CreateApplication(
                backendResume.getId(), ApplicationStage.APPLIED, LocalDate.now(), "Company careers page",
                "Send a concise follow-up to the recruiter", Instant.now().plus(5, ChronoUnit.HOURS),
                "Submitted with the Backend & Platform resume."));

        JobOpportunity reviewing = opportunityService.create(new OpportunityService.CreateOpportunity(
                "Cobalt Cloud", "Platform Engineer", "India", WorkMode.REMOTE,
                "Manual research", "https://example.com/jobs/cobalt-platform", demoDescription(),
                Instant.now().minus(5, ChronoUnit.HOURS)));
        reviewing.markDemo();
        opportunityService.decide(reviewing.getId(), OpportunityStatus.REVIEWING, 84,
                "Good platform fit; validate Kubernetes depth and on-call expectations.");

        JobOpportunity shortlisted = opportunityService.create(new OpportunityService.CreateOpportunity(
                "FinPeak", "Java Engineering Lead", "Hyderabad", WorkMode.HYBRID,
                "Referral lead", "https://example.com/jobs/finpeak-java-lead", demoDescription(),
                Instant.now().minus(2, ChronoUnit.HOURS)));
        shortlisted.markDemo();
        opportunityService.decide(shortlisted.getId(), OpportunityStatus.SHORTLISTED, 88,
                "Strong technical leadership and Java match; identify a warm referral path.");
    }

    private String demoDescription() {
        return "Representative demo record. Replace this with the real job description when reviewing the role.";
    }
}
