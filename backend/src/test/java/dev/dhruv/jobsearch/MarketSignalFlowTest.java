package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;
import dev.dhruv.jobsearch.skill.MarketSignalService;
import dev.dhruv.jobsearch.skill.RoleFamily;
import dev.dhruv.jobsearch.skill.SkillCategory;
import dev.dhruv.jobsearch.skill.SkillEvidenceService;
import dev.dhruv.jobsearch.skill.SkillExtractionMethod;
import dev.dhruv.jobsearch.skill.SkillReviewStatus;
import dev.dhruv.jobsearch.skill.SkillStrength;
import dev.dhruv.jobsearch.skill.SnapshotSourceType;

@SpringBootTest
@Transactional
class MarketSignalFlowTest {

    @Autowired SkillEvidenceService skillEvidence;
    @Autowired MarketSignalService marketSignals;
    @Autowired OpportunityService opportunities;

    @Test
    void calculatesAcceptedOnlyCohortFrequencyTrendAndEvidenceDrilldown() {
        var java = skillEvidence.createSkill(new SkillEvidenceService.NewSkill(
                "Java", SkillCategory.BACKEND, null, List.of()));
        var kafka = skillEvidence.createSkill(new SkillEvidenceService.NewSkill(
                "Apache Kafka", SkillCategory.DATA_PLATFORM, null, List.of("Kafka")));

        var currentStaff = opportunities.create(new OpportunityService.CreateOpportunity(
                "Current Example One", "Staff Data Platform Engineer", "India", WorkMode.REMOTE,
                "Test", "https://example.test/current-one", "Java and Kafka are required.",
                Instant.parse("2026-08-20T00:00:00Z")));
        var currentSenior = opportunities.create(new OpportunityService.CreateOpportunity(
                "Current Example Two", "Senior Data Engineer", "India", WorkMode.HYBRID,
                "Test", "https://example.test/current-two", "Java is preferred; Kafka is mentioned.",
                Instant.parse("2026-08-10T00:00:00Z")));
        var previousStaff = opportunities.create(new OpportunityService.CreateOpportunity(
                "Previous Example", "Staff Data Engineer", "India", WorkMode.REMOTE,
                "Test", "https://example.test/previous", "Java is required.",
                Instant.parse("2026-07-15T00:00:00Z")));
        var summaryOnly = opportunities.create(new OpportunityService.CreateOpportunity(
                "Workbook Summary", "Senior Data Engineer", "India", WorkMode.REMOTE,
                "Daily high-fit workbook", "https://example.test/summary", "Java is required.",
                Instant.parse("2026-08-25T00:00:00Z")));

        accept(currentStaff.getId(), java.id(), SkillStrength.REQUIRED, "Java is required.");
        accept(currentStaff.getId(), kafka.id(), SkillStrength.REQUIRED, "Kafka is required.");
        accept(currentSenior.getId(), java.id(), SkillStrength.PREFERRED, "Java is preferred.");
        propose(currentSenior.getId(), kafka.id(), SkillStrength.MENTIONED, "Kafka is mentioned.");
        accept(previousStaff.getId(), java.id(), SkillStrength.REQUIRED, "Java is required.");
        var summarySnapshot = skillEvidence.captureSnapshot(new SkillEvidenceService.NewSnapshot(
                summaryOnly.getId(), SnapshotSourceType.OPPORTUNITY_DESCRIPTION, "Workbook role summary",
                "Java is required."));
        var summaryObservation = skillEvidence.createObservation(new SkillEvidenceService.NewObservation(
                summarySnapshot.id(), java.id(), SkillStrength.REQUIRED, "Java is required.",
                SkillExtractionMethod.DETERMINISTIC));
        skillEvidence.reviewObservation(summaryObservation.id(), SkillReviewStatus.ACCEPTED,
                "Accepted historically before full-description enforcement.");

        var result = marketSignals.overview(new MarketSignalService.MarketSignalQuery(
                LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"),
                RoleFamily.DATA_PLATFORM, null, "Unclassified", null));

        assertThat(result.currentSampleSize()).isEqualTo(2);
        assertThat(result.previousSampleSize()).isEqualTo(1);
        assertThat(result.acceptedEvidenceCount()).isEqualTo(3);
        var javaSignal = result.signals().stream().filter(item -> item.skillName().equals("Java")).findFirst().orElseThrow();
        assertThat(javaSignal.currentOpeningCount()).isEqualTo(2);
        assertThat(javaSignal.currentFrequencyPercent()).isEqualTo(100.0);
        assertThat(javaSignal.requiredCount()).isEqualTo(1);
        assertThat(javaSignal.preferredCount()).isEqualTo(1);
        assertThat(javaSignal.trendDeltaPercentagePoints()).isEqualTo(0.0);
        var kafkaSignal = result.signals().stream().filter(item -> item.skillName().equals("Apache Kafka")).findFirst().orElseThrow();
        assertThat(kafkaSignal.currentOpeningCount()).isEqualTo(1);
        assertThat(kafkaSignal.currentFrequencyPercent()).isEqualTo(50.0);
        assertThat(kafkaSignal.trendDeltaPercentagePoints()).isEqualTo(50.0);

        var evidence = marketSignals.evidence(kafka.id(), new MarketSignalService.MarketSignalQuery(
                LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"),
                RoleFamily.DATA_PLATFORM, null, "Unclassified", null));
        assertThat(evidence.evidence()).hasSize(1);
        assertThat(evidence.evidence().getFirst().companyName()).isEqualTo("Current Example One");
        assertThat(evidence.evidence().getFirst().strength()).isEqualTo(SkillStrength.REQUIRED);
    }

    private void accept(java.util.UUID opportunityId, java.util.UUID skillId,
            SkillStrength strength, String evidence) {
        var observation = propose(opportunityId, skillId, strength, evidence);
        skillEvidence.reviewObservation(observation.id(), SkillReviewStatus.ACCEPTED, "Verified for test.");
    }

    private SkillEvidenceService.ObservationView propose(java.util.UUID opportunityId, java.util.UUID skillId,
            SkillStrength strength, String evidence) {
        var snapshot = skillEvidence.captureSnapshot(new SkillEvidenceService.NewSnapshot(
                opportunityId, SnapshotSourceType.PASTED_DESCRIPTION, "Test description", evidence));
        return skillEvidence.createObservation(new SkillEvidenceService.NewObservation(
                snapshot.id(), skillId, strength, evidence, SkillExtractionMethod.MANUAL));
    }
}
