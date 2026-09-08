package dev.dhruv.jobsearch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.dhruv.jobsearch.application.*;
import dev.dhruv.jobsearch.ingestion.*;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpportunityFeedBatchTest {
    @Test void feedUsesOneApplicationProjectionInsteadOfPerOpeningLookups() {
        var observations = mock(OpportunityObservationRepository.class);
        var applications = mock(JobApplicationRepository.class);
        var rows = new ArrayList<OpportunityObservation>();
        var today = LocalDate.of(2026, 9, 4);
        for (int i=0; i<500; i++) {
            var op = new JobOpportunity("C5 "+i,"Backend",null,null,null,null,null,null,null);
            var row = mock(OpportunityObservation.class);
            when(row.getOpportunity()).thenReturn(op);
            when(row.getObservedOn()).thenReturn(today);
            when(row.getSourceRank()).thenReturn(i+1);
            when(row.getRecommendation()).thenReturn("Apply");
            rows.add(row);
        }
        var link = mock(JobApplicationRepository.OpeningApplicationLink.class);
        var applicationId = UUID.randomUUID();
        var firstOpportunityId = rows.getFirst().getOpportunity().getId();
        when(link.getOpportunityId()).thenReturn(firstOpportunityId);
        when(link.getId()).thenReturn(applicationId);
        when(link.getStage()).thenReturn(ApplicationStage.APPLIED);
        when(applications.findOpeningLinks()).thenReturn(List.of(link));
        when(observations.findAllByOrderByObservedOnDescSourceRankAsc()).thenReturn(rows);
        when(observations.findObservationDates()).thenReturn(List.of(today));
        when(observations.findLatestObservationDate()).thenReturn(today);
        var feed = new OpportunityIntelligenceService(observations,applications)
                .feed(null,null,null,null,null,null,false);
        assertThat(feed.openings()).hasSize(500);
        assertThat(feed.openings().getFirst().applicationId()).isEqualTo(applicationId);
        assertThat(feed.openings().getFirst().applicationStage()).isEqualTo("APPLIED");
        assertThat(feed.openings().get(1).applicationId()).isNull();
        verify(applications,times(1)).findOpeningLinks();
        verify(applications,never()).findByOpportunityId(any());
        verify(observations,times(1)).findAllByOrderByObservedOnDescSourceRankAsc();
    }
}
