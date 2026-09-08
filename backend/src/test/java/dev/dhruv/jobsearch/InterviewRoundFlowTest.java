package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import dev.dhruv.jobsearch.application.*;
import dev.dhruv.jobsearch.calendar.*;
import dev.dhruv.jobsearch.opportunity.*;
import dev.dhruv.jobsearch.resume.*;

@SpringBootTest @Transactional
class InterviewRoundFlowTest {
    @Autowired InterviewRoundService rounds;
    @Autowired CalendarService calendar;
    @Autowired CalendarEventRepository events;
    @Autowired ApplicationService applications;
    @Autowired OpportunityService opportunities;
    @Autowired ResumeVariantRepository resumes;
    @Autowired EntityManager em;
    private final Instant start=Instant.parse("2026-10-01T05:30:00Z");

    private UUID application(){
        var resume=resumes.save(new ResumeVariant("Fixture resume","Backend","v1",null,null));
        var opportunity=opportunities.create(new OpportunityService.CreateOpportunity("Fixture Co","Senior Engineer","India",WorkMode.REMOTE,"Test",null,"Fixture",Instant.now()));
        return applications.create(opportunity.getId(),new ApplicationService.CreateApplication(resume.getId(),ApplicationStage.APPLIED,null,null,null,null,null)).getId();
    }
    private InterviewRoundService.Command command(InterviewRound.Status status, Instant date, InterviewRoundService.View prior, UUID eventId){
        return new InterviewRoundService.Command("Coding round",InterviewRound.Type.CODING,status,InterviewRound.Outcome.AWAITING_FEEDBACK,
            "Practice concurrency","Feedback pending",date,date==null?null:60,"Online","https://example.com/meeting",30,eventId,
            prior==null?null:prior.version(),prior==null?null:prior.calendarVersion());
    }
    @Test void preservesRoundAndCalendarEditsWithoutAdvancingApplication(){
        UUID app=application();
        var created=rounds.create(app,command(InterviewRound.Status.SCHEDULED,start,null,null));
        em.clear();
        var loaded=rounds.list(app).getFirst();
        assertThat(loaded.preparationNotes()).isEqualTo("Practice concurrency");
        assertThat(loaded.calendarEventId()).isEqualTo(created.calendarEventId());
        calendar.update(created.calendarEventId(),new CalendarService.EventCommand("Rescheduled",CalendarEventType.INTERVIEW,"Calendar context","Office",null,start.plusSeconds(3600),start.plusSeconds(7200),10));
        em.flush(); em.clear();
        var rescheduled=rounds.list(app).getFirst();
        assertThat(rescheduled.startsAt()).isEqualTo(start.plusSeconds(3600));
        var completed=rounds.update(app,created.id(),command(InterviewRound.Status.COMPLETED,rescheduled.startsAt(),rescheduled,null));
        assertThat(events.findById(created.calendarEventId()).orElseThrow().getReminderDismissedAt()).isNotNull();
        assertThat(calendar.dueReminders(start.plusSeconds(3599))).isEmpty();
        assertThat(completed.debrief()).isEqualTo("Feedback pending");
        assertThat(applications.get(app).getStage()).isEqualTo(ApplicationStage.APPLIED);
        assertThat(applications.events(app)).hasSize(1);
    }
    @Test void linksExistingEventAndCalendarDeletionPreservesRound(){
        UUID app=application();
        var existing=calendar.create(new CalendarService.EventCommand("Existing interview",CalendarEventType.INTERVIEW,null,null,null,start,start.plusSeconds(3600),30));
        var round=rounds.create(app,command(InterviewRound.Status.SCHEDULED,null,null,existing.getId()));
        assertThat(events.count()).isEqualTo(1);
        calendar.delete(existing.getId()); em.flush(); em.clear();
        var reloaded=rounds.list(app).getFirst();
        assertThat(reloaded.id()).isEqualTo(round.id());
        assertThat(reloaded.status()).isEqualTo(InterviewRound.Status.AWAITING_SCHEDULING);
        assertThat(reloaded.calendarEventId()).isNull();
        assertThat(reloaded.preparationNotes()).isEqualTo("Practice concurrency");
    }
    @Test void awaitingAndCancellationKeepHistoryAndSuppressReminders(){
        UUID app=application();
        var pending=rounds.create(app,command(InterviewRound.Status.AWAITING_SCHEDULING,null,null,null));
        assertThat(pending.calendarEventId()).isNull();
        var scheduled=rounds.update(app,pending.id(),command(InterviewRound.Status.SCHEDULED,start,pending,null));
        var cancelled=rounds.update(app,pending.id(),command(InterviewRound.Status.CANCELLED,start,scheduled,null));
        assertThat(cancelled.calendarEventId()).isEqualTo(scheduled.calendarEventId());
        calendar.update(cancelled.calendarEventId(),new CalendarService.EventCommand("Cancelled event edit",CalendarEventType.INTERVIEW,null,null,null,start,start.plusSeconds(3600),30));
        assertThat(calendar.dueReminders(start.minusSeconds(1))).isEmpty();
        var current=rounds.list(app).getFirst();
        rounds.update(app,pending.id(),command(InterviewRound.Status.AWAITING_SCHEDULING,null,current,null));
        assertThat(events.count()).isZero();
    }
    @Test void rejectsMissingScheduleAndCrossApplicationEdits(){
        UUID app=application();
        assertThatThrownBy(()->rounds.create(app,command(InterviewRound.Status.SCHEDULED,null,null,null))).isInstanceOf(IllegalArgumentException.class);
        var pending=rounds.create(app,command(InterviewRound.Status.AWAITING_SCHEDULING,null,null,null));
        assertThatThrownBy(()->rounds.update(UUID.randomUUID(),pending.id(),command(InterviewRound.Status.COMPLETED,null,pending,null)))
            .isInstanceOf(dev.dhruv.jobsearch.shared.NotFoundException.class);
    }
    @Test void rejectsDuplicateEventLinksAndStaleCalendarEdits(){
        UUID app=application();
        var round=rounds.create(app,command(InterviewRound.Status.SCHEDULED,start,null,null));
        assertThatThrownBy(()->rounds.create(app,command(InterviewRound.Status.SCHEDULED,null,null,round.calendarEventId())))
            .isInstanceOf(IllegalArgumentException.class);
        calendar.update(round.calendarEventId(),new CalendarService.EventCommand("Changed",CalendarEventType.INTERVIEW,null,null,null,start.plusSeconds(60),start.plusSeconds(3660),10));
        em.flush();
        assertThatThrownBy(()->rounds.update(app,round.id(),command(InterviewRound.Status.COMPLETED,start,round,null)))
            .isInstanceOf(IllegalStateException.class);
    }
}
