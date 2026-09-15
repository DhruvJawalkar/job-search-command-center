package dev.dhruv.jobsearch.calendar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {
    List<CalendarEvent> findByStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(Instant to, Instant from);

    List<CalendarEvent> findByReminderAtLessThanEqualAndReminderDismissedAtIsNullAndEndsAtGreaterThanOrderByReminderAtAsc(
            Instant asOf, Instant notEndedAt);
}
