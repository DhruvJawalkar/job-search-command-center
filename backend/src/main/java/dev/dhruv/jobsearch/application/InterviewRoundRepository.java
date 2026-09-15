package dev.dhruv.jobsearch.application;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InterviewRoundRepository extends JpaRepository<InterviewRound,UUID> {
    List<InterviewRound> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
    Optional<InterviewRound> findByCalendarEventId(UUID calendarEventId);
}
