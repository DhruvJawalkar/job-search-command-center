package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import dev.dhruv.jobsearch.preparation.*;

@SpringBootTest
@Transactional
class PreparationFlowTest {
    @Autowired PreparationService service;

    @Test
    void plansTodayAndPreservesPracticeEvidence() {
        var track=service.createTrack(new PreparationService.NewTrack("System Design","Production design fluency",PreparationCategory.SYSTEM_DESIGN,LocalDate.now().plusWeeks(4),0));
        var milestone=service.createMilestone(track.getId(),new PreparationService.NewMilestone("Three end-to-end designs","Timed interview practice",LocalDate.now().plusWeeks(2),0));
        var item=service.createItem(milestone.getId(),new PreparationService.NewItem("Design a notification service","Cover delivery guarantees",1,45,LocalDate.now(),null,"Queues and idempotency",null,0));
        assertThat(item.getStatus()).isEqualTo(PrepItemStatus.BACKLOG);

        service.setToday(new PreparationService.SetCommitment(item.getId(),45,"Finish a defendable design"));
        service.logSession(item.getId(),new PreparationService.NewSession(PracticeSessionType.DRILL,Instant.now(),48,"Completed the design","Weak capacity estimate","Repeat estimates",2,4,LocalDate.now().plusDays(3)));

        var overview=service.overview();
        assertThat(overview.tracks()).hasSize(1);
        assertThat(overview.tracks().getFirst().milestones().getFirst().items().getFirst().status()).isEqualTo(PrepItemStatus.IN_PROGRESS);
        assertThat(overview.today().status()).isEqualTo(CommitmentStatus.COMPLETED);
        assertThat(overview.stats().sessionsThisWeek()).isEqualTo(1);
        assertThat(overview.stats().minutesThisWeek()).isEqualTo(48);
        assertThat(overview.recentSessions().getFirst().confidenceAfter()).isEqualTo(4);
    }

    @Test
    void movesSprintWorkIntoReview() {
        var track=service.createTrack(new PreparationService.NewTrack("Concurrent systems","Production engineering drills",PreparationCategory.JAVA_BACKEND,LocalDate.now().plusWeeks(2),0));
        var milestone=service.createMilestone(track.getId(),new PreparationService.NewMilestone("Concurrency sprint","Reviewable solutions",LocalDate.now().plusWeeks(2),0));
        var item=service.createItem(milestone.getId(),new PreparationService.NewItem("Build bounded executor","Document correctness and memory limits",1,60,LocalDate.now(),LocalDate.now().plusDays(3),"Java concurrency",null,0));

        service.updateItem(item.getId(),new PreparationService.UpdateItem(PrepItemStatus.IN_REVIEW,3,null,null,null));

        assertThat(service.overview().tracks().getFirst().milestones().getFirst().items().getFirst().status()).isEqualTo(PrepItemStatus.IN_REVIEW);
        assertThat(service.overview().tracks().getFirst().milestones().getFirst().items().getFirst().priority()).isEqualTo(3);
        assertThat(service.overview().stats().readyItems()).isEqualTo(1);
    }

    @Test
    void editsAndDeletesAStory() {
        var track=service.createTrack(new PreparationService.NewTrack("Backend systems","Practice track",PreparationCategory.JAVA_BACKEND,LocalDate.now().plusWeeks(3),0));
        var story=service.createMilestone(track.getId(),new PreparationService.NewMilestone("Initial story","First outcome",null,0));

        service.updateMilestone(story.getId(),new PreparationService.UpdateMilestone("Revised story","Revised outcome",LocalDate.now().plusWeeks(1)));
        assertThat(service.overview().tracks().getFirst().milestones().getFirst().title()).isEqualTo("Revised story");

        service.deleteMilestone(story.getId());
        assertThat(service.overview().tracks().getFirst().milestones()).isEmpty();
    }

