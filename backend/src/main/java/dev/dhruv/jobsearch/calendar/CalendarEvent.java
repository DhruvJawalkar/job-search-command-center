package dev.dhruv.jobsearch.calendar;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "calendar_event")
public class CalendarEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = 240)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CalendarEventType eventType;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 500)
    private String location;

    @Column(length = 2000)
    private String meetingUrl;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private Instant endsAt;

    private Integer reminderMinutesBefore;

    private Instant reminderAt;

    private Instant reminderDismissedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CalendarEvent() {
    }

    public CalendarEvent(String title, CalendarEventType eventType, String description, String location,
            String meetingUrl, Instant startsAt, Instant endsAt, Integer reminderMinutesBefore) {
        this.id = UUID.randomUUID();
        update(title, eventType, description, location, meetingUrl, startsAt, endsAt, reminderMinutesBefore);
    }

    public void update(String title, CalendarEventType eventType, String description, String location,
            String meetingUrl, Instant startsAt, Instant endsAt, Integer reminderMinutesBefore) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Event title is required.");
        if (startsAt == null || endsAt == null) throw new IllegalArgumentException("Event start and end times are required.");
        if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("Event end time must be after its start time.");
        if (reminderMinutesBefore != null && reminderMinutesBefore <= 0) {
            throw new IllegalArgumentException("Reminder lead time must be greater than zero.");
        }
        this.title = title.trim();
        this.eventType = eventType == null ? CalendarEventType.MEETING : eventType;
        this.description = optional(description);
        this.location = optional(location);
        this.meetingUrl = optional(meetingUrl);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reminderMinutesBefore = reminderMinutesBefore;
        this.reminderAt = reminderMinutesBefore == null ? null : startsAt.minus(reminderMinutesBefore, ChronoUnit.MINUTES);
        this.reminderDismissedAt = null;
    }

    public void dismissReminder() {
        if (reminderAt != null) reminderDismissedAt = Instant.now();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public CalendarEventType getEventType() { return eventType; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getMeetingUrl() { return meetingUrl; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Integer getReminderMinutesBefore() { return reminderMinutesBefore; }
    public Instant getReminderAt() { return reminderAt; }
    public Instant getReminderDismissedAt() { return reminderDismissedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
