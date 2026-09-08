package dev.dhruv.jobsearch.calendar;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/calendar")
@org.springframework.transaction.annotation.Transactional
public class CalendarController {

    private final CalendarService service;
    private final dev.dhruv.jobsearch.application.InterviewRoundRepository rounds;

    public CalendarController(CalendarService service, dev.dhruv.jobsearch.application.InterviewRoundRepository rounds) {
        this.service = service;
        this.rounds = rounds;
    }

    private EventResponse response(CalendarEvent event) {
        return EventResponse.from(event, rounds.findByCalendarEventId(event.getId()).orElse(null));
    }

    @GetMapping("/events")
    List<EventResponse> events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.list(from, to).stream().map(this::response).toList();
    }

    @GetMapping("/reminders/due")
    List<EventResponse> dueReminders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {
        return service.dueReminders(asOf).stream().map(this::response).toList();
    }

    @PostMapping("/events")
    ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
        CalendarEvent event = service.create(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/calendar/events/" + event.getId())).body(response(event));
    }

    @PutMapping("/events/{id}")
    EventResponse update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
        return response(service.update(id, request.toCommand()));
    }

    @PostMapping("/events/{id}/reminder/dismiss")
    EventResponse dismissReminder(@PathVariable UUID id) {
        return response(service.dismissReminder(id));
    }

    @DeleteMapping("/events/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record EventRequest(
            @NotBlank @Size(max = 240) String title,
            CalendarEventType eventType,
            @Size(max = 10_000) String description,
            @Size(max = 500) String location,
            @Size(max = 2_000) String meetingUrl,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Positive Integer reminderMinutesBefore) {
        CalendarService.EventCommand toCommand() {
            return new CalendarService.EventCommand(title, eventType, description, location, meetingUrl,
                    startsAt, endsAt, reminderMinutesBefore);
        }
    }

    public record EventResponse(UUID id, String title, CalendarEventType eventType, String description,
            String location, String meetingUrl, Instant startsAt, Instant endsAt, Integer reminderMinutesBefore,
            Instant reminderAt, Instant reminderDismissedAt, Instant createdAt, Instant updatedAt,
            UUID interviewRoundId, UUID applicationId, String interviewStatus) {
        static EventResponse from(CalendarEvent event, dev.dhruv.jobsearch.application.InterviewRound round) {
            return new EventResponse(event.getId(), event.getTitle(), event.getEventType(), event.getDescription(),
                    event.getLocation(), event.getMeetingUrl(), event.getStartsAt(), event.getEndsAt(),
                    event.getReminderMinutesBefore(), event.getReminderAt(), event.getReminderDismissedAt(),
                    event.getCreatedAt(), event.getUpdatedAt(),round==null?null:round.getId(),
                    round==null?null:round.getApplication().getId(),round==null?null:round.getStatus().name());
        }
    }
}
