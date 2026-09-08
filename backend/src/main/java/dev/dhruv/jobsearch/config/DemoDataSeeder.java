package dev.dhruv.jobsearch.config;

import java.math.BigDecimal;
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
import dev.dhruv.jobsearch.contact.OutreachService;
import dev.dhruv.jobsearch.contact.OutreachStatus;
import dev.dhruv.jobsearch.contact.OutreachType;
import dev.dhruv.jobsearch.contact.RelationshipStrength;
import dev.dhruv.jobsearch.ingestion.DailyPriorityAction;
import dev.dhruv.jobsearch.ingestion.DailyPriorityActionRepository;
import dev.dhruv.jobsearch.ingestion.ImportBatch;
import dev.dhruv.jobsearch.ingestion.ImportBatch.ImportCounts;
import dev.dhruv.jobsearch.ingestion.ImportBatchRepository;
import dev.dhruv.jobsearch.ingestion.OpportunityObservation;
import dev.dhruv.jobsearch.ingestion.OpportunityObservation.ImportedObservation;
import dev.dhruv.jobsearch.ingestion.OpportunityObservationRepository;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.OpportunityStatus;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.preparation.PreparationCategory;
import dev.dhruv.jobsearch.preparation.PreparationService;
import dev.dhruv.jobsearch.resume.ResumeVariant;
import dev.dhruv.jobsearch.resume.ResumeVariantRepository;
import dev.dhruv.jobsearch.profile.LocalUserProfile;
import dev.dhruv.jobsearch.profile.LocalUserProfileService;

