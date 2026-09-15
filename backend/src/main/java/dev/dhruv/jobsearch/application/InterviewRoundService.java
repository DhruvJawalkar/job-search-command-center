package dev.dhruv.jobsearch.application;

import java.time.Instant;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.dhruv.jobsearch.calendar.*;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class InterviewRoundService {
    private final InterviewRoundRepository rounds;
    private final JobApplicationRepository applications;
    private final CalendarEventRepository events;
    public InterviewRoundService(InterviewRoundRepository rounds, JobApplicationRepository applications, CalendarEventRepository events){
        this.rounds=rounds; this.applications=applications; this.events=events;
    }
    @Transactional(readOnly=true) public List<View> list(UUID applicationId){
        application(applicationId); return rounds.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream().map(View::from).toList();
    }
    @Transactional public View create(UUID applicationId, Command command){
        var round=new InterviewRound(application(applicationId)); apply(round,command); return View.from(rounds.saveAndFlush(round));
    }
    @Transactional public View update(UUID applicationId, UUID id, Command command){
        var round=rounds.findById(id).filter(r->r.getApplication().getId().equals(applicationId))
            .orElseThrow(()->new NotFoundException("Interview round was not found for this application."));
        if(command.version()==null || command.version()!=round.getVersion() ||
            (round.getCalendarEvent()!=null && !Objects.equals(command.calendarVersion(),round.getCalendarEvent().getVersion()))) {
            throw new IllegalStateException("This round or calendar event changed. Cancel edits and reload before saving.");
        }
        if(command.existingEventId()!=null) throw new IllegalArgumentException("Link an existing event only when creating a round.");
        apply(round,command); rounds.flush(); return View.from(round);
    }
    private JobApplication application(UUID id){return applications.findById(id).orElseThrow(()->new NotFoundException("Application not found."));}
    private void apply(InterviewRound round, Command c){
        if(c.title()==null || c.title().isBlank() || c.type()==null || c.status()==null || c.outcome()==null) throw new IllegalArgumentException("Round title, type, status and outcome are required.");
        CalendarEvent event=round.getCalendarEvent();
        if(c.existingEventId()!=null){
            if(rounds.findByCalendarEventId(c.existingEventId()).isPresent()) throw new IllegalArgumentException("That event is already linked to an interview round.");
            event=events.findById(c.existingEventId()).orElseThrow(()->new NotFoundException("Calendar event not found."));
        }
        if(c.status()==InterviewRound.Status.AWAITING_SCHEDULING){
            if(c.existingEventId()!=null || c.startsAt()!=null) throw new IllegalArgumentException("An awaiting round cannot have a schedule.");
            // Change status before flushing the detached event: preserve the DB schedule constraint.
            round.update(c.title(),c.type(),c.status(),c.outcome(),c.preparationNotes(),c.debrief());
            if(event!=null){round.setCalendarEvent(null); rounds.flush(); events.delete(event); event=null;}
        } else if(c.existingEventId()==null && c.startsAt()!=null){
            if(c.durationMinutes()==null || c.durationMinutes()<1 || c.durationMinutes()>1440) throw new IllegalArgumentException("Duration must be 1–1440 minutes.");
            String title=round.getApplication().getOpportunity().getCompanyName()+" · "+c.title().trim();
            title=title.substring(0,Math.min(title.length(),240));
            var type=c.type()==InterviewRound.Type.RECRUITER ? CalendarEventType.RECRUITER_CALL : CalendarEventType.INTERVIEW;
            Instant end=c.startsAt().plusSeconds(c.durationMinutes()*60L);
            if(event==null) event=events.save(new CalendarEvent(title,type,null,c.location(),c.meetingUrl(),c.startsAt(),end,c.reminderMinutesBefore()));
            else event.update(title,type,event.getDescription(),c.location(),c.meetingUrl(),c.startsAt(),end,c.reminderMinutesBefore());
        }
        if(c.status()==InterviewRound.Status.SCHEDULED && event==null) throw new IllegalArgumentException("Scheduled rounds need a date and duration or an existing calendar event.");
        round.update(c.title(),c.type(),c.status(),c.outcome(),c.preparationNotes(),c.debrief());
        round.setCalendarEvent(event);
        if(event!=null && round.isTerminal()) event.dismissReminder();
        // Application stage is deliberately not changed by interview tracking.
    }
    public record Command(@NotBlank @Size(max=240) String title, @NotNull InterviewRound.Type type,
        @NotNull InterviewRound.Status status, @NotNull InterviewRound.Outcome outcome,
        @Size(max=10000) String preparationNotes, @Size(max=10000) String debrief,
        Instant startsAt, @Min(1) @Max(1440) Integer durationMinutes, @Size(max=500) String location,
        @Size(max=2000) String meetingUrl, @Positive @Max(10080) Integer reminderMinutesBefore,
        UUID existingEventId, Long version, Long calendarVersion){}
    public record View(UUID id, UUID applicationId, String title, InterviewRound.Type type, InterviewRound.Status status,
        InterviewRound.Outcome outcome, String preparationNotes, String debrief, UUID calendarEventId, Instant startsAt,
        Integer durationMinutes, String location, String meetingUrl, Integer reminderMinutesBefore, long version, Long calendarVersion){
        static View from(InterviewRound r){var e=r.getCalendarEvent(); return new View(r.getId(),r.getApplication().getId(),r.getTitle(),r.getRoundType(),r.getStatus(),
            r.getOutcome(),r.getPreparationNotes(),r.getDebrief(),e==null?null:e.getId(),e==null?null:e.getStartsAt(),
            e==null?null:(int)java.time.Duration.between(e.getStartsAt(),e.getEndsAt()).toMinutes(),e==null?null:e.getLocation(),
            e==null?null:e.getMeetingUrl(),e==null?null:e.getReminderMinutesBefore(),r.getVersion(),e==null?null:e.getVersion());}
    }
}
