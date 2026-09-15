package dev.dhruv.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.contact.OutreachService;
import dev.dhruv.jobsearch.contact.OutreachStatus;
import dev.dhruv.jobsearch.contact.OutreachType;
import dev.dhruv.jobsearch.contact.RelationshipStrength;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.opportunity.WorkMode;

@SpringBootTest
@Transactional
class OutreachFlowTest {

    @Autowired
    private OpportunityService opportunityService;

    @Autowired
    private OutreachService outreachService;

    @Test
    void linksAContactAndReferralFollowUpToAnOpportunity() {
        var opportunity = opportunityService.create(new OpportunityService.CreateOpportunity(
                "Referral Target", "Senior Platform Engineer", "Hyderabad", WorkMode.HYBRID,
                "Test", "https://referral.example/jobs/1", "Platform role", Instant.now()));
        var contact = new OutreachService.NewContact("Asha Rao", "Referral Target", "Staff Engineer",
                "https://example.com/asha", null, RelationshipStrength.FORMER_COLLEAGUE, "Worked together");

        var activity = outreachService.create(new OutreachService.CreateOutreach(opportunity.getId(), null,
                contact, OutreachType.REFERRAL_REQUEST, OutreachStatus.PLANNED, "LinkedIn",
                "Ask for role context and a referral", Instant.now().minus(1, ChronoUnit.HOURS), null));

        assertThat(activity.getContact().getFullName()).isEqualTo("Asha Rao");
        assertThat(outreachService.list(null, true)).extracting(item -> item.getId()).contains(activity.getId());

        outreachService.transition(activity.getId(), new OutreachService.UpdateOutreach(
                OutreachStatus.SENT, Instant.now().plus(3, ChronoUnit.DAYS), null, "Message sent"));
        var followedUp = outreachService.recordFollowUp(activity.getId(), new OutreachService.RecordFollowUp(
                Instant.now().plus(5, ChronoUnit.DAYS), "Shared a concise follow-up"));

        assertThat(followedUp.getFollowUpCount()).isEqualTo(1);
        assertThat(followedUp.getFollowUpAt()).isAfter(Instant.now().plus(4, ChronoUnit.DAYS));

        var responded = outreachService.transition(activity.getId(), new OutreachService.UpdateOutreach(
                OutreachStatus.RESPONDED, null, "Happy to refer", "Positive response"));

        assertThat(responded.getStatus()).isEqualTo(OutreachStatus.RESPONDED);
        assertThat(responded.getRequestedAt()).isNotNull();
        assertThat(responded.getRespondedAt()).isNotNull();
        assertThat(responded.getFollowUpCount()).isEqualTo(1);
        assertThat(responded.getOpportunity().getId()).isEqualTo(opportunity.getId());
    }
}
