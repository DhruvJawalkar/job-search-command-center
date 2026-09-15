package dev.dhruv.jobsearch;

import dev.dhruv.jobsearch.contact.OutreachActivity;
import dev.dhruv.jobsearch.contact.OutreachStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OutreachTimestampTest {
    private OutreachActivity outreach(OutreachStatus status) {
        return new OutreachActivity(null, null, null, null, status, "C5 test", null, null, null);
    }

    @Test void droppingAnUnsentPlanDoesNotCountAsSentOrAnswered() {
        var activity = outreach(OutreachStatus.PLANNED);
        activity.transitionTo(OutreachStatus.CLOSED, null, "Dropped", null);
        assertThat(activity.getRequestedAt()).isNull();
        assertThat(activity.getRespondedAt()).isNull();
    }

    @Test void droppingSentOutreachDoesNotInventAResponse() {
        var activity = outreach(OutreachStatus.SENT);
        var sentAt = activity.getRequestedAt();
        activity.transitionTo(OutreachStatus.CLOSED, null, "Dropped", null);
        assertThat(activity.getRequestedAt()).isEqualTo(sentAt).isNotNull();
        assertThat(activity.getRespondedAt()).isNull();
    }

    @Test void closingAnsweredOutreachPreservesItsOriginalEvidence() {
        for (var status : new OutreachStatus[]{OutreachStatus.RESPONDED, OutreachStatus.REFERRED, OutreachStatus.DECLINED}) {
            var activity = outreach(status);
            var responseAt = activity.getRespondedAt();
            activity.transitionTo(OutreachStatus.CLOSED, null, "Closed", null);
            assertThat(activity.getRespondedAt()).isEqualTo(responseAt).isNotNull();
        }
    }

    @Test void importingAClosedRecordDoesNotInventCommunication() {
        var activity = outreach(OutreachStatus.CLOSED);
        assertThat(activity.getRequestedAt()).isNull();
        assertThat(activity.getRespondedAt()).isNull();
    }
}
