package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.skill.SkillCategory;
import dev.dhruv.jobsearch.skill.DeterministicSkillExtractionService;
import dev.dhruv.jobsearch.skill.SkillEvidenceService;
import dev.dhruv.jobsearch.skill.SkillExtractionMethod;
import dev.dhruv.jobsearch.skill.SkillReviewStatus;
import dev.dhruv.jobsearch.skill.SkillStrength;
import dev.dhruv.jobsearch.skill.SnapshotSourceType;

@SpringBootTest
@Transactional
class SkillEvidenceFlowTest {

    @Autowired SkillEvidenceService skillEvidence;
    @Autowired DeterministicSkillExtractionService extraction;
    @Autowired OpportunityService opportunities;

    @Test
    void preservesCanonicalSkillsSourceEvidenceAndHumanReview() {
        var opportunity = opportunities.create(new OpportunityService.CreateOpportunity(
                "Example AI", "Principal Platform Engineer", "Hyderabad", WorkMode.HYBRID,
                "Test", "https://example.ai/jobs/platform", "Build agent platforms with LangGraph.", Instant.now()));

        var skill = skillEvidence.createSkill(new SkillEvidenceService.NewSkill(
                "LangGraph", SkillCategory.AI_AGENT, "Stateful agent orchestration", List.of("Lang Graph")));
        assertThatThrownBy(() -> skillEvidence.createSkill(new SkillEvidenceService.NewSkill(
                "lang graph", SkillCategory.AI_AGENT, null, List.of())))
                .isInstanceOf(IllegalStateException.class);

        var snapshot = skillEvidence.captureSnapshot(new SkillEvidenceService.NewSnapshot(
                opportunity.getId(), SnapshotSourceType.PASTED_DESCRIPTION, "Recruiter brief",
                "Build production agent workflows with LangGraph and human approval."));
        var duplicateSnapshot = skillEvidence.captureSnapshot(new SkillEvidenceService.NewSnapshot(
                opportunity.getId(), SnapshotSourceType.PASTED_DESCRIPTION, "Same brief",
                "Build production agent workflows with LangGraph and human approval."));
        assertThat(duplicateSnapshot.id()).isEqualTo(snapshot.id());

        var proposed = skillEvidence.createObservation(new SkillEvidenceService.NewObservation(
                snapshot.id(), skill.id(), SkillStrength.REQUIRED,
                "Build production agent workflows with LangGraph and human approval.",
                SkillExtractionMethod.MANUAL));
        assertThat(proposed.reviewStatus()).isEqualTo(SkillReviewStatus.PROPOSED);

        var accepted = skillEvidence.reviewObservation(proposed.id(), SkillReviewStatus.ACCEPTED,
                "Verified against the pasted job description.");
        assertThat(accepted.reviewStatus()).isEqualTo(SkillReviewStatus.ACCEPTED);
        assertThat(accepted.reviewedAt()).isNotNull();

        var overview = skillEvidence.overview();
        assertThat(overview.catalogSize()).isEqualTo(1);
        assertThat(overview.proposedCount()).isZero();
        assertThat(overview.acceptedCount()).isEqualTo(1);
        assertThat(overview.skills().getFirst().aliases()).containsExactly("Lang Graph");
        assertThat(overview.observations().getFirst().companyName()).isEqualTo("Example AI");
    }

    @Test
    void seedsExtractsAndReviewsDeterministicEvidenceIdempotently() {
        var opportunity = opportunities.create(new OpportunityService.CreateOpportunity(
                "Example Cloud", "Senior Platform Engineer", "India", WorkMode.REMOTE,
                "Test", "https://example.cloud/jobs/platform",
                "Minimum qualifications:\nHands-on experience with Java and Spring Boot is required.\n"
                        + "Preferred qualifications:\nFamiliarity with LangGraph and Kubernetes is a plus.",
                Instant.now()));

        var seeded = skillEvidence.seedReviewedBacklog();
        assertThat(seeded.skillsCreated()).isGreaterThan(40);
        assertThat(skillEvidence.seedReviewedBacklog().skillsCreated()).isZero();
        var summaryOnly = extraction.extract(new DeterministicSkillExtractionService.ExtractionCommand(
                List.of(opportunity.getId()), false));
        assertThat(summaryOnly.opportunitiesScanned()).isZero();
        assertThat(summaryOnly.observationsCreated()).isZero();
        skillEvidence.captureSnapshot(new SkillEvidenceService.NewSnapshot(
                opportunity.getId(), SnapshotSourceType.LIVE_JOB_PAGE, opportunity.getSourceUrl(),
                "Minimum qualifications:\nHands-on experience with Java and Spring Boot is required.\n"
                        + "Preferred qualifications:\nFamiliarity with LangGraph and Kubernetes is a plus."));

        var first = extraction.extract(new DeterministicSkillExtractionService.ExtractionCommand(
                List.of(opportunity.getId()), false));
        assertThat(first.opportunitiesScanned()).isEqualTo(1);
        assertThat(first.observationsCreated()).isGreaterThanOrEqualTo(4);

        var second = extraction.extract(new DeterministicSkillExtractionService.ExtractionCommand(
                List.of(opportunity.getId()), false));
        assertThat(second.snapshotsCreated()).isZero();
        assertThat(second.observationsCreated()).isZero();

        var proposed = skillEvidence.listObservations(opportunity.getId(), SkillReviewStatus.PROPOSED);
        assertThat(proposed).extracting(SkillEvidenceService.ObservationView::skillName)
                .contains("Java", "Spring Boot", "LangGraph", "Kubernetes");
        var reviewed = skillEvidence.reviewObservations(
                proposed.stream().limit(2).map(SkillEvidenceService.ObservationView::id).toList(),
                SkillReviewStatus.ACCEPTED, "Validated in batch.");
        assertThat(reviewed).allMatch(item -> item.reviewStatus() == SkillReviewStatus.ACCEPTED);
    }

