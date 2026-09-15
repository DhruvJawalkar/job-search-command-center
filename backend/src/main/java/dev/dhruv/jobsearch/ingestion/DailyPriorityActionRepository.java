package dev.dhruv.jobsearch.ingestion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DailyPriorityActionRepository extends JpaRepository<DailyPriorityAction, UUID> {

    Optional<DailyPriorityAction> findByActionDateAndPriorityRank(LocalDate actionDate, int priorityRank);

    List<DailyPriorityAction> findByActionDateOrderByPriorityRankAsc(LocalDate actionDate);

    @Query("select max(a.actionDate) from DailyPriorityAction a")
    LocalDate findLatestActionDate();
}