    @Test
    void persistsExplicitSprintMembershipAndPreventsEarlyClosure() {
        var track=service.createTrack(new PreparationService.NewTrack("Sprint persistence","Stable two-week board",PreparationCategory.SYSTEM_DESIGN,null,0));
        var story=service.createMilestone(track.getId(),new PreparationService.NewMilestone("Sprint story","Deliverable",null,0));
        var item=service.createItem(story.getId(),new PreparationService.NewItem("Keep this task on the board","Explicit membership",1,45,null,null,null,null,0));

        var sprint=service.startSprint();
        service.addItemToCurrentSprint(item.getId());

        var overview=service.overview();
        assertThat(overview.activeSprint().id()).isEqualTo(sprint.id());
        assertThat(overview.activeSprint().endDate()).isEqualTo(overview.activeSprint().startDate().plusDays(13));
        assertThat(overview.activeSprint().tasks()).extracting(PreparationService.SprintTaskView::itemId).containsExactly(item.getId());
        assertThat(overview.tracks().getFirst().milestones().getFirst().items().getFirst().status()).isEqualTo(PrepItemStatus.READY);
        assertThatThrownBy(() -> service.closeSprint(sprint.id())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can be closed on or after");
    }

    @Test
    void persistsACompleteTrackReorder() {
        var first=service.createTrack(new PreparationService.NewTrack("First","",PreparationCategory.SYSTEM_DESIGN,null,0));
        var second=service.createTrack(new PreparationService.NewTrack("Second","",PreparationCategory.JAVA_BACKEND,null,0));
        var third=service.createTrack(new PreparationService.NewTrack("Third","",PreparationCategory.AI_DATA,null,0));

        service.reorderTracks(java.util.List.of(third.getId(),first.getId(),second.getId()));

        assertThat(service.overview().tracks()).extracting(PreparationService.TrackView::name)
                .containsExactly("Third","First","Second");
        assertThat(service.overview().tracks()).extracting(PreparationService.TrackView::displayOrder)
                .containsExactly(0,1,2);
    }

    @Test
    void savesAndRemovesTrackResources() {
        var track=service.createTrack(new PreparationService.NewTrack("API design","",PreparationCategory.API_DESIGN,null,0));
        var resource=service.createResource(track.getId(),new PreparationService.NewTrackResource("API guidance","https://example.com/api","Reference notes"));

        assertThat(service.overview().tracks().getFirst().resources()).extracting(PreparationService.TrackResourceView::title)
                .containsExactly("API guidance");

        service.deleteResource(resource.getId());
        assertThat(service.overview().tracks().getFirst().resources()).isEmpty();
    }

    @Test
    void editsTaskDetailsAndReturnsItsSessionHistory() {
        var track=service.createTrack(new PreparationService.NewTrack("API design","",PreparationCategory.API_DESIGN,null,0));
        var story=service.createMilestone(track.getId(),new PreparationService.NewMilestone("API story","",null,0));
        var item=service.createItem(story.getId(),new PreparationService.NewItem("Draft API","First pass",2,45,null,null,"REST",null,0));

        service.editItem(item.getId(),new PreparationService.EditItem("Design production API","Versioning and idempotency",
                PrepItemStatus.IN_PROGRESS,1,75,LocalDate.now(),LocalDate.now().plusDays(2),"REST, pagination",
                LocalDate.now().plusDays(4),null));
        service.logSession(item.getId(),new PreparationService.NewSession(PracticeSessionType.BUILD,Instant.now(),35,
                "Completed resource model",null,"Add error contract",2,4,LocalDate.now().plusDays(4)));

        var updated=service.overview().tracks().getFirst().milestones().getFirst().items().getFirst();
        assertThat(updated.title()).isEqualTo("Design production API");
        assertThat(updated.estimatedMinutes()).isEqualTo(75);
        assertThat(updated.status()).isEqualTo(PrepItemStatus.IN_PROGRESS);
        assertThat(service.sessionsForItem(item.getId())).hasSize(1);
        assertThat(service.sessionsForItem(item.getId()).getFirst().durationMinutes()).isEqualTo(35);
    }
}
