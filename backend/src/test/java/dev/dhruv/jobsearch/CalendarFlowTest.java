package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.calendar.CalendarEventType;
import dev.dhruv.jobsearch.calendar.CalendarService;

@SpringBootTest
@Transactional
class CalendarFlowTest {

    @Autowired CalendarService service;

    @Test
    void persistsEventsAcrossViewsAndTracksReminderLifecycle() {
        Instant start = Instant.now().plusSeconds(3_600);
        var created = service.create(new CalendarService.EventCommand("Apple system design interview",
                CalendarEventType.INTERVIEW, "Architecture and behavioral round", "Apple Hyderabad",
                "https://meet.example.com/interview", start, start.plusSeconds(3_600), 120));

        assertThat(service.list(start.minusSeconds(1), start.plusSeconds(7_200)))
                .extracting(event -> event.getId()).containsExactly(created.getId());
        assertThat(service.dueReminders(start.minusSeconds(3_600)))
                .extracting(event -> event.getId()).containsExactly(created.getId());

        service.dismissReminder(created.getId());
        assertThat(service.dueReminders(start.minusSeconds(3_600))).isEmpty();

        var updated = service.update(created.getId(), new CalendarService.EventCommand("Apple interview loop",
                CalendarEventType.INTERVIEW, null, null, null, start.plusSeconds(1_800), start.plusSeconds(7_200), 30));
        assertThat(updated.getTitle()).isEqualTo("Apple interview loop");
        assertThat(updated.getReminderAt()).isEqualTo(start);
        assertThat(updated.getReminderDismissedAt()).isNull();
    }
}