@Component
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private final JobOpportunityRepository opportunityRepository;
    private final ResumeVariantRepository resumeRepository;
    private final OpportunityService opportunityService;
    private final ApplicationService applicationService;
    private final LocalUserProfileService profileService;
    private final ImportBatchRepository importBatchRepository;
    private final OpportunityObservationRepository observationRepository;
    private final DailyPriorityActionRepository dailyActionRepository;
    private final OutreachService outreachService;
    private final PreparationService preparationService;

    public DemoDataSeeder(JobOpportunityRepository opportunityRepository,
            ResumeVariantRepository resumeRepository,
            OpportunityService opportunityService,
            ApplicationService applicationService,
            LocalUserProfileService profileService,
            ImportBatchRepository importBatchRepository,
            OpportunityObservationRepository observationRepository,
            DailyPriorityActionRepository dailyActionRepository,
            OutreachService outreachService,
            PreparationService preparationService) {
        this.opportunityRepository = opportunityRepository;
        this.resumeRepository = resumeRepository;
        this.opportunityService = opportunityService;
        this.applicationService = applicationService;
        this.profileService = profileService;
        this.importBatchRepository = importBatchRepository;
        this.observationRepository = observationRepository;
        this.dailyActionRepository = dailyActionRepository;
        this.outreachService = outreachService;
        this.preparationService = preparationService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!profileService.get().isOnboardingCompleted()) {
            profileService.save(new LocalUserProfile.ProfileValues(
                    "Alex", "Staff Backend Engineer\nPlatform Engineer", "Staff",
                    "Bengaluru, Remote within India", "HYBRID, REMOTE", "Product engineering, developer tools",
                    "200-5000 employees", "Example Systems", "Grow into organization-wide technical leadership",
                    "Ownership, learning, respectful challenge", "Java, distributed systems, cloud platforms",
                    "Ad-tech, gambling", "08:30", "Asia/Kolkata", true));
        }
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

        seedDailyBrief(backendResume, applied, reviewing, shortlisted);
        seedOutreach(shortlisted);
        seedPreparation(reviewing);
    }

    private void seedDailyBrief(ResumeVariant resume, JobOpportunity first, JobOpportunity second,
            JobOpportunity third) {
        LocalDate today = LocalDate.now();
        String sourceFile = "synthetic-demo-high-fit-openings.xlsx";
        String actionFile = "synthetic-demo-top-three-actions.txt";
        ImportBatch batch = importBatchRepository.save(new ImportBatch(
                sourceFile, actionFile, today, "d".repeat(64)));

        seedObservation(batch, resume, first, 1, "9.100", "Apply",
                "Senior backend role with cross-team platform ownership.",
                "Strong match for Java, distributed systems, and technical leadership.",
                "Confirm the scope of architecture ownership and the on-call rotation.", "1".repeat(64));
        seedObservation(batch, resume, second, 2, "8.400", "Research",
                "Platform role focused on cloud infrastructure and developer productivity.",
                "Good platform fit with room to validate Kubernetes depth.",
                "Clarify time-zone overlap and production support expectations.", "2".repeat(64));
        seedObservation(batch, resume, third, 3, "8.800", "Network",
                "Technical lead role combining Java delivery with team-level influence.",
                "Strong leadership fit and a plausible warm introduction path.",
                "Validate how much of the role remains hands-on.", "3".repeat(64));

        seedDailyAction(batch, first, 1, "Review the tailored resume and application evidence.", actionFile);
        seedDailyAction(batch, second, 2, "Validate the platform scope and on-call expectations.", actionFile);
        seedDailyAction(batch, third, 3, "Draft a concise referral request for the warm contact.", actionFile);

        batch.complete(new ImportCounts(3, 3, 0, 3, 0, 3, 0));
        importBatchRepository.save(batch);
    }

    private void seedObservation(ImportBatch batch, ResumeVariant resume, JobOpportunity opportunity,
            int rank, String score, String recommendation, String summary, String rationale, String risks,
            String fingerprint) {
        BigDecimal fit = new BigDecimal(score);
        OpportunityObservation observation = new OpportunityObservation(batch, opportunity, LocalDate.now());
        observation.refresh(batch, resume, new ImportedObservation(
                rank, LocalDate.now().minusDays(rank), LocalDate.now(), fit,
                fit.subtract(new BigDecimal("0.200")), fit.add(new BigDecimal("0.100")),
                fit, fit, recommendation, summary, rationale, risks,
                "User must verify role-specific work authorization requirements.",
                resume.getName(), fingerprint, "Synthetic demo row " + rank));
        observationRepository.save(observation);
    }

    private void seedDailyAction(ImportBatch batch, JobOpportunity opportunity, int rank,
            String actionText, String sourceFile) {
        DailyPriorityAction action = new DailyPriorityAction(batch, LocalDate.now(), rank);
        action.refresh(batch, opportunity, actionText, sourceFile);
        dailyActionRepository.save(action);
    }

    private void seedOutreach(JobOpportunity opportunity) {
        OutreachService.NewContact contact = new OutreachService.NewContact(
                "Jordan Lee", "FinPeak", "Staff Engineer", "https://example.com/people/jordan-lee",
                null, RelationshipStrength.WARM, "Synthetic demo contact.");
        outreachService.create(new OutreachService.CreateOutreach(
                opportunity.getId(), null, contact, OutreachType.REFERRAL_REQUEST, OutreachStatus.PLANNED,
                "LinkedIn", "Ask for a brief perspective on the team before requesting a referral.",
                Instant.now().plus(1, ChronoUnit.DAYS), "Synthetic demo outreach; no message was sent."));
    }

    private void seedPreparation(JobOpportunity opportunity) {
        var track = preparationService.createTrack(new PreparationService.NewTrack(
                "System design practice", "Prepare evidence for production-scale architecture discussions.",
                PreparationCategory.SYSTEM_DESIGN, LocalDate.now().plusDays(30), 1));
        var milestone = preparationService.createMilestone(track.getId(), new PreparationService.NewMilestone(
                "Design a production-scale service", "Practice trade-offs, failure modes, and observability.",
                LocalDate.now().plusDays(14), 1));
        var item = preparationService.createItem(milestone.getId(), new PreparationService.NewItem(
                "Design a multi-region notification service",
                "Produce a 30-minute architecture sketch and a five-minute executive summary.",
                1, 45, LocalDate.now(), LocalDate.now().plusDays(7), "Distributed systems",
                opportunity.getId(), 1));
        preparationService.startSprint();
        preparationService.addItemToCurrentSprint(item.getId());
        preparationService.setToday(new PreparationService.SetCommitment(
                item.getId(), 45, "Create the first architecture sketch and record the top three trade-offs."));
    }

    private String demoDescription() {
        return "Representative demo record. Replace this with the real job description when reviewing the role.";
    }
}
