package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import dev.dhruv.jobsearch.application.ApplicationArtifact;
import dev.dhruv.jobsearch.application.ApplicationArtifactType;
import dev.dhruv.jobsearch.application.ApplicationService;
import dev.dhruv.jobsearch.application.ApplicationStage;
import dev.dhruv.jobsearch.application.JobApplication;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.resume.ResumeVariant;
import dev.dhruv.jobsearch.resume.ResumeVariantRepository;

@SpringBootTest
@Transactional
class ApplicationArtifactFlowTest {

    @Autowired private OpportunityService opportunityService;
    @Autowired private ApplicationService applicationService;
    @Autowired private ResumeVariantRepository resumeRepository;
    @Autowired private WebApplicationContext webApplicationContext;

    @Test
    void preservesAnImmutableResumeAndOptionalJobDescriptionOnTheFilesystem() throws Exception {
        ResumeVariant variant = resumeRepository.save(new ResumeVariant(
                "Distributed systems", "Senior Software Engineer", "v5a", null, null));
        JobOpportunity opportunity = opportunityService.create(new OpportunityService.CreateOpportunity(
                "Example: Platform Co", "Senior Software Engineer / Storage Team", "Hyderabad", WorkMode.HYBRID,
                "Test", null, "Build reliable storage services", Instant.now()));
        byte[] pdf = "%PDF-1.7\nexact submitted resume\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile resume = new MockMultipartFile(
                "resumeFile", "my-final-resume.pdf", "application/pdf", pdf);
        String jobDescription = "Build reliable distributed storage systems.\nOwn production operations.";

        JobApplication application = applicationService.createWithArtifacts(opportunity.getId(),
                new ApplicationService.CreateApplication(variant.getId(), ApplicationStage.DRAFT, null,
                        "Company portal", "Submit the application", Instant.now().plus(1, ChronoUnit.DAYS),
                        "Draft created with preserved artifacts"), resume, jobDescription);

        List<ApplicationArtifact> artifacts = applicationService.artifacts(application.getId());
        assertThat(artifacts).extracting(ApplicationArtifact::getArtifactType)
                .containsExactly(ApplicationArtifactType.JOB_DESCRIPTION_TEXT, ApplicationArtifactType.RESUME_PDF);

        ApplicationArtifact resumeArtifact = artifacts.stream()
                .filter(value -> value.getArtifactType() == ApplicationArtifactType.RESUME_PDF).findFirst().orElseThrow();
        ApplicationArtifact descriptionArtifact = artifacts.stream()
                .filter(value -> value.getArtifactType() == ApplicationArtifactType.JOB_DESCRIPTION_TEXT).findFirst().orElseThrow();
        assertThat(resumeArtifact.getStoredRelativePath())
                .isEqualTo("Example Platform Co/Submitted_Resume_Senior_Software_Engineer_Storage_Team.pdf");
        assertThat(resumeArtifact.getOriginalFilename()).isEqualTo("my-final-resume.pdf");
        assertThat(resumeArtifact.getContentHash()).hasSize(64);
        assertThat(descriptionArtifact.getStoredRelativePath())
                .isEqualTo("Example Platform Co/JobDescription_Senior_Software_Engineer_Storage_Team.txt");

        Path root = Path.of("target/test-application-resumes").toAbsolutePath().normalize();
        assertThat(Files.readAllBytes(root.resolve(resumeArtifact.getStoredRelativePath()))).isEqualTo(pdf);
        assertThat(Files.readString(root.resolve(descriptionArtifact.getStoredRelativePath())))
                .isEqualTo(jobDescription);
    }

    @Test
    void acceptsTheResumeAndDescriptionFromTheApplicationDialogMultipartRequest() throws Exception {
        ResumeVariant variant = resumeRepository.save(new ResumeVariant(
                "Backend", "Senior Backend Engineer", "v5a", null, null));
        JobOpportunity opportunity = opportunityService.create(new OpportunityService.CreateOpportunity(
                "Multipart Co", "Senior Backend Engineer", "Hyderabad", WorkMode.HYBRID,
                "Test", null, "Build backend services", Instant.now()));
        String requestJson = """
                {"resumeVariantId":"%s","stage":"DRAFT","channel":"Company portal",
                 "nextAction":"Submit application","nextActionAt":"%s","note":"Artifact test"}
                """.formatted(variant.getId(), Instant.now().plus(1, ChronoUnit.DAYS));
        MockMultipartFile request = new MockMultipartFile(
                "request", "request.json", MediaType.APPLICATION_JSON_VALUE,
                requestJson.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile resume = new MockMultipartFile(
                "resumeFile", "submitted.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.7\nsubmitted\n%%EOF".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile description = new MockMultipartFile(
                "jobDescription", "", MediaType.TEXT_PLAIN_VALUE,
                "A preserved job description".getBytes(StandardCharsets.UTF_8));
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(multipart("/api/v1/opportunities/{id}/applications", opportunity.getId())
                        .file(request).file(resume).file(description))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artifacts.length()").value(2))
                .andExpect(jsonPath("$.artifacts[?(@.artifactType == 'RESUME_PDF')].storedPath")
                        .value("application-resumes/Multipart Co/Submitted_Resume_Senior_Backend_Engineer.pdf"));
    }
}
