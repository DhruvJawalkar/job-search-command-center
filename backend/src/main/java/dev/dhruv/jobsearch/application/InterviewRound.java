package dev.dhruv.jobsearch.application;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import dev.dhruv.jobsearch.calendar.CalendarEvent;

@Entity @Table(name="interview_round")
public class InterviewRound {
    public enum Type { RECRUITER, CODING, SYSTEM_DESIGN, BEHAVIORAL, HIRING_MANAGER, CUSTOM }
    public enum Status { AWAITING_SCHEDULING, SCHEDULED, COMPLETED, CANCELLED }
    public enum Outcome { NOT_RECORDED, AWAITING_FEEDBACK, ADVANCED, REJECTED }
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="application_id") private JobApplication application;
    @OneToOne @JoinColumn(name="calendar_event_id",unique=true) private CalendarEvent calendarEvent;
    @Column(nullable=false,length=240) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private Type roundType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private Outcome outcome;
    @Column(columnDefinition="text") private String preparationNotes;
    @Column(columnDefinition="text") private String debrief;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @Version private long version;
    protected InterviewRound() {}
    public InterviewRound(JobApplication application) { id=UUID.randomUUID(); this.application=application; }
    public void update(String title, Type type, Status status, Outcome outcome, String preparationNotes, String debrief) {
        this.title=title.trim(); this.roundType=type; this.status=status; this.outcome=outcome;
        this.preparationNotes=preparationNotes; this.debrief=debrief;
    }
    public void setCalendarEvent(CalendarEvent event) { this.calendarEvent=event; }
    public void unschedule() { calendarEvent=null; if(status==Status.SCHEDULED) status=Status.AWAITING_SCHEDULING; }
    public boolean isTerminal() { return status==Status.COMPLETED || status==Status.CANCELLED; }
    @PrePersist void createTimestamp(){ createdAt=Instant.now(); updatedAt=createdAt; }
    @PreUpdate void updateTimestamp(){ updatedAt=Instant.now(); }
    public UUID getId(){return id;} public JobApplication getApplication(){return application;}
    public CalendarEvent getCalendarEvent(){return calendarEvent;} public String getTitle(){return title;}
    public Type getRoundType(){return roundType;} public Status getStatus(){return status;} public Outcome getOutcome(){return outcome;}
    public String getPreparationNotes(){return preparationNotes;} public String getDebrief(){return debrief;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
