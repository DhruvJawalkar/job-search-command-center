package dev.dhruv.jobsearch.application;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_event")
public class ApplicationEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(length = 48)
    private ApplicationStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private ApplicationStage toStage;

    @Column(columnDefinition = "text")
    private String note;

    @Column(nullable = false)
    private Instant occurredAt;

    protected ApplicationEvent() {
    }

    public ApplicationEvent(JobApplication application, ApplicationStage fromStage, ApplicationStage toStage,
            String note) {
        this.id = UUID.randomUUID();
        this.application = application;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.note = note;
        this.occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ApplicationStage getFromStage() { return fromStage; }
    public ApplicationStage getToStage() { return toStage; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
}