    @Test
    void retainsEveryReviewedAiPreparationConceptInTheCanonicalCatalog() {
        skillEvidence.seedReviewedBacklog();

        var overview = skillEvidence.overview();
        var searchableNames = new HashSet<String>();
        overview.skills().forEach(skill -> {
            searchableNames.add(skill.name().toLowerCase());
            skill.aliases().forEach(alias -> searchableNames.add(alias.toLowerCase()));
        });

        assertThat(searchableNames).contains(
                "modern ai platforms and agent frameworks",
                "microsoft agent framework",
                "semantic kernel",
                "copilot studio",
                "mcp services",
                "langchain",
                "langgraph",
                "autogen",
                "crewai",
                "openai",
                "anthropic",
                "unified aiops platform",
                "ai agents, skills, workflows, and automation capabilities",
                "agent sdks",
                "agentic ai development patterns",
                "agent orchestration and execution frameworks",
                "agent memory systems",
                "agent observability and evaluation",
                "secure enterprise tool integrations",
                "automated aiops remediation workflows",
                "fastmcp",
                "rag",
                "agentic engineering toolchain",
                "kubeflow",
                "mlflow",
                "llm serving and inference frameworks",
                "production-grade ai solution delivery");

        // The preparation item intentionally combines two products; retain both as distinct skills.
        assertThat(searchableNames).contains("azure openai", "azure ai foundry");
    }

    @Test
    void correctsEvidenceAndMergesDuplicateTaxonomyEntries() {
        var opportunity = opportunities.create(new OpportunityService.CreateOpportunity(
                "Example Data", "Data Engineer", "India", WorkMode.REMOTE,
                "Test", "https://example.data/jobs/engineer", "Build streaming pipelines.", Instant.now()));
        var kafka = skillEvidence.createSkill(new SkillEvidenceService.NewSkill(
                "Apache Kafka", SkillCategory.DATA_PLATFORM, null, List.of("Kafka")));
        var duplicate = skillEvidence.createSkill(new SkillEvidenceService.NewSkill(
                "Kafka Streams", SkillCategory.DATA_PLATFORM, null, List.of()));
        var snapshot = skillEvidence.captureSnapshot(new SkillEvidenceService.NewSnapshot(
                opportunity.getId(), SnapshotSourceType.PASTED_DESCRIPTION, "Test",
                "Preferred experience with Kafka Streams."));
        var observation = skillEvidence.createObservation(new SkillEvidenceService.NewObservation(
                snapshot.id(), duplicate.id(), SkillStrength.PREFERRED, "Preferred experience with Kafka Streams.",
                SkillExtractionMethod.DETERMINISTIC));

        var corrected = skillEvidence.correctObservation(observation.id(), new SkillEvidenceService.Correction(
                duplicate.id(), SkillStrength.REQUIRED, "Kafka Streams experience is required.",
                "Corrected during review."));
        assertThat(corrected.reviewStatus()).isEqualTo(SkillReviewStatus.ACCEPTED);
        assertThat(corrected.strength()).isEqualTo(SkillStrength.REQUIRED);

        var merged = skillEvidence.mergeSkill(duplicate.id(), kafka.id());
        assertThat(merged.aliases()).contains("Kafka Streams");
        assertThat(skillEvidence.overview().catalogSize()).isEqualTo(1);
        assertThat(skillEvidence.overview().observations().getFirst().skillName()).isEqualTo("Apache Kafka");
    }
}
