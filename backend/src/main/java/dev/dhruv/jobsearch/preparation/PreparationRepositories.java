package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PreparationTrackRepository extends JpaRepository<PreparationTrack,UUID>{
    List<PreparationTrack> findAllByOrderByDisplayOrderAscCreatedAtAsc();
    @Query("select coalesce(max(t.displayOrder),-1) from PreparationTrack t") int findMaxDisplayOrder();
}
interface PreparationTrackResourceRepository extends JpaRepository<PreparationTrackResource,UUID>{List<PreparationTrackResource> findAllByOrderByCreatedAtAsc();}
interface PreparationMilestoneRepository extends JpaRepository<PreparationMilestone,UUID>{List<PreparationMilestone> findAllByOrderByDisplayOrderAscCreatedAtAsc();}
interface PreparationItemRepository extends JpaRepository<PreparationItem,UUID>{List<PreparationItem> findAllByOrderByDisplayOrderAscPriorityAscCreatedAtAsc();long countByStatusIn(List<PrepItemStatus> statuses);}
interface PracticeSessionRepository extends JpaRepository<PracticeSession,UUID>{
    List<PracticeSession> findTop8ByOrderByPracticedAtDesc();
    List<PracticeSession> findByPrepItemIdOrderByPracticedAtDesc(UUID prepItemId);
    @Query("select coalesce(sum(s.durationMinutes),0) from PracticeSession s where s.practicedAt >= :since") long totalMinutesSince(@Param("since") Instant since);
    long countByPracticedAtGreaterThanEqual(Instant since);
}
interface DailyPrepCommitmentRepository extends JpaRepository<DailyPrepCommitment,UUID>{Optional<DailyPrepCommitment> findByCommitmentDate(LocalDate date);}
interface PreparationSprintRepository extends JpaRepository<PreparationSprint,UUID>{
    Optional<PreparationSprint> findFirstByStatusOrderByStartDateDesc(SprintStatus status);
    List<PreparationSprint> findTop4ByStatusOrderByStartDateDesc(SprintStatus status);
}
interface PreparationSprintItemRepository extends JpaRepository<PreparationSprintItem,UUID>{
    List<PreparationSprintItem> findBySprintIdOrderByAddedAtAsc(UUID sprintId);
    boolean existsBySprintIdAndItemId(UUID sprintId,UUID itemId);
}
