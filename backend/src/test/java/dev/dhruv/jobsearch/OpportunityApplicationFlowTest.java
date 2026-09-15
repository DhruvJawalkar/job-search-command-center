package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.application.ApplicationService;
import dev.dhruv.jobsearch.application.ApplicationStage;
import dev.dhruv.jobsearch.application.JobApplication;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.OpportunityStatus;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.resume.ResumeVariant;
import dev.dhruv.jobsearch.resume.ResumeVariantRepository;

@SpringBootTest
@Transactional
class OpportunityApplicationFlowTest {

    @Autowired
    private OpportunityService opportunityService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ResumeVariantRepository resumeRepository;

    @Test
    void preservesTheOpportunityResumeAndApplicationTimeline() {
        ResumeVariant resume = resumeRepository.save(new ResumeVariant(
                "Java backend", "Senior Backend Engineer", "v1", null, "sha256-demo"));
        JobOpportunity opportunity = opportunityService.create(new OpportunityService.CreateOpportunity(
                "Example Co", "Senior Backend Engineer", "Hyderabad", WorkMode.HYBRID,
                "Test", "https://example.com/jobs/42?utm_source=test", "Build reliable services", Instant.now()));
        opportunityService.decide(opportunity.getId(), OpportunityStatus.SHORTLISTED, 92, "Strong match");

        JobApplication application = applicationService.create(opportunity.getId(),
                new ApplicationService.CreateApplication(resume.getId(), ApplicationStage.DRAFT, null, null,
                        "Finish tailoring the summary", Instant.now().plus(1, ChronoUnit.DAYS), "Draft created"));
        applicationService.transition(application.getId(), new ApplicationService.TransitionApplication(
                ApplicationStage.APPLIED, "Follow up in five business days",
                Instant.now().plus(5, ChronoUnit.DAYS), "Application submitted"));

        JobApplication saved = applicationService.get(application.getId());
        assertThat(saved.getStage()).isEqualTo(ApplicationStage.APPLIED);
        assertThat(saved.getAppliedOn()).isEqualTo(LocalDate.now());
        assertThat(saved.getResumeVariant().getContentHash()).isEqualTo("sha256-demo");
        assertThat(saved.getOpportunity().getStatus()).isEqualTo(OpportunityStatus.APPLIED);
        assertThat(applicationService.events(application.getId())).hasSize(2);

        Instant rescheduled = Instant.now().plus(7, ChronoUnit.DAYS);
        applicationService.updateFollowUp(application.getId(), new ApplicationService.UpdateApplicationFollowUp(
                ApplicationStage.APPLIED, "Send a concise recruiter follow-up", rescheduled, "Follow-up revised"));
        assertThat(applicationService.get(application.getId()).getNextAction()).isEqualTo("Send a concise recruiter follow-up");
        assertThat(applicationService.get(application.getId()).isFollowUpActive()).isTrue();

        applicationService.dropFollowUp(application.getId(), "No further reminder needed");
        assertThat(applicationService.get(application.getId()).isFollowUpActive()).isFalse();
        assertThat(applicationService.events(application.getId())).hasSize(3);
    }

    @Test
    void createsAnApplicationWithoutSchedulingAFollowUp() {
        ResumeVariant resume = resumeRepository.save(new ResumeVariant(
                "Distributed systems", "Senior Software Engineer", "v1", null, "sha256-no-follow-up"));
        JobOpportunity opportunity = opportunityService.create(new OpportunityService.CreateOpportunity(
                "Optional Follow-up Co", "Senior Software Engineer", "Hyderabad", WorkMode.HYBRID,
                "Test", "https://example.com/jobs/optional-follow-up", "Build reliable services", Instant.now()));

        JobApplication application = applicationService.create(opportunity.getId(),
                new ApplicationService.CreateApplication(resume.getId(), ApplicationStage.DRAFT, null, null,
                        null, null, "Application created without a reminder"));

        JobApplication saved = applicationService.get(application.getId());
        assertThat(saved.getNextAction()).isNull();
        assertThat(saved.getNextActionAt()).isNull();
        assertThat(saved.isFollowUpActive()).isFalse();
    }
}
