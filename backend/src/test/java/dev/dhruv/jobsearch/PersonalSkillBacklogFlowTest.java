package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.preparation.PreparationCategory;
import dev.dhruv.jobsearch.preparation.PreparationService;
import dev.dhruv.jobsearch.skill.LearningResourceStatus;
import dev.dhruv.jobsearch.skill.LearningResourceType;
import dev.dhruv.jobsearch.skill.PersonalSkillBacklogService;
import dev.dhruv.jobsearch.skill.PersonalSkillStatus;
import dev.dhruv.jobsearch.skill.ProjectEvidenceType;
import dev.dhruv.jobsearch.skill.SkillEvidenceService;
import dev.dhruv.jobsearch.skill.SkillProficiencyLevel;

@SpringBootTest
@Transactional
class PersonalSkillBacklogFlowTest {
    @Autowired SkillEvidenceService skillEvidence;
    @Autowired PersonalSkillBacklogService backlog;
    @Autowired PreparationService preparation;

    @Test
    void synchronizesPreparationWithoutLosingCompoundGoalsAndKeepsLinksReplaySafe() {
        skillEvidence.seedReviewedBacklog();
        var track = preparation.createTrack(new PreparationService.NewTrack(
                "Learning about Agentic Systems & Incorporating them to worflows", "Agent learning",
                PreparationCategory.AI_DATA, null, 0));
        var milestone = preparation.createMilestone(track.getId(), new PreparationService.NewMilestone(
                "AI skill backlog", "Reviewed job-opening skills", null, 0));
        preparation.createItem(milestone.getId(), new PreparationService.NewItem(
                "LangGraph", "Stateful workflows", 3, 120, null, null, "LangGraph", null, 0));
        preparation.createItem(milestone.getId(), new PreparationService.NewItem(
                "Azure OpenAI and Azure AI Foundry", "Microsoft AI platform", 3, 180,
                null, null, "Azure OpenAI, Azure AI Foundry", null, 1));

        var first = backlog.synchronizeAiPreparationBacklog();
        var second = backlog.synchronizeAiPreparationBacklog();

        assertThat(first.preparationItemsScanned()).isEqualTo(2);
        assertThat(first.backlogItemsCreated()).isEqualTo(3);
        assertThat(first.linksCreated()).isEqualTo(3);
        assertThat(second.backlogItemsCreated()).isZero();
        assertThat(second.linksCreated()).isZero();
        assertThat(backlog.overview().items()).extracting(PersonalSkillBacklogService.BacklogItemView::skillName)
                .containsExactlyInAnyOrder("LangGraph", "Azure OpenAI", "Azure AI Foundry");
        assertThat(backlog.overview().items()).allMatch(item -> item.status() == PersonalSkillStatus.BACKLOG);
        assertThat(backlog.overview().activeCount()).isZero();
    }

    @Test
    void persistsLevelsRationaleResourcesAndProjectEvidence() {
        skillEvidence.seedReviewedBacklog();
        var skill = skillEvidence.overview().skills().stream().filter(item -> item.name().equals("LangGraph"))
                .findFirst().orElseThrow();
        var saved = backlog.update(skill.id(), new PersonalSkillBacklogService.UpdateBacklog(
                SkillProficiencyLevel.PRACTICING, SkillProficiencyLevel.PROFICIENT, 1,
                "High demand in target agent-platform roles.", PersonalSkillStatus.ACTIVE,
                LocalDate.now().plusWeeks(2)));
        backlog.addResource(saved.id(), new PersonalSkillBacklogService.NewResource(
                "LangGraph documentation", "https://langchain-ai.github.io/langgraph/",
                LearningResourceType.DOCUMENTATION, LearningResourceStatus.IN_PROGRESS, "Build the durable-agent tutorial."));
        backlog.addProjectEvidence(saved.id(), new PersonalSkillBacklogService.NewProjectEvidence(
                "Governed agent project", "https://example.test/governed-agent", ProjectEvidenceType.PROJECT,
                "Human-approved remediation workflow", "Traceable rollback decision", null));

        var item = backlog.overview().items().getFirst();
        assertThat(item.currentLevel()).isEqualTo(SkillProficiencyLevel.PRACTICING);
        assertThat(item.targetLevel()).isEqualTo(SkillProficiencyLevel.PROFICIENT);
        assertThat(item.priority()).isEqualTo(1);
        assertThat(item.resources()).hasSize(1);
        assertThat(item.projectEvidence()).hasSize(1);
    }
}
