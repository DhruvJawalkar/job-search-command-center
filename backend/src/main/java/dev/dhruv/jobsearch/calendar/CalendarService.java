package dev.dhruv.jobsearch.calendar;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class CalendarService {

    private final CalendarEventRepository events;
    private final dev.dhruv.jobsearch.application.InterviewRoundRepository rounds;

    public CalendarService(CalendarEventRepository events, dev.dhruv.jobsearch.application.InterviewRoundRepository rounds) {
        this.events = events;
        this.rounds = rounds;
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> list(Instant from, Instant to) {
        Instant effectiveFrom = from == null ? Instant.now().minus(30, ChronoUnit.DAYS) : from;
        Instant effectiveTo = to == null ? Instant.now().plus(120, ChronoUnit.DAYS) : to;
        if (!effectiveTo.isAfter(effectiveFrom)) throw new IllegalArgumentException("Calendar range end must be after its start.");
        return events.findByStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(effectiveTo, effectiveFrom);
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> dueReminders(Instant asOf) {
        Instant effectiveAsOf = asOf == null ? Instant.now() : asOf;
        return events.findByReminderAtLessThanEqualAndReminderDismissedAtIsNullAndEndsAtGreaterThanOrderByReminderAtAsc(
                effectiveAsOf, effectiveAsOf);
    }

    @Transactional
    public CalendarEvent create(EventCommand command) {
        return events.save(new CalendarEvent(command.title(), command.eventType(), command.description(), command.location(),
                command.meetingUrl(), command.startsAt(), command.endsAt(), command.reminderMinutesBefore()));
    }

    @Transactional
    public CalendarEvent update(UUID id, EventCommand command) {
        CalendarEvent event = get(id);
        event.update(command.title(), command.eventType(), command.description(), command.location(), command.meetingUrl(),
                command.startsAt(), command.endsAt(), command.reminderMinutesBefore());
        rounds.findByCalendarEventId(id).filter(r -> r.isTerminal()).ifPresent(r -> event.dismissReminder());
        return event;
    }

    @Transactional
    public CalendarEvent dismissReminder(UUID id) {
        CalendarEvent event = get(id);
        event.dismissReminder();
        return event;
    }

    @Transactional
    public void delete(UUID id) {
        CalendarEvent event=get(id);
        rounds.findByCalendarEventId(id).ifPresent(round -> { round.unschedule(); rounds.flush(); });
        events.delete(event);
    }

    private CalendarEvent get(UUID id) {
        return events.findById(id).orElseThrow(() -> new NotFoundException("Calendar event " + id + " was not found."));
    }

    public record EventCommand(String title, CalendarEventType eventType, String description, String location,
            String meetingUrl, Instant startsAt, Instant endsAt, Integer reminderMinutesBefore) {}
}
